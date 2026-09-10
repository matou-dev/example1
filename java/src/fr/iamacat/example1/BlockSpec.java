package fr.iamacat.example1;

import fr.iamacat.spi.MatouParse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Registration source: block kinds parsed once from a content file
 * through the SPI reference parser ({@link MatouParse}), zero MC, same
 * parse-once pattern as {@code ExamplePack.fromFiles}. A spec carries
 * the block short name plus the physics the bridge drives its generic
 * block from (hardness, opacity) — never hardcoded per content. The
 * registry name is NOT here: it comes from the operator
 * ({@code packs.cfg} wire block), content-ref matched by short name.
 * Pure, Java 8, zero deps beyond matou-spi.
 */
public final class BlockSpec {
    private final String namespace;
    private final String name;
    private final float hardness;
    private final boolean opaque;

    public BlockSpec(String namespace, String name, float hardness,
            boolean opaque) {
        if (namespace == null) {
            throw new NullPointerException(
                    "E_EXAMPLE_BLOCKSPEC:null namespace");
        }
        if (name == null) {
            throw new NullPointerException("E_EXAMPLE_BLOCKSPEC:null name");
        }
        if (name.isEmpty()) {
            throw new IllegalArgumentException(
                    "E_EXAMPLE_BLOCKSPEC:bad name <>");
        }
        if (!Float.isFinite(hardness)) {
            throw new IllegalArgumentException(
                    "E_EXAMPLE_BLOCKSPEC:bad hardness <" + hardness
                            + "> for <" + name + ">");
        }
        this.namespace = namespace;
        this.name = name;
        this.hardness = hardness;
        this.opaque = opaque;
    }

    /** Content namespace the block was declared in (never a registry). */
    public String namespace() {
        return namespace;
    }

    /** Short name, registry matched by the operator wire (after ':'). */
    public String name() {
        return name;
    }

    public float hardness() {
        return hardness;
    }

    public boolean opaque() {
        return opaque;
    }

    /**
     * Parses every {@code block} instance of one content file once.
     * Files without blocks yield an empty list, never an error. Loud on
     * unreadable / unparsable / mistyped hardness or opacity — never
     * defaulted (NaN/Infinity hardness included: the parser accepts the
     * raw double, the spec refuses it).
     */
    @SuppressWarnings("unchecked")
    public static List<BlockSpec> fromFile(String path) {
        if (path == null) {
            throw new NullPointerException("E_EXAMPLE_BLOCKSPEC:null path");
        }
        final Map<String, Object> tree;
        try {
            tree = MatouParse.parseFile(path);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "E_EXAMPLE_BLOCKSPEC:unreadable <" + path + "> ("
                            + e.getMessage() + ")", e);
        }
        Object ns = tree.get("namespace");
        if (!(ns instanceof String)) {
            throw new IllegalArgumentException(
                    "E_EXAMPLE_BLOCKSPEC:bad namespace in <" + path + ">");
        }
        Object instances = tree.get("instances");
        if (!(instances instanceof List)) {
            throw new IllegalArgumentException(
                    "E_EXAMPLE_BLOCKSPEC:bad shape in <" + path + ">");
        }
        List<BlockSpec> out = new ArrayList<BlockSpec>();
        for (Object o : (List<Object>) instances) {
            if (!(o instanceof Map)) {
                throw new IllegalArgumentException(
                        "E_EXAMPLE_BLOCKSPEC:bad instance in <" + path
                                + ">");
            }
            Map<String, Object> inst = (Map<String, Object>) o;
            if (!"Block".equals(inst.get("decl"))) {
                continue;
            }
            Object rawName = inst.get("name");
            if (!(rawName instanceof String)
                    || ((String) rawName).isEmpty()) {
                throw new IllegalArgumentException(
                        "E_EXAMPLE_BLOCKSPEC:bad name in <" + path + ">");
            }
            String bname = (String) rawName;
            Object fields = inst.get("fields");
            if (!(fields instanceof Map)) {
                throw new IllegalArgumentException(
                        "E_EXAMPLE_BLOCKSPEC:bad fields <" + bname
                                + "> in <" + path + ">");
            }
            Map<String, Object> f = (Map<String, Object>) fields;
            Object h = f.get("hardness");
            if (!(h instanceof Number)
                    || !Float.isFinite(((Number) h).floatValue())) {
                throw new IllegalArgumentException(
                        "E_EXAMPLE_BLOCKSPEC:bad hardness <" + bname
                                + "> in <" + path + ">");
            }
            Object op = f.get("opaque");
            if (!(op instanceof Boolean)) {
                throw new IllegalArgumentException(
                        "E_EXAMPLE_BLOCKSPEC:bad opaque <" + bname
                                + "> in <" + path + ">");
            }
            out.add(new BlockSpec((String) ns, bname,
                    ((Number) h).floatValue(),
                    ((Boolean) op).booleanValue()));
        }
        return Collections.unmodifiableList(out);
    }
}
