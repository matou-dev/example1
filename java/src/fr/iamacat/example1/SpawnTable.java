package fr.iamacat.example1;

import fr.iamacat.spi.MatouParse;
import java.util.List;
import java.util.Map;

/**
 * Spawn table wiring: the content mob ref parsed once from the owned
 * content file through the SPI reference parser ({@link MatouParse}),
 * zero MC, same parse-once pattern as {@link LootTable#fromFile}. The
 * table is the single mob's qualified ref ({@code namespace:name}) plus its
 * spec {@code hp} plus its authorial spawn policy ({@code cap},
 * {@code budget}, {@code y_min}/{@code y_max}) — the bridge transports
 * these values into the seal, it never owns them (hub decisions/SPAWN.md
 * content-decides tranche: no bridge constant names a spawn number). The
 * bridge lands the hp on the beast's max-health attribute (hp tranche). A
 * content with zero or several mobs refuses loudly: picking a spawnee
 * silently would be a default, and per-mob tables are a documented
 * re-opener, not a quiet guess. A mob with a missing or non-positive hp,
 * cap or budget, or an unordered y band, refuses loudly too: the seams
 * never default numbers. Pure, Java 8,
 * zero deps beyond matou-spi.
 */
public final class SpawnTable {
    private final String mob;
    private final long hp;
    private final long cap;
    private final long budget;
    private final long yMin;
    private final long yMax;

    private SpawnTable(String mob, long hp, long cap, long budget,
            long yMin, long yMax) {
        this.mob = mob;
        this.hp = hp;
        this.cap = cap;
        this.budget = budget;
        this.yMin = yMin;
        this.yMax = yMax;
    }

    /** Qualified content mob ref ({@code "ns:name"}), never null. */
    public String mob() {
        return mob;
    }

    /** Spec hp (positive), applied to the beast's max health, never null. */
    public long hp() {
        return hp;
    }

    /** Authorial living cap (positive): a full census lands nothing. */
    public long cap() {
        return cap;
    }

    /** Authorial landings per tick while room remains (positive). */
    public long budget() {
        return budget;
    }

    /** Authorial spawn ordinate floor (0 <= yMin <= yMax). */
    public long yMin() {
        return yMin;
    }

    /** Authorial spawn ordinate ceiling (0 <= yMin <= yMax). */
    public long yMax() {
        return yMax;
    }

    /**
     * Parses the owned content file once and seals the single mob ref plus
     * its authorial spawn policy. Loud on unreadable / unparsable / bad
     * namespace / zero or several mobs / missing or bad hp, cap, budget
     * or y band — never defaulted.
     */
    @SuppressWarnings("unchecked")
    public static SpawnTable fromFile(String path) {
        if (path == null) {
            throw new NullPointerException("E_EXAMPLE_SPAWN:null path");
        }
        final Map<String, Object> tree;
        try {
            tree = MatouParse.parseFile(path);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "E_EXAMPLE_SPAWN:unreadable <" + path + "> ("
                            + e.getMessage() + ")", e);
        }
        Object ns = tree.get("namespace");
        if (!(ns instanceof String) || ((String) ns).isEmpty()) {
            throw new IllegalArgumentException(
                    "E_EXAMPLE_SPAWN:bad namespace in <" + path + ">");
        }
        Object instances = tree.get("instances");
        if (!(instances instanceof List)) {
            throw new IllegalArgumentException(
                    "E_EXAMPLE_SPAWN:bad shape in <" + path + ">");
        }
        String found = null;
        long foundHp = -1L;
        long foundCap = -1L;
        long foundBudget = -1L;
        long foundYMin = -1L;
        long foundYMax = -1L;
        for (Object o : (List<Object>) instances) {
            if (!(o instanceof Map)) {
                throw new IllegalArgumentException(
                        "E_EXAMPLE_SPAWN:bad instance in <" + path + ">");
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
                        "E_EXAMPLE_SPAWN:bad mob in <" + path + ">");
            }
            Object hp = ((Map<String, Object>) fields).get("hp");
            if (!(hp instanceof Number)
                    || ((Number) hp).longValue() <= 0L) {
                throw new IllegalArgumentException(
                        "E_EXAMPLE_SPAWN:bad hp <" + rawName + "> in <"
                                + path + "> (positive u32, never defaulted)");
            }
            Object cap = ((Map<String, Object>) fields).get("cap");
            if (!(cap instanceof Number)
                    || ((Number) cap).longValue() <= 0L) {
                throw new IllegalArgumentException(
                        "E_EXAMPLE_SPAWN:bad cap <" + rawName + "> in <"
                                + path + "> (positive u32, never defaulted)");
            }
            Object budget = ((Map<String, Object>) fields).get("budget");
            if (!(budget instanceof Number)
                    || ((Number) budget).longValue() <= 0L) {
                throw new IllegalArgumentException(
                        "E_EXAMPLE_SPAWN:bad budget <" + rawName + "> in <"
                                + path + "> (positive u32, never defaulted)");
            }
            Object yMin = ((Map<String, Object>) fields).get("y_min");
            Object yMax = ((Map<String, Object>) fields).get("y_max");
            if (!(yMin instanceof Number) || !(yMax instanceof Number)
                    || ((Number) yMin).longValue() < 0L
                    || ((Number) yMax).longValue()
                            < ((Number) yMin).longValue()) {
                throw new IllegalArgumentException(
                        "E_EXAMPLE_SPAWN:bad y <" + rawName + "> in <"
                                + path + "> (0 <= y_min <= y_max, never "
                                + "defaulted)");
            }
            if (found != null) {
                throw new IllegalArgumentException(
                        "E_EXAMPLE_SPAWN:multi <" + found + ","
                                + rawName + "> in <" + path + "> (single "
                                + "table spawns one mob — per-mob tables "
                                + "are a re-opener, never a quiet pick)");
            }
            found = (String) rawName;
            foundHp = ((Number) hp).longValue();
            foundCap = ((Number) cap).longValue();
            foundBudget = ((Number) budget).longValue();
            foundYMin = ((Number) yMin).longValue();
            foundYMax = ((Number) yMax).longValue();
        }
        if (found == null) {
            throw new IllegalArgumentException(
                    "E_EXAMPLE_SPAWN:empty <" + path + "> (no mob funds "
                            + "the census)");
        }
        return new SpawnTable(ns + ":" + found, foundHp, foundCap,
                foundBudget, foundYMin, foundYMax);
    }
}
