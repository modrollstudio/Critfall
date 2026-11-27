package studio.modroll.critfall.neoforge.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import studio.modroll.critfall.Critfall;
import studio.modroll.critfall.gametest.RolledScenarios;

/** NeoForge registration shim: delegates to the shared {@link RolledScenarios} bodies. */
@GameTestHolder(Critfall.MOD_ID)
@PrefixGameTestTemplate(false)
public class RolledGameTests {

    private static final String TEMPLATE = "empty";

    @GameTest(template = TEMPLATE)
    public void attackRollsNowAndLandsLater(GameTestHelper helper) {
        RolledScenarios.attackRollsNowAndLandsLater(helper);
    }

    @GameTest(template = TEMPLATE)
    public void hazardSaveRollsNowAndLandsLater(GameTestHelper helper) {
        RolledScenarios.hazardSaveRollsNowAndLandsLater(helper);
    }

    @GameTest(template = TEMPLATE)
    public void healRollsNowAndLandsLater(GameTestHelper helper) {
        RolledScenarios.healRollsNowAndLandsLater(helper);
    }

    @GameTest(template = TEMPLATE)
    public void damageRollsNowAndLandsLater(GameTestHelper helper) {
        RolledScenarios.damageRollsNowAndLandsLater(helper);
    }
}
