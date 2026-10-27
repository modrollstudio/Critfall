package studio.modroll.critfall.neoforge.uishots;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import studio.modroll.critfall.Critfall;
import studio.modroll.critfall.uishots.UiShots;

/** Drives the dev-only screenshot run; every dev run loads this class, but only runUiShots names a folder. */
@Mod(value = Critfall.MOD_ID, dist = Dist.CLIENT)
public final class NeoForgeUiShots {

    public NeoForgeUiShots() {
        if (UiShots.directory().isPresent()) {
            NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> UiShots.tick());
        }
    }
}
