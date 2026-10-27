package studio.modroll.critfall.feedback;

import net.minecraft.world.entity.LivingEntity;
import studio.modroll.critfall.combat.CritfallTags;

/**
 * Which killing blows always get a flavor line, whatever {@code feedback.flavor.kill_chance_percent}
 * says: a mob with a name (a name tag, or a mod's named elites and bosses such as Critfall: Depths
 * gives its own) and any boss in {@code #c:bosses}.
 */
public final class FlavorKills {

    private FlavorKills() {}

    public static boolean alwaysFlavored(LivingEntity target) {
        return target.hasCustomName() || target.getType().is(CritfallTags.BOSSES);
    }
}
