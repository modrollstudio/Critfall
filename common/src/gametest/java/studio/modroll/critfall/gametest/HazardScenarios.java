package studio.modroll.critfall.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Pig;
import studio.modroll.critfall.api.HazardAttack;
import studio.modroll.critfall.api.HazardSave;
import studio.modroll.critfall.api.ModifierProvider;
import studio.modroll.critfall.api.RollService;
import studio.modroll.critfall.api.combat.AttackOutcome;
import studio.modroll.critfall.api.combat.AttackResult;
import studio.modroll.critfall.api.combat.HazardSaveResult;
import studio.modroll.critfall.api.combat.SuccessDamage;
import studio.modroll.critfall.api.dice.DiceExpression;
import studio.modroll.critfall.api.dice.RollMode;
import studio.modroll.critfall.api.event.CritfallEvents;
import studio.modroll.critfall.api.event.HazardRollEvent;
import studio.modroll.critfall.api.feedback.CheckFeedbackPayload;
import studio.modroll.critfall.combat.CombatText;
import studio.modroll.critfall.combat.Rules;
import studio.modroll.critfall.feedback.CapturingFeedbackSink;
import studio.modroll.critfall.feedback.FeedbackSink;

/**
 * Hazards: damage with no living attacker, rolled through {@link RollService}. The target is a pig: 10
 * health, AC 10, save +0.
 */
public final class HazardScenarios {

    private static final DiceExpression TWO_D6 = DiceExpression.parse("2d6");
    private static final DiceExpression ONE_D4 = DiceExpression.parse("1d4");
    private static final ResourceLocation CACTUS_PROOF = ResourceLocation.parse("critfall_test:cactus_proof");

    private HazardScenarios() {}

    /** Answers saves the way Checks does: a save key maps to an ability, whose save modifier it returns. */
    private static final class ChecksStyleProvider implements ModifierProvider {
        private final Map<String, String> saveAbilities = Map.of(ModifierProvider.DEXTERITY_SAVE, "dex");
        private final Map<String, Integer> saveModifiers = Map.of("dex", 4, "con", -1);
        private final Integer armorClass;
        final List<String> saveKeys = new ArrayList<>();
        final List<LivingEntity> acAttackers = new ArrayList<>();

        ChecksStyleProvider(Integer armorClass) {
            this.armorClass = armorClass;
        }

        @Override
        public OptionalInt saveModifier(LivingEntity entity, String saveKey) {
            saveKeys.add(saveKey);
            String ability = saveAbilities.get(saveKey);
            return ability == null ? OptionalInt.empty() : OptionalInt.of(saveModifiers.get(ability));
        }

        @Override
        public OptionalInt acModifier(LivingEntity defender, LivingEntity attacker) {
            acAttackers.add(attacker);
            return armorClass == null ? OptionalInt.empty() : OptionalInt.of(armorClass);
        }
    }

    private static HazardSave spikes() {
        return HazardSave.of("Spike trap", DamageTypes.CACTUS, ModifierProvider.DEXTERITY_SAVE, 13, TWO_D6);
    }

    private static HazardAttack darts() {
        return HazardAttack.of("Dart trap", DamageTypes.ARROW, 5, ONE_D4);
    }

    public static void failedSaveTakesTheRolledDamage(GameTestHelper helper) {
        Pig pig = spawnPig(helper);
        run(
                helper,
                null,
                sink -> {
                    // d20 5 + 0 = 5 vs DC 13 fails; 2d6 rolls 3 + 4 = 7.
                    HazardSaveResult result = RollService.performHazardSave(pig, spikes());
                    if (result.saved() || result.damage() != 7) {
                        helper.fail("expected a failed save taking 7, got " + result);
                    }
                    expectHealth(helper, pig, pig.getMaxHealth() - 7);
                    expectReadout(helper, sink, pig, "Spike trap: save d20 5+0=5 vs DC 13 — FAILED 2d6 = 7");
                },
                5,
                3,
                4);
        helper.succeed();
    }

