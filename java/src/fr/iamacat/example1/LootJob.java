package fr.iamacat.example1;

import fr.iamacat.spi.Cell;
import fr.iamacat.spi.Counts;
import fr.iamacat.spi.MatouId;
import fr.iamacat.spi.MatouJob;
import fr.iamacat.spi.Snapshot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loot job: pure drop decision over bridge-sealed harvests (hub
 * decisions/LOOT.md). Reads the sealed harvest set under {@link #HARVESTED}
 * (harvest cells {@code "x,y,z:kind"} to harvest tick, numbers) plus the
 * loot table under {@link #TABLE} (harvest kind to content item ref) plus
 * the per-harvest count under {@link #COUNT}, and emits one volume drop
 * cell {@code "x,y,z:ns:item"} per due harvest — the content item ref,
 * never a landable name (the bridge resolves the carrier, same split as
 * structure palettes). Pure like every job ({@code MatouJob} contract):
 * the harvest store itself stays bridge-owned, this job only reads the
 * sealed snapshot. Java 8, zero deps beyond matou-spi.
 *
 * <p>Kinds are closed and code-owned ({@link #ORE}, {@link #BEAST}): the
 * bridge classifies live events into kinds, content decides what each kind
 * pays. Drops are immediate — a harvest sealed at tick T is due at tick T
 * (no repop delay): the live store claim and this decision agree tick by
 * tick, tripwired the same way. Missing, mistyped or out-of-range states
 * are refused loudly under {@code E_LOOT_*}, never defaulted.
 */
public final class LootJob implements MatouJob<List<String>> {
    /** Sealed harvests: {@code "x,y,z:kind"} to harvest tick. */
    public static final MatouId HARVESTED =
            MatouId.of("example1.loot", "harvested");
    /** Loot table: harvest kind to content item ref. */
    public static final MatouId TABLE =
            MatouId.of("example1.loot", "table");
    /** Items paid per due harvest (tranche 1: always 1, no fortune). */
    public static final MatouId COUNT =
            MatouId.of("example1.loot", "count");
    /** Refusal prefix for the per-harvest count (Counts-style trio). */
    public static final String COUNT_CODE = "E_LOOT_COUNT";

    /** Harvest kind: the registered ore was harvested. */
    public static final String ORE = "ore";
    /** Harvest kind: a mob was killed. */
    public static final String BEAST = "beast";

    @Override
    public List<String> decide(Snapshot snap) {
        if (snap == null) {
            throw new NullPointerException("E_LOOT_JOB:null snapshot");
        }
        Map<?, ?> harvested = snap.mapOf(HARVESTED);
        Map<String, String> drops = tableOf(snap.mapOf(TABLE));
        int count = Counts.positive(snap.get(COUNT), COUNT, COUNT_CODE);
        List<String> out = new ArrayList<String>();
        for (Map.Entry<?, ?> e : harvested.entrySet()) {
            if (!(e.getKey() instanceof String)) {
                throw new IllegalArgumentException("E_LOOT_JOB:type <"
                        + e.getKey() + "> (want String harvest)");
            }
            if (!(e.getValue() instanceof Number)) {
                throw new IllegalArgumentException("E_LOOT_JOB:type <"
                        + e.getValue() + "> (want number tick)");
            }
            long minedTick = ((Number) e.getValue()).longValue();
            if (minedTick < 0) {
                throw new IllegalArgumentException("E_LOOT_JOB:range <"
                        + minedTick + "> (want >= 0)");
            }
            String harvest = (String) e.getKey();
            int cut = harvest.indexOf(':');
            String head = cut < 0 ? harvest : harvest.substring(0, cut);
            String kind = cut < 0 ? "" : harvest.substring(cut + 1);
            String[] parts = head.split(",", -1);
            int x;
            int y;
            int z;
            try {
                if (parts.length != 3 || kind.isEmpty()) {
                    throw new NumberFormatException("shape");
                }
                x = Integer.parseInt(parts[0]);
                y = Integer.parseInt(parts[1]);
                z = Integer.parseInt(parts[2]);
            } catch (NumberFormatException bad) {
                throw new IllegalArgumentException("E_LOOT_JOB:shape <"
                        + harvest + "> (want \"x,y,z:kind\")");
            }
            String item = drops.get(kind);
            if (item == null) {
                throw new IllegalArgumentException("E_LOOT_JOB:unknown <"
                        + kind + "> (table pays "
                        + new ArrayList<String>(drops.keySet()) + ")");
            }
            if (minedTick <= snap.tick()) {
                for (int c = 0; c < count; c++) {
                    out.add(Cell.of(x, y, z, item).render());
                }
            }
        }
        return Collections.unmodifiableList(out);
    }

    /**
     * Table rule: every key and value a non-empty string, both kinds paid.
     * A superset of kinds stays open (future tables add kinds here); a
     * missing kind refuses at wiring time, never as a silent no-drop.
     */
    static Map<String, String> tableOf(Map<?, ?> raw) {
        Map<String, String> drops = new LinkedHashMap<String, String>();
        for (Map.Entry<?, ?> e : raw.entrySet()) {
            if (!(e.getKey() instanceof String)
                    || ((String) e.getKey()).isEmpty()
                    || !(e.getValue() instanceof String)
                    || ((String) e.getValue()).isEmpty()) {
                throw new IllegalArgumentException("E_LOOT_JOB:type <"
                        + e.getKey() + " -> " + e.getValue()
                        + "> (want kind -> item-ref strings)");
            }
            drops.put((String) e.getKey(), (String) e.getValue());
        }
        if (!drops.containsKey(ORE) || !drops.containsKey(BEAST)) {
            throw new IllegalArgumentException("E_LOOT_JOB:table <"
                    + new ArrayList<String>(drops.keySet())
                    + "> (want ore+beast entries)");
        }
        return Collections.unmodifiableMap(drops);
    }
}
