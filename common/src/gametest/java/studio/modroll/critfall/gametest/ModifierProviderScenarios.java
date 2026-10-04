package studio.modroll.critfall.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import studio.modroll.critfall.Critfall;
import studio.modroll.critfall.api.AttackContext;
import studio.modroll.critfall.api.AttackDelivery;
import studio.modroll.critfall.api.ModifierProvider;
import studio.modroll.critfall.api.RollService;
import studio.modroll.critfall.api.combat.AttackOutcome;
import studio.modroll.critfall.api.combat.AttackResult;
import studio.modroll.critfall.api.feedback.RollFeedbackPayload;
import studio.modroll.critfall.combat.CombatText;
import studio.modroll.critfall.combat.Rules;
import studio.modroll.critfall.feedback.CapturingFeedbackSink;
import studio.modroll.critfall.feedback.FeedbackSink;
import studio.modroll.critfall.feedback.SaveFeedbackPayload;

/**
 * The modifier provider end to end. Shipped profiles: husk +3 with 1d6+1, skeleton +4 with the bow's
 * 1d8+2, pig AC 10 and save +0.
 */
public final class ModifierProviderScenarios {

    private static final float VANILLA_HIT = 3.0F;
    private static final float ARROW_DAMAGE = 6.0F;
    private static final float SPELL_DAMAGE = 6.0F;
    private static final String SAVE_PROFILE =
            "{\"matches\": [\"minecraft:mob_attack\"], \"resolution\": \"save\", \"damage\": \"2d6\","
                    + " \"save\": {\"dc\": 13}}";
    private static final Rules PROVIDERS_OFF = new Rules(
            Rules.DEFAULTS.attackRolls(),
            Rules.DEFAULTS.damageDice(),
            Rules.DEFAULTS.crits(),
            Rules.DEFAULTS.fumbles(),
            Rules.DEFAULTS.spells(),
            Rules.DEFAULTS.fallbacks(),
            Rules.DEFAULTS.feedback(),
            Rules.DEFAULTS.balance(),
            Rules.DEFAULTS.dryRun(),
            new Rules.ModifierProviders(false));

    /** Answers with fixed values (null = defer to Critfall) and records what it was asked. */
    private static final class FixedProvider implements ModifierProvider {
        final List<AttackDelivery> attackDeliveries = new ArrayList<>();
        final List<String> saveKeys = new ArrayList<>();
        /** Each AC question as defender then attacker. */
        final List<LivingEntity> acAsked = new ArrayList<>();

        private final Integer attack;
        private final Integer damage;
        private final Integer save;
        private final Integer armorClass;

        FixedProvider(Integer attack, Integer damage, Integer save) {
            this(attack, damage, save, null);
        }

        FixedProvider(Integer attack, Integer damage, Integer save, Integer armorClass) {
            this.attack = attack;
            this.damage = damage;
            this.save = save;
            this.armorClass = armorClass;
        }

        @Override
        public OptionalInt attackModifier(LivingEntity attacker, LivingEntity target, AttackDelivery delivery) {
            attackDeliveries.add(delivery);
            return answer(attack);
        }

        @Override
        public OptionalInt damageModifier(LivingEntity attacker, AttackDelivery delivery) {
            return answer(damage);
        }

        @Override
        public OptionalInt saveModifier(LivingEntity entity, String saveKey) {
            saveKeys.add(saveKey);
            return answer(save);
        }

        @Override
        public OptionalInt acModifier(LivingEntity defender, LivingEntity attacker) {
            acAsked.add(defender);
            acAsked.add(attacker);
            return answer(armorClass);
        }

        private static OptionalInt answer(Integer value) {
            return value == null ? OptionalInt.empty() : OptionalInt.of(value);
        }
    }

