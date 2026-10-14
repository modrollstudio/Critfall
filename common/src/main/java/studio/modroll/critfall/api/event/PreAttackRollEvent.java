package studio.modroll.critfall.api.event;

import net.minecraft.world.entity.LivingEntity;
import studio.modroll.critfall.api.AttackContext;
import studio.modroll.critfall.api.dice.RollMode;

/**
 * Fired before the d20 is rolled (PLAN §4.4). Listeners may raise/lower the attack bonus, force
 * advantage/disadvantage, or cancel the attack entirely (a canceled attack deals no damage and runs
 * no outcome tables). Since 0.2.14 they can also {@link #grantAdvantage grant advantage} or {@link
 * #imposeDisadvantage impose disadvantage}, combined the 5e way like {@link HazardRollEvent}, {@link #addBonus
 * add a bonus} and {@link #rerollOnes reroll a natural 1}. {@code attacker}/{@code target} may be null in tests.
 */
public final class PreAttackRollEvent {

    private final LivingEntity attacker;
    private final LivingEntity target;
    private final AttackContext context;
    private int attackBonus;
    private boolean advantage;
    private boolean disadvantage;
    private boolean rerollOnes;
    private boolean canceled;

    /** {@code mode} is the attack's own mode, and counts as one source of advantage or disadvantage. */
    public PreAttackRollEvent(
            LivingEntity attacker, LivingEntity target, AttackContext context, int attackBonus, RollMode mode) {
        this.attacker = attacker;
        this.target = target;
        this.context = context;
        this.attackBonus = attackBonus;
        mode(mode);
    }

    public LivingEntity attacker() {
        return attacker;
    }

    public LivingEntity target() {
        return target;
    }

    public AttackContext context() {
        return context;
    }

    public int attackBonus() {
        return attackBonus;
    }

    /** Replaces the attack bonus, including what {@link #addBonus} added so far. */
    public void attackBonus(int value) {
        this.attackBonus = value;
    }

    /**
     * Adds {@code amount} to the attack bonus. Bonuses from several listeners add up; a negative amount is a
     * penalty. The readout's modifier includes it. Since 0.2.14.
     */
    public void addBonus(int amount) {
        this.attackBonus += amount;
    }

    /**
     * The mode the d20 will roll with, from every source so far (the attack's own, earlier listeners' {@link
     * #grantAdvantage} and {@link #imposeDisadvantage}, or the last {@link #mode(RollMode)}): advantage alone
     * rolls with advantage, disadvantage alone with disadvantage, and both, or neither, roll normally.
     */
    public RollMode mode() {
        if (advantage == disadvantage) {
            return RollMode.NORMAL;
        }
        return advantage ? RollMode.ADVANTAGE : RollMode.DISADVANTAGE;
    }

    /**
     * Replaces the mode, dropping every source so far: {@link #mode()} then reads {@code value}. Later
     * listeners' {@link #grantAdvantage} and {@link #imposeDisadvantage} combine with it.
     */
    public void mode(RollMode value) {
        advantage = value == RollMode.ADVANTAGE;
        disadvantage = value == RollMode.DISADVANTAGE;
    }

    /** Adds a source of advantage. Several count as one. Since 0.2.14. */
    public void grantAdvantage() {
        advantage = true;
    }

    /** Adds a source of disadvantage. Several count as one. Since 0.2.14. */
    public void imposeDisadvantage() {
        disadvantage = true;
    }

    /**
     * A natural 1 on the attack's d20 is rolled again, once, and the new roll stands even if it is another 1
     * (the halfling's Luck). With advantage or disadvantage it happens when the kept die is a 1, and only that
     * die rolls again. Several listeners asking still reroll once. Since 0.2.14.
     */
    public void rerollOnes() {
        rerollOnes = true;
    }

    /** Whether a listener called {@link #rerollOnes}. Since 0.2.14. */
    public boolean rerollsOnes() {
        return rerollOnes;
    }

    public void cancel() {
        this.canceled = true;
    }

    public boolean isCanceled() {
        return canceled;
    }
}
