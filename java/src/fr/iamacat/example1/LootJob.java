package fr.iamacat.example1;

import fr.iamacat.spi.Cell;
import fr.iamacat.spi.Counts;
import fr.iamacat.spi.MatouId;
import fr.iamacat.spi.MatouJob;
import fr.iamacat.spi.Snapshot;
import fr.iamacat.spi.LootStates;
import fr.iamacat.spi.StateVocabulary;
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
 * the per-kind counts under {@link #COUNT} (harvest kind to authorial
 * items per due harvest), and emits one volume drop cell
 * {@code "x,y,z:ns:item"} per due harvest — the content item ref, never
 * a landable name (the bridge resolves the carrier, same split as
 * structure palettes). Pure like every job ({@code MatouJob} contract):
 * the harvest store itself stays bridge-owned, this job only reads the
 * sealed snapshot. Java 8, zero deps beyond matou-spi.
 *
 * <p>Kinds are open but ore-anchored: the {@code ore} kind pays mined
 * blocks, one {@code beast.<mob>} kind per sealed mob pays that mob's
 * kills (see {@link #beastKind}), and the legacy bare {@code beast}
 * kind pays the sole sealed mob's kills on single-mob tables (the
 * bridge classifies live events into kinds, content decides what each
 * kind pays). Drops are immediate — a harvest sealed at tick T is due
 * at tick T (no repop delay): the live store claim and this decision
 * agree tick by tick, tripwired the same way. Missing, mistyped or
 * out-of-range states are refused loudly under {@code E_LOOT_*}, never
 * defaulted.
 */
public final class LootJob implements MatouJob<List<String>> {
    /** Shared loot vocabulary (T3 registry: the seal resolves the same). */
    private static final StateVocabulary VOCABULARY =
            LootStates.vocabulary(ExampleIds.LOOT_NS);
    /** Sealed harvests: {@code "x,y,z:kind"} to harvest tick. */
    public static final MatouId HARVESTED =
            LootStates.harvested(VOCABULARY);
    /** Loot table: harvest kind to content item ref. */
    public static final MatouId TABLE = LootStates.table(VOCABULARY);
    /** Items paid per due harvest, one positive entry per table kind. */
    public static final MatouId COUNT = LootStates.count(VOCABULARY);
    /** Refusal prefix for the per-harvest counts (Counts-style trio). */
    public static final String COUNT_CODE = "E_LOOT_COUNT";

    /** Harvest kind: the registered ore was harvested. */
    public static final String ORE = "ore";
    /**
     * Harvest kind: a mob was killed, on single-mob tables (the sole
     * sealed mob's kills — multi-mob tables serve one
     * {@code beast.<mob>} kind per mob instead, never a quiet pick).
     */
    public static final String BEAST = "beast";
    /** Separator between the {@code beast} kind and the mob name. */
    private static final String BEAST_SEP = ".";

    /**
     * Harvest kind one mob's kills are recorded under
     * ({@code "beast.<mob>"} — the single spelling of the per-mob
     * beast-kind join, shared by content tables, policy and bridge
     * wires). Loud on null/empty mob — never defaulted.
     */
    public static String beastKind(String mob) {
        if (mob == null) {
            throw new NullPointerException("E_LOOT_JOB:null mob "
                    + "(want a sealed short mob name)");
        }
        if (mob.isEmpty()) {
            throw new IllegalArgumentException("E_LOOT_JOB:bad mob <> "
                    + "(want a sealed short mob name — never defaulted)");
        }
        return BEAST + BEAST_SEP + mob;
    }

    @Override
    public List<String> decide(Snapshot snap) {
        if (snap == null) {
            throw new NullPointerException("E_LOOT_JOB:null snapshot");
        }
        Map<?, ?> harvested = snap.mapOf(HARVESTED);
        Map<String, String> drops = tableOf(snap.mapOf(TABLE));
        Map<String, Integer> counts = countsOf(snap.mapOf(COUNT), drops);
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
            Integer count = counts.get(kind);
            if (count == null) {
                throw new IllegalArgumentException(COUNT_CODE + ":missing <"
                        + COUNT + " " + kind + "> (every paid kind seals "
                        + "its count — never defaulted)");
            }
            if (minedTick <= snap.tick()) {
                for (int c = 0; c < count.intValue(); c++) {
                    out.add(Cell.of(x, y, z, item).render());
                }
            }
        }
        return Collections.unmodifiableList(out);
    }

    /**
     * Table rule: every key and value a non-empty string, the ore kind
     * paid plus at least one beast kind (the bare {@code beast} kind on
     * single-mob tables, one {@code beast.<mob>} kind per mob
     * otherwise). A superset of kinds stays open (future tables add
     * kinds here); a missing kind refuses at wiring time, never as a
     * silent no-drop.
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
        if (!drops.containsKey(ORE)) {
            throw new IllegalArgumentException("E_LOOT_JOB:table <"
                    + new ArrayList<String>(drops.keySet())
                    + "> (want the ore entry)");
        }
        boolean beast = drops.containsKey(BEAST);
        if (!beast) {
            for (String kind : drops.keySet()) {
                if (kind.startsWith(BEAST + BEAST_SEP)
                        && kind.length() > BEAST.length() + 1) {
                    beast = true;
                    break;
                }
            }
        }
        if (!beast) {
            throw new IllegalArgumentException("E_LOOT_JOB:table <"
                    + new ArrayList<String>(drops.keySet())
                    + "> (want a beast entry)");
        }
        return Collections.unmodifiableMap(drops);
    }

    /**
     * Counts rule: every key a non-empty kind string, every value a
     * positive number, one entry per paid table kind (an unpaid count
     * or a kind without its count refuses — either would be a silent
     * no-drop or a silent default).
     */
    static Map<String, Integer> countsOf(Map<?, ?> raw,
            Map<String, String> table) {
        if (raw == null) {
            throw new IllegalArgumentException(COUNT_CODE + ":missing <"
                    + COUNT + "> (want kind -> count entries)");
        }
        Map<String, Integer> counts = new LinkedHashMap<String, Integer>();
        for (Map.Entry<?, ?> e : raw.entrySet()) {
            if (!(e.getKey() instanceof String)
                    || ((String) e.getKey()).isEmpty()) {
                throw new IllegalArgumentException(COUNT_CODE + ":type <"
                        + e.getKey() + " -> " + e.getValue()
                        + "> (want kind -> count entries)");
            }
            String kind = (String) e.getKey();
            int count = Counts.positive(e.getValue(), COUNT, COUNT_CODE);
            if (!table.containsKey(kind)) {
                throw new IllegalArgumentException(COUNT_CODE + ":unknown <"
                        + kind + "> (the table pays "
                        + new ArrayList<String>(table.keySet()) + ")");
            }
            counts.put(kind, Integer.valueOf(count));
        }
        for (String kind : table.keySet()) {
            if (!counts.containsKey(kind)) {
                throw new IllegalArgumentException(COUNT_CODE + ":missing <"
                        + COUNT + " " + kind + "> (every paid kind seals "
                        + "its count — never defaulted)");
            }
        }
        return Collections.unmodifiableMap(counts);
    }
}
