package studio.modroll.critfall.uishots;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.BooleanSupplier;

/** A queue of steps run one client tick at a time; a step stays at the head until it reports done. */
final class ShotScript {

    private static final int WAIT_TIMEOUT_TICKS = 600;

    private final Deque<Step> steps = new ArrayDeque<>();
    private int ticksOnStep;

    @FunctionalInterface
    interface Step {
        /** Returns whether the step is done; {@code tick} counts the ticks spent on it so far. */
        boolean run(int tick);
    }

    ShotScript then(Runnable action) {
        steps.add(tick -> {
            action.run();
            return true;
        });
        return this;
    }

    ShotScript waitTicks(int ticks) {
        steps.add(tick -> tick >= ticks);
        return this;
    }

    /** Fails the run when {@code condition} still does not hold after the timeout. */
    ShotScript waitFor(String what, BooleanSupplier condition) {
        steps.add(tick -> {
            if (condition.getAsBoolean()) {
                return true;
            }
            if (tick > WAIT_TIMEOUT_TICKS) {
                throw new IllegalStateException("UI shots timed out waiting for " + what);
            }
            return false;
        });
        return this;
    }

    boolean finished() {
        return steps.isEmpty();
    }

    void tick() {
        Step step = steps.peek();
        if (step == null) {
            return;
        }
        if (step.run(ticksOnStep++)) {
            steps.poll();
            ticksOnStep = 0;
        }
    }
}
