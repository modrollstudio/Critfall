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
import net.minecraft.world.item.ItemStack;
import studio.modroll.critfall.RollService;
import studio.modroll.critfall.combat.AttackDice;
import studio.modroll.critfall.combat.Derivation;
import studio.modroll.critfall.combat.Rules;
import studio.modroll.critfall.data.EntityProfile;
import studio.modroll.critfall.data.ItemProfile;
import studio.modroll.critfall.data.ProfileLookup;
import studio.modroll.critfall.data.ProfileStore;

/**
 * Debug commands for pack devs (PLAN.md §8.2): {@code /critfall inspect <entity>} shows the
 * effective combat stats and which profile file won; {@code /critfall check [item]} does the
 * same for the held (or named) item. Both are read-only and require permission level 2.
 */
public final class CritfallCommands {

    private CritfallCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(Commands.literal("critfall")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("inspect")
                        .then(Commands.argument("target", EntityArgument.entity())
                                .executes(ctx -> inspect(ctx.getSource(), EntityArgument.getEntity(ctx, "target")))))
                .then(Commands.literal("check")
                        .executes(ctx -> checkHeld(ctx.getSource()))
                        .then(Commands.argument("item", ItemArgument.item(context))
                                .executes(ctx -> check(
                                        ctx.getSource(),
                                        ItemArgument.getItem(ctx, "item").createItemStack(1, false))))));
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
            Rules.FallbackMode mode = RollService.rules().fallbacks().unknownEntity();
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

        profile.filter(p -> !p.damageModifiers().isEmpty()).ifPresent(p -> {
            send(
                    source,
                    "  Damage modifiers: resist " + describe(p.damageModifiers().resist())
                            + ", immune " + describe(p.damageModifiers().immune())
                            + ", vulnerable " + describe(p.damageModifiers().vulnerable()));
        });
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
                            + (RollService.rules().fallbacks().unknownWeapon() == Rules.FallbackMode.DERIVE
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
