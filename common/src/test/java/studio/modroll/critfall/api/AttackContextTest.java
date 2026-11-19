package studio.modroll.critfall.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import studio.modroll.critfall.api.dice.DiceExpression;
import studio.modroll.critfall.api.dice.RollMode;

class AttackContextTest {

    @BeforeAll
    static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void meleeFactorySetsDeliveryAndDefaults() {
        AttackContext ctx = AttackContext.melee(null, ItemStack.EMPTY);
        assertEquals(AttackDelivery.MELEE, ctx.delivery());
        assertEquals(RollMode.NORMAL, ctx.mode());
        assertTrue(ctx.attackBonusOverride().isEmpty());
        assertTrue(ctx.damageDiceOverride().isEmpty());
    }

    @Test
    void withersProduceOverrides() {
        AttackContext ctx = AttackContext.thrown(null, ItemStack.EMPTY)
                .withMode(RollMode.ADVANTAGE)
                .withAttackBonus(5)
                .withDamageDice(DiceExpression.parse("2d6"));
        assertEquals(AttackDelivery.THROWN, ctx.delivery());
        assertEquals(RollMode.ADVANTAGE, ctx.mode());
        assertEquals(5, ctx.attackBonusOverride().getAsInt());
        assertEquals("2d6", ctx.damageDiceOverride().orElseThrow().toString());
    }

    @Test
    void defenderAcBonusDefaultsToZeroAndIsSetByWither() {
        AttackContext base = AttackContext.melee(null, ItemStack.EMPTY);
        assertEquals(0, base.defenderAcBonus());

        AttackContext cover = base.withDefenderAcBonus(5);
        assertEquals(5, cover.defenderAcBonus());
        assertEquals(0, base.defenderAcBonus(), "the wither returns a copy, leaving the original untouched");

        assertEquals(-2, base.withDefenderAcBonus(-2).defenderAcBonus(), "penalties are allowed");
    }

    @Test
    void defenderAcBonusComposesWithOtherWithers() {
        AttackContext ctx = AttackContext.spell(null, ItemStack.EMPTY)
                .withAttackBonus(3)
                .withDefenderAcBonus(5)
                .withMode(RollMode.ADVANTAGE);
        assertEquals(5, ctx.defenderAcBonus());
        assertEquals(3, ctx.attackBonusOverride().getAsInt());
        assertEquals(RollMode.ADVANTAGE, ctx.mode());
    }

    @Test
    void nullDeliveryRejected() {
        assertThrows(
                NullPointerException.class,
                () -> new AttackContext(
                        null, null, ItemStack.EMPTY, RollMode.NORMAL, OptionalInt.empty(), Optional.empty(), 0));
    }

    @Test
    void labelIsEmptyUnlessGivenAndWithersKeepIt() {
        AttackContext plain = AttackContext.spell(null, ItemStack.EMPTY);
        assertEquals("", plain.label());
        AttackContext named = plain.withLabel("Fire Bolt")
                .withMode(RollMode.ADVANTAGE)
                .withAttackBonus(5)
                .withDamageDice(DiceExpression.parse("1d10"))
                .withDefenderAcBonus(2);
        assertEquals("Fire Bolt", named.label());
        assertEquals("", plain.label(), "the wither returns a copy");
        assertEquals(
                "",
                new AttackContext(
                                AttackDelivery.MELEE,
                                null,
                                ItemStack.EMPTY,
                                RollMode.NORMAL,
                                OptionalInt.empty(),
                                Optional.empty(),
                                0)
                        .label(),
                "the constructor from before 0.2.20 has no label");
        assertThrows(NullPointerException.class, () -> plain.withLabel(null));
    }
}
