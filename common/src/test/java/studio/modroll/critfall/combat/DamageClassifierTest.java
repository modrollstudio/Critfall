package studio.modroll.critfall.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DamageClassifierTest {

    // classify(exempt, alwaysHits, spellTagged, projectile, hasLivingAttacker, direct)

    @Test
    void exemptWinsOverEverything() {
        assertEquals(DamageCategory.EXEMPT, DamageClassifier.classify(true, true, true, true, true, true));
        assertEquals(DamageCategory.EXEMPT, DamageClassifier.classify(true, false, false, false, false, false));
    }

    @Test
    void alwaysHitsBeatsRemainingCategories() {
        assertEquals(DamageCategory.ALWAYS_HITS, DamageClassifier.classify(false, true, true, true, true, true));
        assertEquals(DamageCategory.ALWAYS_HITS, DamageClassifier.classify(false, true, false, false, false, false));
    }

    @Test
    void spellTagBeatsProjectileAndMeleeHeuristics() {
        // A projectile-tagged spell (Iron's spell projectiles) stays a spell.
        assertEquals(DamageCategory.SPELL, DamageClassifier.classify(false, false, true, true, true, false));
        // Spell mods often make the CASTER the direct entity — must not read as melee.
        assertEquals(DamageCategory.SPELL, DamageClassifier.classify(false, false, true, false, true, true));
        // Casterless tagged magic is still classified SPELL; the pipeline passes it through.
        assertEquals(DamageCategory.SPELL, DamageClassifier.classify(false, false, true, false, false, false));
    }

    @Test
    void projectileTagMeansProjectile() {
        assertEquals(DamageCategory.PROJECTILE, DamageClassifier.classify(false, false, false, true, true, false));
    }

    @Test
    void dispenserArrowWithoutAttackerIsStillProjectile() {
        assertEquals(DamageCategory.PROJECTILE, DamageClassifier.classify(false, false, false, true, false, false));
    }

    @Test
    void noLivingAttackerIsEnvironmental() {
        assertEquals(DamageCategory.ENVIRONMENTAL, DamageClassifier.classify(false, false, false, false, false, false));
        assertEquals(DamageCategory.ENVIRONMENTAL, DamageClassifier.classify(false, false, false, false, false, true));
    }

    @Test
    void directLivingAttackIsMelee() {
        assertEquals(DamageCategory.MELEE, DamageClassifier.classify(false, false, false, false, true, true));
    }

    @Test
    void indirectLivingAttackIsSpell() {
        assertEquals(DamageCategory.SPELL, DamageClassifier.classify(false, false, false, false, true, false));
    }
}
