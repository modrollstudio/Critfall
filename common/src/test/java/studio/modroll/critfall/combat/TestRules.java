package studio.modroll.critfall.combat;

/** Builders for Rules variants — records have no withers and tests tweak one group at a time. */
final class TestRules {

    private TestRules() {}

    static Rules withCrits(Rules.Crits crits) {
        Rules base = Rules.DEFAULTS;
        return new Rules(
                base.attackRolls(),
                base.damageDice(),
                crits,
                base.fumbles(),
                base.fallbacks(),
                base.feedback(),
                base.balance());
    }

    static Rules withFumbles(Rules.Fumbles fumbles) {
        Rules base = Rules.DEFAULTS;
        return new Rules(
                base.attackRolls(),
                base.damageDice(),
                base.crits(),
                fumbles,
                base.fallbacks(),
                base.feedback(),
                base.balance());
    }

    static Rules.Fumbles fumbles(boolean enabled, boolean nat1AlwaysMisses, boolean confirmation, int dc) {
        return new Rules.Fumbles(
                enabled,
                nat1AlwaysMisses,
                confirmation,
                dc,
                200,
                true,
                Rules.DurabilityMode.SET_TO_1,
                25,
                Rules.HitNearestAlly.DEFAULTS,
                Rules.SelfDamage.DEFAULTS,
                false,
                Rules.Stumble.DEFAULTS,
                Rules.AppliesTo.PLAYERS_AND_MOBS);
    }
}
