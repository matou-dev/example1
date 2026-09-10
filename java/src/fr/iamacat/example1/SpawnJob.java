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
import java.util.Map;

/**
 * Spawn job: pure budgeted mob placement over a bridge-sealed census (hub
 * decisions/SPAWN.md). Reads the sealed living census under {@link #CENSUS}
 * (entity id to spawn cell {@code "x,y,z:mob"}) plus the spawn table under
 * {@link #TABLE} (the content mob ref every spawn carries) plus the cap
 * under {@link #CAP} plus the per-tick budget under {@link #BUDGET} plus
 * the spawn ordinate range under {@link #Y}, and emits one spawn cell
 * {@code "x,y,z:ns:mob"} per due slot — the content mob ref, never a
 * vanilla entity name (the bridge resolves the host, same split as loot
 * carriers and structure palettes). Pure like every job ({@code MatouJob}
 * contract): the census store itself stays bridge-owned, this job only
 * reads the sealed snapshot. Java 8, zero deps beyond matou-spi.
 *
 * <p>Budget math: {@code room = cap - census.size()}, {@code due =
 * min(budget, max(room, 0))} — a full census means no spawn, never a
 * negative one. Due slots land at seeded pads ({@link MatouRng} addressed
 * by namespace, mob and tick, x/z in {@link ExampleIds#GRID}, y in the
 * sealed range), so live and verdict replay agree tick by tick. Missing,
 * mistyped or out-of-range states are refused loudly under
 * {@code E_SPAWN_*}, never defaulted.
 */
public final class SpawnJob implements MatouJob<List<String>> {
    /** Sealed living census: entity id to spawn cell. */
    public static final MatouId CENSUS =
            MatouId.of("example1.spawn", "census");
    /** Spawn table: the content mob ref every spawn carries. */
    public static final MatouId TABLE =
            MatouId.of("example1.spawn", "table");
    /** Living cap: census at cap means no spawn (never negative). */
    public static final MatouId CAP =
            MatouId.of("example1.spawn", "cap");
    /** Spawns landed per tick while room remains. */
    public static final MatouId BUDGET =
            MatouId.of("example1.spawn", "budget");
    /** Spawn ordinate range: {@code [yMin, yMax]} long pair, inclusive. */
    public static final MatouId Y =
            MatouId.of("example1.spawn", "y");
    /** Refusal prefix for the living cap (Counts-style trio). */
    public static final String CAP_CODE = "E_SPAWN_CAP";
    /** Refusal prefix for the per-tick budget (Counts-style trio). */
    public static final String BUDGET_CODE = "E_SPAWN_BUDGET";
    /** Refusal prefix for the ordinate range. */
    public static final String Y_CODE = "E_SPAWN_Y";

    @Override
    public List<String> decide(Snapshot snap) {
        if (snap == null) {
            throw new NullPointerException("E_SPAWN_JOB:null snapshot");
        }
        Map<?, ?> census = snap.mapOf(CENSUS);
        String mob = tableOf(snap.get(TABLE));
        int cap = Counts.positive(snap.get(CAP), CAP, CAP_CODE);
        int budget = Counts.positive(snap.get(BUDGET), BUDGET, BUDGET_CODE);
        int[] range = yOf(snap.get(Y));
        int room = cap - censusSize(census, mob);
        int due = Math.min(budget, Math.max(room, 0));
        List<String> out = new ArrayList<String>(due);
        if (due == 0) {
            return Collections.unmodifiableList(out);
        }
        MatouRng rng = MatouRng.forAddress("example1.spawn", mob,
                Long.toString(snap.tick()));
        int span = range[1] - range[0] + 1;
        for (int s = 0; s < due; s++) {
            int x = rng.nextInt(ExampleIds.GRID);
            int z = rng.nextInt(ExampleIds.GRID);
            int y = range[0] + (span == 1 ? 0 : rng.nextInt(span));
            out.add(Cell.of(x, y, z, mob).render());
        }
        return Collections.unmodifiableList(out);
    }

    /**
     * Table rule: one non-empty namespaced content ref
     * ({@code "ns:name"} — the bridge matches the host, content never
     * names vanilla).
     */
    static String tableOf(Object raw) {
        if (raw == null) {
            throw new IllegalArgumentException(
                    "E_SPAWN_JOB:missing <" + TABLE + ">");
        }
        if (!(raw instanceof String) || ((String) raw).isEmpty()
                || ((String) raw).indexOf(':') < 0) {
            throw new IllegalArgumentException("E_SPAWN_JOB:type <"
                    + raw + "> (want \"ns:mob\" content ref)");
        }
        return (String) raw;
    }

    /** Ordinate range rule: long pair {@code [yMin, yMax]}, ordered. */
    static int[] yOf(Object raw) {
        if (raw == null) {
            throw new IllegalArgumentException(
                    Y_CODE + ":missing <" + Y + ">");
        }
        if (!(raw instanceof List)) {
            throw new IllegalArgumentException(Y_CODE + ":type <"
                    + raw + "> (want [yMin, yMax] longs)");
        }
        List<?> pair = (List<?>) raw;
        if (pair.size() != 2 || !(pair.get(0) instanceof Number)
                || !(pair.get(1) instanceof Number)) {
            throw new IllegalArgumentException(Y_CODE + ":shape <"
                    + raw + "> (want [yMin, yMax] longs)");
        }
        int min = ((Number) pair.get(0)).intValue();
        int max = ((Number) pair.get(1)).intValue();
        if (min < 0 || max < min) {
            throw new IllegalArgumentException(Y_CODE + ":range <"
                    + raw + "> (want 0 <= yMin <= yMax)");
        }
        return new int[]{min, max};
    }

    /**
     * Census rule: entity id strings to spawn cells the table owns.
     * A foreign mob in the census refuses — the census only ever holds
     * what this table spawned, so anything else is a leak, never a
     * quiet neighbour.
     */
    static int censusSize(Map<?, ?> census, String mob) {
        for (Map.Entry<?, ?> e : census.entrySet()) {
            if (!(e.getKey() instanceof String)
                    || ((String) e.getKey()).isEmpty()) {
                throw new IllegalArgumentException("E_SPAWN_JOB:type <"
                        + e.getKey() + "> (want entity-id string)");
            }
            try {
                if (Integer.parseInt((String) e.getKey()) < 0) {
                    throw new NumberFormatException("negative");
                }
            } catch (NumberFormatException bad) {
                throw new IllegalArgumentException("E_SPAWN_JOB:type <"
                        + e.getKey() + "> (want entity-id string)");
            }
            if (!(e.getValue() instanceof String)) {
                throw new IllegalArgumentException("E_SPAWN_JOB:type <"
                        + e.getValue() + "> (want \"x,y,z:mob\" cell)");
            }
            String cell = (String) e.getValue();
            int cut = cell.indexOf(':');
            if (cut < 0 || !cell.substring(cut + 1).equals(mob)) {
                throw new IllegalArgumentException("E_SPAWN_JOB:foreign <"
                        + cell + "> (census holds <" + mob + "> only)");
            }
            String[] parts = cell.substring(0, cut).split(",", -1);
            try {
                if (parts.length != 3) {
                    throw new NumberFormatException("shape");
                }
                Integer.parseInt(parts[0]);
                Integer.parseInt(parts[1]);
                Integer.parseInt(parts[2]);
            } catch (NumberFormatException bad) {
                throw new IllegalArgumentException("E_SPAWN_JOB:shape <"
                        + cell + "> (want \"x,y,z:mob\")");
            }
        }
        return census.size();
    }
}