    public static void noProviderRollsExactlyAsBefore(GameTestHelper helper) {
        Husk husk = spawnHusk(helper);
        Pig pig = spawnPig(helper);
        run(
                helper,
                null,
                Rules.DEFAULTS,
                sink -> {
                    // 13 + 3 = 16 vs AC 10 -> hit, 1d6+1 rolls 4 -> 5
                    meleeHit(helper, husk, pig);
                    expectHealth(helper, pig, pig.getMaxHealth() - 5.0F);
                    expectReadout(helper, sink.lastRoll(), "d20 13+3=16 vs AC 10", "1d6+1 = 5");
                },
                13,
                4);
        helper.succeed();
    }

    public static void providerReplacesMeleeAttackBonus(GameTestHelper helper) {
        Husk husk = spawnHusk(helper);
        Pig pig = spawnPig(helper);
        FixedProvider provider = new FixedProvider(-5, null, null);
        run(
                helper,
                provider,
                Rules.DEFAULTS,
                sink -> {
                    // 13 - 5 = 8 -> miss, where the husk's own +3 would hit
                    meleeHit(helper, husk, pig);
                    expectHealth(helper, pig, pig.getMaxHealth());
                    expectReadout(helper, sink.lastRoll(), "d20 13-5=8 vs AC 10");
                },
                13);
        expectAsked(helper, provider.attackDeliveries, List.of(AttackDelivery.MELEE));
        helper.succeed();
    }

    public static void providerAddsToDamageModifier(GameTestHelper helper) {
        Husk husk = spawnHusk(helper);
        Pig pig = spawnPig(helper);
        run(
                helper,
                new FixedProvider(null, 4, null),
                Rules.DEFAULTS,
                sink -> {
                    // 1d6+1 plus 4 is 1d6+5, rolls 4 -> 9
                    meleeHit(helper, husk, pig);
                    expectHealth(helper, pig, pig.getMaxHealth() - 9.0F);
                    expectReadout(helper, sink.lastRoll(), "d20 13+3=16 vs AC 10", "1d6+5 = 9");
                },
                13,
                4);
        helper.succeed();
    }

    public static void providerReplacesRangedAttackAndAddsToDamage(GameTestHelper helper) {
        Skeleton skeleton = CombatScenarios.spawnCalm(helper, EntityType.SKELETON, 1, 1);
        skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        Pig pig = spawnPig(helper);
        Arrow arrow = ProjectileScenarios.shotArrow(helper, skeleton);
        FixedProvider provider = new FixedProvider(-3, -1, null);
        run(
                helper,
                provider,
                Rules.DEFAULTS,
                sink -> {
                    // 13 - 3 = 10 vs AC 10 -> hit; the bow's 1d8+2 minus 1 is 1d8+1, rolls 4 -> 5
                    pig.hurt(helper.getLevel().damageSources().arrow(arrow, skeleton), ARROW_DAMAGE);
                    expectHealth(helper, pig, pig.getMaxHealth() - 5.0F);
                },
                13,
                4);
        expectAsked(helper, provider.attackDeliveries, List.of(AttackDelivery.PROJECTILE));
        helper.succeed();
    }

    public static void providerReplacesSaveBonus(GameTestHelper helper) {
        Husk husk = spawnHusk(helper);
        Pig pig = spawnPig(helper);
        FixedProvider provider = new FixedProvider(null, 2, 5);
        run(
                helper,
                provider,
                Rules.DEFAULTS,
                sink -> SpellScenarios.withSpellProfile(SAVE_PROFILE, () -> {
                    // 8 + 5 = 13 vs DC 13 saves; 2d6+2 rolls 4+5+2 = 11 -> half = 5
                    pig.hurt(SpellScenarios.spellSource(helper, husk), SPELL_DAMAGE);
                    expectHealth(helper, pig, pig.getMaxHealth() - 5.0F);
                    SaveFeedbackPayload save = sink.lastSave();
                    if (save == null
                            || !CombatText.actionBar(save).getString().startsWith("save d20 8+5=13 vs DC 13")) {
                        helper.fail("the save readout must show the provided modifier");
                    }
                }),
                8,
                4,
                5);
        expectAsked(helper, provider.saveKeys, List.of(ModifierProvider.SPELL_SAVE));
        helper.succeed();
    }

