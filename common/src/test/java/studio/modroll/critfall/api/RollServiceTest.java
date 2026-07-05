package studio.modroll.critfall.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import studio.modroll.critfall.RollRuntime;
import studio.modroll.critfall.dice.DiceRoller;
import studio.modroll.critfall.dice.SequenceRandom;

class RollServiceTest {

    @BeforeAll
    static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @AfterEach
    void reset() {
        RollRuntime.setRoller(new DiceRoller(new java.util.Random()));
    }

    @Test
    void rollDelegatesToInjectedRoller() {
        // Force a d6 face of 4: SequenceRandom returns face-1 internally, total is 4.
        RollRuntime.setRoller(new DiceRoller(SequenceRandom.ofDieFaces(4)));
        assertEquals(4, RollService.roll("1d6").total());
    }
}