    public static void successfulSaveTakesHalf(GameTestHelper helper) {
        Pig pig = spawnPig(helper);
        run(
                helper,
                null,
                sink -> {
                    // d20 15 saves; 2d6 rolls 3 + 4 = 7, halved to 3.
                    HazardSaveResult result = RollService.performHazardSave(pig, spikes());
                    if (!result.saved() || result.damage() != 3) {
                        helper.fail("expected a save taking half (3), got " + result);
                    }
                    expectHealth(helper, pig, pig.getMaxHealth() - 3);
                    expectReadout(helper, sink, pig, "save d20 15+0=15 vs DC 13 — SAVED, half damage 3");
                },
                15,
                3,
                4);
        helper.succeed();
    }

    public static void successfulSaveCanTakeNone(GameTestHelper helper) {
        Pig pig = spawnPig(helper);
        run(
                helper,
                null,
                sink -> {
                    // d20 15 saves, and no damage dice are rolled at all.
                    HazardSaveResult result =
                            RollService.performHazardSave(pig, spikes().withOnSuccess(SuccessDamage.NONE));
                    if (!result.saved() || result.damage() != 0) {
                        helper.fail("expected a save taking nothing, got " + result);
                    }
                    expectHealth(helper, pig, pig.getMaxHealth());
                    expectReadout(helper, sink, pig, "SAVED, no damage");
                },
                15);
        helper.succeed();
    }

    /** The save modifier comes from the provider for the hazard's ability, as Checks supplies it. */
    public static void checksStyleProviderModifiesTheSave(GameTestHelper helper) {
        Pig pig = spawnPig(helper);
        ChecksStyleProvider provider = new ChecksStyleProvider(null);
        run(
                helper,
                provider,
                sink -> {
                    // DEX save: d20 10 + 4 = 14 vs DC 13 saves (it would fail at +0); 2d6 rolls 2 + 2, halved to 2.
                    HazardSaveResult dex = RollService.performHazardSave(pig, spikes());
                    if (!dex.saved() || dex.save().saveBonus() != 4 || dex.damage() != 2) {
                        helper.fail("expected the provider's +4 to save the DEX save, got " + dex);
                    }
                    expectReadout(helper, sink, pig, "Spike trap: save d20 10+4=14 vs DC 13 — SAVED, half damage 2");
                    // An ability the provider does not map keeps Critfall's own save bonus (+0): 10 fails.
                    HazardSave gas = HazardSave.of(
                            "Gas vent", DamageTypes.MAGIC, ModifierProvider.CONSTITUTION_SAVE, 13, TWO_D6);
                    HazardSaveResult con = RollService.performHazardSave(pig, gas);
                    if (con.saved() || con.save().saveBonus() != 0 || con.damage() != 2) {
                        helper.fail("an unmapped save key must keep the pig's +0, got " + con);
                    }
                    expectHealth(helper, pig, pig.getMaxHealth() - 4);
                    if (!provider.saveKeys.equals(
                            List.of(ModifierProvider.DEXTERITY_SAVE, ModifierProvider.CONSTITUTION_SAVE))) {
                        helper.fail("the provider must be asked for each hazard's save key, was " + provider.saveKeys);
                    }
                },
                10,
                2,
                2,
                10,
                1,
                1);
        helper.succeed();
    }

    public static void saveWithDisadvantageKeepsTheLowerDie(GameTestHelper helper) {
        Pig pig = spawnPig(helper);
        run(
                helper,
                null,
                sink -> {
                    // 18 and 4 under disadvantage keep 4: fails. 2d6 rolls 1 + 1.
                    HazardSaveResult result =
                            RollService.performHazardSave(pig, spikes().withMode(RollMode.DISADVANTAGE));
                    if (result.saved()
                            || result.save().natural() != 4
                            || result.save().roll().mode() != RollMode.DISADVANTAGE
                            || result.save().roll().dropped().orElse(0) != 18) {
                        helper.fail("expected disadvantage to keep 4 and drop 18, got " + result);
                    }
                    expectReadout(helper, sink, pig, "save d20 dis 18/4 → 4+0=4 vs DC 13 — FAILED 2d6 = 2");
                },
                18,
                4,
                1,
                1);
        helper.succeed();
    }

