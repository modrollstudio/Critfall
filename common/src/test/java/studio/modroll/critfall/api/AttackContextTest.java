package studio.modroll.critfall.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void nullDeliveryRejected() {
        assertThrows(
                NullPointerException.class,
                () -> new AttackContext(
                        null,
                        null,
                        ItemStack.EMPTY,
                        RollMode.NORMAL,
                        java.util.OptionalInt.empty(),
                        java.util.Optional.empty()));
    }
}
