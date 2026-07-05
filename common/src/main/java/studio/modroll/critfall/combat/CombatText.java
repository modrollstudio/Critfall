package studio.modroll.critfall.combat;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import studio.modroll.critfall.feedback.ConsequenceLine;
import studio.modroll.critfall.feedback.RollFeedbackPayload;
import studio.modroll.critfall.feedback.SaveFeedbackPayload;

/**
 * Plain-text roll feedback for the action bar: {@link #actionBar(RollFeedbackPayload)} and
 * {@link #actionBar(SaveFeedbackPayload)} render a resolved feedback payload, used both by the
 * modless action-bar fallback ({@code FeedbackDispatcher}) and (reused later) by the client.
 */
public final class CombatText {

    private CombatText() {}

    /** Renders a resolved attack roll's feedback payload into a single action-bar {@link Component}. */
    public static Component actionBar(RollFeedbackPayload p) {
        int bonus = p.attackTotal() - p.natural();
        String roll = "d20 " + p.natural() + (bonus >= 0 ? "+" + bonus : String.valueOf(bonus)) + "=" + p.attackTotal()
                + " vs AC " + p.armorClass();
        MutableComponent line = Component.literal(roll + " — ");
        switch (p.outcome()) {
            case MISS ->
                line.append(
                        p.natural() == 1
                                ? Component.translatableWithFallback(
                                        "critfall.feedback.nat1_no_fumble", "NAT 1 — no fumble")
                                : Component.translatableWithFallback("critfall.feedback.miss", "MISS"));
            case FUMBLE -> line.append(Component.translatableWithFallback("critfall.feedback.fumble", "FUMBLE!"));
            case HIT -> {
                line.append(Component.translatableWithFallback("critfall.feedback.hit", "HIT"));
                appendDamage(line, p);
            }
            case CRIT -> {
                line.append(Component.translatableWithFallback("critfall.feedback.crit", "CRIT!"));
                appendDamage(line, p);
            }
        }
        for (ConsequenceLine consequence : p.consequences()) {
            line.append(" — ").append(translate(consequence));
        }
        return line;
    }

    /** Renders a resolved saving throw's feedback payload into a single action-bar {@link Component}. */
    public static Component actionBar(SaveFeedbackPayload p) {
        int bonus = p.saveTotal() - p.natural();
        String roll = "save d20 " + p.natural() + (bonus >= 0 ? "+" + bonus : String.valueOf(bonus)) + "="
                + p.saveTotal() + " vs DC " + p.dc();
        MutableComponent line = Component.literal(roll + " — ");
        if (!p.saved()) {
            line.append(Component.translatableWithFallback("critfall.feedback.save_failed", "FAILED"));
            if (p.showDamage()) {
                line.append(Component.literal(" " + p.diceNotation() + " = " + p.damage()));
            }
        } else {
            line.append(
                    p.onSuccess() == Rules.SaveOutcome.NEGATE
                            ? Component.translatableWithFallback("critfall.feedback.save_negated", "SAVED, no damage")
                            : Component.translatableWithFallback(
                                    "critfall.feedback.save_halved", "SAVED, half damage"));
            if (p.showDamage() && p.onSuccess() == Rules.SaveOutcome.HALF) {
                line.append(Component.literal(" " + p.damage()));
            }
        }
        return line;
    }

    private static void appendDamage(MutableComponent line, RollFeedbackPayload p) {
        if (p.showDamage()) {
            line.append(Component.literal(" " + p.diceNotation() + " = " + p.damage()));
        }
    }

    private static Component translate(ConsequenceLine consequence) {
        String fallback = consequenceFallback(consequence.key());
        return consequence
                .arg()
                .map(arg -> Component.translatableWithFallback(consequence.key(), fallback, arg))
                .orElseGet(() -> Component.translatableWithFallback(consequence.key(), fallback));
    }

    /**
     * English fallback shown to modless/vanilla clients that have no critfall lang loaded. Task 14's
     * {@code en_us.json} must match these strings so mod-client localization and the fallback read
     * identically. Unknown future keys degrade to the raw key.
     */
    private static String consequenceFallback(String key) {
        return switch (key) {
            case ConsequenceLine.DURABILITY_BROKEN -> "weapon nearly broken!";
            case ConsequenceLine.DURABILITY_WORN -> "weapon takes a beating";
            case ConsequenceLine.HIT_ALLY -> "you hit %s!";
            case ConsequenceLine.SELF_DAMAGE -> "you hurt yourself!";
            case ConsequenceLine.DROP_WEAPON -> "you drop your weapon!";
            case ConsequenceLine.STUMBLE -> "you stumble!";
            case ConsequenceLine.APPLY_EFFECT -> "%s!";
            case ConsequenceLine.KNOCKBACK -> "a staggering blow!";
            default -> key;
        };
    }
}
