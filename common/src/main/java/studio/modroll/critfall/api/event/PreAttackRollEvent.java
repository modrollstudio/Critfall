package studio.modroll.critfall.api.event;

import net.minecraft.world.entity.LivingEntity;
import studio.modroll.critfall.api.AttackContext;
import studio.modroll.critfall.dice.RollMode;

/**
 * Fired before the d20 is rolled (PLAN §4.4). Listeners may raise/lower the attack bonus, force
 * advantage/disadvantage, or cancel the attack entirely (a canceled attack deals no damage and runs
 * no outcome tables). {@code attacker}/{@code target} may be null in tests.
 */
public final class PreAttackRollEvent {

    private final LivingEntity attacker;
    private final LivingEntity target;
    private final AttackContext context;
    private int attackBonus;
    private RollMode mode;
    private boolean canceled;

    public PreAttackRollEvent(
            LivingEntity attacker, LivingEntity target, AttackContext context, int attackBonus, RollMode mode) {
        this.attacker = attacker;
        this.target = target;
        this.context = context;
        this.attackBonus = attackBonus;
        this.mode = mode;
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

    public void attackBonus(int value) {
        this.attackBonus = value;
    }

    public RollMode mode() {
        return mode;
    }

    public void mode(RollMode value) {
        this.mode = value;
    }

    public void cancel() {
        this.canceled = true;
    }

    public boolean isCanceled() {
        return canceled;
    }
}
