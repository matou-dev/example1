package fr.iamacat.example1;

import fr.iamacat.spi.MatouParse;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loot table wiring: kind to content item ref, parsed once from the owned
 * content file through the SPI reference parser ({@link MatouParse}),
 * zero MC, same parse-once pattern as {@code BlockSpec.fromFile}. The
 * table has exactly two entries: {@code ore} and {@code beast} both pay
 * the content's single mob drop (hub decisions/LOOT.md single-table
 * scope — the vein block tells <i>where</i> ore lives, the mob drop tells
 * <i>what</i> everything pays, no fifth genre, no new fields), plus the
 * authorial items-per-harvest {@code count} from the mob's
 * {@code drop_count} — the bridge transports it into the seal, it never
 * owns the number (content-decides tranche). A content with zero or
 * several mobs refuses loudly: picking a payer silently would be a
 * default, and per-mob tables are a documented re-opener, not a quiet
 * guess. Pure, Java 8, zero deps beyond matou-spi.
 */
public final class LootTable {
    private final Map<String, String> drops;
    private final long count;

    private LootTable(Map<String, String> drops, long count) {
        this.drops = drops;
        this.count = count;
    }

    /** Kind to content item ref ({@code ore} + {@code beast}), never null. */
    public Map<String, String> drops() {
        return drops;
    }

    /** Authorial items per harvest (positive), never defaulted. */
    public long count() {
        return count;
    }

    /**
     * Parses the owned content file once and seals the two-entry table
     * plus the authorial count. Loud on unreadable / unparsable / zero or
     * several mobs / missing or bad drop / missing or non-positive
     * drop_count — never defaulted.
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
        String payer = null;
        String payerMob = null;
        long payerCount = -1L;
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
            if (payer != null) {
                throw new IllegalArgumentException(
                        "E_EXAMPLE_LOOT:multi <" + payerMob + ","
                                + rawName + "> in <" + path + "> (single "
                                + "table pays one drop — per-mob tables "
                                + "are a re-opener, never a quiet pick)");
            }
            payer = (String) drop;
            payerMob = (String) rawName;
            payerCount = ((Number) dropCount).longValue();
        }
        if (payer == null) {
            throw new IllegalArgumentException(
                    "E_EXAMPLE_LOOT:empty <" + path + "> (no mob funds "
                            + "block drops)");
        }
        Map<String, String> drops = new LinkedHashMap<String, String>();
        drops.put(LootJob.ORE, payer);
        drops.put(LootJob.BEAST, payer);
        return new LootTable(Collections.unmodifiableMap(drops),
                payerCount);
    }
}
