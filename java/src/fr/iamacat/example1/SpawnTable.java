package fr.iamacat.example1;

import fr.iamacat.spi.MatouParse;
import java.util.List;
import java.util.Map;

/**
 * Spawn table wiring: the content mob ref parsed once from the owned
 * content file through the SPI reference parser ({@link MatouParse}),
 * zero MC, same parse-once pattern as {@link LootTable#fromFile}. The
 * table is the single mob's qualified ref ({@code namespace:name}) plus its
 * spec {@code hp} — the bridge lands the hp on the beast's max-health
 * attribute (hub decisions/SPAWN.md hp tranche). A content with zero or
 * several mobs refuses loudly: picking a spawnee silently would be a
 * default, and per-mob tables are a documented re-opener, not a quiet
 * guess. A mob with a missing or non-positive hp refuses loudly too: the
 * attribute seam never defaults health. Pure, Java 8,
 * zero deps beyond matou-spi.
 */
public final class SpawnTable {
    private final String mob;
    private final long hp;

    private SpawnTable(String mob, long hp) {
        this.mob = mob;
        this.hp = hp;
    }

    /** Qualified content mob ref ({@code "ns:name"}), never null. */
    public String mob() {
        return mob;
    }

    /** Spec hp (positive), applied to the beast's max health, never null. */
    public long hp() {
        return hp;
    }

    /**
     * Parses the owned content file once and seals the single mob ref.
     * Loud on unreadable / unparsable / bad namespace / zero or several
     * mobs — never defaulted.
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
            if (found != null) {
                throw new IllegalArgumentException(
                        "E_EXAMPLE_SPAWN:multi <" + found + ","
                                + rawName + "> in <" + path + "> (single "
                                + "table spawns one mob — per-mob tables "
                                + "are a re-opener, never a quiet pick)");
            }
            found = (String) rawName;
            foundHp = ((Number) hp).longValue();
        }
        if (found == null) {
            throw new IllegalArgumentException(
                    "E_EXAMPLE_SPAWN:empty <" + path + "> (no mob funds "
                            + "the census)");
        }
        return new SpawnTable(ns + ":" + found, foundHp);
    }
}
