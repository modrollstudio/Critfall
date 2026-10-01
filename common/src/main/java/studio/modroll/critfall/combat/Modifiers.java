package studio.modroll.critfall.combat;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.WeakHashMap;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import net.minecraft.world.entity.LivingEntity;
import studio.modroll.critfall.Critfall;
import studio.modroll.critfall.RollRuntime;
import studio.modroll.critfall.api.AttackDelivery;
import studio.modroll.critfall.api.ModifierProvider;
import studio.modroll.critfall.api.dice.DiceExpression;
import studio.modroll.critfall.api.dice.DiceParseException;

/**
 * Swaps Critfall's own bonus for the registered {@link ModifierProvider}'s answer when it gives one.
 * Bad answers are logged once per provider and roll type, since real-time combat asks on every hit.
 */
public final class Modifiers {

    enum Roll {
        ATTACK,
        DAMAGE,
        SAVE
    }

    /** Weak so a replaced provider can be collected. */
    private static final Map<ModifierProvider, EnumSet<Roll>> REPORTED = new WeakHashMap<>();

    /** Test seam. */
    static volatile BiConsumer<String, Throwable> errorLog = Critfall.LOG::error;

    private Modifiers() {}

    public static int attackBonus(
            Rules rules, LivingEntity attacker, LivingEntity target, AttackDelivery delivery, int own) {
        ModifierProvider p = provider(rules);
        return p == null
                ? own
                : ask(p, Roll.ATTACK, () -> p.attackModifier(attacker, target, delivery))
                        .orElse(own);
    }

    public static DiceExpression damageDice(
            Rules rules, LivingEntity attacker, AttackDelivery delivery, DiceExpression own) {
        ModifierProvider p = provider(rules);
        OptionalInt modifier =
                p == null ? OptionalInt.empty() : ask(p, Roll.DAMAGE, () -> p.damageModifier(attacker, delivery));
        if (modifier.isEmpty()) {
            return own;
        }
        try {
            return own.withModifier(modifier.getAsInt());
        } catch (DiceParseException e) {
            reportOnce(p, Roll.DAMAGE, e);
            return own;
        }
    }

    public static int saveBonus(Rules rules, LivingEntity entity, String saveKey, int own) {
        ModifierProvider p = provider(rules);
        return p == null
                ? own
                : ask(p, Roll.SAVE, () -> p.saveModifier(entity, saveKey)).orElse(own);
    }

    /** Null when providers are turned off or none is registered. */
    private static ModifierProvider provider(Rules rules) {
        return rules.modifierProviders().enabled()
                ? RollRuntime.modifierProvider().orElse(null)
                : null;
    }

    private static OptionalInt ask(ModifierProvider provider, Roll roll, Supplier<OptionalInt> query) {
        try {
            OptionalInt value = query.get();
            if (value != null) {
                return value;
            }
            reportOnce(provider, roll, new NullPointerException("returned null"));
        } catch (Throwable t) {
            reportOnce(provider, roll, t);
        }
        return OptionalInt.empty();
    }

    private static void reportOnce(ModifierProvider provider, Roll roll, Throwable cause) {
        boolean first;
        synchronized (REPORTED) {
            first = REPORTED.computeIfAbsent(provider, k -> EnumSet.noneOf(Roll.class))
                    .add(roll);
        }
        if (first) {
            errorLog.accept(
                    "Bad " + roll.name().toLowerCase(Locale.ROOT) + " modifier from "
                            + provider.getClass().getName() + ", using Critfall's own (logged once)",
                    cause);
        }
    }
}