    public static void emptyProviderFallsBackToOwnBonus(GameTestHelper helper) {
        Husk husk = spawnHusk(helper);
        Pig pig = spawnPig(helper);
        FixedProvider provider = new FixedProvider(null, null, null);
        run(
                helper,
                provider,
                Rules.DEFAULTS,
                sink -> {
                    meleeHit(helper, husk, pig);
                    expectHealth(helper, pig, pig.getMaxHealth() - 5.0F);
                    expectReadout(helper, sink.lastRoll(), "d20 13+3=16 vs AC 10", "1d6+1 = 5");
                },
                13,
                4);
        expectAsked(helper, provider.attackDeliveries, List.of(AttackDelivery.MELEE));
        helper.succeed();
    }

    public static void toggleOffIgnoresProvider(GameTestHelper helper) {
        Husk husk = spawnHusk(helper);
        Pig pig = spawnPig(helper);
        FixedProvider provider = new FixedProvider(-5, 4, 5);
        run(
                helper,
                provider,
                PROVIDERS_OFF,
                sink -> {
                    meleeHit(helper, husk, pig);
                    expectHealth(helper, pig, pig.getMaxHealth() - 5.0F);
                },
                13,
                4);
        expectAsked(helper, provider.attackDeliveries, List.of());
        helper.succeed();
    }

    public static void drivenAttackUsesProvider(GameTestHelper helper) {
        Husk husk = spawnHusk(helper);
        Pig pig = spawnPig(helper);
        RollService.suppress(husk);
        RollService.suppress(pig);
        try {
            run(
                    helper,
                    new FixedProvider(8, 1, null),
                    Rules.DEFAULTS,
                    sink -> {
                        // 2 + 8 = 10 -> hit, where the husk's own +3 would miss; 1d6+1 plus 1 rolls 4 -> 6
                        AttackResult result = RollService.performAttack(husk, pig, melee(helper, husk));
                        if (result.outcome() != AttackOutcome.HIT || result.attackBonus() != 8) {
                            helper.fail(
                                    "expected a HIT on +8, got " + result.outcome() + " on " + result.attackBonus());
                        }
                        expectHealth(helper, pig, pig.getMaxHealth() - 6.0F);
                    },
                    2,
                    4);
        } finally {
            RollService.release(husk);
            RollService.release(pig);
        }
        helper.succeed();
    }

    public static void explicitAttackBonusBeatsProvider(GameTestHelper helper) {
        Husk husk = spawnHusk(helper);
        Pig pig = spawnPig(helper);
        RollService.suppress(husk);
        RollService.suppress(pig);
        FixedProvider provider = new FixedProvider(-10, null, null);
        try {
            run(
                    helper,
                    provider,
                    Rules.DEFAULTS,
                    sink -> {
                        AttackResult result = RollService.performAttack(
                                husk, pig, melee(helper, husk).withAttackBonus(2));
                        if (result.outcome() != AttackOutcome.HIT || result.attackBonus() != 2) {
                            helper.fail(
                                    "expected a HIT on +2, got " + result.outcome() + " on " + result.attackBonus());
                        }
                    },
                    13,
                    4);
        } finally {
            RollService.release(husk);
            RollService.release(pig);
        }
        expectAsked(helper, provider.attackDeliveries, List.of());
        helper.succeed();
    }

