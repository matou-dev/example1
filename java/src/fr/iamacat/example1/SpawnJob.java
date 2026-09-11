package fr.iamacat.example1;

import fr.iamacat.spi.Cell;
import fr.iamacat.spi.Counts;
import fr.iamacat.spi.MatouId;
import fr.iamacat.spi.MatouJob;
import fr.iamacat.spi.MatouRng;
import fr.iamacat.spi.Snapshot;
import fr.iamacat.spi.SpawnStates;
import fr.iamacat.spi.StateVocabulary;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Spawn job: pure budgeted mob placement over a bridge-sealed census (hub
 * decisions/SPAWN.md). Reads the sealed living census under {@link #CENSUS}
 * (entity id to spawn cell {@code "x,y,z:mob"}) plus the spawn table under
 * {@link #TABLE} plus the caps under {@link #CAP} plus the per-tick budgets
 * under {@link #BUDGET} plus the spawn ordinate ranges under {@link #Y},
 * and emits one spawn cell {@code "x,y,z:ns:mob"} per due slot — the
 * content mob ref, never a vanilla entity name (the bridge resolves the
 * host, same split as loot carriers and structure palettes). Pure like
 * every job ({@code MatouJob} contract): the census store itself stays
 * bridge-owned, this job only reads the sealed snapshot. Java 8, zero deps
 * beyond matou-spi.
 *
 * <p>Two seal shapes, one decision: sole-mob seals carry the single
 * qualified mob ref string plus one cap number, one budget number and one
 * {@code [yMin, yMax]} pair (exactly the pre-second-beast seal — those
 * snapshots decide byte-identical cells, the bridge is untouched); per-mob
 * seals carry the ordered mob list under {@link #TABLE} plus one
 * {@code mob -> value} map under {@link #CAP} / {@link #BUDGET} plus one
 * {@code mob -> [yMin, yMax]} map under {@link #Y} (keys are the qualified
 * refs listed under {@link #TABLE}). Shapes never mix: a list table wants
 * the three maps, a string table wants the three sole values.
 *
 * <p>Budget math per mob: {@code room = cap - census(mob)}, {@code due =
 * min(budget, max(room, 0))} — a full census means no spawn, never a
 * negative one. Due slots land at seeded pads ({@link MatouRng} addressed
 * by namespace, mob and tick, x/z in {@link ExampleIds#GRID}, y in that
 * mob's sealed range), so live and verdict replay agree tick by tick;
 * mobs decide in {@link #TABLE} order, and a sole mob (either shape)
 * decides exactly the pre-second-beast cells. Missing, mistyped or
 * out-of-range states are refused loudly under
 * {@code E_SPAWN_*}, never defaulted.
 */
public final class SpawnJob implements MatouJob<List<String>> {
    /** Shared spawn vocabulary (T3 registry: the seal resolves the same). */
    private static final StateVocabulary VOCABULARY =
            SpawnStates.vocabulary(ExampleIds.SPAWN_NS);
    /** Sealed living census: entity id to spawn cell. */
    public static final MatouId CENSUS = SpawnStates.census(VOCABULARY);
    /**
     * Spawn table: sole-mob seals carry the single content mob ref string,
     * per-mob seals the ordered list of content mob refs every spawn
     * carries.
     */
    public static final MatouId TABLE = SpawnStates.table(VOCABULARY);
    /**
     * Living caps: sole-mob seals carry the single cap number, per-mob
     * seals the qualified-mob to cap map (census at cap means no spawn,
     * never negative).
     */
    public static final MatouId CAP = SpawnStates.cap(VOCABULARY);
    /**
     * Per-tick budgets: sole-mob seals carry the single budget number,
     * per-mob seals the qualified-mob to budget map (landings per tick
     * while room remains).
     */
    public static final MatouId BUDGET = SpawnStates.budget(VOCABULARY);
    /**
     * Spawn ordinate ranges: sole-mob seals carry the {@code [yMin, yMax]}
     * long pair, per-mob seals the qualified-mob to pair map, inclusive.
     */
    public static final MatouId Y = SpawnStates.y(VOCABULARY);
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
        List<String> table = tableOf(snap.get(TABLE));
        Map<String, Integer> caps = capsOf(snap.get(CAP), table);
        Map<String, Integer> budgets = budgetsOf(snap.get(BUDGET), table);
        Map<String, int[]> bands = bandsOf(snap.get(Y), table);
        Map<String, Integer> counts = censusCounts(census, table);
        Map<String, Integer> dues = new LinkedHashMap<String, Integer>();
        int total = 0;
        for (String mob : table) {
            int room = caps.get(mob).intValue()
                    - counts.get(mob).intValue();
            int due = Math.min(budgets.get(mob).intValue(),
                    Math.max(room, 0));
            dues.put(mob, Integer.valueOf(due));
            total += due;
        }
        List<String> out = new ArrayList<String>(total);
        if (total == 0) {
            return Collections.unmodifiableList(out);
        }
        for (String mob : table) {
            int due = dues.get(mob).intValue();
            if (due == 0) {
                continue;
            }
            int[] range = bands.get(mob);
            MatouRng rng = MatouRng.forAddress(ExampleIds.SPAWN_NS, mob,
                    Long.toString(snap.tick()));
            int span = range[1] - range[0] + 1;
            for (int s = 0; s < due; s++) {
                int x = rng.nextInt(ExampleIds.GRID);
                int z = rng.nextInt(ExampleIds.GRID);
                int y = range[0] + (span == 1 ? 0 : rng.nextInt(span));
                out.add(Cell.of(x, y, z, mob).render());
            }
        }
        return Collections.unmodifiableList(out);
    }

    /**
     * Table rule: one non-empty namespaced content ref
     * ({@code "ns:name"} — the bridge matches the host, content never
     * names vanilla) or, since the second-beast tranche, the ordered list
     * of those refs (file order — mobs decide in this order). A sole
     * string stays valid for sole-mob seals and decides exactly the
     * pre-tranche cells.
     */
    static List<String> tableOf(Object raw) {
        if (raw == null) {
            throw new IllegalArgumentException(
                    "E_SPAWN_JOB:missing <" + TABLE + ">");
        }
        if (raw instanceof String) {
            String mob = (String) raw;
            if (mob.isEmpty() || mob.indexOf(':') < 0) {
                throw new IllegalArgumentException("E_SPAWN_JOB:type <"
                        + raw + "> (want \"ns:mob\" content ref)");
            }
            return Collections.singletonList(mob);
        }
        if (!(raw instanceof List)) {
            throw new IllegalArgumentException("E_SPAWN_JOB:type <"
                    + raw + "> (want \"ns:mob\" content ref)");
        }
        List<?> refs = (List<?>) raw;
        if (refs.isEmpty()) {
            throw new IllegalArgumentException("E_SPAWN_JOB:empty <"
                    + TABLE + "> (no mob funds the census)");
        }
        List<String> table = new ArrayList<String>(refs.size());
        for (Object ref : refs) {
            if (!(ref instanceof String) || ((String) ref).isEmpty()
                    || ((String) ref).indexOf(':') < 0) {
                throw new IllegalArgumentException("E_SPAWN_JOB:type <"
                        + ref + "> (want \"ns:mob\" content ref)");
            }
            if (table.contains(ref)) {
                throw new IllegalArgumentException("E_SPAWN_JOB:dupe <"
                        + ref + "> (one row per mob — a second row "
                        + "would be a quiet pick)");
            }
            table.add((String) ref);
        }
        return Collections.unmodifiableList(table);
    }

    /**
     * Cap rule: the sole cap number (sole-mob table — exactly the
     * pre-tranche seal) or the qualified-mob to cap map (per-mob table:
     * every sealed mob funds a positive cap, no foreign keys).
     */
    static Map<String, Integer> capsOf(Object raw, List<String> table) {
        if (raw == null) {
            throw new IllegalArgumentException(
                    CAP_CODE + ":missing <" + CAP + ">");
        }
        if (raw instanceof Number) {
            if (table.size() != 1) {
                throw new IllegalArgumentException(CAP_CODE + ":shape <"
                        + raw + "> (a sole cap wants a sole-mob table — "
                        + "per-mob seals carry a mob->cap map)");
            }
            Map<String, Integer> caps =
                    new LinkedHashMap<String, Integer>();
            caps.put(table.get(0), Integer.valueOf(
                    Counts.positive(raw, CAP, CAP_CODE)));
            return Collections.unmodifiableMap(caps);
        }
        if (!(raw instanceof Map)) {
            throw new IllegalArgumentException(CAP_CODE + ":type <"
                    + raw + "> (want mob->cap map)");
        }
        Map<?, ?> byMob = (Map<?, ?>) raw;
        Map<String, Integer> caps = new LinkedHashMap<String, Integer>();
        for (String mob : table) {
            if (!byMob.containsKey(mob)) {
                throw new IllegalArgumentException(CAP_CODE + ":missing <"
                        + mob + "> (every sealed mob funds a cap — "
                        + "never defaulted)");
            }
            caps.put(mob, Integer.valueOf(Counts.positive(
                    byMob.get(mob), CAP, CAP_CODE)));
        }
        for (Object key : byMob.keySet()) {
            if (!table.contains(key)) {
                throw new IllegalArgumentException(CAP_CODE + ":foreign <"
                        + key + "> (sealed mobs are " + table + " only)");
            }
        }
        return Collections.unmodifiableMap(caps);
    }

    /**
     * Budget rule: the sole budget number (sole-mob table — exactly the
     * pre-tranche seal) or the qualified-mob to budget map (per-mob
     * table: every sealed mob funds a positive budget, no foreign keys).
     */
    static Map<String, Integer> budgetsOf(Object raw,
            List<String> table) {
        if (raw == null) {
            throw new IllegalArgumentException(
                    BUDGET_CODE + ":missing <" + BUDGET + ">");
        }
        if (raw instanceof Number) {
            if (table.size() != 1) {
                throw new IllegalArgumentException(
                        BUDGET_CODE + ":shape <" + raw + "> (a sole "
                                + "budget wants a sole-mob table — "
                                + "per-mob seals carry a mob->budget "
                                + "map)");
            }
            Map<String, Integer> budgets =
                    new LinkedHashMap<String, Integer>();
            budgets.put(table.get(0), Integer.valueOf(
                    Counts.positive(raw, BUDGET, BUDGET_CODE)));
            return Collections.unmodifiableMap(budgets);
        }
        if (!(raw instanceof Map)) {
            throw new IllegalArgumentException(BUDGET_CODE + ":type <"
                    + raw + "> (want mob->budget map)");
        }
        Map<?, ?> byMob = (Map<?, ?>) raw;
        Map<String, Integer> budgets =
                new LinkedHashMap<String, Integer>();
        for (String mob : table) {
            if (!byMob.containsKey(mob)) {
                throw new IllegalArgumentException(
                        BUDGET_CODE + ":missing <" + mob + "> (every "
                                + "sealed mob funds a budget — never "
                                + "defaulted)");
            }
            budgets.put(mob, Integer.valueOf(Counts.positive(
                    byMob.get(mob), BUDGET, BUDGET_CODE)));
        }
        for (Object key : byMob.keySet()) {
            if (!table.contains(key)) {
                throw new IllegalArgumentException(
                        BUDGET_CODE + ":foreign <" + key
                                + "> (sealed mobs are " + table + " only)");
            }
        }
        return Collections.unmodifiableMap(budgets);
    }

    /**
     * Ordinate range rule: the {@code [yMin, yMax]} long pair (sole-mob
     * table — exactly the pre-tranche seal) or the qualified-mob to pair
     * map (per-mob table: every sealed mob funds an ordered band, no
     * foreign keys).
     */
    static Map<String, int[]> bandsOf(Object raw, List<String> table) {
        if (raw == null) {
            throw new IllegalArgumentException(
                    Y_CODE + ":missing <" + Y + ">");
        }
        if (raw instanceof List) {
            if (table.size() != 1) {
                throw new IllegalArgumentException(Y_CODE + ":shape <"
                        + raw + "> (a sole band wants a sole-mob table — "
                        + "per-mob seals carry a mob->band map)");
            }
            Map<String, int[]> bands =
                    new LinkedHashMap<String, int[]>();
            bands.put(table.get(0), yOf(raw));
            return Collections.unmodifiableMap(bands);
        }
        if (!(raw instanceof Map)) {
            throw new IllegalArgumentException(Y_CODE + ":type <"
                    + raw + "> (want mob->[yMin, yMax] map)");
        }
        Map<?, ?> byMob = (Map<?, ?>) raw;
        Map<String, int[]> bands = new LinkedHashMap<String, int[]>();
        for (String mob : table) {
            if (!byMob.containsKey(mob)) {
                throw new IllegalArgumentException(Y_CODE + ":missing <"
                        + mob + "> (every sealed mob funds a y band — "
                        + "never defaulted)");
            }
            bands.put(mob, yEntryOf(byMob.get(mob), mob));
        }
        for (Object key : byMob.keySet()) {
            if (!table.contains(key)) {
                throw new IllegalArgumentException(Y_CODE + ":foreign <"
                        + key + "> (sealed mobs are " + table + " only)");
            }
        }
        return Collections.unmodifiableMap(bands);
    }

    /** Ordinate range rule: long pair {@code [yMin, yMax]}, ordered. */
    static int[] yOf(Object raw) {
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

    /** Ordinate range rule for one sealed mob: long pair, ordered. */
    static int[] yEntryOf(Object raw, String mob) {
        if (!(raw instanceof List)) {
            throw new IllegalArgumentException(Y_CODE + ":type <"
                    + raw + "> for <" + mob + "> (want [yMin, yMax] "
                    + "longs)");
        }
        List<?> pair = (List<?>) raw;
        if (pair.size() != 2 || !(pair.get(0) instanceof Number)
                || !(pair.get(1) instanceof Number)) {
            throw new IllegalArgumentException(Y_CODE + ":shape <"
                    + raw + "> for <" + mob + "> (want [yMin, yMax] "
                    + "longs)");
        }
        int min = ((Number) pair.get(0)).intValue();
        int max = ((Number) pair.get(1)).intValue();
        if (min < 0 || max < min) {
            throw new IllegalArgumentException(Y_CODE + ":range <"
                    + raw + "> for <" + mob + "> (want 0 <= yMin "
                    + "<= yMax)");
        }
        return new int[]{min, max};
    }

    /**
     * Census rule: entity id strings to spawn cells the table owns,
     * counted per sealed mob in {@link #TABLE} order. A mob outside the
     * sealed table refuses — the census only ever holds what this table
     * spawned, so anything else is a leak, never a quiet neighbour.
     */
    static Map<String, Integer> censusCounts(Map<?, ?> census,
            List<String> table) {
        Map<String, Integer> counts =
                new LinkedHashMap<String, Integer>();
        for (String mob : table) {
            counts.put(mob, Integer.valueOf(0));
        }
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
            String mob = cut < 0 ? "" : cell.substring(cut + 1);
            if (cut < 0 || !counts.containsKey(mob)) {
                throw new IllegalArgumentException("E_SPAWN_JOB:foreign <"
                        + cell + "> (census holds " + table + " only)");
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
            counts.put(mob, Integer.valueOf(
                    counts.get(mob).intValue() + 1));
        }
        return Collections.unmodifiableMap(counts);
    }
}
