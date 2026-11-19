package studio.modroll.critfall.neoforge.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import studio.modroll.critfall.Critfall;
import studio.modroll.critfall.gametest.HealScenarios;

/** NeoForge registration shim: delegates to the shared {@link HealScenarios} bodies. */
@GameTestHolder(Critfall.MOD_ID)
@PrefixGameTestTemplate(false)
public class HealGameTests {

    private static final String TEMPLATE = "empty";

    @GameTest(template = TEMPLATE)
    public void healRollsHealsAndShowsTheReadout(GameTestHelper helper) {
        HealScenarios.healRollsHealsAndShowsTheReadout(helper);
    }

    @GameTest(template = TEMPLATE)
    public void healStopsAtMaximumHealth(GameTestHelper helper) {
        HealScenarios.healStopsAtMaximumHealth(helper);
    }
}
