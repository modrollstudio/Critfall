package studio.modroll.critfall.combat;

import net.minecraft.world.entity.Entity;

/**
 * The targets a rolled attack missed with one arrow. Vanilla bounces an arrow whose hurt failed back
 * the way it came, so a missed shot would fly back into its shooter and roll again against them. Each
 * loader's {@code AbstractArrow} mixin implements this: an arrow that missed a target flies on past it
 * and never strikes it again. {@link DamageInterception} records the miss.
 */
public interface MissedShotHolder {

    /** The in-flight hurt of this arrow on {@code target} is a rolled miss. */
    void critfall$addMissed(Entity target);
}