    public static void negativeDamageModifierStillDealsOne(GameTestHelper helper) {
        Husk husk = spawnHusk(helper);
        Pig pig = spawnPig(helper);
        run(
                helper,
                new FixedProvider(null, -10, null),
                Rules.DEFAULTS,
                sink -> {
                    // 1d6+1 minus 10 is 1d6-9, rolls 4 -> -5, floored to 1
                    meleeHit(helper, husk, pig);
                    expectHealth(helper, pig, pig.getMaxHealth() - 1.0F);
                    expectReadout(helper, sink.lastRoll(), "1d6-9 = 1");
                },
                13,
                4);
        helper.succeed();
    }

    public static void diceLessDamageIgnoresProvider(GameTestHelper helper) {
        Husk husk = spawnHusk(helper);
        Pig pig = spawnPig(helper);
        run(
                helper,
                new FixedProvider(null, 5, null),
                Rules.DEFAULTS,
                sink -> {
                    // a 1-damage spell derives the flat "1", which has no dice to add to
                    pig.hurt(SpellScenarios.spellSource(helper, husk), 1.0F);
                    expectHealth(helper, pig, pig.getMaxHealth() - 1.0F);
                    expectReadout(helper, sink.lastRoll(), "d20 13+3=16 vs AC 10", " 1 = 1");
                },
                13);
        helper.succeed();
    }

    public static void failedSaveNegativeModifierStillDealsOne(GameTestHelper helper) {
        Husk husk = spawnHusk(helper);
        Pig pig = spawnPig(helper);
        run(
                helper,
                new FixedProvider(null, -20, null),
                Rules.DEFAULTS,
                sink -> SpellScenarios.withSpellProfile(SAVE_PROFILE, () -> {
                    // 12 vs DC 13 fails; 2d6-20 rolls 4+5-20 = -11, floored to 1
                    pig.hurt(SpellScenarios.spellSource(helper, husk), SPELL_DAMAGE);
                    expectHealth(helper, pig, pig.getMaxHealth() - 1.0F);
                }),
                12,
                4,
                5);
        helper.succeed();
    }

    public static void providerRaisesMeleeArmorClass(GameTestHelper helper) {
        Husk husk = spawnHusk(helper);
        Pig pig = spawnPig(helper);
        FixedProvider provider = new FixedProvider(null, null, null, 2);
        run(
                helper,
                provider,
                Rules.DEFAULTS,
                sink -> {
                    // 7 + 3 = 10 would hit the pig's own AC 10; plus 2 is AC 12 -> miss
                    meleeHit(helper, husk, pig);
                    expectHealth(helper, pig, pig.getMaxHealth());
                    expectReadout(helper, sink.lastRoll(), "d20 7+3=10 vs AC 12");
                },
                7);
        expectAsked(helper, provider.acAsked, List.of(pig, husk));
        helper.succeed();
    }

    public static void providerLowersMeleeArmorClass(GameTestHelper helper) {
        Husk husk = spawnHusk(helper);
        Pig pig = spawnPig(helper);
        run(
                helper,
                new FixedProvider(null, null, null, -2),
                Rules.DEFAULTS,
                sink -> {
                    // 5 + 3 = 8 would miss AC 10; minus 2 is AC 8 -> hit, 1d6+1 rolls 4 -> 5
                    meleeHit(helper, husk, pig);
                    expectHealth(helper, pig, pig.getMaxHealth() - 5.0F);
                    expectReadout(helper, sink.lastRoll(), "d20 5+3=8 vs AC 8", "1d6+1 = 5");
                },
                5,
                4);
        helper.succeed();
    }

    public static void providedArmorClassAppliesToRangedAttacks(GameTestHelper helper) {
        Skeleton skeleton = CombatScenarios.spawnCalm(helper, EntityType.SKELETON, 1, 1);
        skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        Pig pig = spawnPig(helper);
        Arrow arrow = ProjectileScenarios.shotArrow(helper, skeleton);
        FixedProvider provider = new FixedProvider(null, null, null, 2);
        run(
                helper,
                provider,
                Rules.DEFAULTS,
                sink -> {
                    // 7 + 4 = 11 vs AC 12 -> miss, where the pig's own AC 10 would be hit
                    pig.hurt(helper.getLevel().damageSources().arrow(arrow, skeleton), ARROW_DAMAGE);
                    expectHealth(helper, pig, pig.getMaxHealth());
                    expectReadout(helper, sink.lastRoll(), "d20 7+4=11 vs AC 12");
                },
                7);
        expectAsked(helper, provider.acAsked, List.of(pig, skeleton));
        helper.succeed();
    }

