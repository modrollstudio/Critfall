package studio.modroll.critfall.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import studio.modroll.critfall.api.dice.RollMode;

class JumpAttacksTest {

    @Test
    void untouchedAdvantageStands() {
        assertEquals(RollMode.ADVANTAGE, JumpAttacks.withJumpAdvantage(RollMode.ADVANTAGE));
    }

    @Test
    void disadvantageCancelsTheJumpAdvantage() {
        assertEquals(RollMode.NORMAL, JumpAttacks.withJumpAdvantage(RollMode.DISADVANTAGE));
    }

    @Test
    void listenerSettingNormalClearsTheAdvantage() {
        assertEquals(RollMode.NORMAL, JumpAttacks.withJumpAdvantage(RollMode.NORMAL));
    }
}
