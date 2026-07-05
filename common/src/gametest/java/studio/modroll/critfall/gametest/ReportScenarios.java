package studio.modroll.critfall.gametest;

import java.util.List;
import net.minecraft.gametest.framework.GameTestHelper;
import studio.modroll.critfall.command.CritfallCommands;
import studio.modroll.critfall.tools.CoverageReport;

/**
 * {@code /critfall report} runs end-to-end AND classifies coverage correctly: a mob covered by the
 * shipped default pack (zombie) reports {@code profile:<id>}, while a living entity with no shipped
 * profile (armor stand) reports {@code fallback}.
 */
public final class ReportScenarios {

    private ReportScenarios() {}

    public static void reportClassifiesProfileVsFallback(GameTestHelper helper) {
        List<CoverageReport.EntityRow> rows = CritfallCommands.collectEntityRows();

        CoverageReport.EntityRow zombie = rows.stream()
                .filter(r -> r.id().equals("minecraft:zombie"))
                .findFirst()
                .orElse(null);
        if (zombie == null) {
            helper.fail("no coverage row for minecraft:zombie");
        }
        if (!zombie.source().startsWith("profile:")) {
            helper.fail("zombie should be profiled, got " + zombie.source());
        }

        CoverageReport.EntityRow stand = rows.stream()
                .filter(r -> r.id().equals("minecraft:armor_stand"))
                .findFirst()
                .orElse(null);
        if (stand == null) {
            helper.fail("no coverage row for minecraft:armor_stand");
        }
        if (!stand.source().equals("fallback")) {
            helper.fail("armor_stand should be a fallback, got " + stand.source());
        }

        int result = CommandScenarios.execute(helper, CommandScenarios.serverSource(helper), "critfall report");
        if (result < 1) {
            helper.fail("/critfall report returned " + result);
        }
        helper.succeed();
    }
}
