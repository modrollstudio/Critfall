package studio.modroll.critfall.gametest;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.random.RandomGenerator;

/**
 * GameTest twin of the unit-test SequenceRandom: forces exact die faces so in-game tests can
 * script a nat 1, a nat 20, or any roll sequence.
 */
final class ScriptedRandom implements RandomGenerator {

    private final Queue<Integer> faces = new ArrayDeque<>();

    private ScriptedRandom(int... faces) {
        for (int face : faces) {
            this.faces.add(face);
        }
    }

    /** Each value is the die face the next roll should show (1-based). */
    static ScriptedRandom ofDieFaces(int... faces) {
        return new ScriptedRandom(faces);
    }

    @Override
    public int nextInt(int bound) {
        Integer face = faces.poll();
        if (face == null) {
            throw new IllegalStateException("ScriptedRandom exhausted: more rolls happened than were scripted");
        }
        if (face < 1 || face > bound) {
            throw new IllegalStateException("forced face " + face + " is impossible for a d" + bound);
        }
        return face - 1;
    }

    @Override
    public long nextLong() {
        throw new IllegalStateException("dice rolling must go through nextInt(bound)");
    }

    boolean isExhausted() {
        return faces.isEmpty();
    }
}
