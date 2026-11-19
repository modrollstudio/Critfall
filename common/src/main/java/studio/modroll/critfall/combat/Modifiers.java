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
import studio.modroll.critfall.api.ArmorClassSource;
import studio.modroll.critfall.api.AttackDelivery;
import studio.modroll.critfall.api.ModifierProvider;
import studio.modroll.critfall.api.dice.DiceExpression;
import studio.modroll.critfall.api.dice.DiceParseException;

/**
 * Applies the registered {@link ModifierProvider}'s answer when it gives one: it replaces Critfall's own
 * attack and save bonus, and adds to Critfall's own damage dice and the defender's AC. Each {@link
 * ArmorClassSource} then adds to the AC. Bad answers are logged once per provider or source and roll type, since
 * real time combat asks on every hit.
 */
public final class Modifiers {

    enum Roll {
        ATTACK,
        DAMAGE,
        SAVE,
        ARMOR_CLASS
    }

    /** Largest AC modifier magnitude accepted; the same bound as a dice constant. */
    static final int MAX_AC_MODIFIER = 1_000_000;

    /** Weak so a replaced provider or a removed source can be collected. */
    private static final Map<Object, EnumSet<Roll>> REPORTED = new WeakHashMap<>();

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
            return own.plusModifier(modifier.getAsInt());
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

    /**
     * Critfall's own AC for the defender plus the provider's modifier, when it gives one in range, then what each
     * AC source adds, in the order they were added.
     */
    public static int armorClass(Rules rules, LivingEntity defender, LivingEntity attacker, int own) {
        int armorClass = withProvidedArmorClass(rules, defender, attacker, own);
        if (!rules.modifierProviders().enabled()) {
            return armorClass;
        }
        for (ArmorClassSource source : RollRuntime.armorClassSources()) {
            armorClass += sourceAdds(source, defender, attacker, armorClass);
        }
        return armorClass;
    }

    private static int withProvidedArmorClass(Rules rules, LivingEntity defender, LivingEntity attacker, int own) {
        ModifierProvider p = provider(rules);
        OptionalInt modifier =
                p == null ? OptionalInt.empty() : ask(p, Roll.ARMOR_CLASS, () -> p.acModifier(defender, attacker));
        if (modifier.isEmpty()) {
            return own;
        }
        int value = modifier.getAsInt();
        if (value < -MAX_AC_MODIFIER || value > MAX_AC_MODIFIER) {
            reportOnce(p, Roll.ARMOR_CLASS, new IllegalArgumentException(value + " is beyond ±" + MAX_AC_MODIFIER));
            return own;
        }
        return own + value;
    }

    /** 0 for a source that throws or answers out of range. */
    private static int sourceAdds(ArmorClassSource source, LivingEntity defender, LivingEntity attacker, int soFar) {
        int value;
        try {
            value = source.add(defender, attacker, soFar);
        } catch (Throwable t) {
            reportOnce(source, Roll.ARMOR_CLASS, t);
            return 0;
        }
        if (value < -MAX_AC_MODIFIER || value > MAX_AC_MODIFIER) {
            reportOnce(
                    source, Roll.ARMOR_CLASS, new IllegalArgumentException(value + " is beyond ±" + MAX_AC_MODIFIER));
            return 0;
        }
        return value;
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

    /** {@code from} is a {@link ModifierProvider} or an {@link ArmorClassSource}. */
    private static void reportOnce(Object from, Roll roll, Throwable cause) {
        boolean first;
        synchronized (REPORTED) {
            first = REPORTED.computeIfAbsent(from, k -> EnumSet.noneOf(Roll.class))
                    .add(roll);
        }
        if (first) {
            String fallback = from instanceof ArmorClassSource ? "counting it as 0" : "using Critfall's own";
            errorLog.accept(
                    "Bad " + roll.name().toLowerCase(Locale.ROOT).replace('_', ' ') + " modifier from "
                            + from.getClass().getName() + ", " + fallback + " (logged once)",
                    cause);
        }
    }
}
