package fr.iamacat.example1;

import fr.iamacat.spi.Cell;
import fr.iamacat.spi.Counts;
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
    public static final MatouId SCATTER = ExampleIds.SCATTER;

    public List<String> decide(Snapshot snap) {
        if (snap == null) {
            throw new NullPointerException("E_EXAMPLE_SNAPSHOT:null");
        }
        int count = Counts.positive(snap.get(SCATTER), SCATTER,
                ExampleIds.COUNT_CODE);
        MatouRng rng = MatouRng.forAddress(SCATTER.namespace, SCATTER.name,
                Long.toString(snap.tick()));
        List<String> out = new ArrayList<String>(count);
        for (int i = 0; i < count; i++) {
            out.add(Cell.of(rng.nextInt(ExampleIds.GRID),
                    rng.nextInt(ExampleIds.GRID)).render());
        }
        return Collections.unmodifiableList(out);
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
