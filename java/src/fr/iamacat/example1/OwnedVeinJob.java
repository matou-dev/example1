package fr.iamacat.example1;

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
    public static final MatouId VEIN = MatouId.parse("example1.content:my_vein");
    static final int CELLS = 16;

    public List<String> decide(Snapshot snap) {
        if (snap == null) {
            throw new NullPointerException("E_EXAMPLE_SNAPSHOT:null");
        }
        int count = countOf(snap.get(VEIN));
        MatouRng rng = MatouRng.forAddress("example1.content", "my_vein",
                Long.toString(snap.tick()));
        List<String> out = new ArrayList<String>(count);
        for (int i = 0; i < count; i++) {
            out.add(rng.nextInt(CELLS) + "," + rng.nextInt(CELLS));
        }
        return Collections.unmodifiableList(out);
    }

    static int countOf(Object raw) {
        return countOf(raw, VEIN);
    }

    static int countOf(Object raw, MatouId id) {
        if (raw == null) {
            throw new IllegalArgumentException(
                    "E_EXAMPLE_COUNT:missing <" + id + ">");
        }
        if (!(raw instanceof Number)) {
            throw new IllegalArgumentException(
                    "E_EXAMPLE_COUNT:type <" + raw + "> (want u32 number)");
        }
        int count = ((Number) raw).intValue();
        if (count <= 0) {
            throw new IllegalArgumentException(
                    "E_EXAMPLE_COUNT:range <" + raw + "> (want > 0)");
        }
        return count;
    }
}
