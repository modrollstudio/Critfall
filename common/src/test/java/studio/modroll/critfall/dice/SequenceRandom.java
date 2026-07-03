package studio.modroll.critfall.dice;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.random.RandomGenerator;

/**
 * Test RNG that returns a predetermined sequence of die faces, letting tests force exact rolls
 * (e.g. a nat 1 or nat 20). Fails loudly if a forced face can't come from the requested die or if
 * more rolls happen than were scripted.
 */
public final class SequenceRandom implements RandomGenerator {

    private final Queue<Integer> faces = new ArrayDeque<>();

    private SequenceRandom(int... faces) {
        for (int face : faces) {
            this.faces.add(face);
        }
    }

    /** Each value is the die face the next roll should show (1-based). */
    public static SequenceRandom ofDieFaces(int... faces) {
        return new SequenceRandom(faces);
    }

    @Override
    public int nextInt(int bound) {
        Integer face = faces.poll();
        if (face == null) {
            throw new AssertionError("SequenceRandom exhausted: more rolls happened than were scripted");
        }
        if (face < 1 || face > bound) {
            throw new AssertionError("forced face " + face + " is impossible for a d" + bound);
        }
        return face - 1;
    }

    @Override
    public long nextLong() {
        throw new AssertionError("dice rolling must go through nextInt(bound)");
    }

    public boolean isExhausted() {
        return faces.isEmpty();
    }
}
