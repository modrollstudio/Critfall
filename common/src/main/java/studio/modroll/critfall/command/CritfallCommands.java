package studio.modroll.critfall.command;

import com.mojang.brigadier.CommandDispatcher;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
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
                                        ItemArgument.getItem(ctx, "item").createItemStack(1, false))))));
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
