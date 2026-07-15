package studio.modroll.critfall.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import studio.modroll.critfall.api.combat.SaveResult;
import studio.modroll.critfall.api.dice.DiceRoller;
import studio.modroll.critfall.api.dice.SequenceRandom;

class RollServiceTest {

    @BeforeAll
    static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @AfterEach
    void reset() {
        RollService.resetRoller();
    }

    @Test
    void rollDelegatesToInjectedRoller() {
        // Force a d6 face of 4: SequenceRandom returns face-1 internally, total is 4.
        RollService.setRoller(new DiceRoller(SequenceRandom.ofDieFaces(4)));
        assertEquals(4, RollService.roll("1d6").total());
    }

    @Test
    void seamForcesNat20() {
        RollService.setRoller(new DiceRoller(SequenceRandom.ofDieFaces(20)));
        SaveResult save = RollService.savingThrow(null, 0, 21);
        assertEquals(20, save.natural());
        assertFalse(save.saved());
    }

    @Test
    void seamForcesNat1() {
        RollService.setRoller(new DiceRoller(SequenceRandom.ofDieFaces(1)));
        SaveResult save = RollService.savingThrow(null, 0, 1);
        assertEquals(1, save.natural());
        assertTrue(save.saved());
    }
}
