package fr.iamacat.example1;

import fr.iamacat.spi.MatouParse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Spawn table wiring: the content mob refs parsed once from the owned
 * content file through the SPI reference parser ({@link MatouParse}),
 * zero MC, same parse-once pattern as {@link LootTable#fromFile}. Mob
 * rows carry their own fields, so sealing is one pass in file order:
 * every {@code Mob} funds its short instance name plus its spec
 * {@code hp} plus its authorial spawn policy ({@code cap},
 * {@code budget}, {@code y_min}/{@code y_max}) — the bridge transports
 * these values into the seal, it never owns them (hub decisions/SPAWN.md
 * content-decides tranche: no bridge constant names a spawn number). The
 * bridge lands each hp on its beast's max-health attribute (hp tranche).
 * A content with zero mobs refuses loudly, as does a mob with a missing
 * or non-positive hp, cap or budget, an unordered y band, or a duplicate
 * mob row: the seams never default numbers. Multi-mob tables seal per
 * mob: the per-mob readers {@link #hp(String)} / {@link #cap(String)} /
 * {@link #budget(String)} / {@link #yMin(String)} / {@link #yMax(String)}
 * serve one mob, {@link #mobs()} lists short names in file order,
 * {@link #mobRefs()} qualifies them in file order (the registration
 * enumerates every sealed mob through it), and the legacy
 * {@link #mob()} / {@link #hp()} / {@link #cap()} / {@link #budget()} /
 * {@link #yMin()} / {@link #yMax()} serve the sole sealed mob and refuse
 * on multi-mob tables (same sole-view pattern as
 * {@link CombatTable#weakspots()}). Pure, Java 8,
 * zero deps beyond matou-spi.
 */
public final class SpawnTable {
    private final String namespace;
    private final Map<String, Long> perMobHp;
    private final Map<String, Long> perMobCap;
    private final Map<String, Long> perMobBudget;
    private final Map<String, Long> perMobYMin;
    private final Map<String, Long> perMobYMax;

    private SpawnTable(String namespace,
            Map<String, Long> perMobHp, Map<String, Long> perMobCap,
            Map<String, Long> perMobBudget, Map<String, Long> perMobYMin,
            Map<String, Long> perMobYMax) {
        this.namespace = namespace;
        this.perMobHp = perMobHp;
        this.perMobCap = perMobCap;
        this.perMobBudget = perMobBudget;
        this.perMobYMin = perMobYMin;
        this.perMobYMax = perMobYMax;
    }

    /**
     * Short instance names of every sealed mob, in file order
     * (unmodifiable, never empty).
     */
    public Set<String> mobs() {
        return Collections.unmodifiableSet(
                new LinkedHashSet<String>(perMobHp.keySet()));
    }

    /**
     * Qualified content mob refs ({@code "ns:name"}), in file order
     * (unmodifiable, never empty). The bridge registration enumerates
     * every sealed mob through this list: one generic beast
     * registration covers them all, the NBT identity distinguishes
     * them at runtime — never one registration per mob (hub
     * decisions/VIRTUAL_HITBOXES.md, second-beast row).
     */
    public List<String> mobRefs() {
        List<String> out = new ArrayList<String>();
        for (String shortMob : perMobHp.keySet()) {
            out.add(namespace + ":" + shortMob);
        }
        return Collections.unmodifiableList(out);
    }

    /**
     * Qualified content mob ref ({@code "ns:name"}) for one sealed mob.
     * Loud on null/unknown mob — never defaulted.
     */
    public String mobRef(String mob) {
        if (mob == null) {
            throw new NullPointerException("E_EXAMPLE_SPAWN:null mob "
                    + "(want a sealed mob — see mobs())");
        }
        if (!perMobHp.containsKey(mob)) {
            throw new IllegalArgumentException(
                    "E_EXAMPLE_SPAWN:unknown mob <" + mob + "> (want one "
                            + "of " + perMobHp.keySet() + " — never "
                            + "defaulted)");
        }
        return namespace + ":" + mob;
    }

    /**
     * Spec hp for one mob (positive), applied to that beast's max
     * health. Loud on null/unknown mob — never defaulted.
     */
    public long hp(String mob) {
        if (mob == null) {
            throw new NullPointerException("E_EXAMPLE_SPAWN:null mob "
                    + "(want a sealed mob — see mobs())");
        }
        Long hit = perMobHp.get(mob);
        if (hit == null) {
            throw new IllegalArgumentException(
                    "E_EXAMPLE_SPAWN:unknown mob <" + mob + "> (want one "
                            + "of " + perMobHp.keySet() + " — never "
                            + "defaulted)");
        }
        return hit.longValue();
    }

    /**
     * Authorial living cap for one mob (positive): a full census lands
     * nothing. Loud on null/unknown mob — never defaulted.
     */
    public long cap(String mob) {
        if (mob == null) {
            throw new NullPointerException("E_EXAMPLE_SPAWN:null mob "
                    + "(want a sealed mob — see mobs())");
        }
        Long hit = perMobCap.get(mob);
        if (hit == null) {
            throw new IllegalArgumentException(
                    "E_EXAMPLE_SPAWN:unknown mob <" + mob + "> (want one "
                            + "of " + perMobCap.keySet() + " — never "
                            + "defaulted)");
        }
        return hit.longValue();
    }

    /**
     * Authorial landings per tick for one mob while room remains
     * (positive). Loud on null/unknown mob — never defaulted.
     */
    public long budget(String mob) {
        if (mob == null) {
            throw new NullPointerException("E_EXAMPLE_SPAWN:null mob "
                    + "(want a sealed mob — see mobs())");
        }
        Long hit = perMobBudget.get(mob);
        if (hit == null) {
            throw new IllegalArgumentException(
                    "E_EXAMPLE_SPAWN:unknown mob <" + mob + "> (want one "
                            + "of " + perMobBudget.keySet() + " — never "
                            + "defaulted)");
        }
        return hit.longValue();
    }

    /**
     * Authorial spawn ordinate floor for one mob
     * (0 {@code <=} yMin {@code <=} yMax). Loud on null/unknown mob —
     * never defaulted.
     */
    public long yMin(String mob) {
        if (mob == null) {
            throw new NullPointerException("E_EXAMPLE_SPAWN:null mob "
                    + "(want a sealed mob — see mobs())");
        }
        Long hit = perMobYMin.get(mob);
        if (hit == null) {
            throw new IllegalArgumentException(
                    "E_EXAMPLE_SPAWN:unknown mob <" + mob + "> (want one "
                            + "of " + perMobYMin.keySet() + " — never "
                            + "defaulted)");
        }
        return hit.longValue();
    }

    /**
     * Authorial spawn ordinate ceiling for one mob
     * (0 {@code <=} yMin {@code <=} yMax). Loud on null/unknown mob —
     * never defaulted.
     */
    public long yMax(String mob) {
        if (mob == null) {
            throw new NullPointerException("E_EXAMPLE_SPAWN:null mob "
                    + "(want a sealed mob — see mobs())");
        }
        Long hit = perMobYMax.get(mob);
        if (hit == null) {
            throw new IllegalArgumentException(
                    "E_EXAMPLE_SPAWN:unknown mob <" + mob + "> (want one "
                            + "of " + perMobYMax.keySet() + " — never "
                            + "defaulted)");
        }
        return hit.longValue();
    }

    /**
     * Qualified content mob ref ({@code "ns:name"}), never null.
     * Sole-mob view: refuses unless exactly one mob is sealed.
     */
    public String mob() {
        return namespace + ":" + soleMob();
    }

    /**
     * Spec hp (positive), applied to the beast's max health, never null.
     * Sole-mob view: refuses unless exactly one mob is sealed.
     */
    public long hp() {
        return hp(soleMob());
    }

    /**
     * Authorial living cap (positive): a full census lands nothing.
     * Sole-mob view: refuses unless exactly one mob is sealed.
     */
    public long cap() {
        return cap(soleMob());
    }

    /**
     * Authorial landings per tick while room remains (positive).
     * Sole-mob view: refuses unless exactly one mob is sealed.
     */
    public long budget() {
        return budget(soleMob());
    }

    /**
     * Authorial spawn ordinate floor (0 {@code <=} yMin {@code <=} yMax).
     * Sole-mob view: refuses unless exactly one mob is sealed.
     */
    public long yMin() {
        return yMin(soleMob());
    }

    /**
     * Authorial spawn ordinate ceiling (0 {@code <=} yMin {@code <=} yMax).
     * Sole-mob view: refuses unless exactly one mob is sealed.
     */
    public long yMax() {
        return yMax(soleMob());
    }

    private String soleMob() {
        if (perMobHp.size() != 1) {
            throw new IllegalArgumentException(
                    "E_EXAMPLE_SPAWN:multi sole-view <"
                            + perMobHp.keySet() + "> (the legacy "
                            + "view serves one mob — use "
                            + "hp(mob)/cap(mob)/budget(mob)/"
                            + "yMin(mob)/yMax(mob))");
        }
        return perMobHp.keySet().iterator().next();
    }

    /**
     * Parses the owned content file once and seals every mob ref plus
     * its authorial spawn policy, in file order. Loud on unreadable /
     * unparsable / bad namespace / zero mobs / missing or bad hp, cap,
     * budget or y band / duplicate mob — never defaulted.
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
        Map<String, Long> hp = new LinkedHashMap<String, Long>();
        Map<String, Long> cap = new LinkedHashMap<String, Long>();
        Map<String, Long> budget = new LinkedHashMap<String, Long>();
        Map<String, Long> yMin = new LinkedHashMap<String, Long>();
        Map<String, Long> yMax = new LinkedHashMap<String, Long>();
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
            Object hpRaw = ((Map<String, Object>) fields).get("hp");
            if (!(hpRaw instanceof Number)
                    || ((Number) hpRaw).longValue() <= 0L) {
                throw new IllegalArgumentException(
                        "E_EXAMPLE_SPAWN:bad hp <" + rawName + "> in <"
                                + path + "> (positive u32, never defaulted)");
            }
            Object capRaw = ((Map<String, Object>) fields).get("cap");
            if (!(capRaw instanceof Number)
                    || ((Number) capRaw).longValue() <= 0L) {
                throw new IllegalArgumentException(
                        "E_EXAMPLE_SPAWN:bad cap <" + rawName + "> in <"
                                + path + "> (positive u32, never defaulted)");
            }
            Object budgetRaw =
                    ((Map<String, Object>) fields).get("budget");
            if (!(budgetRaw instanceof Number)
                    || ((Number) budgetRaw).longValue() <= 0L) {
                throw new IllegalArgumentException(
                        "E_EXAMPLE_SPAWN:bad budget <" + rawName + "> in <"
                                + path + "> (positive u32, never defaulted)");
            }
            Object yMinRaw = ((Map<String, Object>) fields).get("y_min");
            Object yMaxRaw = ((Map<String, Object>) fields).get("y_max");
            if (!(yMinRaw instanceof Number) || !(yMaxRaw instanceof Number)
                    || ((Number) yMinRaw).longValue() < 0L
                    || ((Number) yMaxRaw).longValue()
                            < ((Number) yMinRaw).longValue()) {
                throw new IllegalArgumentException(
                        "E_EXAMPLE_SPAWN:bad y <" + rawName + "> in <"
                                + path + "> (0 <= y_min <= y_max, never "
                                + "defaulted)");
            }
            if (hp.containsKey(rawName)) {
                throw new IllegalArgumentException(
                        "E_EXAMPLE_SPAWN:dupe <" + rawName + "> in <"
                                + path + "> (one row per mob — a second "
                                + "row would be a quiet pick)");
            }
            hp.put((String) rawName,
                    Long.valueOf(((Number) hpRaw).longValue()));
            cap.put((String) rawName,
                    Long.valueOf(((Number) capRaw).longValue()));
            budget.put((String) rawName,
                    Long.valueOf(((Number) budgetRaw).longValue()));
            yMin.put((String) rawName,
                    Long.valueOf(((Number) yMinRaw).longValue()));
            yMax.put((String) rawName,
                    Long.valueOf(((Number) yMaxRaw).longValue()));
        }
        if (hp.isEmpty()) {
            throw new IllegalArgumentException(
                    "E_EXAMPLE_SPAWN:empty <" + path + "> (no mob funds "
                            + "the census)");
        }
        return new SpawnTable((String) ns,
                Collections.unmodifiableMap(hp),
                Collections.unmodifiableMap(cap),
                Collections.unmodifiableMap(budget),
                Collections.unmodifiableMap(yMin),
                Collections.unmodifiableMap(yMax));
    }
}
