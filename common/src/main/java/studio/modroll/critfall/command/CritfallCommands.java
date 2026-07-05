package studio.modroll.critfall.command;

import com.mojang.brigadier.CommandDispatcher;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import studio.modroll.critfall.RollRuntime;
import studio.modroll.critfall.combat.AttackDice;
import studio.modroll.critfall.combat.Derivation;
import studio.modroll.critfall.combat.Rules;
import studio.modroll.critfall.data.EntityProfile;
import studio.modroll.critfall.data.ItemProfile;
import studio.modroll.critfall.data.ProfileLookup;
import studio.modroll.critfall.data.ProfileStore;
import studio.modroll.critfall.tools.CoverageReport;
import studio.modroll.critfall.tools.DatapackGenerator;

/**
 * Debug commands for pack devs (PLAN.md §8.2): {@code /critfall inspect [entity]} shows the
 * effective combat stats and which profile file won (no argument = whatever the caller's
 * crosshair points at); {@code /critfall check [item]} does the same for the held (or named)
 * item. Both are read-only and require permission level 2.
 */
public final class CritfallCommands {

    /** How far {@code /critfall inspect} with no argument looks for an entity, in blocks. */
    private static final double INSPECT_RANGE = 32.0;

    private CritfallCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(Commands.literal("critfall")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("inspect")
                        .executes(ctx -> inspectLookedAt(ctx.getSource()))
                        .then(Commands.argument("target", EntityArgument.entity())
                                .executes(ctx -> inspect(ctx.getSource(), EntityArgument.getEntity(ctx, "target")))))
                .then(Commands.literal("check")
                        .executes(ctx -> checkHeld(ctx.getSource()))
                        .then(Commands.argument("item", ItemArgument.item(context))
                                .executes(ctx -> check(
                                        ctx.getSource(),
                                        ItemArgument.getItem(ctx, "item").createItemStack(1, false)))))
                .then(Commands.literal("generate")
                        .executes(ctx -> generate(ctx.getSource(), false, false))
                        .then(Commands.literal("confirm").executes(ctx -> generate(ctx.getSource(), false, true)))
                        .then(Commands.literal("missing")
                                .executes(ctx -> generate(ctx.getSource(), true, false))
                                .then(Commands.literal("confirm")
                                        .executes(ctx -> generate(ctx.getSource(), true, true)))))
                .then(Commands.literal("report").executes(ctx -> report(ctx.getSource()))));
    }

    /** Raycasts along the caller's view (blocks occlude) and inspects the first living entity hit. */
    private static int inspectLookedAt(CommandSourceStack source) {
        Entity caller = source.getEntity();
        if (caller == null) {
            source.sendFailure(
                    Component.literal("Only an entity can look at something — use /critfall inspect <entity>"));
            return 0;
        }
        Vec3 eye = caller.getEyePosition();
        Vec3 view = caller.getViewVector(1.0F);
        Vec3 reach = eye.add(view.scale(INSPECT_RANGE));
        // Stop at the first block so an entity behind a wall isn't inspected through it.
        HitResult blockHit = caller.level()
                .clip(new ClipContext(eye, reach, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caller));
        Vec3 end = blockHit.getType() == HitResult.Type.MISS ? reach : blockHit.getLocation();
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(
                caller,
                eye,
                end,
                caller.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0),
                entity -> entity instanceof LivingEntity && entity.isPickable(),
                eye.distanceToSqr(end));
        if (hit == null) {
            source.sendFailure(Component.literal("Not looking at a living entity (within " + (int) INSPECT_RANGE
                    + " blocks) — aim at one or use /critfall inspect <entity>"));
            return 0;
        }
        return inspect(source, hit.getEntity());
    }

    private static int inspect(CommandSourceStack source, Entity entity) {
        if (!(entity instanceof LivingEntity living)) {
            source.sendFailure(Component.literal("Critfall only rolls for living entities"));
            return 0;
        }
        ResourceLocation typeId = BuiltInRegistries.ENTITY_TYPE.getKey(living.getType());
        Optional<EntityProfile> profile = ProfileLookup.forEntity(living);

        send(source, "Critfall inspect: " + living.getName().getString() + " (" + typeId + ")");
        if (profile.isPresent()) {
            send(
                    source,
                    "  Profile: " + profile.get().id() + " (priority "
                            + profile.get().priority() + ")");
        } else {
            Rules.FallbackMode mode = RollRuntime.rules().fallbacks().unknownEntity();
            send(
                    source,
                    "  Profile: none matched — "
                            + (mode == Rules.FallbackMode.DERIVE
                                    ? "stats derived from attributes"
                                    : "unknown_entity=vanilla_passthrough, attacks on it stay vanilla"));
        }

        OptionalInt profileAc = profile.map(EntityProfile::armorClass).orElse(OptionalInt.empty());
        double armor = attribute(living, Attributes.ARMOR);
        double toughness = attribute(living, Attributes.ARMOR_TOUGHNESS);
        send(
                source,
                "  Armor Class: "
                        + (profileAc.isPresent()
                                ? profileAc.getAsInt() + " (profile)"
                                : Derivation.armorClass(armor, toughness) + " (derived: armor " + armor + ", toughness "
                                        + toughness + ")"));

        OptionalInt profileBonus = profile.map(EntityProfile::attackBonus).orElse(OptionalInt.empty());
        double attackDamage = attribute(living, Attributes.ATTACK_DAMAGE);
        send(
                source,
                "  Attack bonus: "
                        + (profileBonus.isPresent()
                                ? "+" + profileBonus.getAsInt() + " (profile)"
                                : "+" + Derivation.attackBonus(attackDamage) + " (derived: attack damage "
                                        + attackDamage + ")"));

        Optional<ItemProfile> heldProfile = ProfileLookup.forItem(living.getMainHandItem());
        Optional<AttackDice.Resolved> resolved = AttackDice.resolve(heldProfile, profile, attackDamage);
        if (resolved.isPresent()) {
            String diceSource = heldProfile.flatMap(ItemProfile::damage).isPresent()
                    ? "item profile " + heldProfile.get().id()
                    : "entity profile";
            send(source, "  Melee damage: " + resolved.get().dice() + " (" + diceSource + ")");
            send(source, "  Crit range: " + resolved.get().critRange());
        } else {
            send(source, "  Melee damage: derived per-hit from the vanilla damage amount");
            send(source, "  Crit range: " + AttackDice.entityCritRange(profile));
        }

        profile.flatMap(EntityProfile::rangedDamage)
                .ifPresent(dice -> send(source, "  Ranged damage: " + dice + " (entity profile)"));
        profile.map(EntityProfile::saveBonus)
                .orElse(OptionalInt.empty())
                .ifPresent(bonus -> send(source, "  Save bonus: " + (bonus >= 0 ? "+" : "") + bonus + " (profile)"));
        profile.filter(p -> !p.damageModifiers().isEmpty()).ifPresent(p -> {
            send(
                    source,
                    "  Damage modifiers: resist " + describe(p.damageModifiers().resist())
                            + ", immune " + describe(p.damageModifiers().immune())
                            + ", vulnerable " + describe(p.damageModifiers().vulnerable()));
        });
        profile.flatMap(EntityProfile::fumbleTable)
                .ifPresent(table -> send(source, "  Fumble table: " + table + tableStatus(table)));
        profile.flatMap(EntityProfile::critTable)
                .ifPresent(table -> send(source, "  Crit table: " + table + tableStatus(table)));
        return 1;
    }

    private static int checkHeld(CommandSourceStack source) {
        if (!(source.getEntity() instanceof Player player)
                || player.getMainHandItem().isEmpty()) {
            source.sendFailure(Component.literal("Hold an item in your main hand or use /critfall check <item>"));
            return 0;
        }
        return check(source, player.getMainHandItem());
    }

    private static int check(CommandSourceStack source, ItemStack stack) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        Optional<ItemProfile> profile = ProfileLookup.forItem(stack);

        send(source, "Critfall check: " + stack.getHoverName().getString() + " (" + itemId + ")");
        if (profile.isEmpty()) {
            send(
                    source,
                    "  Profile: none matched — "
                            + (RollRuntime.rules().fallbacks().unknownWeapon() == Rules.FallbackMode.DERIVE
                                    ? "damage dice derived per-hit (or the attacker's entity profile applies)"
                                    : "unknown_weapon=vanilla_passthrough, attacks with it stay vanilla"));
            return 1;
        }
        ItemProfile p = profile.get();
        send(source, "  Profile: " + p.id() + " (priority " + p.priority() + ")");
        send(
                source,
                "  Damage dice: "
                        + p.damage().map(Object::toString).orElse("none (falls back to attacker profile/derivation)"));
        send(
                source,
                "  Modifier from: "
                        + (p.modifierFrom() == ItemProfile.ModifierFrom.ATTACK_DAMAGE_ATTRIBUTE
                                ? "attack_damage_attribute (flat bonus computed per attacker at hit time)"
                                : "none"));
        send(
                source,
                "  Crit range: " + p.critRange().orElse(20)
                        + (p.critRange().isPresent() ? " (profile)" : " (default)"));
        p.fumbleTable().ifPresent(table -> send(source, "  Fumble table: " + table + tableStatus(table)));
        p.critTable().ifPresent(table -> send(source, "  Crit table: " + table + tableStatus(table)));
        if (!p.properties().isEmpty()) {
            send(source, "  Properties: " + String.join(", ", p.properties()));
        }
        return 1;
    }

    /**
     * Scans every living entity type and weapon-like item, derives a profile for each, and writes an
     * editable datapack (PLAN §8.2.1). Writes into the world's {@code datapacks/} folder so a
     * following {@code /reload} loads it. Overwriting an existing generated pack needs {@code confirm}.
     */
    private static int generate(CommandSourceStack source, boolean onlyMissing, boolean confirmed) {
        Path root = source.getServer().getWorldPath(LevelResource.DATAPACK_DIR).resolve(DatapackGenerator.NAMESPACE);
        if (Files.exists(root) && !confirmed) {
            source.sendFailure(Component.literal(DatapackGenerator.NAMESPACE
                    + " already exists — run /critfall generate" + (onlyMissing ? " missing" : "")
                    + " confirm to overwrite it"));
            return 0;
        }

        List<DatapackGenerator.EntityStat> entities = new ArrayList<>();
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            AttributeSupplier attrs = livingSupplier(type);
            if (attrs == null) {
                continue; // not a living entity — nothing to roll for
            }
            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            if (onlyMissing && entityProfiled(type, id)) {
                continue;
            }
            entities.add(new DatapackGenerator.EntityStat(
                    id,
                    supplierValue(attrs, Attributes.ARMOR),
                    supplierValue(attrs, Attributes.ARMOR_TOUGHNESS),
                    supplierValue(attrs, Attributes.ATTACK_DAMAGE)));
        }

        List<DatapackGenerator.ItemStat> items = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            double attackDamage = mainhandAttackDamage(item);
            if (attackDamage <= 0) {
                continue; // not weapon-like
            }
            if (onlyMissing && ProfileLookup.forItem(new ItemStack(item)).isPresent()) {
                continue;
            }
            items.add(new DatapackGenerator.ItemStat(BuiltInRegistries.ITEM.getKey(item), attackDamage));
        }

        Map<String, String> files = DatapackGenerator.generate(entities, items);
        try {
            writePack(root, files);
        } catch (IOException e) {
            source.sendFailure(Component.literal("Failed to write datapack: " + e.getMessage()));
            return 0;
        }
        int entityCount = entities.size();
        int itemCount = items.size();
        source.sendSuccess(
                () -> Component.literal("Generated " + entityCount + " entity profiles and " + itemCount
                        + " item profiles to " + root + ". Run /reload to load them."),
                true);
        return 1;
    }

    private static boolean entityProfiled(EntityType<?> type, ResourceLocation id) {
        return entityProfile(type, id).isPresent();
    }

    /** The profile matching this entity TYPE (id + tag matching against the type's holder tags). */
    private static Optional<EntityProfile> entityProfile(EntityType<?> type, ResourceLocation id) {
        return ProfileStore.findEntityProfile(
                id,
                tag -> BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(type).is(TagKey.create(Registries.ENTITY_TYPE, tag)));
    }

    /** The attribute supplier for a living entity type, or {@code null} for non-living types. */
    @SuppressWarnings("unchecked")
    private static AttributeSupplier livingSupplier(EntityType<?> type) {
        return DefaultAttributes.hasSupplier(type)
                ? DefaultAttributes.getSupplier((EntityType<? extends LivingEntity>) type)
                : null;
    }

    private static double supplierValue(AttributeSupplier attrs, Holder<Attribute> attribute) {
        return attrs.hasAttribute(attribute) ? attrs.getValue(attribute) : 0.0;
    }

    /** The main-hand base attack damage an item grants (0 if it is not weapon-like). */
    private static double mainhandAttackDamage(Item item) {
        ItemAttributeModifiers mods =
                new ItemStack(item).getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        double sum = 0.0;
        for (ItemAttributeModifiers.Entry entry : mods.modifiers()) {
            if (entry.attribute().equals(Attributes.ATTACK_DAMAGE)
                    && entry.modifier().operation() == AttributeModifier.Operation.ADD_VALUE) {
                sum += entry.modifier().amount();
            }
        }
        return sum;
    }

    private static void writePack(Path root, Map<String, String> files) throws IOException {
        // Clear only the two profile dirs this command owns; leave any hand-edits elsewhere alone.
        deleteDir(root.resolve("data/" + DatapackGenerator.NAMESPACE + "/critfall/entity_profile"));
        deleteDir(root.resolve("data/" + DatapackGenerator.NAMESPACE + "/critfall/item_profile"));
        Files.createDirectories(root);
        Files.writeString(root.resolve("pack.mcmeta"), DatapackGenerator.packMcmeta());
        for (Map.Entry<String, String> file : files.entrySet()) {
            Path out = root.resolve(file.getKey());
            Files.createDirectories(out.getParent());
            Files.writeString(out, file.getValue());
        }
    }

    private static void deleteDir(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException ignored) {
                    // best-effort cleanup; a leftover file just gets overwritten below
                }
            });
        }
    }

    /**
     * Exports a coverage report (PLAN §8.2.4): every living entity and weapon-like item, whether an
     * explicit profile or the fallback drives it, plus the effective values. CSV + JSON, into a
     * {@code critfall-reports/} folder in the game/server directory.
     */
    private static int report(CommandSourceStack source) {
        List<CoverageReport.EntityRow> entities = collectEntityRows();
        List<CoverageReport.ItemRow> items = collectItemRows();
        Path dir = source.getServer().getServerDirectory().resolve("critfall-reports");
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        try {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("entities-" + stamp + ".csv"), CoverageReport.entitiesCsv(entities));
            Files.writeString(dir.resolve("entities-" + stamp + ".json"), CoverageReport.entitiesJson(entities));
            Files.writeString(dir.resolve("items-" + stamp + ".csv"), CoverageReport.itemsCsv(items));
            Files.writeString(dir.resolve("items-" + stamp + ".json"), CoverageReport.itemsJson(items));
        } catch (IOException e) {
            source.sendFailure(Component.literal("Failed to write report: " + e.getMessage()));
            return 0;
        }
        long entProfiled =
                entities.stream().filter(r -> !r.source().equals("fallback")).count();
        long itemProfiled =
                items.stream().filter(r -> !r.source().equals("fallback")).count();
        source.sendSuccess(
                () -> Component.literal(entities.size() + " entities: " + entProfiled + " profiled, "
                        + (entities.size() - entProfiled) + " fallback · " + items.size() + " items: " + itemProfiled
                        + " profiled, " + (items.size() - itemProfiled) + " fallback. Wrote " + dir),
                true);
        return 1;
    }

    /** Effective coverage rows for every living entity type — public so a GameTest can assert on them. */
    public static List<CoverageReport.EntityRow> collectEntityRows() {
        List<CoverageReport.EntityRow> rows = new ArrayList<>();
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            AttributeSupplier attrs = livingSupplier(type);
            if (attrs == null) {
                continue;
            }
            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            Optional<EntityProfile> profile = entityProfile(type, id);
            String source = profile.map(p -> "profile:" + p.id()).orElse("fallback");
            double armor = supplierValue(attrs, Attributes.ARMOR);
            double tough = supplierValue(attrs, Attributes.ARMOR_TOUGHNESS);
            double atk = supplierValue(attrs, Attributes.ATTACK_DAMAGE);
            int ac = profile.map(EntityProfile::armorClass)
                    .filter(OptionalInt::isPresent)
                    .map(OptionalInt::getAsInt)
                    .orElse(Derivation.armorClass(armor, tough));
            int bonus = profile.map(EntityProfile::attackBonus)
                    .filter(OptionalInt::isPresent)
                    .map(OptionalInt::getAsInt)
                    .orElse(Derivation.attackBonus(atk));
            Optional<AttackDice.Resolved> resolved = AttackDice.resolve(Optional.empty(), profile, atk);
            String melee = resolved.map(r -> r.dice().toString())
                    .orElse(Derivation.damageDice(atk).toString());
            int critRange = resolved.map(AttackDice.Resolved::critRange).orElse(AttackDice.entityCritRange(profile));
            String ranged = profile.flatMap(EntityProfile::rangedDamage)
                    .map(Object::toString)
                    .orElse("-");
            String save = profile.map(EntityProfile::saveBonus)
                    .filter(OptionalInt::isPresent)
                    .map(o -> String.valueOf(o.getAsInt()))
                    .orElse("-");
            rows.add(new CoverageReport.EntityRow(id.toString(), source, ac, bonus, melee, critRange, ranged, save));
        }
        return rows;
    }

    private static List<CoverageReport.ItemRow> collectItemRows() {
        List<CoverageReport.ItemRow> rows = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            double atk = mainhandAttackDamage(item);
            if (atk <= 0) {
                continue;
            }
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            Optional<ItemProfile> profile = ProfileLookup.forItem(new ItemStack(item));
            String source = profile.map(p -> "profile:" + p.id()).orElse("fallback");
            String dice = profile.flatMap(ItemProfile::damage)
                    .map(Object::toString)
                    .orElse(Derivation.damageDice(atk).toString());
            String modifierFrom = profile.map(p -> p.modifierFrom() == ItemProfile.ModifierFrom.ATTACK_DAMAGE_ATTRIBUTE
                            ? "attack_damage_attribute"
                            : "none")
                    .orElse("attack_damage_attribute");
            int critRange = profile.map(p -> p.critRange().orElse(20)).orElse(20);
            rows.add(new CoverageReport.ItemRow(id.toString(), source, dice, modifierFrom, critRange));
        }
        return rows;
    }

    private static String tableStatus(ResourceLocation table) {
        return ProfileStore.outcomeTable(table).isPresent() ? " (loaded)" : " (MISSING — not in any datapack!)";
    }

    private static String describe(List<?> entries) {
        return entries.isEmpty() ? "-" : entries.toString();
    }

    private static double attribute(LivingEntity entity, Holder<Attribute> attribute) {
        return entity.getAttributes().hasAttribute(attribute) ? entity.getAttributeValue(attribute) : 0.0;
    }

    private static void send(CommandSourceStack source, String line) {
        source.sendSuccess(() -> Component.literal(line), false);
    }
}
