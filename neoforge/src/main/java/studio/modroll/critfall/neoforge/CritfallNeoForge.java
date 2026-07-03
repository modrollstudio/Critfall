package studio.modroll.critfall.neoforge;

import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import studio.modroll.critfall.Critfall;

@Mod(Critfall.MOD_ID)
public final class CritfallNeoForge {

    public CritfallNeoForge() {
        Critfall.init();
        NeoForge.EVENT_BUS.addListener(DamageEventHandler::onIncomingDamage);
    }
}
