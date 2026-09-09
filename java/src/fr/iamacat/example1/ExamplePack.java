package fr.iamacat.example1;

import fr.iamacat.spi.ConfigurablePack;
import fr.iamacat.spi.MatouId;
import fr.iamacat.spi.MatouJob;
import fr.iamacat.spi.MatouParse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * B2 content pack: exposes the M2 jobs plus their counts as
 * bridge-digestible states. Counts come from content files parsed ONCE via
 * the SPI reference parser ({@link #fromFiles} / {@link #configure}) —
 * never read on the tick path, never hardcoded. Pure, Java 8, zero deps
 * beyond matou-spi.
 *
 * <p>Structure wiring is optional and additive: without
 * {@code structureFile} the pack stays the legacy 2-job pack (owned +
 * additive), so existing operators and the bridge template keep working
 * byte for byte; with it the pack also seals the composite {@code hut}
 * count and exposes its {@link StructurePlaceJob} (own volume plus the
 * {@code well} part tree) as third job. Palette aliases come from
 * {@code block.<ref>} operator args ({@code content-ref -> landable
 * block}, strict both ways, empty = identity): content decides
 * <i>where</i>, the operator decides <i>what</i>, as with the wire block
 * of plane cells. The decide path is fully wired (pure, shape-agnostic
 * merge); landing {@code x,y,z:block} cells needs a 3D-capable sink
 * (bridge {@code WorldCellSink}).
 */
public final class ExamplePack implements ConfigurablePack {
    public static final String NAMESPACE = "example1";
    static final String OWNED_KEY = "ownedFile";
    static final String SCATTER_KEY = "scatterFile";
    static final String STRUCTURE_KEY = "structureFile";
    static final String BLOCK_ALIAS_PREFIX = "block.";

    private int ownedCount;
    private int scatterCount;
    private StructurePlaceJob structure;
    private boolean configured;

    /** No-arg for the reflective loader; {@link #configure} must follow. */
    public ExamplePack() {
        this.configured = false;
    }

    public ExamplePack(int ownedCount, int scatterCount) {
        this.ownedCount =
                OwnedVeinJob.countOf(Integer.valueOf(ownedCount));
        this.scatterCount =
                OwnedVeinJob.countOf(Integer.valueOf(scatterCount));
        this.structure = null;
        this.configured = true;
    }

    /** Wired pack: {@code structure} is shared (immutable, pure). */
    public ExamplePack(int ownedCount, int scatterCount,
            StructurePlaceJob structure) {
        this(ownedCount, scatterCount);
        if (structure == null) {
            throw new NullPointerException("E_EXAMPLE_STRUCT:null job");
        }
        this.structure = structure;
    }

    public void configure(Map<String, String> args) {
        if (args == null) {
            throw new NullPointerException("E_EXAMPLE_ARGS:null");
        }
        String owned = args.get(OWNED_KEY);
        if (owned == null) {
            throw new IllegalArgumentException(
                    "E_EXAMPLE_CONTENT:missing key <ownedFile>");
        }
        String scatter = args.get(SCATTER_KEY);
        if (scatter == null) {
            throw new IllegalArgumentException(
                    "E_EXAMPLE_CONTENT:missing key <scatterFile>");
        }
        ExamplePack ready;
        String structurePath = args.get(STRUCTURE_KEY);
        if (structurePath == null) {
            ready = fromFiles(owned, scatter);
        } else {
            ready = fromFiles(owned, scatter, structurePath,
                    blockAliases(args));
        }
        this.ownedCount = ready.ownedCount;
        this.scatterCount = ready.scatterCount;
        this.structure = ready.structure;
        this.configured = true;
    }

    /**
     * Parses both content files once through the SPI reference parser.
     * Loud on unreadable / unparsable / missing feature — never defaulted.
     * Legacy 2-job pack: no structure wired.
     */
    public static ExamplePack fromFiles(String ownedPath,
            String scatterPath) {
        if (ownedPath == null) {
            throw new NullPointerException(
                    "E_EXAMPLE_CONTENT:null owned path");
        }
        if (scatterPath == null) {
            throw new NullPointerException(
                    "E_EXAMPLE_CONTENT:null scatter path");
        }
        return new ExamplePack(
                featureCount(parse(ownedPath), "my_vein", ownedPath),
                featureCount(parse(scatterPath), "scatter_additive",
                        scatterPath));
    }

    /**
     * Parses all three content files once; the structure file wires the
     * composite {@code hut} (name derived from {@link StructurePlaceJob#HUT},
     * never recopied) with identity palette. Loud on unreadable / missing
     * structure — never defaulted.
     */
    public static ExamplePack fromFiles(String ownedPath,
            String scatterPath, String structurePath) {
        return fromFiles(ownedPath, scatterPath, structurePath,
                Collections.<String, String>emptyMap());
    }

    /**
     * Parses all three content files once with palette aliases
     * ({@code content-ref -> landable block}, strict both ways). Loud on
     * unreadable / missing structure / unmapped palette / unknown alias —
     * never defaulted.
     */
    public static ExamplePack fromFiles(String ownedPath,
            String scatterPath, String structurePath,
            Map<String, String> aliases) {
        if (structurePath == null) {
            throw new NullPointerException(
                    "E_EXAMPLE_CONTENT:null structure path");
        }
        if (aliases == null) {
            throw new NullPointerException(
                    "E_EXAMPLE_CONTENT:null aliases");
        }
        ExamplePack legacy = fromFiles(ownedPath, scatterPath);
        return new ExamplePack(legacy.ownedCount, legacy.scatterCount,
                StructurePlaceJob.fromFile(structurePath,
                        StructurePlaceJob.HUT.name, aliases));
    }

    /** Operator {@code block.<ref>} args, prefix stripped, order kept. */
    static Map<String, String> blockAliases(Map<String, String> args) {
        Map<String, String> aliases = new LinkedHashMap<String, String>();
        for (Map.Entry<String, String> e : args.entrySet()) {
            if (e.getKey() != null
                    && e.getKey().startsWith(BLOCK_ALIAS_PREFIX)) {
                aliases.put(e.getKey().substring(
                        BLOCK_ALIAS_PREFIX.length()), e.getValue());
            }
        }
        return aliases;
    }

    private static Map<String, Object> parse(String path) {
        try {
            return MatouParse.parseFile(path);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "E_EXAMPLE_CONTENT:unreadable <" + path + "> ("
                            + e.getMessage() + ")");
        }
    }

    @SuppressWarnings("unchecked")
    private static int featureCount(Map<String, Object> tree,
            String feature, String path) {
        Object instances = tree.get("instances");
        if (instances instanceof List) {
            for (Object o : (List<Object>) instances) {
                if (!(o instanceof Map)) {
                    continue;
                }
                Map<String, Object> inst = (Map<String, Object>) o;
                if ("Feature".equals(inst.get("decl"))
                        && feature.equals(inst.get("name"))) {
                    Object fields = inst.get("fields");
                    if (fields instanceof Map) {
                        Object count = ((Map<String, Object>) fields)
                                .get("count");
                        if (count instanceof Number) {
                            return OwnedVeinJob.countOf(count);
                        }
                    }
                    throw new IllegalArgumentException(
                            "E_EXAMPLE_CONTENT:bad count <" + feature
                                    + "> in <" + path + ">");
                }
            }
        }
        throw new IllegalArgumentException(
                "E_EXAMPLE_CONTENT:missing feature <" + feature + "> in <"
                        + path + ">");
    }

    public String namespace() {
        return NAMESPACE;
    }

    public Map<MatouId, Object> states(long tick) {
        if (!configured) {
            throw new IllegalStateException("E_EXAMPLE_PACK:unconfigured");
        }
        if (tick < 0) {
            throw new IllegalArgumentException(
                    "E_EXAMPLE_TICK:range <" + tick + "> (want >= 0)");
        }
        Map<MatouId, Object> states = new LinkedHashMap<MatouId, Object>();
        states.put(OwnedVeinJob.VEIN, Long.valueOf(ownedCount));
        states.put(AdditiveScatterJob.SCATTER, Long.valueOf(scatterCount));
        if (structure != null) {
            states.put(structure.id(),
                    Long.valueOf(structure.contentCount()));
        }
        return Collections.unmodifiableMap(states);
    }

    public List<MatouJob<List<String>>> jobs() {
        List<MatouJob<List<String>>> jobs =
                new ArrayList<MatouJob<List<String>>>();
        jobs.add(new OwnedVeinJob());
        jobs.add(new AdditiveScatterJob());
        if (structure != null) {
            jobs.add(structure);
        }
        return Collections.unmodifiableList(jobs);
    }
}
