package studio.modroll.critfall.combat;

import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Difficulty decorator for a hit on a player. Vanilla scales a mob's damage to a player by difficulty
 * in {@code Player.hurt}, before the loader hook {@link DamageInterception} runs in, so the incoming
 * amount is already scaled and a rolled amount written in its place would lose the scaling. This
 * reads the amount as it was before scaling, so dice derived from it match every difficulty, and
 * scales every amount written, so the roll lands as vanilla's damage would: Easy halves it plus 1 (never
 * raising it), Hard adds half. Same formula as vanilla and NeoForge's default scaling function.
 */
final class DifficultyDamage implements IncomingDamage {

    private final IncomingDamage delegate;
    private final Difficulty difficulty;

    private DifficultyDamage(IncomingDamage delegate, Difficulty difficulty) {
        this.delegate = delegate;
        this.difficulty = difficulty;
    }

    /** {@code dmg} wrapped when vanilla scaled it ({@code source} scales and {@code target} is a player), else as is. */
    static IncomingDamage wrap(IncomingDamage dmg, LivingEntity target, DamageSource source) {
        if (!(target instanceof Player) || !source.scalesWithDifficulty()) {
            return dmg;
        }
        Difficulty difficulty = target.level().getDifficulty();
        return difficulty == Difficulty.NORMAL ? dmg : new DifficultyDamage(dmg, difficulty);
    }

    /** Vanilla's {@code Player.hurt} scaling. */
    static float scale(Difficulty difficulty, float amount) {
        return switch (difficulty) {
            case PEACEFUL -> 0.0F;
            case EASY -> Math.min(amount / 2.0F + 1.0F, amount);
            case NORMAL -> amount;
            case HARD -> amount * 3.0F / 2.0F;
        };
    }

    /**
     * The amount before {@link #scale}. Easy leaves anything under 2 alone and maps 2 and up to 2 and up,
     * so it inverts exactly. Peaceful damage never reaches a hook (vanilla drops a 0 hit), so it is kept.
     */
    static float unscale(Difficulty difficulty, float amount) {
        return switch (difficulty) {
            case PEACEFUL, NORMAL -> amount;
            case EASY -> amount < 2.0F ? amount : (amount - 1.0F) * 2.0F;
            case HARD -> amount * 2.0F / 3.0F;
        };
    }

    @Override
    public float amount() {
        return unscale(difficulty, delegate.amount());
    }

    @Override
    public void setAmount(float amount) {
        delegate.setAmount(scale(difficulty, amount));
    }

    @Override
    public void cancel() {
        delegate.cancel();
    }

    @Override
    public void bypassArmor() {
        delegate.bypassArmor();
    }
}
