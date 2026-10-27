package studio.modroll.critfall.combat;

import java.math.BigDecimal;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import studio.modroll.critfall.api.ModifierProvider;
import studio.modroll.critfall.api.dice.RollDetail;
import studio.modroll.critfall.api.dice.RollMode;
import studio.modroll.critfall.api.feedback.CheckFeedbackPayload;
import studio.modroll.critfall.api.feedback.ConsequenceLine;
import studio.modroll.critfall.api.feedback.RollFeedbackPayload;
import studio.modroll.critfall.feedback.SaveFeedbackPayload;

/**
 * Plain-text roll feedback for the action bar: {@link #actionBar(RollFeedbackPayload)},
 * {@link #actionBar(SaveFeedbackPayload)} and {@link #actionBar(CheckFeedbackPayload)} render a resolved
 * feedback payload, used both by the
 * modless action-bar fallback ({@code FeedbackDispatcher}) and (reused later) by the client.
 */
public final class CombatText {

    private CombatText() {}

    /** Renders a resolved attack roll's feedback payload into a single action-bar {@link Component}. */
    public static Component actionBar(RollFeedbackPayload p) {
        int bonus = p.attackTotal() - p.natural();
        String roll = d20(p.roll()) + signed(bonus) + "=" + p.attackTotal() + " " + versusArmorClass(p);
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
        return withDryRun(p.dryRun(), line);
    }

    /** Renders a resolved saving throw's feedback payload into a single action-bar {@link Component}. */
    public static Component actionBar(SaveFeedbackPayload p) {
        int bonus = p.saveTotal() - p.natural();
        String roll = "save " + d20(p.roll()) + signed(bonus) + "=" + p.saveTotal() + " vs DC " + p.dc();
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
        return withDryRun(p.dryRun(), line);
    }

    /**
     * Renders a labelled save, check or hazard attack: {@code Spike trap: DEX save d20 8+2=10 vs DC 13 —
     * FAILED 2d6 = 7}, {@code Perception: check d20 14+3=17 vs DC 15 — SUCCESS}, {@code Dart trap: d20
     * 15+5=20 vs AC 14 — HIT 1d4 = 3}. A hidden outcome stops after the total: {@code Search: check d20
     * 14+3=17}.
     */
    public static Component actionBar(CheckFeedbackPayload p) {
        MutableComponent line = Component.empty();
        if (!p.label().isEmpty()) {
            line.append(Component.translatableWithFallback(p.label(), p.label()))
                    .append(": ");
        }
        String roll = d20(p.roll()) + signed(p.bonus()) + "=" + p.total();
        String dice = p.showsDamage() ? " " + p.diceNotation() + " = " + p.damage() : "";
        if (p.outcomeHidden()) {
            switch (p.kind()) {
                case SAVE -> line.append(saveName(p)).append(" " + roll);
                case CHECK -> line.append("check " + roll);
                case ATTACK -> line.append(roll);
            }
            return line;
        }
        switch (p.kind()) {
            case SAVE -> {
                line.append(saveName(p)).append(" " + roll + " vs DC " + p.dc() + " — ");
                if (!p.success()) {
                    line.append(Component.translatableWithFallback("critfall.feedback.save_failed", "FAILED"))
                            .append(dice);
                } else if (!p.showsDamage()) {
                    line.append(Component.translatableWithFallback("critfall.feedback.saved", "SAVED"));
                } else if (p.damage() > 0) {
                    line.append(Component.translatableWithFallback(
                                    "critfall.feedback.save_halved", "SAVED, half damage"))
                            .append(" " + p.damage());
                } else {
                    line.append(
                            Component.translatableWithFallback("critfall.feedback.save_negated", "SAVED, no damage"));
                }
            }
            case CHECK -> {
                line.append("check " + roll + " vs DC " + p.dc() + " — ");
                line.append(
                        p.success()
                                ? Component.translatableWithFallback("critfall.feedback.check_success", "SUCCESS")
                                : Component.translatableWithFallback("critfall.feedback.check_failed", "FAILED"));
            }
            case ATTACK -> {
                line.append(roll + " vs AC " + p.dc() + " — ");
                if (!p.success()) {
                    line.append(Component.translatableWithFallback("critfall.feedback.miss", "MISS"));
                } else {
                    line.append(
                                    p.crit()
                                            ? Component.translatableWithFallback("critfall.feedback.crit", "CRIT!")
                                            : Component.translatableWithFallback("critfall.feedback.hit", "HIT"))
                            .append(dice);
                }
            }
        }
        return line;
    }

    /**
     * {@code "save"}, or the save key's name: {@code critfall.save.<namespace>.<path>} from the lang file, else
     * Critfall's English name for its own keys, else the key itself.
     */
    private static Component saveName(CheckFeedbackPayload p) {
        if (p.saveKey().isEmpty()) {
            return Component.literal("save");
        }
        return Component.translatableWithFallback(saveNameKey(p.saveKey()), saveNameFallback(p.saveKey()));
    }

    /** {@code critfall.save.critfall.dexterity} for {@code critfall:dexterity}. */
    private static String saveNameKey(String saveKey) {
        return "critfall.save." + saveKey.replace(':', '.');
    }

    /** English names for modless clients; {@code en_us.json} must match. */
    private static String saveNameFallback(String saveKey) {
        return switch (saveKey) {
            case ModifierProvider.SPELL_SAVE -> "Spell save";
            case ModifierProvider.STRENGTH_SAVE -> "STR save";
            case ModifierProvider.DEXTERITY_SAVE -> "DEX save";
            case ModifierProvider.CONSTITUTION_SAVE -> "CON save";
            case ModifierProvider.INTELLIGENCE_SAVE -> "INT save";
            case ModifierProvider.WISDOM_SAVE -> "WIS save";
            case ModifierProvider.CHARISMA_SAVE -> "CHA save";
            default -> saveKey;
        };
    }

    private static String d20(RollDetail roll) {
        if (!roll.hasTwoDice()) {
            return "d20 " + roll.kept();
        }
        String mode = roll.mode() == RollMode.ADVANTAGE ? "adv" : "dis";
        return "d20 " + mode + " " + roll.dropped().getAsInt() + "/" + roll.kept() + " → " + roll.kept();
    }

    private static String versusArmorClass(RollFeedbackPayload p) {
        if (p.defenderAcBonus() == 0) {
            return "vs AC " + p.armorClass();
        }
        return "vs AC " + p.armorClass() + " (" + p.baseArmorClass() + signed(p.defenderAcBonus()) + ")";
    }

    private static String signed(int value) {
        return value >= 0 ? "+" + value : String.valueOf(value);
    }

    /** Prepends the dry-run marker so a calibrating dev never mistakes a shown roll for a real hit. */
    private static Component withDryRun(boolean dryRun, MutableComponent line) {
        return dryRun ? Component.literal("dry-run · ").append(line) : line;
    }

    private static void appendDamage(MutableComponent line, RollFeedbackPayload p) {
        if (p.showDamage()) {
            line.append(
                    Component.literal(" " + p.diceNotation() + " = " + p.damage() + multiplier(p.critMultiplier())));
        }
    }

    /** {@code " ×1.5"} for a carried crit multiplier, nothing for 1; at most two decimals, no trailing zeros. */
    static String multiplier(float multiplier) {
        if (multiplier == 1.0F) {
            return "";
        }
        String text = BigDecimal.valueOf(Math.round(multiplier * 100) / 100.0)
                .stripTrailingZeros()
                .toPlainString();
        return " ×" + text;
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
            case ConsequenceLine.RESISTED -> "no damage taken";
            default -> key;
        };
    }
}