    public static void hazardAttackHitAppliesTheDice(GameTestHelper helper) {
        Pig pig = spawnPig(helper);
        run(
                helper,
                null,
                sink -> {
                    // d20 10 + 5 = 15 vs AC 10 hits; 1d4 rolls 3.
                    AttackResult result = RollService.performHazardAttack(pig, darts());
                    if (result.outcome() != AttackOutcome.HIT || result.damage() != 3) {
                        helper.fail("expected a HIT for 3, got " + result);
                    }
                    expectHealth(helper, pig, pig.getMaxHealth() - 3);
                    expectReadout(helper, sink, pig, "Dart trap: d20 10+5=15 vs AC 10 — HIT 1d4 = 3");
                },
                10,
                3);
        helper.succeed();
    }

    /** A natural 1 misses with no fumble confirmation roll: the scripted roller would run dry. */
    public static void hazardAttackNaturalOneJustMisses(GameTestHelper helper) {
        Pig pig = spawnPig(helper);
        run(
                helper,
                null,
                sink -> {
                    AttackResult result = RollService.performHazardAttack(pig, darts());
                    if (result.outcome() != AttackOutcome.MISS) {
                        helper.fail("a hazard's natural 1 must be a plain miss, got " + result.outcome());
                    }
                    expectHealth(helper, pig, pig.getMaxHealth());
                    expectReadout(helper, sink, pig, "Dart trap: d20 1+5=6 vs AC 10 — MISS");
                },
                1);
        helper.succeed();
    }

    /** The default crit rule maximises the dice without rolling them. */
    public static void hazardAttackCritUsesTheCritRule(GameTestHelper helper) {
        Pig pig = spawnPig(helper);
        run(
                helper,
                null,
                sink -> {
                    AttackResult result = RollService.performHazardAttack(pig, darts());
                    if (result.outcome() != AttackOutcome.CRIT || result.damage() != 4) {
                        helper.fail("expected a CRIT for the maximised 4, got " + result);
                    }
                    expectReadout(helper, sink, pig, "CRIT! 1d4 = 4");
                },
                20);
        helper.succeed();
    }

    public static void hazardAttackWithAdvantageKeepsTheHigherDie(GameTestHelper helper) {
        Pig pig = spawnPig(helper);
        run(
                helper,
                null,
                sink -> {
                    // 3 and 12 under advantage keep 12: 17 vs AC 10 hits; 1d4 rolls 2.
                    AttackResult result = RollService.performHazardAttack(pig, darts().withMode(RollMode.ADVANTAGE));
                    if (!result.isHit()
                            || result.natural() != 12
                            || result.roll().mode() != RollMode.ADVANTAGE) {
                        helper.fail("expected advantage to keep 12 and hit, got " + result);
                    }
                    expectReadout(helper, sink, pig, "d20 adv 3/12 → 12+5=17 vs AC 10 — HIT 1d4 = 2");
                },
                3,
                12,
                2);
        helper.succeed();
    }

    /** The provider's AC modifier applies, asked with no attacker. */
    public static void hazardAttackUsesTheProvidedArmorClass(GameTestHelper helper) {
        Pig pig = spawnPig(helper);
        ChecksStyleProvider provider = new ChecksStyleProvider(6);
        run(
                helper,
                provider,
                sink -> {
                    // 10 + 5 = 15 would hit AC 10, but the provided +6 makes it AC 16.
                    AttackResult result = RollService.performHazardAttack(pig, darts());
                    if (result.isHit() || result.armorClass() != 16) {
                        helper.fail("expected a miss against AC 16, got " + result);
                    }
                    if (provider.acAttackers.size() != 1 || provider.acAttackers.getFirst() != null) {
                        helper.fail(
                                "the AC modifier must be asked once with a null attacker, was " + provider.acAttackers);
                    }
                    expectHealth(helper, pig, pig.getMaxHealth());
                },
                10);
        helper.succeed();
    }

