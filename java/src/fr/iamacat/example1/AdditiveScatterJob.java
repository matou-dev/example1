package fr.iamacat.example1;

import fr.iamacat.spi.MatouId;
import fr.iamacat.spi.MatouJob;
import fr.iamacat.spi.MatouRng;
import fr.iamacat.spi.Snapshot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * M2 late-additive job: decides {@code scatter_additive} placements from the
 * snapshot, then merges them AFTER owned placements without ever replacing
 * one (Q3-Q4: non-owned content stays additive). Pure, Java 8, zero deps
 * beyond matou-spi.
 */
public final class AdditiveScatterJob implements MatouJob<List<String>> {
    public static final MatouId SCATTER =
            MatouId.parse("example1.overworld:scatter_additive");
    static final int CELLS = 16;

    public List<String> decide(Snapshot snap) {
        if (snap == null) {
            throw new NullPointerException("E_EXAMPLE_SNAPSHOT:null");
        }
        int count = OwnedVeinJob.countOf(countRaw(snap));
        MatouRng rng = MatouRng.forAddress("example1.overworld",
                "scatter_additive", Long.toString(snap.tick()));
        List<String> out = new ArrayList<String>(count);
        for (int i = 0; i < count; i++) {
            out.add(rng.nextInt(CELLS) + "," + rng.nextInt(CELLS));
        }
        return Collections.unmodifiableList(out);
    }

    private static Object countRaw(Snapshot snap) {
        Object raw = snap.get(SCATTER);
        if (raw == null) {
            throw new IllegalArgumentException(
                    "E_EXAMPLE_COUNT:missing <example1.overworld:scatter_additive>");
        }
        return raw;
    }

    /**
     * Late merge: owned order preserved verbatim, additive entries appended
     * only when absent (no replace, no duplicate). Both inputs stay untouched.
     */
    public static List<String> merge(List<String> owned, List<String> additive) {
        if (owned == null) {
            throw new NullPointerException("E_EXAMPLE_MERGE:null owned");
        }
        if (additive == null) {
            throw new NullPointerException("E_EXAMPLE_MERGE:null additive");
        }
        Set<String> seen = new LinkedHashSet<String>(owned);
        List<String> out = new ArrayList<String>(owned);
        for (String cell : additive) {
            if (seen.add(cell)) {
                out.add(cell);
            }
        }
        return Collections.unmodifiableList(out);
    }
}
