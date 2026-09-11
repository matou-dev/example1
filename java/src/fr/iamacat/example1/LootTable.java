package fr.iamacat.example1;

import fr.iamacat.spi.MatouParse;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Loot table wiring: per-mob drops parsed once from the owned content
 * file through the SPI reference parser ({@link MatouParse}), zero MC,
 * same parse-once pattern as {@code BlockSpec.fromFile}. Every
 * {@code Mob} funds its own {@code (drop, drop_count)} pair: the
 * per-mob readers {@link #drop(String)} / {@link #count(String)} serve
 * one mob, {@link #mobs()} lists short names in file order, and the
 * kind builders {@link #dropsPerKind()} / {@link #countsPerKind()} seal
 * the wire table (the {@code ore} kind pays the first sealed mob in
 * file order — a harvest has no victim to name — plus one
 * {@code beast.<mob>} kind per mob, see {@link LootJob#beastKind}).
 * The legacy {@link #drops()} / {@link #count()} serve the sole sealed
 * mob and refuse on multi-mob tables (same sole-view pattern as
 * {@link SpawnTable#mob()} and {@link CombatTable#weakspots()}): a
 * single-mob table seals exactly as before (zero behaviour change, old
 * seals stay byte-identical). A content with zero mobs, a missing or
 * bad drop, or a missing or non-positive drop_count refuses loudly
 * too. Pure, Java 8, zero deps beyond matou-spi.
 */
public final class LootTable {
    private final Map<String, String> perMobDrop;
    private final Map<String, Long> perMobCount;

    private LootTable(Map<String, String> perMobDrop,
            Map<String, Long> perMobCount) {
        this.perMobDrop = perMobDrop;
        this.perMobCount = perMobCount;
    }

    /**
     * Short instance names of every sealed mob, in file order
     * (unmodifiable, never empty).
     */
    public Set<String> mobs() {
        return Collections.unmodifiableSet(
                new LinkedHashSet<String>(perMobDrop.keySet()));
    }

    /**
     * Content drop item ref funding one mob's kills (never null, never
     * empty). Loud on null/unknown mob — never defaulted.
     */
    public String drop(String mob) {
        if (mob == null) {
            throw new NullPointerException("E_EXAMPLE_LOOT:null mob "
                    + "(want a sealed mob — see mobs())");
        }
        String hit = perMobDrop.get(mob);
        if (hit == null) {
            throw new IllegalArgumentException(
                    "E_EXAMPLE_LOOT:unknown mob <" + mob + "> (want one "
                            + "of " + perMobDrop.keySet() + " — never "
                            + "defaulted)");
        }
        return hit;
    }

    /**
     * Authorial items paid per due harvest of one mob's kills
     * (positive). Loud on null/unknown mob — never defaulted.
     */
    public long count(String mob) {
        if (mob == null) {
            throw new NullPointerException("E_EXAMPLE_LOOT:null mob "
                    + "(want a sealed mob — see mobs())");
        }
        Long hit = perMobCount.get(mob);
        if (hit == null) {
            throw new IllegalArgumentException(
                    "E_EXAMPLE_LOOT:unknown mob <" + mob + "> (want one "
                            + "of " + perMobCount.keySet() + " — never "
                            + "defaulted)");
        }
        return hit.longValue();
    }

    /**
     * Wire table: harvest kind to content item ref (the {@code ore}
     * kind pays the first sealed mob in file order, plus one
     * {@code beast.<mob>} kind per mob). Unmodifiable, insertion
     * order, never empty.
     */
    public Map<String, String> dropsPerKind() {
        Map<String, String> drops = new LinkedHashMap<String, String>();
        String first = perMobDrop.keySet().iterator().next();
        drops.put(LootJob.ORE, perMobDrop.get(first));
        for (Map.Entry<String, String> funded : perMobDrop.entrySet()) {
            drops.put(LootJob.beastKind(funded.getKey()),
                    funded.getValue());
        }
        return Collections.unmodifiableMap(drops);
    }

    /**
     * Wire counts: harvest kind to authorial items per due harvest
     * (one positive entry per {@link #dropsPerKind()} kind).
     * Unmodifiable, insertion order, never empty.
     */
    public Map<String, Long> countsPerKind() {
        Map<String, Long> counts = new LinkedHashMap<String, Long>();
        String first = perMobCount.keySet().iterator().next();
        counts.put(LootJob.ORE, perMobCount.get(first));
        for (Map.Entry<String, Long> funded : perMobCount.entrySet()) {
            counts.put(LootJob.beastKind(funded.getKey()),
                    funded.getValue());
        }
        return Collections.unmodifiableMap(counts);
    }

    /**
     * Kind to content item ref ({@code ore} + {@code beast}), never
     * null. Sole-mob view: refuses unless exactly one mob is sealed.
     */
    public Map<String, String> drops() {
        return dropsPerKindOf(soleMob());
    }

    /**
     * Authorial items per harvest (positive), never defaulted.
     * Sole-mob view: refuses unless exactly one mob is sealed.
     */
    public long count() {
        return count(soleMob());
    }

    private Map<String, String> dropsPerKindOf(String mob) {
        Map<String, String> drops = new LinkedHashMap<String, String>();
        drops.put(LootJob.ORE, perMobDrop.get(mob));
        drops.put(LootJob.BEAST, perMobDrop.get(mob));
        return Collections.unmodifiableMap(drops);
    }

    private String soleMob() {
        if (perMobDrop.size() != 1) {
            throw new IllegalArgumentException(
                    "E_EXAMPLE_LOOT:multi sole-view <"
                            + perMobDrop.keySet() + "> (the legacy "
                            + "view serves one mob — use "
                            + "drop(mob)/count(mob))");
        }
        return perMobDrop.keySet().iterator().next();
    }

    /**
     * Parses the owned content file once and seals every mob's
     * {@code (drop, drop_count)} pair, in file order. Loud on
     * unreadable / unparsable / zero mobs / missing or bad drop /
     * missing or non-positive drop_count / duplicate mob — never
     * defaulted.
     */
    @SuppressWarnings("unchecked")
    public static LootTable fromFile(String path) {
        if (path == null) {
            throw new NullPointerException("E_EXAMPLE_LOOT:null path");
        }
        final Map<String, Object> tree;
        try {
            tree = MatouParse.parseFile(path);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "E_EXAMPLE_LOOT:unreadable <" + path + "> ("
                            + e.getMessage() + ")", e);
        }
        Object instances = tree.get("instances");
        if (!(instances instanceof List)) {
            throw new IllegalArgumentException(
                    "E_EXAMPLE_LOOT:bad shape in <" + path + ">");
        }
        Map<String, String> dropByMob =
                new LinkedHashMap<String, String>();
        Map<String, Long> countByMob =
                new LinkedHashMap<String, Long>();
        for (Object o : (List<Object>) instances) {
            if (!(o instanceof Map)) {
                throw new IllegalArgumentException(
                        "E_EXAMPLE_LOOT:bad instance in <" + path + ">");
            }
            Map<String, Object> inst = (Map<String, Object>) o;
            if (!"Mob".equals(inst.get("decl"))) {
                continue;
            }
            Object rawName = inst.get("name");
            Object fields = inst.get("fields");
            if (!(rawName instanceof String)
                    || ((String) rawName).isEmpty()
                    || !(fields instanceof Map)) {
                throw new IllegalArgumentException(
                        "E_EXAMPLE_LOOT:bad mob in <" + path + ">");
            }
            Object drop = ((Map<String, Object>) fields).get("drop");
            if (!(drop instanceof String)
                    || ((String) drop).isEmpty()) {
                throw new IllegalArgumentException(
                        "E_EXAMPLE_LOOT:bad drop <" + rawName + "> in <"
                                + path + ">");
            }
            Object dropCount =
                    ((Map<String, Object>) fields).get("drop_count");
            if (!(dropCount instanceof Number)
                    || ((Number) dropCount).longValue() <= 0L) {
                throw new IllegalArgumentException(
                        "E_EXAMPLE_LOOT:bad drop_count <" + rawName
                                + "> in <" + path + "> (positive u32, "
                                + "never defaulted)");
            }
            if (dropByMob.containsKey(rawName)) {
                throw new IllegalArgumentException(
                        "E_EXAMPLE_LOOT:dupe <" + rawName + "> in <"
                                + path + "> (one row per mob — a second "
                                + "row would be a quiet pick)");
            }
            dropByMob.put((String) rawName, (String) drop);
            countByMob.put((String) rawName,
                    Long.valueOf(((Number) dropCount).longValue()));
        }
        if (dropByMob.isEmpty()) {
            throw new IllegalArgumentException(
                    "E_EXAMPLE_LOOT:empty <" + path + "> (no mob funds "
                            + "block drops)");
        }
        return new LootTable(Collections.unmodifiableMap(dropByMob),
                Collections.unmodifiableMap(countByMob));
    }
}
