package studio.modroll.critfall.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DamageClassifierTest {

    // classify(exempt, alwaysHits, projectile, hasLivingAttacker, direct)

    @Test
    void exemptWinsOverEverything() {
        assertEquals(DamageCategory.EXEMPT, DamageClassifier.classify(true, true, true, true, true));
        assertEquals(DamageCategory.EXEMPT, DamageClassifier.classify(true, false, false, false, false));
    }

    @Test
    void alwaysHitsBeatsRemainingCategories() {
        assertEquals(DamageCategory.ALWAYS_HITS, DamageClassifier.classify(false, true, true, true, true));
        assertEquals(DamageCategory.ALWAYS_HITS, DamageClassifier.classify(false, true, false, false, false));
    }

    @Test
    void projectileTagMeansProjectile() {
        assertEquals(DamageCategory.PROJECTILE, DamageClassifier.classify(false, false, true, true, false));
    }

    @Test
    void dispenserArrowWithoutAttackerIsStillProjectile() {
        assertEquals(DamageCategory.PROJECTILE, DamageClassifier.classify(false, false, true, false, false));
    }

    @Test
    void noLivingAttackerIsEnvironmental() {
        assertEquals(DamageCategory.ENVIRONMENTAL, DamageClassifier.classify(false, false, false, false, false));
        assertEquals(DamageCategory.ENVIRONMENTAL, DamageClassifier.classify(false, false, false, false, true));
    }

    @Test
    void directLivingAttackIsMelee() {
        assertEquals(DamageCategory.MELEE, DamageClassifier.classify(false, false, false, true, true));
    }

    @Test
    void indirectLivingAttackIsOther() {
        assertEquals(DamageCategory.OTHER, DamageClassifier.classify(false, false, false, true, false));
    }
}
