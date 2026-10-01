package studio.modroll.critfall.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.BiConsumer;
import net.minecraft.world.entity.LivingEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import studio.modroll.critfall.api.AttackDelivery;
import studio.modroll.critfall.api.ModifierProvider;
import studio.modroll.critfall.api.RollService;
import studio.modroll.critfall.api.dice.DiceExpression;

/** {@link Modifiers} never dereferences the entities, so these pass null. Own bonuses: attack 3, save 2, 1d6+1. */
class ModifiersTest {

    private static final DiceExpression OWN_DICE = DiceExpression.parse("1d6+1");
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

    /** Answers every roll with {@code value} and records what it was asked. */
    private static final class FixedProvider implements ModifierProvider {
        final List<AttackDelivery> deliveries = new ArrayList<>();
        final List<String> saveKeys = new ArrayList<>();
        private final OptionalInt value;

        FixedProvider(OptionalInt value) {
            this.value = value;
        }

        @Override
        public OptionalInt attackModifier(LivingEntity attacker, LivingEntity target, AttackDelivery delivery) {
            deliveries.add(delivery);
            return value;
        }

        @Override
        public OptionalInt damageModifier(LivingEntity attacker, AttackDelivery delivery) {
            deliveries.add(delivery);
            return value;
        }

        @Override
        public OptionalInt saveModifier(LivingEntity entity, String saveKey) {
            saveKeys.add(saveKey);
            return value;
        }
    }

    private static final class ThrowingProvider implements ModifierProvider {
        @Override
        public OptionalInt attackModifier(LivingEntity attacker, LivingEntity target, AttackDelivery delivery) {
            throw new IllegalStateException("boom");
        }

        @Override
        public OptionalInt damageModifier(LivingEntity attacker, AttackDelivery delivery) {
            throw new IllegalStateException("boom");
        }

        @Override
        public OptionalInt saveModifier(LivingEntity entity, String saveKey) {
            throw new IllegalStateException("boom");
        }
    }

    private final List<String> logged = new ArrayList<>();
    private BiConsumer<String, Throwable> realLog;

    @BeforeEach
    void captureLog() {
        realLog = Modifiers.errorLog;
        Modifiers.errorLog = (message, cause) -> logged.add(message);
    }

    @AfterEach
    void clear() {
        RollService.clearModifierProvider();
        Modifiers.errorLog = realLog;
    }

    private static int attack(Rules rules) {
        return Modifiers.attackBonus(rules, null, null, AttackDelivery.MELEE, 3);
    }

    private static DiceExpression damage(Rules rules) {
        return Modifiers.damageDice(rules, null, AttackDelivery.MELEE, OWN_DICE);
    }

    private static int save(Rules rules) {
        return Modifiers.saveBonus(rules, null, ModifierProvider.SPELL_SAVE, 2);
    }

    private static void assertOwnBonuses(Rules rules) {
        assertEquals(3, attack(rules));
        assertSame(OWN_DICE, damage(rules));
        assertEquals(2, save(rules));
    }

    @Test
    void noProviderKeepsOwnBonuses() {
        assertOwnBonuses(Rules.DEFAULTS);
    }

    @Test
    void presentValueReplacesEachRollType() {
        FixedProvider provider = new FixedProvider(OptionalInt.of(7));
        RollService.registerModifierProvider(provider);
        assertEquals(7, Modifiers.attackBonus(Rules.DEFAULTS, null, null, AttackDelivery.PROJECTILE, 3));
        assertEquals(DiceExpression.parse("1d6+7"), damage(Rules.DEFAULTS));
        assertEquals(7, save(Rules.DEFAULTS));
        assertEquals(List.of(AttackDelivery.PROJECTILE, AttackDelivery.MELEE), provider.deliveries);
        assertEquals(List.of(ModifierProvider.SPELL_SAVE), provider.saveKeys);
    }

    @Test
    void presentZeroStillReplaces() {
        RollService.registerModifierProvider(new FixedProvider(OptionalInt.of(0)));
        assertEquals(0, attack(Rules.DEFAULTS));
        assertEquals(DiceExpression.parse("1d6"), damage(Rules.DEFAULTS));
    }

    @Test
    void emptyAnswerKeepsOwnBonuses() {
        RollService.registerModifierProvider(new ModifierProvider() {});
        assertOwnBonuses(Rules.DEFAULTS);
    }

    @Test
    void toggleOffIgnoresTheProvider() {
        FixedProvider provider = new FixedProvider(OptionalInt.of(7));
        RollService.registerModifierProvider(provider);
        assertOwnBonuses(PROVIDERS_OFF);
        assertTrue(provider.deliveries.isEmpty() && provider.saveKeys.isEmpty(), "a disabled provider is never asked");
    }

    @Test
    void diceLessDamageIsLeftAlone() {
        RollService.registerModifierProvider(new FixedProvider(OptionalInt.of(5)));
        DiceExpression flat = DiceExpression.parse("1");
        assertSame(flat, Modifiers.damageDice(Rules.DEFAULTS, null, AttackDelivery.MELEE, flat));
        assertTrue(logged.isEmpty(), "leaving a flat amount alone is not a bad answer");
    }

    @Test
    void throwingProviderFallsBackAndLogsOncePerProviderAndRollType() {
        RollService.registerModifierProvider(new ThrowingProvider());
        for (int i = 0; i < 3; i++) {
            assertEquals(3, attack(Rules.DEFAULTS));
        }
        assertEquals(1, logged.size(), "three throwing attack answers, one log line");
        assertTrue(logged.get(0).contains("attack"), logged.get(0));

        for (int i = 0; i < 2; i++) {
            assertSame(OWN_DICE, damage(Rules.DEFAULTS));
            assertEquals(2, save(Rules.DEFAULTS));
        }
        assertEquals(3, logged.size(), "damage and save each log once more");

        RollService.registerModifierProvider(new ThrowingProvider());
        attack(Rules.DEFAULTS);
        assertEquals(4, logged.size(), "a different provider gets its own log line");
    }

    @Test
    void nullAndOutOfRangeAnswersFallBackAndLogOnce() {
        RollService.registerModifierProvider(new ModifierProvider() {
            @Override
            public OptionalInt saveModifier(LivingEntity entity, String saveKey) {
                return null;
            }

            @Override
            public OptionalInt damageModifier(LivingEntity attacker, AttackDelivery delivery) {
                return OptionalInt.of(Integer.MAX_VALUE);
            }
        });
        for (int i = 0; i < 2; i++) {
            assertEquals(2, save(Rules.DEFAULTS));
            assertSame(OWN_DICE, damage(Rules.DEFAULTS));
        }
        assertEquals(2, logged.size(), logged.toString());
    }

    @Test
    void lastRegisteredProviderWins() {
        FixedProvider first = new FixedProvider(OptionalInt.of(1));
        FixedProvider second = new FixedProvider(OptionalInt.of(2));
        RollService.registerModifierProvider(first);
        RollService.registerModifierProvider(second);
        assertSame(second, RollService.modifierProvider().orElseThrow());
        assertEquals(2, attack(Rules.DEFAULTS));
        assertTrue(first.deliveries.isEmpty(), "the replaced provider is never asked");
    }

    @Test
    void clearingEmptiesTheSlot() {
        RollService.registerModifierProvider(new FixedProvider(OptionalInt.of(1)));
        RollService.clearModifierProvider();
        assertTrue(RollService.modifierProvider().isEmpty());
        assertEquals(3, attack(Rules.DEFAULTS));
    }
}
