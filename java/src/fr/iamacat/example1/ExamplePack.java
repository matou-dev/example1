package fr.iamacat.example1;

import fr.iamacat.spi.ConfigurablePack;
import fr.iamacat.spi.MatouId;
import fr.iamacat.spi.MatouJob;
import fr.iamacat.spi.MatouParse;
import java.util.Arrays;
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
 */
public final class ExamplePack implements ConfigurablePack {
    public static final String NAMESPACE = "example1";
    static final String OWNED_KEY = "ownedFile";
    static final String SCATTER_KEY = "scatterFile";

    private int ownedCount;
    private int scatterCount;
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
        this.configured = true;
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
        ExamplePack ready = fromFiles(owned, scatter);
        this.ownedCount = ready.ownedCount;
        this.scatterCount = ready.scatterCount;
        this.configured = true;
    }

    /**
     * Parses both content files once through the SPI reference parser.
     * Loud on unreadable / unparsable / missing feature — never defaulted.
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
        return Collections.unmodifiableMap(states);
    }

    public List<MatouJob<List<String>>> jobs() {
        return Collections.unmodifiableList(Arrays
                .<MatouJob<List<String>>>asList(new OwnedVeinJob(),
                        new AdditiveScatterJob()));
    }
}