    /** The damage type meets the target's resistances; here an assigned profile makes the pig immune. */
    public static void hazardDamageRespectsImmunity(GameTestHelper helper) {
        Pig pig = spawnPig(helper);
        EntityProfileScenarios.withProfile(
                CACTUS_PROOF, "{\"matches\": [], \"damage_modifiers\": {\"immune\": [\"minecraft:cactus\"]}}", () -> {
                    RollService.setEntityProfile(pig, CACTUS_PROOF);
                    run(
                            helper,
                            null,
                            sink -> {
                                HazardSaveResult result = RollService.performHazardSave(pig, spikes());
                                if (result.saved() || result.damage() != 0) {
                                    helper.fail("an immune target takes nothing on a failed save, got " + result);
                                }
                                expectHealth(helper, pig, pig.getMaxHealth());
                            },
                            5,
                            3,
                            4);
                });
        helper.succeed();
    }

    /**
     * A Checks-style species trait: advantage on CON saves against poison, granted by a {@link
     * HazardRollEvent} listener. A save the trait does not cover rolls one die, and the attack-roll events
     * never fire for a hazard.
     */
    public static void listenerGrantsAdvantageOnHazardSave(GameTestHelper helper) {
        Pig pig = spawnPig(helper);
        List<HazardRollEvent> seen = new ArrayList<>();
        List<RollMode> startModes = new ArrayList<>();
        int[] attackEvents = new int[1];
        CritfallEvents.onPreAttackRoll(e -> attackEvents[0]++);
        CritfallEvents.onHazardRoll(event -> {
            seen.add(event);
            startModes.add(event.mode());
            boolean poisonSave = event.save()
                            .map(HazardSave::saveKey)
                            .filter(ModifierProvider.CONSTITUTION_SAVE::equals)
                            .isPresent()
                    && event.damageType() == DamageTypes.MAGIC;
            if (poisonSave) {
                event.grantAdvantage();
            }
        });
        try {
            run(
                    helper,
                    null,
                    sink -> {
                        // Advantage on 4 and 15 keeps 15: saves DC 13. 2d6 rolls 2 + 2, halved to 2.
                        HazardSave poison = HazardSave.of(
                                "Poison gas", DamageTypes.MAGIC, ModifierProvider.CONSTITUTION_SAVE, 13, TWO_D6);
                        HazardSaveResult result = RollService.performHazardSave(pig, poison);
                        if (!result.saved()
                                || result.save().roll().mode() != RollMode.ADVANTAGE
                                || result.save().natural() != 15) {
                            helper.fail("the listener's advantage must keep 15 and save, got " + result);
                        }
                        expectReadout(
                                helper,
                                sink,
                                pig,
                                "Poison gas: save d20 adv 4/15 → 15+0=15 vs DC 13 — SAVED, half damage 2");
                        // The trait does not cover a DEX save: one die, 10 fails. 2d6 rolls 1 + 1.
                        HazardSaveResult spikes = RollService.performHazardSave(pig, spikes());
                        if (spikes.saved() || spikes.save().roll().mode() != RollMode.NORMAL) {
                            helper.fail("a save the listener skips must roll normally, got " + spikes);
                        }
                    },
                    4,
                    15,
                    2,
                    2,
                    10,
                    1,
                    1);
            if (seen.size() != 2) {
                helper.fail("expected one hazard roll event per save, got " + seen.size());
                return;
            }
            HazardRollEvent first = seen.getFirst();
            if (first.target() != pig
                    || first.kind() != HazardRollEvent.Kind.SAVE
                    || !first.label().equals("Poison gas")
                    || startModes.getFirst() != RollMode.NORMAL) {
                helper.fail("the event must carry the target, kind, label and starting mode");
            }
            if (attackEvents[0] != 0) {
                helper.fail("a hazard must not fire PreAttackRollEvent, fired " + attackEvents[0]);
            }
        } finally {
            CritfallEvents.clearListeners();
        }
        helper.succeed();
    }

