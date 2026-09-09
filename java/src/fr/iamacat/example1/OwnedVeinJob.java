package fr.iamacat.example1;

import fr.iamacat.spi.Cell;
import fr.iamacat.spi.Counts;
import fr.iamacat.spi.MatouId;
import fr.iamacat.spi.MatouJob;
import fr.iamacat.spi.MatouRng;
import fr.iamacat.spi.Snapshot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * M2 owned backend job: decides {@code my_vein} placements from the snapshot.
 * Pure (no IO, no Minecraft, no clock): same snapshot in, equal decision out.
 * Count comes from the snapshot (backend-owned data); absent or bad count is
 * refused loudly, never defaulted. Java 8, zero deps beyond matou-spi.
 */
public final class OwnedVeinJob implements MatouJob<List<String>> {
    public static final MatouId VEIN = ExampleIds.VEIN;

    public List<String> decide(Snapshot snap) {
        if (snap == null) {
            throw new NullPointerException("E_EXAMPLE_SNAPSHOT:null");
        }
        int count = countOf(snap.get(VEIN));
        MatouRng rng = MatouRng.forAddress(VEIN.namespace, VEIN.name,
                Long.toString(snap.tick()));
        List<String> out = new ArrayList<String>(count);
        for (int i = 0; i < count; i++) {
            out.add(Cell.of(rng.nextInt(ExampleIds.GRID),
                    rng.nextInt(ExampleIds.GRID)).render());
        }
        return Collections.unmodifiableList(out);
    }

    static int countOf(Object raw) {
        return countOf(raw, VEIN);
    }

    static int countOf(Object raw, MatouId id) {
        return Counts.positive(raw, id, ExampleIds.COUNT_CODE);
    }
}