    public static void providedArmorClassAppliesToSpellAttacks(GameTestHelper helper) {
        Husk husk = spawnHusk(helper);
        Pig pig = spawnPig(helper);
        FixedProvider provider = new FixedProvider(null, null, null, 2);
        run(
                helper,
                provider,
                Rules.DEFAULTS,
                sink -> {
                    // 7 + 3 = 10 vs AC 12 -> miss
                    pig.hurt(SpellScenarios.spellSource(helper, husk), SPELL_DAMAGE);
                    expectHealth(helper, pig, pig.getMaxHealth());
                    expectReadout(helper, sink.lastRoll(), "d20 7+3=10 vs AC 12");
                },
                7);
        expectAsked(helper, provider.acAsked, List.of(pig, husk));
        helper.succeed();
    }

    public static void drivenAttackUsesProvidedArmorClass(GameTestHelper helper) {
        Husk husk = spawnHusk(helper);
        Pig pig = spawnPig(helper);
        RollService.suppress(husk);
        RollService.suppress(pig);
        FixedProvider provider = new FixedProvider(null, null, null, 2);
        try {
            run(
                    helper,
                    provider,
                    Rules.DEFAULTS,
                    sink -> {
                        // attackRoll: 7 + 3 = 10 vs AC 12 -> miss
                        AttackResult rolled = RollService.attackRoll(husk, pig, melee(helper, husk));
                        expectArmorClass(helper, rolled, AttackOutcome.MISS, 12, 12);
                        // performAttack, half cover on top: 9 + 3 = 12 vs AC 13 (12+1) -> miss
                        AttackResult performed = RollService.performAttack(
                                husk, pig, melee(helper, husk).withDefenderAcBonus(1));
                        expectArmorClass(helper, performed, AttackOutcome.MISS, 13, 12);
                        expectHealth(helper, pig, pig.getMaxHealth());
                        expectReadout(helper, sink.lastRoll(), "d20 9+3=12 vs AC 13 (12+1)");
                    },
                    7,
                    9);
        } finally {
            RollService.release(husk);
            RollService.release(pig);
        }
        expectAsked(helper, provider.acAsked, List.of(pig, husk, pig, husk));
        helper.succeed();
    }

    public static void badArmorClassAnswerFallsBackAndLogsOnce(GameTestHelper helper) {
        Husk husk = spawnHusk(helper);
        Pig pig = spawnPig(helper);
        ModifierProvider throwing = new ModifierProvider() {
            @Override
            public OptionalInt acModifier(LivingEntity defender, LivingEntity attacker) {
                throw new IllegalStateException("boom");
            }
        };
        List<String> errors = capturingErrors(() -> run(
                helper,
                throwing,
                Rules.DEFAULTS,
                sink -> {
                    // 7 + 3 = 10 vs the pig's own AC 10 -> hit, twice driven (resolve only) and once real time
                    for (int i = 0; i < 2; i++) {
                        AttackResult result = RollService.attackRoll(husk, pig, melee(helper, husk));
                        expectArmorClass(helper, result, AttackOutcome.HIT, 10, 10);
                    }
                    meleeHit(helper, husk, pig);
                    expectHealth(helper, pig, pig.getMaxHealth() - 5.0F);
                    expectReadout(helper, sink.lastRoll(), "d20 7+3=10 vs AC 10");
                },
                7,
                4,
                7,
                4,
                7,
                4));
        long armorClassErrors =
                errors.stream().filter(line -> line.contains("armor class")).count();
        if (armorClassErrors != 1) {
            helper.fail("expected one armor class error for three bad answers, got " + errors);
        }
        helper.succeed();
    }

