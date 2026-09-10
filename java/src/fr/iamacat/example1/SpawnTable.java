package fr.iamacat.example1;

import fr.iamacat.spi.MatouParse;
import java.util.List;
import java.util.Map;

/**
 * Spawn table wiring: the content mob ref parsed once from the owned
 * content file through the SPI reference parser ({@link MatouParse}),
 * zero MC, same parse-once pattern as {@link LootTable#fromFile}. The
 * table is the single mob's qualified ref ({@code namespace:name}) — the
 * bridge lands it on a vanilla host entity (hub decisions/SPAWN.md
 * tranche 1: zero registration risk, a custom entity class follows the
 * registration path later). A content with zero or several mobs refuses
 * loudly: picking a spawnee silently would be a default, and per-mob
 * tables are a documented re-opener, not a quiet guess. Pure, Java 8,
 * zero deps beyond matou-spi.
 */
public final class SpawnTable {
    private final String mob;

    private SpawnTable(String mob) {
        this.mob = mob;
    }

    /** Qualified content mob ref ({@code "ns:name"}), never null. */
    public String mob() {
        return mob;
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
            if (!(rawName instanceof String)
                    || ((String) rawName).isEmpty()) {
                throw new IllegalArgumentException(
                        "E_EXAMPLE_SPAWN:bad mob in <" + path + ">");
            }
            if (found != null) {
                throw new IllegalArgumentException(
                        "E_EXAMPLE_SPAWN:multi <" + found + ","
                                + rawName + "> in <" + path + "> (single "
                                + "table spawns one mob — per-mob tables "
                                + "are a re-opener, never a quiet pick)");
            }
            found = (String) rawName;
        }
        if (found == null) {
            throw new IllegalArgumentException(
                    "E_EXAMPLE_SPAWN:empty <" + path + "> (no mob funds "
                            + "the census)");
        }
        return new SpawnTable(ns + ":" + found);
    }
}
