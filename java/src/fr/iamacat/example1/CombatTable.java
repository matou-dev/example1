package fr.iamacat.example1;

import fr.iamacat.spi.MatouParse;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Combat table wiring: the content weakspot table plus the mob reach
 * attributes, parsed once from the owned content file through the SPI
 * reference parser ({@link MatouParse}), zero MC, same parse-once
 * pattern as {@link LootTable#fromFile}. Since syntax
 * {@code spec/SYNTAX-V6.md} the join is per-mob: each {@code Weakspot}
 * row funds one {@code (mob, bone)} pair through its required
 * {@code mob : mob_ref} field (the instance name IS the bone, so
 * {@code (mob, bone)} keys never shadow another name), and every
 * {@code Mob} row funds its own {@code reach} (V1 {@code f32} field —
 * no genre needed). The bridge transports these values into the seal
 * at wire time, it never owns them (hub
 * {@code decisions/VIRTUAL_HITBOXES.md} combat-policy tranche: no
 * bridge constant names a weakspot or a reach). Mob keys are short
 * instance names (the namespace prefix of the parser-validated
 * qualified ref is stripped).
 *
 * <p>Version split: {@code syntax <= 5} files keep the V5 co-location
 * single-mob path (one owned file funds one mob plus its weakspots,
 * same join as the loot/spawn tables — a weakspot carrying {@code mob}
 * is refused, the parser stays syntactic); {@code syntax >= 6} files
 * seal per-mob. A content with zero mobs, a missing or non-positive
 * reach, an empty table, a missing or non-positive mult, a weakspot
 * without its mob (v6), a weakspot naming an unknown mob, a mob
 * funding no weakspot, or a duplicate {@code (mob, bone)} refuses
 * loudly: defaults would be silent combat behaviour. The legacy
 * {@link #weakspots()} / {@link #reach()} serve the sole sealed mob
 * and refuse on multi-mob tables — per-mob readers use
 * {@link #weakspots(String)} / {@link #reach(String)}. Pure, Java 8,
 * zero deps beyond matou-spi.
 */
public final class CombatTable {
    private final Map<String, Map<String, Float>> perMobWeakspots;
    private final Map<String, Double> perMobReach;

    private CombatTable(Map<String, Map<String, Float>> perMobWeakspots,
            Map<String, Double> perMobReach) {
        this.perMobWeakspots = perMobWeakspots;
        this.perMobReach = perMobReach;
    }

    /**
     * Short instance names of every sealed mob, in file order
     * (unmodifiable, never empty).
     */
    public Set<String> mobs() {
        return Collections.unmodifiableSet(
                new LinkedHashSet<String>(perMobWeakspots.keySet()));
    }

    /**
     * Bone name to damage multiplier for one mob (insertion-ordered,
     * unmodifiable, never empty; every value positive and finite).
     * Loud on null/unknown mob — never defaulted.
     */
    public Map<String, Float> weakspots(String mob) {
        if (mob == null) {
            throw new NullPointerException("E_EXAMPLE_COMBAT:null mob "
                    + "(want a sealed mob — see mobs())");
        }
        Map<String, Float> hit = perMobWeakspots.get(mob);
        if (hit == null) {
            throw new IllegalArgumentException(
                    "E_EXAMPLE_COMBAT:unknown mob <" + mob + "> (want one "
                            + "of " + perMobWeakspots.keySet() + " — "
                            + "never defaulted)");
        }
        return hit;
    }

    /**
     * Mob reach attribute for one mob, eye-to-hitVec cutoff (positive,
     * finite). Loud on null/unknown mob — never defaulted.
     */
    public double reach(String mob) {
        if (mob == null) {
            throw new NullPointerException("E_EXAMPLE_COMBAT:null mob "
                    + "(want a sealed mob — see mobs())");
        }
        Double hit = perMobReach.get(mob);
        if (hit == null) {
            throw new IllegalArgumentException(
                    "E_EXAMPLE_COMBAT:unknown mob <" + mob + "> (want one "
                            + "of " + perMobReach.keySet() + " — never "
                            + "defaulted)");
        }
        return hit.doubleValue();
    }

    /**
     * Bone name to damage multiplier (insertion-ordered, unmodifiable,
     * never empty; every value positive and finite). Sole-mob view:
     * refuses unless exactly one mob is sealed.
     */
    public Map<String, Float> weakspots() {
        return weakspots(soleMob());
    }

    /**
     * Mob reach attribute, eye-to-hitVec cutoff (positive, finite).
     * Sole-mob view: refuses unless exactly one mob is sealed.
     */
    public double reach() {
        return reach(soleMob());
    }

    private String soleMob() {
        if (perMobWeakspots.size() != 1) {
            throw new IllegalArgumentException(
                    "E_EXAMPLE_COMBAT:multi sole-view <"
                            + perMobWeakspots.keySet() + "> (the legacy "
                            + "view serves one mob — use "
                            + "weakspots(mob)/reach(mob))");
        }
        return perMobWeakspots.keySet().iterator().next();
    }

    /**
     * Parses the owned content file once and seals the weakspot table
     * plus the mob reaches. Loud on unreadable / unparsable / zero mobs
     * / missing or bad reach / empty table / missing or bad mult /
     * weakspot without mob (v6) / unknown mob / lonely mob / duplicate
     * bone / weakspot carrying mob (v5) / several mobs (v5) — never
     * defaulted.
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
        Object syntaxRaw = tree.get("syntax");
        Object instances = tree.get("instances");
        if (!(syntaxRaw instanceof Number)
                || !(instances instanceof List)) {
            throw new IllegalArgumentException(
                    "E_EXAMPLE_COMBAT:bad shape in <" + path + ">");
        }
        if (((Number) syntaxRaw).intValue() >= 6) {
            return fromFileV6(path, (List<Object>) instances);
        }
        return fromFileV5(path, (List<Object>) instances);
    }

    private static double readReach(String path, String rawName,
            Object fields) {
        if (!(fields instanceof Map)) {
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
        return ((Number) reach).doubleValue();
    }

    private static float readMult(String path, Object rawBone,
            Object fields) {
        if (!(fields instanceof Map)) {
            throw new IllegalArgumentException(
                    "E_EXAMPLE_COMBAT:bad weakspot in <" + path + ">");
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
        return ((Number) mult).floatValue();
    }

    private static void checkBone(String path, Object rawBone,
            Object fields) {
        if (!(rawBone instanceof String)
                || ((String) rawBone).isEmpty()
                || !(fields instanceof Map)) {
            throw new IllegalArgumentException(
                    "E_EXAMPLE_COMBAT:bad weakspot in <" + path
                            + ">");
        }
    }

    private static void checkMob(String path, Object rawName,
            Object fields) {
        if (!(rawName instanceof String)
                || ((String) rawName).isEmpty()
                || !(fields instanceof Map)) {
            throw new IllegalArgumentException(
                    "E_EXAMPLE_COMBAT:bad mob in <" + path + ">");
        }
    }

    @SuppressWarnings("unchecked")
    private static CombatTable fromFileV5(String path,
            List<Object> instances) {
        String found = null;
        double foundReach = Double.NaN;
        Map<String, Float> foundWeak = new LinkedHashMap<String, Float>();
        for (Object o : instances) {
            if (!(o instanceof Map)) {
                throw new IllegalArgumentException(
                        "E_EXAMPLE_COMBAT:bad instance in <" + path
                                + ">");
            }
            Map<String, Object> inst = (Map<String, Object>) o;
            if ("Weakspot".equals(inst.get("decl"))) {
                Object rawBone = inst.get("name");
                Object fields = inst.get("fields");
                checkBone(path, rawBone, fields);
                if (((Map<String, Object>) fields).containsKey("mob")) {
                    throw new IllegalArgumentException(
                            "E_EXAMPLE_COMBAT:mob in v5 <" + rawBone
                                    + "> in <" + path + "> (the weakspot "
                                    + "mob join needs syntax 6 — never "
                                    + "a quiet co-location)");
                }
                float mult = readMult(path, rawBone, fields);
                if (foundWeak.containsKey(rawBone)) {
                    throw new IllegalArgumentException(
                            "E_EXAMPLE_COMBAT:dupe <" + rawBone + "> in <"
                                    + path + "> (one row per bone — "
                                    + "a second row would be a quiet "
                                    + "pick)");
                }
                foundWeak.put((String) rawBone, Float.valueOf(mult));
                continue;
            }
            if (!"Mob".equals(inst.get("decl"))) {
                continue;
            }
            Object rawName = inst.get("name");
            Object fields = inst.get("fields");
            checkMob(path, rawName, fields);
            double reach = readReach(path, (String) rawName, fields);
            if (found != null) {
                throw new IllegalArgumentException(
                        "E_EXAMPLE_COMBAT:multi <" + found + ","
                                + rawName + "> in <" + path + "> (single "
                                + "table arms one mob — per-mob tables "
                                + "are a re-opener, never a quiet pick)");
            }
            found = (String) rawName;
            foundReach = reach;
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
        Map<String, Map<String, Float>> weak = new LinkedHashMap<String, Map<String, Float>>();
        weak.put(found, Collections.unmodifiableMap(foundWeak));
        Map<String, Double> reach = new LinkedHashMap<String, Double>();
        reach.put(found, Double.valueOf(foundReach));
        return new CombatTable(Collections.unmodifiableMap(weak),
                Collections.unmodifiableMap(reach));
    }

    @SuppressWarnings("unchecked")
    private static CombatTable fromFileV6(String path,
            List<Object> instances) {
        // Two passes: weakspots may forward-ref mobs declared later
        // (like all refs), so every reach lands before any join.
        Map<String, Double> reach = new LinkedHashMap<String, Double>();
        for (Object o : instances) {
            if (!(o instanceof Map)) {
                throw new IllegalArgumentException(
                        "E_EXAMPLE_COMBAT:bad instance in <" + path
                                + ">");
            }
            Map<String, Object> inst = (Map<String, Object>) o;
            if (!"Mob".equals(inst.get("decl"))) {
                continue;
            }
            Object rawName = inst.get("name");
            Object fields = inst.get("fields");
            checkMob(path, rawName, fields);
            reach.put((String) rawName,
                    Double.valueOf(
                            readReach(path, (String) rawName, fields)));
        }
        Map<String, Map<String, Float>> weak =
                new LinkedHashMap<String, Map<String, Float>>();
        for (Object o : instances) {
            Map<String, Object> inst = (Map<String, Object>) o;
            if (!"Weakspot".equals(inst.get("decl"))) {
                continue;
            }
            Object rawBone = inst.get("name");
            Object fields = inst.get("fields");
            checkBone(path, rawBone, fields);
            Map<String, Object> f = (Map<String, Object>) fields;
            float mult = readMult(path, rawBone, fields);
            Object mobRef = f.get("mob");
            if (!(mobRef instanceof String)
                    || ((String) mobRef).isEmpty()) {
                throw new IllegalArgumentException(
                        "E_EXAMPLE_COMBAT:no mob <" + rawBone
                                + "> in <" + path + "> (a syntax 6 "
                                + "weakspot names its mob — never "
                                + "defaulted)");
            }
            String ref = (String) mobRef;
            int cut = ref.lastIndexOf(':');
            String mob = cut < 0 ? ref : ref.substring(cut + 1);
            if (!reach.containsKey(mob)) {
                throw new IllegalArgumentException(
                        "E_EXAMPLE_COMBAT:unknown mob <" + ref
                                + "> for <" + rawBone + "> in <"
                                + path + "> (want one of "
                                + reach.keySet() + " — never "
                                + "defaulted)");
            }
            Map<String, Float> funded = weak.get(mob);
            if (funded == null) {
                funded = new LinkedHashMap<String, Float>();
                weak.put(mob, funded);
            }
            if (funded.containsKey(rawBone)) {
                throw new IllegalArgumentException(
                        "E_EXAMPLE_COMBAT:dupe <" + mob + "/"
                                + rawBone + "> in <" + path + "> "
                                + "(one row per mob bone — a second "
                                + "row would be a quiet pick)");
            }
            funded.put((String) rawBone, Float.valueOf(mult));
        }
        if (reach.isEmpty()) {
            throw new IllegalArgumentException(
                    "E_EXAMPLE_COMBAT:empty <" + path + "> (no mob funds "
                            + "the combat table)");
        }
        boolean funded = false;
        for (Map<String, Float> rows : weak.values()) {
            funded |= !rows.isEmpty();
        }
        if (!funded) {
            throw new IllegalArgumentException(
                    "E_EXAMPLE_COMBAT:empty <" + path + "> (no weakspot "
                            + "funds a multiplier — a table nobody pays "
                            + "would be a silent no-op)");
        }
        for (String mob : reach.keySet()) {
            Map<String, Float> rows = weak.get(mob);
            if (rows == null || rows.isEmpty()) {
                throw new IllegalArgumentException(
                        "E_EXAMPLE_COMBAT:lonely mob <" + mob + "> in <"
                                + path + "> (every mob funds a weakspot "
                                + "— an unfunded mob would be a silent "
                                + "no-op)");
            }
        }
        Map<String, Map<String, Float>> sealed =
                new LinkedHashMap<String, Map<String, Float>>();
        for (Map.Entry<String, Map<String, Float>> e
                : weak.entrySet()) {
            sealed.put(e.getKey(),
                    Collections.unmodifiableMap(e.getValue()));
        }
        return new CombatTable(Collections.unmodifiableMap(sealed),
                Collections.unmodifiableMap(reach));
    }
}