    /** Runs {@code action} with {@code provider} registered (null for none), feedback captured, and scripted rolls. */
    private static void run(
            GameTestHelper helper,
            ModifierProvider provider,
            Rules rules,
            Consumer<CapturingFeedbackSink> action,
            int... faces) {
        CapturingFeedbackSink sink = new CapturingFeedbackSink();
        FeedbackSink previous = FeedbackSink.get();
        FeedbackSink.set(sink);
        if (provider != null) {
            RollService.registerModifierProvider(provider);
        }
        try {
            CombatScenarios.withRolls(helper, rules, () -> action.accept(sink), faces);
        } finally {
            RollService.clearModifierProvider();
            FeedbackSink.set(previous);
        }
    }

    private static Husk spawnHusk(GameTestHelper helper) {
        return CombatScenarios.spawnCalm(helper, EntityType.HUSK, 1, 1);
    }

    private static Pig spawnPig(GameTestHelper helper) {
        return CombatScenarios.spawnCalm(helper, EntityType.PIG, 3, 3);
    }

    private static void meleeHit(GameTestHelper helper, Husk husk, Pig pig) {
        pig.hurt(helper.getLevel().damageSources().mobAttack(husk), VANILLA_HIT);
    }

    private static AttackContext melee(GameTestHelper helper, Husk husk) {
        return AttackContext.melee(helper.getLevel().damageSources().mobAttack(husk), husk.getMainHandItem());
    }

    /** Runs {@code action} and returns the messages Critfall logged at ERROR meanwhile. */
    private static List<String> capturingErrors(Runnable action) {
        Logger logger = (Logger) LogManager.getLogger(Critfall.MOD_NAME);
        List<String> errors = new CopyOnWriteArrayList<>();
        AbstractAppender appender =
                new AbstractAppender("critfall-gametest-errors", null, null, true, Property.EMPTY_ARRAY) {
                    @Override
                    public void append(LogEvent event) {
                        if (event.getLevel() == Level.ERROR) {
                            errors.add(event.getMessage().getFormattedMessage());
                        }
                    }
                };
        appender.start();
        logger.addAppender(appender);
        try {
            action.run();
        } finally {
            logger.removeAppender(appender);
            appender.stop();
        }
        return errors;
    }

    private static void expectArmorClass(
            GameTestHelper helper, AttackResult result, AttackOutcome outcome, int armorClass, int baseArmorClass) {
        if (result.outcome() != outcome
                || result.armorClass() != armorClass
                || result.baseArmorClass() != baseArmorClass) {
            helper.fail("expected a " + outcome + " vs AC " + armorClass + " (base " + baseArmorClass + "), got "
                    + result.outcome() + " vs AC " + result.armorClass() + " (base " + result.baseArmorClass()
                    + ")");
        }
    }

    private static <T> void expectAsked(GameTestHelper helper, List<T> asked, List<T> expected) {
        if (!asked.equals(expected)) {
            helper.fail("expected the provider to be asked " + expected + ", was " + asked);
        }
    }

    private static void expectReadout(GameTestHelper helper, RollFeedbackPayload payload, String... fragments) {
        if (payload == null) {
            helper.fail("no feedback payload was dispatched");
            return;
        }
        String readout = CombatText.actionBar(payload).getString();
        for (String fragment : fragments) {
            if (!readout.contains(fragment)) {
                helper.fail("the readout must contain \"" + fragment + "\", was \"" + readout + "\"");
            }
        }
    }

    private static void expectHealth(GameTestHelper helper, LivingEntity entity, float expected) {
        if (Math.abs(entity.getHealth() - expected) > 0.001F) {
            helper.fail("expected health " + expected + " but was " + entity.getHealth(), entity);
        }
    }
}
