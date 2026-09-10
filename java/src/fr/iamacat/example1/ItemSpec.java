package fr.iamacat.example1;

import fr.iamacat.spi.MatouParse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Registration source: item kinds parsed once from a content file
 * through the SPI reference parser ({@link MatouParse}), zero MC, same
 * parse-once pattern as {@link BlockSpec}. A spec carries the item short
 * name plus its stack size and label — never hardcoded per content.
 * Pure, Java 8, zero deps beyond matou-spi.
 */
public final class ItemSpec {
    private final String namespace;
    private final String name;
    private final int stack;
    private final String label;

    public ItemSpec(String namespace, String name, int stack, String label) {
        if (namespace == null) {
            throw new NullPointerException("E_EXAMPLE_ITEMSPEC:null namespace");
        }
        if (name == null) {
            throw new NullPointerException("E_EXAMPLE_ITEMSPEC:null name");
        }
        if (name.isEmpty()) {
            throw new IllegalArgumentException("E_EXAMPLE_ITEMSPEC:bad name <>");
        }
        if (stack <= 0 || stack > 64) {
            throw new IllegalArgumentException(
                    "E_EXAMPLE_ITEMSPEC:bad stack <" + stack + "> for <" + name
                            + "> (1..64)");
        }
        if (label == null) {
            throw new NullPointerException(
                    "E_EXAMPLE_ITEMSPEC:null label for <" + name + ">");
        }
        this.namespace = namespace;
        this.name = name;
        this.stack = stack;
        this.label = label;
    }

    /** Content namespace the item was declared in (never a registry). */
    public String namespace() {
        return namespace;
    }

    /** Short name, registry matched by the operator wire or loot table. */
    public String name() {
        return name;
    }

    /** Max stack size in Minecraft (1..64). */
    public int stack() {
        return stack;
    }

    /** Display label from the content declaration. */
    public String label() {
        return label;
    }

    /**
     * Parses every {@code item} instance of one content file once.
     * Files without items yield an empty list, never an error. Loud on
     * unreadable / unparsable / mistyped stack or label — never defaulted.
     */
    @SuppressWarnings("unchecked")
    public static List<ItemSpec> fromFile(String path) {
        if (path == null) {
            throw new NullPointerException("E_EXAMPLE_ITEMSPEC:null path");
        }
        final Map<String, Object> tree;
        try {
            tree = MatouParse.parseFile(path);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "E_EXAMPLE_ITEMSPEC:unreadable <" + path + "> ("
                            + e.getMessage() + ")", e);
        }
        Object ns = tree.get("namespace");
        if (!(ns instanceof String) || ((String) ns).isEmpty()) {
            throw new IllegalArgumentException(
                    "E_EXAMPLE_ITEMSPEC:bad namespace in <" + path + ">");
        }
        Object instances = tree.get("instances");
        if (!(instances instanceof List)) {
            throw new IllegalArgumentException(
                    "E_EXAMPLE_ITEMSPEC:bad shape in <" + path + ">");
        }
        List<ItemSpec> out = new ArrayList<ItemSpec>();
        for (Object o : (List<Object>) instances) {
            if (!(o instanceof Map)) {
                throw new IllegalArgumentException(
                        "E_EXAMPLE_ITEMSPEC:bad instance in <" + path + ">");
            }
            Map<String, Object> inst = (Map<String, Object>) o;
            if (!"Item".equals(inst.get("decl"))) {
                continue;
            }
            Object rawName = inst.get("name");
            if (!(rawName instanceof String)
                    || ((String) rawName).isEmpty()) {
                throw new IllegalArgumentException(
                        "E_EXAMPLE_ITEMSPEC:bad name in <" + path + ">");
            }
            String iname = (String) rawName;
            Object fields = inst.get("fields");
            if (!(fields instanceof Map)) {
                throw new IllegalArgumentException(
                        "E_EXAMPLE_ITEMSPEC:bad fields <" + iname
                                + "> in <" + path + ">");
            }
            Map<String, Object> f = (Map<String, Object>) fields;
            Object s = f.get("stack");
            if (!(s instanceof Number)) {
                throw new IllegalArgumentException(
                        "E_EXAMPLE_ITEMSPEC:bad stack <" + iname
                                + "> in <" + path + ">");
            }
            long slong = ((Number) s).longValue();
            if (slong <= 0L || slong > 64L) {
                throw new IllegalArgumentException(
                        "E_EXAMPLE_ITEMSPEC:bad stack <" + iname
                                + "> in <" + path + "> (1..64)");
            }
            Object lbl = f.get("label");
            if (!(lbl instanceof String)) {
                throw new IllegalArgumentException(
                        "E_EXAMPLE_ITEMSPEC:bad label <" + iname
                                + "> in <" + path + ">");
            }
            out.add(new ItemSpec((String) ns, iname, (int) slong,
                    (String) lbl));
        }
        return Collections.unmodifiableList(out);
    }
}
