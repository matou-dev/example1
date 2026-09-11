package fr.iamacat.example1;

import fr.iamacat.spi.MatouParse;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Combat table wiring: the content weakspot table plus the mob reach
 * attribute, parsed once from the owned content file through the SPI
 * reference parser ({@link MatouParse}), zero MC, same parse-once
 * pattern as {@link LootTable#fromFile}. The table is the single mob's
 * {@code reach} (V1 {@code f32} field — no genre needed) plus every
 * {@code Weakspot} instance in the file (syntax
 * {@code spec/SYNTAX-V5.md}: the instance name IS the bone, so keys
 * never shadow another name), joined by file co-location — one owned
 * file funds one mob plus its weakspots, same join as the loot/spawn
 * tables. The bridge transports these values into the seal at wire
 * time, it never owns them (hub
 * {@code decisions/VIRTUAL_HITBOXES.md} combat-policy tranche: no
 * bridge constant names a weakspot or a reach). A content with zero or
 * several mobs, a missing or non-positive reach, an empty table, a
 * missing or non-positive mult, or a duplicate bone refuses loudly:
 * defaults would be silent combat behaviour. Pure, Java 8, zero deps
 * beyond matou-spi.
 */
public final class CombatTable {
    private final Map<String, Float> weakspots;
    private final double reach;

    private CombatTable(Map<String, Float> weakspots, double reach) {
        this.weakspots = weakspots;
        this.reach = reach;
    }

    /**
     * Bone name to damage multiplier (insertion-ordered, unmodifiable,
     * never empty; every value positive and finite).
     */
    public Map<String, Float> weakspots() {
        return weakspots;
    }

    /** Mob reach attribute, eye-to-hitVec cutoff (positive, finite). */
    public double reach() {
        return reach;
    }

    /**
     * Parses the owned content file once and seals the weakspot table
     * plus the mob reach. Loud on unreadable / unparsable / zero or
     * several mobs / missing or bad reach / empty table / missing or
     * bad mult / duplicate bone — never defaulted.
     */
    @SuppressWarnings("unchecked")
    public static CombatTable fromFile(String path) {
        if (path == null) {
            throw new NullPointerException("E_EXAMPLE_COMBAT:null path");
        }
        final Map<String, Object> tree;
        try {
            tree = MatouParse.parseFile(path);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "E_EXAMPLE_COMBAT:unreadable <" + path + "> ("
                            + e.getMessage() + ")", e);
        }
        Object instances = tree.get("instances");
        if (!(instances instanceof List)) {
            throw new IllegalArgumentException(
                    "E_EXAMPLE_COMBAT:bad shape in <" + path + ">");
        }
        String found = null;
        double foundReach = Double.NaN;
        Map<String, Float> foundWeak = new LinkedHashMap<String, Float>();
        for (Object o : (List<Object>) instances) {
            if (!(o instanceof Map)) {
                throw new IllegalArgumentException(
                        "E_EXAMPLE_COMBAT:bad instance in <" + path
                                + ">");
            }
            Map<String, Object> inst = (Map<String, Object>) o;
            if ("Weakspot".equals(inst.get("decl"))) {
                Object rawBone = inst.get("name");
                Object fields = inst.get("fields");
                if (!(rawBone instanceof String)
                        || ((String) rawBone).isEmpty()
                        || !(fields instanceof Map)) {
                    throw new IllegalArgumentException(
                            "E_EXAMPLE_COMBAT:bad weakspot in <" + path
                                    + ">");
                }
                Object mult = ((Map<String, Object>) fields).get("mult");
                if (!(mult instanceof Number)
                        || !Float.isFinite(
                                ((Number) mult).floatValue())
                        || ((Number) mult).floatValue() <= 0.0F) {
                    throw new IllegalArgumentException(
                            "E_EXAMPLE_COMBAT:bad mult <" + rawBone
                                    + "> in <" + path + "> (positive "
                                    + "f32, never defaulted)");
                }
                if (foundWeak.containsKey(rawBone)) {
                    throw new IllegalArgumentException(
                            "E_EXAMPLE_COMBAT:dupe <" + rawBone + "> in <"
                                    + path + "> (one row per bone — "
                                    + "a second row would be a quiet "
                                    + "pick)");
                }
                foundWeak.put((String) rawBone,
                        Float.valueOf(((Number) mult).floatValue()));
                continue;
            }
            if (!"Mob".equals(inst.get("decl"))) {
                continue;
            }
            Object rawName = inst.get("name");
            Object fields = inst.get("fields");
            if (!(rawName instanceof String)
                    || ((String) rawName).isEmpty()
                    || !(fields instanceof Map)) {
                throw new IllegalArgumentException(
                        "E_EXAMPLE_COMBAT:bad mob in <" + path + ">");
            }
            Object reach = ((Map<String, Object>) fields).get("reach");
            if (!(reach instanceof Number)
                    || !Double.isFinite(
                            ((Number) reach).doubleValue())
                    || ((Number) reach).doubleValue() <= 0.0d) {
                throw new IllegalArgumentException(
                        "E_EXAMPLE_COMBAT:bad reach <" + rawName + "> in <"
                                + path + "> (positive f32, never "
                                + "defaulted)");
            }
            if (found != null) {
                throw new IllegalArgumentException(
                        "E_EXAMPLE_COMBAT:multi <" + found + ","
                                + rawName + "> in <" + path + "> (single "
                                + "table arms one mob — per-mob tables "
                                + "are a re-opener, never a quiet pick)");
            }
            found = (String) rawName;
            foundReach = ((Number) reach).doubleValue();
        }
        if (found == null) {
            throw new IllegalArgumentException(
                    "E_EXAMPLE_COMBAT:empty <" + path + "> (no mob funds "
                            + "the combat table)");
        }
        if (foundWeak.isEmpty()) {
            throw new IllegalArgumentException(
                    "E_EXAMPLE_COMBAT:empty <" + path + "> (no weakspot "
                            + "funds a multiplier — a table nobody pays "
                            + "would be a silent no-op)");
        }
        return new CombatTable(
                Collections.unmodifiableMap(foundWeak), foundReach);
    }
}
