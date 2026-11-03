package studio.modroll.critfall.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.world.Difficulty;
import org.junit.jupiter.api.Test;

class DifficultyDamageTest {

    @Test
    void scalesLikeVanillaPlayerHurt() {
        assertEquals(3.5F, DifficultyDamage.scale(Difficulty.EASY, 5.0F));
        assertEquals(1.5F, DifficultyDamage.scale(Difficulty.EASY, 1.5F), "Easy never raises a small hit");
        assertEquals(5.0F, DifficultyDamage.scale(Difficulty.NORMAL, 5.0F));
        assertEquals(7.5F, DifficultyDamage.scale(Difficulty.HARD, 5.0F));
        assertEquals(0.0F, DifficultyDamage.scale(Difficulty.PEACEFUL, 5.0F));
    }

    @Test
    void unscaleRecoversTheAmountBeforeScaling() {
        for (Difficulty difficulty : new Difficulty[] {Difficulty.EASY, Difficulty.NORMAL, Difficulty.HARD}) {
            for (float amount : new float[] {0.5F, 1.0F, 1.9F, 2.0F, 3.0F, 5.0F, 7.0F, 21.0F}) {
                float scaled = DifficultyDamage.scale(difficulty, amount);
                assertEquals(amount, DifficultyDamage.unscale(difficulty, scaled), 1.0E-5F, difficulty + " " + amount);
            }
        }
    }
}
