package studio.modroll.critfall.api.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import studio.modroll.critfall.api.AttackContext;
import studio.modroll.critfall.api.AttackDelivery;
import studio.modroll.critfall.api.combat.AttackOutcome;
import studio.modroll.critfall.api.combat.AttackResult;
import studio.modroll.critfall.api.dice.RollMode;

class CritfallEventsTest {

    @BeforeAll
    static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @AfterEach
    void cleanup() {
        CritfallEvents.clearListeners();
    }

    private static AttackContext ctx() {
        return AttackContext.melee(null, ItemStack.EMPTY);
    }

    @Test
    void preAttackListenerCanMutateBonusAndMode() {
        CritfallEvents.onPreAttackRoll(e -> {
            e.attackBonus(e.attackBonus() + 2);
            e.mode(RollMode.ADVANTAGE);
        });
        PreAttackRollEvent fired = CritfallEvents.firePreAttackRoll(null, null, ctx(), 3, RollMode.NORMAL);
        assertEquals(5, fired.attackBonus());
        assertEquals(RollMode.ADVANTAGE, fired.mode());
        assertFalse(fired.isCanceled());
    }

    @Test
    void preAttackListenerCanCancel() {
        CritfallEvents.onPreAttackRoll(PreAttackRollEvent::cancel);
        assertTrue(CritfallEvents.firePreAttackRoll(null, null, ctx(), 0, RollMode.NORMAL)
                .isCanceled());
    }

    @Test
    void postAttackListenerCanAdjustDamageAndVeto() {
        CritfallEvents.onPostAttackRoll(e -> {
            e.finalDamage(e.finalDamage() * 2);
            e.veto();
        });
        AttackResult result = new AttackResult(AttackOutcome.HIT, 15, 18, 12, 6);
        PostAttackRollEvent fired = CritfallEvents.firePostAttackRoll(null, null, ctx(), result);
        assertEquals(12, fired.finalDamage());
        assertTrue(fired.isVetoed());
    }

    @Test
    void combatInteractionListenerObservesAttackerTargetAndDelivery() {
        CombatInteractionEvent[] seen = new CombatInteractionEvent[1];
        CritfallEvents.onCombatInteraction(e -> seen[0] = e);
        CritfallEvents.fireCombatInteraction(null, null, null, AttackDelivery.THROWN);
        assertNotNull(seen[0]);
        assertNull(seen[0].attacker());
        assertNull(seen[0].target());
        assertEquals(AttackDelivery.THROWN, seen[0].delivery());
    }

    @Test
    void clearListenersDropsCombatInteractionListeners() {
        boolean[] called = {false};
        CritfallEvents.onCombatInteraction(e -> called[0] = true);
        CritfallEvents.clearListeners();
        CritfallEvents.fireCombatInteraction(null, null, null, AttackDelivery.MELEE);
        assertFalse(called[0]);
    }

    @Test
    void listenerExceptionIsIsolated() {
        CritfallEvents.onCrit(e -> {
            throw new RuntimeException("boom");
        });
        boolean[] second = {false};
        CritfallEvents.onCrit(e -> second[0] = true);
        AttackResult result = new AttackResult(AttackOutcome.CRIT, 20, 24, 12, 7);
        CritfallEvents.fireCrit(null, null, ctx(), result); // must not throw
        assertTrue(second[0]);
    }
}
