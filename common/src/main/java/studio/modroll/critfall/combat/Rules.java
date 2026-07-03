package studio.modroll.critfall.combat;

import studio.modroll.critfall.dice.DiceExpression;

/**
 * Feature flags for every combat mechanic, loaded from {@code config/critfall/rules.json} (see
 * docs/rules-config.md). Turning any flag off must cleanly restore vanilla behavior for that
 * mechanic only — GameTests enforce this per flag.
 *
 * <p>Defaults are conservative about fumble frequency (PLAN.md §9.1): real-time combat rolls
 * 30+ attacks/minute, so raw 5% nat-1 consequences would fire constantly. Confirmation roll is ON
 * and a 10s cooldown applies out of the box.
 */
public record Rules(
        AttackRolls attackRolls,
        boolean damageDice,
        Crits crits,
        Fumbles fumbles,
        Fallbacks fallbacks,
        FeedbackVisibility feedback,
        Balance balance) {

    public static final int FORMAT_VERSION = 1;

    public record AttackRolls(boolean enabled, boolean players, boolean mobs, boolean projectiles, boolean spells) {
        public static final AttackRolls DEFAULTS = new AttackRolls(true, true, true, true, true);
    }

    /** How a critical hit computes damage. */
    public enum CritRule {
        /** Dice maximized, modifiers unchanged. */
        MAX_DICE,
        /** Dice rolled twice, modifiers added once (5e style). */
        DOUBLE_DICE,
        /** Roll once, double everything. */
        DOUBLE_TOTAL
    }

    /**
     * @param applyEffect gates the {@code critfall:apply_effect} outcome effect (status effect on
     *     the target, e.g. the nat-20 "shot in the eye" slowness)
     * @param knockback gates the {@code critfall:knockback} outcome effect
     */
    public record Crits(
            boolean enabled, CritRule rule, boolean nat20AlwaysHits, boolean applyEffect, boolean knockback) {
        public static final Crits DEFAULTS = new Crits(true, CritRule.MAX_DICE, true, true, true);
    }

    /** How a fumble damages the attacker's weapon. */
    public enum DurabilityMode {
        /** Drop remaining durability to 1 — dramatic, one fumble nearly trashes the tool. */
        SET_TO_1,
        /** Lose {@code durabilityPercent}% of max durability — repeated fumbles wear instead. */
        PERCENT_LOSS
    }

    /** Whose nat 1s can trigger fumble consequences at all. */
    public enum AppliesTo {
        PLAYERS,
        MOBS,
        PLAYERS_AND_MOBS
    }

    /**
     * Gate + defaults for the {@code critfall:hit_nearest_ally} effect: the fumbled swing lands
     * on the nearest bystander around the attacker instead. {@code canHitPlayers} and
     * {@code respectPvpRules} are server policy, never overridable from datapack tables.
     */
    public record HitNearestAlly(boolean enabled, int radius, boolean canHitPlayers, boolean respectPvpRules) {
        public static final HitNearestAlly DEFAULTS = new HitNearestAlly(true, 4, true, true);
    }

    /** Gate + default dice for the {@code critfall:self_damage} effect. */
    public record SelfDamage(boolean enabled, DiceExpression dice) {
        public static final SelfDamage DEFAULTS = new SelfDamage(false, DiceExpression.parse("1d4"));
    }

    /** Gate + default duration for the {@code critfall:stumble} effect (slowness on the attacker). */
    public record Stumble(boolean enabled, int slownessTicks) {
        public static final Stumble DEFAULTS = new Stumble(false, 40);
    }

    /**
     * @param confirmationRoll a nat 1 only triggers consequences if a second d20 rolls below
     *     {@code confirmationDc} — roughly halves real-time fumble frequency
     * @param cooldownTicks after a triggered fumble, further nat 1s are plain misses this long
     * @param dropWeapon gates the {@code critfall:drop_weapon} outcome effect
     */
    public record Fumbles(
            boolean enabled,
            boolean nat1AlwaysMisses,
            boolean confirmationRoll,
            int confirmationDc,
            int cooldownTicks,
            boolean durabilityBreak,
            DurabilityMode durabilityMode,
            int durabilityPercent,
            HitNearestAlly hitNearestAlly,
            SelfDamage selfDamage,
            boolean dropWeapon,
            Stumble stumble,
            AppliesTo appliesTo) {
        public static final Fumbles DEFAULTS = new Fumbles(
                true,
                true,
                true,
                10,
                200,
                true,
                DurabilityMode.SET_TO_1,
                25,
                HitNearestAlly.DEFAULTS,
                SelfDamage.DEFAULTS,
                false,
                Stumble.DEFAULTS,
                AppliesTo.PLAYERS_AND_MOBS);
    }

    /** What happens for entities/items no profile matches. */
    public enum FallbackMode {
        /** Derive plausible stats from vanilla attributes (PLAN.md §4.3). */
        DERIVE,
        /** Leave the damage event entirely vanilla. */
        VANILLA_PASSTHROUGH
    }

    public record Fallbacks(FallbackMode unknownEntity, FallbackMode unknownWeapon) {
        public static final Fallbacks DEFAULTS = new Fallbacks(FallbackMode.DERIVE, FallbackMode.DERIVE);
    }

    /** Who sees the roll readout (M3: action bar for the involved players; M6 widens this). */
    public enum FeedbackVisibility {
        EVERYONE,
        ATTACKER_ONLY,
        OFF
    }

    public record Balance(double globalDamageMultiplier, boolean disableVanillaArmorReduction) {
        public static final Balance DEFAULTS = new Balance(1.0, true);
    }

    public static final Rules DEFAULTS = new Rules(
            AttackRolls.DEFAULTS,
            true,
            Crits.DEFAULTS,
            Fumbles.DEFAULTS,
            Fallbacks.DEFAULTS,
            FeedbackVisibility.EVERYONE,
            Balance.DEFAULTS);
}
