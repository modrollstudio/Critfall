package studio.modroll.critfall.combat;

/**
 * Dry-run decorator (PLAN §8.2.3): the amount reader passes through, but every mutator is a no-op so
 * the vanilla damage stands. {@link DamageInterception} still rolls and reports the attack — a pack
 * dev sees the numbers during normal play without the mod changing their balance. Effect suppression
 * (durability, redirected hits, …) is handled separately by skipping the outcome executor.
 */
final class DryRunDamage implements IncomingDamage {

    private final IncomingDamage delegate;

    DryRunDamage(IncomingDamage delegate) {
        this.delegate = delegate;
    }

    @Override
    public float amount() {
        return delegate.amount();
    }

    @Override
    public void setAmount(float amount) {
        // no-op: vanilla damage passes through unchanged
    }

    @Override
    public void cancel() {
        // no-op: a miss/fumble does not stop vanilla damage in dry-run
    }

    @Override
    public void bypassArmor() {
        // no-op: leave vanilla armor handling alone
    }
}
