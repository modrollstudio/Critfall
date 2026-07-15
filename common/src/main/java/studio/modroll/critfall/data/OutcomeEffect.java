package studio.modroll.critfall.data;

import com.google.gson.JsonObject;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;
import studio.modroll.critfall.api.dice.DiceExpression;

/**
 * One consequence an outcome table can inflict, parsed and validated at datapack load. Effects
 * carry only what the JSON pins; absent numbers fall back to their rules.json default at execution
 * time so packs stay in charge of balance. An unknown effect type is dropped with a warning
 * (forward compatibility) — see {@code OutcomeTable.parse}.
 */
public sealed interface OutcomeEffect {

    /** No-op weight filler so a table can fire "nothing happens" most of the time. */
    record Nothing() implements OutcomeEffect {}

    /** Damages the attacker's held weapon per the rules.json {@code durability_break} mode. */
    record DamageDurability() implements OutcomeEffect {}

    /**
     * The fumbled swing lands on the nearest bystander around the attacker (excluding the original
     * target), rolling the attack's damage dice against them. Player targets respect the
     * rules.json PvP/team policy. {@code radius} overrides the rules.json default.
     */
    record HitNearestAlly(OptionalInt radius) implements OutcomeEffect {}

    /** The attacker takes {@code dice} damage; absent dice use the rules.json default. */
    record SelfDamage(Optional<DiceExpression> dice) implements OutcomeEffect {}

    /** The attacker drops their main-hand item on the ground. */
    record DropWeapon() implements OutcomeEffect {}

    /** Slowness on the attacker; absent ticks use the rules.json default. */
    record Stumble(OptionalInt slownessTicks) implements OutcomeEffect {}

    /** A status effect on the target — e.g. the nat-20 "shot in the eye" slowness. */
    record ApplyEffect(ResourceLocation effect, int ticks, int amplifier) implements OutcomeEffect {}

    /** Extra knockback on the target, in vanilla knockback-enchantment levels. */
    record Knockback(double strength) implements OutcomeEffect {}

    /**
     * Parses the effect named by {@code type} from its parameter object, or empty (after a
     * warning) for a type this version does not know. Bad parameters of a KNOWN type throw — the
     * author must fix them.
     */
    static Optional<OutcomeEffect> parse(
            ResourceLocation type, JsonObject params, String context, Consumer<String> warn) {
        if (!type.getNamespace().equals("critfall")) {
            warn.accept(context + ": unknown effect type \"" + type + "\" (skipped)");
            return Optional.empty();
        }
        LenientJson j = new LenientJson(params, context + " effect " + type.getPath(), warn);
        Optional<OutcomeEffect> effect =
                switch (type.getPath()) {
                    case "nothing" -> Optional.of(new Nothing());
                    case "damage_durability" -> Optional.of(new DamageDurability());
                    case "hit_nearest_ally" -> {
                        OptionalInt radius = j.optionalInt("radius");
                        if (radius.isPresent() && (radius.getAsInt() < 1 || radius.getAsInt() > 64)) {
                            throw new IllegalArgumentException("hit_nearest_ally 'radius' must be 1..64");
                        }
                        yield Optional.of(new HitNearestAlly(radius));
                    }
                    case "self_damage" ->
                        Optional.of(new SelfDamage(j.optionalString("dice").map(DiceExpression::parse)));
                    case "drop_weapon" -> Optional.of(new DropWeapon());
                    case "stumble" -> {
                        OptionalInt ticks = j.optionalInt("slowness_ticks");
                        if (ticks.isPresent() && ticks.getAsInt() < 1) {
                            throw new IllegalArgumentException("stumble 'slowness_ticks' must be at least 1");
                        }
                        yield Optional.of(new Stumble(ticks));
                    }
                    case "apply_effect" -> {
                        String effectId = j.optionalString("effect")
                                .orElseThrow(() -> new IllegalArgumentException(
                                        "apply_effect needs an 'effect' id (e.g. \"minecraft:slowness\")"));
                        ResourceLocation id = ResourceLocation.tryParse(effectId);
                        if (id == null) {
                            throw new IllegalArgumentException("invalid effect id \"" + effectId + "\"");
                        }
                        int ticks = j.getInt("ticks", -1);
                        if (ticks < 1) {
                            throw new IllegalArgumentException("apply_effect needs 'ticks' of at least 1");
                        }
                        int amplifier = j.getInt("amplifier", 0);
                        if (amplifier < 0 || amplifier > 255) {
                            throw new IllegalArgumentException("apply_effect 'amplifier' must be 0..255");
                        }
                        yield Optional.of(new ApplyEffect(id, ticks, amplifier));
                    }
                    case "knockback" -> {
                        OptionalDouble strength = optionalDouble(j, "strength");
                        double value = strength.orElse(1.0);
                        if (value <= 0 || value > 10) {
                            throw new IllegalArgumentException("knockback 'strength' must be in (0, 10]");
                        }
                        yield Optional.of(new Knockback(value));
                    }
                    default -> {
                        warn.accept(context + ": unknown effect type \"" + type + "\" (skipped)");
                        yield Optional.empty();
                    }
                };
        if (effect.isPresent()) {
            j.finish();
        }
        return effect;
    }

    private static OptionalDouble optionalDouble(LenientJson j, String key) {
        return j.has(key) ? OptionalDouble.of(j.getDouble(key, 0)) : OptionalDouble.empty();
    }
}