    /** A listener can adjust a hazard attack too: disadvantage keeps the lower die and misses. */
    public static void listenerImposesDisadvantageOnHazardAttack(GameTestHelper helper) {
        Pig pig = spawnPig(helper);
        CritfallEvents.onHazardRoll(event -> {
            if (event.kind() == HazardRollEvent.Kind.ATTACK) {
                event.imposeDisadvantage();
            }
        });
        try {
            run(
                    helper,
                    null,
                    sink -> {
                        // Disadvantage on 15 and 3 keeps 3: 8 vs AC 10 misses.
                        AttackResult result = RollService.performHazardAttack(pig, darts());
                        if (result.isHit() || result.roll().mode() != RollMode.DISADVANTAGE || result.natural() != 3) {
                            helper.fail("the listener's disadvantage must keep 3 and miss, got " + result);
                        }
                        expectHealth(helper, pig, pig.getMaxHealth());
                        expectReadout(helper, sink, pig, "Dart trap: d20 dis 15/3 → 3+5=8 vs AC 10 — MISS");
                    },
                    15,
                    3);
        } finally {
            CritfallEvents.clearListeners();
        }
        helper.succeed();
    }

    /**
     * Two add-ons disagree: one grants advantage on the poison save, the other imposes disadvantage. 5e
     * cancels them, so the save rolls one die, and a later listener reads the combined mode as normal.
     */
    public static void advantageAndDisadvantageListenersRollNormally(GameTestHelper helper) {
        Pig pig = spawnPig(helper);
        List<RollMode> finalModes = new ArrayList<>();
        CritfallEvents.onHazardRoll(HazardRollEvent::grantAdvantage);
        CritfallEvents.onHazardRoll(HazardRollEvent::imposeDisadvantage);
        CritfallEvents.onHazardRoll(event -> finalModes.add(event.mode()));
        try {
            run(
                    helper,
                    null,
                    sink -> {
                        // One die: 10 + 0 = 10 vs DC 13 fails. 2d6 rolls 1 + 1.
                        HazardSave poison = HazardSave.of(
                                "Poison gas", DamageTypes.MAGIC, ModifierProvider.CONSTITUTION_SAVE, 13, TWO_D6);
                        HazardSaveResult result = RollService.performHazardSave(pig, poison);
                        if (result.saved()
                                || result.save().roll().mode() != RollMode.NORMAL
                                || result.save().roll().hasTwoDice()) {
                            helper.fail("advantage and disadvantage must cancel to one die, got " + result);
                        }
                        expectReadout(helper, sink, pig, "Poison gas: save d20 10+0=10 vs DC 13 — FAILED 2d6 = 2");
                    },
                    10,
                    1,
                    1);
            if (!finalModes.equals(List.of(RollMode.NORMAL))) {
                helper.fail("a later listener must read the combined mode as NORMAL, read " + finalModes);
            }
        } finally {
            CritfallEvents.clearListeners();
        }
        helper.succeed();
    }

    private static void run(
            GameTestHelper helper, ModifierProvider provider, Consumer<CapturingFeedbackSink> action, int... faces) {
        CapturingFeedbackSink sink = new CapturingFeedbackSink();
        FeedbackSink previous = FeedbackSink.get();
        FeedbackSink.set(sink);
        if (provider != null) {
            RollService.registerModifierProvider(provider);
        }
        try {
            CombatScenarios.withRolls(helper, Rules.DEFAULTS, () -> action.accept(sink), faces);
        } finally {
            RollService.clearModifierProvider();
            FeedbackSink.set(previous);
        }
    }

    private static Pig spawnPig(GameTestHelper helper) {
        return CombatScenarios.spawnCalm(helper, EntityType.PIG, 2, 2);
    }

    /** The readout went to the target and contains {@code fragment}. */
    private static void expectReadout(
            GameTestHelper helper, CapturingFeedbackSink sink, LivingEntity target, String fragment) {
        CheckFeedbackPayload payload = sink.lastCheck();
        if (payload == null) {
            helper.fail("no readout was sent");
            return;
        }
        if (sink.lastCheckRoller() != target) {
            helper.fail("the readout must go to the target, went to " + sink.lastCheckRoller());
        }
        String readout = CombatText.actionBar(payload).getString();
        if (!readout.contains(fragment)) {
            helper.fail("the readout must contain \"" + fragment + "\", was \"" + readout + "\"");
        }
    }

    private static void expectHealth(GameTestHelper helper, LivingEntity entity, float expected) {
        if (Math.abs(entity.getHealth() - expected) > 0.001F) {
            helper.fail("expected health " + expected + " but was " + entity.getHealth(), entity);
        }
    }
}
