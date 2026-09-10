package fr.iamacat.example1;

import fr.iamacat.spi.Cell;
import fr.iamacat.spi.MatouId;
import fr.iamacat.spi.MatouJob;
import fr.iamacat.spi.MatouParse;
import fr.iamacat.spi.MatouRng;
import fr.iamacat.spi.Snapshot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * V4 vein job: seeded clusters of one block (SYNTAX-V4 {@code vein} genre).
 * Pure (no IO, no Minecraft, no clock). Java 8, zero deps beyond matou-spi.
 *
 * <p>The content file wires identity (block ref, extents, seed, count);
 * the tick path reads only snapshot states: {@code count} under
 * {@link #id()} plus the {@code size} vector under {@link #sizeId()},
 * both sealed from content by {@link ExamplePack#states}. Missing, mistyped
 * or non-positive states are refused {@code Counts}-style (missing / type /
 * range trios with the job's own codes), never defaulted — the parser stays
 * syntactic (any ints), the job owns the semantics, same split as
 * {@code structure.size}. Each of {@code count} clusters lands the full
 * {@code size} extents at a seeded origin, so the decision is a solid
 * cluster, never a scatter. Cells are
 * {@code "x,y,z:block"} volume cells carrying the landable block name
 * (operator-mapped through {@code veinblock.} aliases, identity when the
 * map is empty).
 *
 * <p>The vein genre has no anchor field, so clusters land on one named
 * band ({@link #BASE_Y}): content decides <i>where</i> (here), the operator
 * decides <i>what</i> (the landable block), same split as structure
 * palettes. The band sits below the plane slice (63) and the structure
 * slices (64..65), so vein cells never collide with legacy decisions.
 */
public final class VeinPlaceJob implements MatouJob<List<String>> {
    /** Content root wired by default (name derived here, never recopied). */
    public static final MatouId ORE =
            MatouId.parse("example1.veins:ore_vein");

    /**
     * Cluster base ordinate: clusters occupy
     * {@code BASE_Y..BASE_Y+size[1]-1}. A constant, never a default —
     * the genre carries no anchor, so the band is named here, loudly.
     */
    public static final int BASE_Y = 60;

    private final MatouId id;
    private final MatouId sizeId;
    private final String block;
    private final int[] size;
    private final int seed;
    private final int contentCount;

    public VeinPlaceJob(MatouId id, String block, int[] size, int seed,
            int count) {
        if (id == null) {
            throw new NullPointerException("E_EXAMPLE_VEIN:null id");
        }
        if (block == null || block.isEmpty()) {
            throw new IllegalArgumentException(
                    "E_EXAMPLE_VEIN:bad block <" + block + "> for <"
                            + id + ">");
        }
        this.id = id;
        this.sizeId = MatouId.of(id.namespace, id.name + "_size");
        this.block = block;
        this.size = vecOf(size, id);
        this.seed = seed;
        this.contentCount = OwnedVeinJob.countOf(Integer.valueOf(count));
    }

    public MatouId id() { return id; }

    /**
     * Snapshot id the {@code size} vector is sealed under (derived once,
     * never concatenated at call sites).
     */
    public MatouId sizeId() { return sizeId; }

    /** Landable block every cluster cell carries. */
    public String block() { return block; }

    public int[] size() { return size.clone(); }

    /** Content salt, part of the RNG address. */
    public int seed() { return seed; }

    /** Count from the content file (tick counts come from snapshots). */
    public int contentCount() { return contentCount; }

    public List<String> decide(Snapshot snap) {
        if (snap == null) {
            throw new NullPointerException("E_EXAMPLE_SNAPSHOT:null");
        }
        int count = OwnedVeinJob.countOf(snap.get(id), id);
        int[] extents = sizeOf(snap.get(sizeId), sizeId);
        MatouRng rng = MatouRng.forAddress(id.namespace, id.name,
                Integer.toString(seed), Long.toString(snap.tick()));
        List<String> out = new ArrayList<String>(
                count * extents[0] * extents[1] * extents[2]);
        for (int c = 0; c < count; c++) {
            int ox = rng.nextInt(ExampleIds.GRID);
            int oz = rng.nextInt(ExampleIds.GRID);
            for (int dx = 0; dx < extents[0]; dx++) {
                for (int dy = 0; dy < extents[1]; dy++) {
                    for (int dz = 0; dz < extents[2]; dz++) {
                        out.add(Cell.of(ox + dx, BASE_Y + dy, oz + dz,
                                block).render());
                    }
                }
            }
        }
        return Collections.unmodifiableList(out);
    }

    /**
     * Snapshot size rule, {@code Counts}-style trio under the job's own
     * code: null is missing, a non-3-vector is a type error, a
     * non-positive extent is a range error. Never defaulted.
     */
    static int[] sizeOf(Object raw, MatouId at) {
        if (raw == null) {
            throw new IllegalArgumentException(
                    "E_EXAMPLE_SIZE:missing <" + at + ">");
        }
        if (!(raw instanceof List)) {
            throw new IllegalArgumentException("E_EXAMPLE_SIZE:type <"
                    + raw + "> (want 3-vector)");
        }
        List<?> items = (List<?>) raw;
        if (items.size() != 3) {
            throw new IllegalArgumentException("E_EXAMPLE_SIZE:type <"
                    + raw + "> (want 3-vector)");
        }
        int[] out = new int[3];
        for (int i = 0; i < 3; i++) {
            Object e = items.get(i);
            if (!(e instanceof Number)) {
                throw new IllegalArgumentException(
                        "E_EXAMPLE_SIZE:type <" + raw + "> (want 3-vector)");
            }
            out[i] = ((Number) e).intValue();
            if (out[i] <= 0) {
                throw new IllegalArgumentException("E_EXAMPLE_SIZE:range <"
                        + out[0] + "," + out[1] + "," + out[2]
                        + "> (want all > 0)");
            }
        }
        return out;
    }

    /** Ctor extents: exactly 3 components, all positive. */
    private static int[] vecOf(int[] v, MatouId id) {
        if (v == null) {
            throw new NullPointerException(
                    "E_EXAMPLE_VEIN:null size <" + id + ">");
        }
        if (v.length != 3) {
            throw new IllegalArgumentException("E_EXAMPLE_SIZE:shape <len "
                    + v.length + "> (want 3)");
        }
        for (int c : v) {
            if (c <= 0) {
                throw new IllegalArgumentException("E_EXAMPLE_SIZE:range <"
                        + v[0] + "," + v[1] + "," + v[2] + "> (want all > 0)");
            }
        }
        return v.clone();
    }

    /** Single-file wiring, identity block (convenience over fromFiles). */
    public static VeinPlaceJob fromFile(String path, String name) {
        return fromFile(path, name,
                Collections.<String, String>emptyMap());
    }

    /** Single-file wiring with landable aliases (strict both ways). */
    public static VeinPlaceJob fromFile(String path, String name,
            Map<String, String> aliases) {
        if (path == null) {
            throw new NullPointerException(
                    "E_EXAMPLE_CONTENT:null vein path");
        }
        if (name == null) {
            throw new NullPointerException(
                    "E_EXAMPLE_CONTENT:null vein name");
        }
        if (aliases == null) {
            throw new NullPointerException(
                    "E_EXAMPLE_CONTENT:null aliases");
        }
        Map<String, FileModel> files = loadFiles(
                Collections.singletonList(path));
        return wireRoot(files,
                files.keySet().iterator().next() + ":" + name, aliases);
    }

    /** Multi-file wiring, identity block (root is qualified ns:name). */
    public static VeinPlaceJob fromFiles(List<String> paths,
            String qualifiedName) {
        return fromFiles(paths, qualifiedName,
                Collections.<String, String>emptyMap());
    }

    /** Multi-file wiring with landable aliases (strict both ways). */
    public static VeinPlaceJob fromFiles(List<String> paths,
            String qualifiedName, Map<String, String> aliases) {
        if (paths == null) {
            throw new NullPointerException(
                    "E_EXAMPLE_CONTENT:null vein files");
        }
        if (qualifiedName == null) {
            throw new NullPointerException(
                    "E_EXAMPLE_CONTENT:null vein name");
        }
        if (aliases == null) {
            throw new NullPointerException(
                    "E_EXAMPLE_CONTENT:null aliases");
        }
        if (paths.isEmpty()) {
            throw new IllegalArgumentException(
                    "E_EXAMPLE_CONTENT:no vein files");
        }
        return wireRoot(loadFiles(paths), qualifiedName, aliases);
    }

    /** One parsed content file: namespace plus veins by local name. */
    private static final class FileModel {
        final String path;
        final String namespace;
        final Map<String, Map<String, Object>> veins;
        FileModel(String path, String namespace,
                Map<String, Map<String, Object>> veins) {
            this.path = path;
            this.namespace = namespace;
            this.veins = veins;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, FileModel> loadFiles(List<String> paths) {
        Map<String, FileModel> files = new LinkedHashMap<String, FileModel>();
        for (String path : paths) {
            if (path == null) {
                throw new NullPointerException(
                        "E_EXAMPLE_CONTENT:null vein path");
            }
            Map<String, Object> tree;
            try {
                tree = MatouParse.parseFile(path);
            } catch (Exception e) {
                throw new IllegalArgumentException(
                        "E_EXAMPLE_CONTENT:unreadable <" + path + "> ("
                                + e.getMessage() + ")");
            }
            String namespace = String.valueOf(tree.get("namespace"));
            if (files.containsKey(namespace)) {
                throw new IllegalArgumentException(
                        "E_EXAMPLE_CONTENT:duplicate namespace <" + namespace
                                + "> (<" + files.get(namespace).path + "> vs <"
                                + path + ">)");
            }
            Map<String, Map<String, Object>> byName =
                    new HashMap<String, Map<String, Object>>();
            Object instances = tree.get("instances");
            if (instances instanceof List) {
                for (Object o : (List<Object>) instances) {
                    if (!(o instanceof Map)) continue;
                    Map<String, Object> inst = (Map<String, Object>) o;
                    if (!"Vein".equals(inst.get("decl"))) continue;
                    Object fields = inst.get("fields");
                    if (fields instanceof Map) {
                        byName.put(String.valueOf(inst.get("name")),
                                (Map<String, Object>) fields);
                    }
                }
            }
            files.put(namespace, new FileModel(path, namespace, byName));
        }
        return files;
    }

    /** Shared root wiring: root check, field checks, alias coverage. */
    private static VeinPlaceJob wireRoot(Map<String, FileModel> files,
            String qualifiedName, Map<String, String> aliases) {
        MatouId root = MatouId.parse(qualifiedName);
        if (!files.containsKey(root.namespace)) {
            throw new IllegalArgumentException(
                    "E_EXAMPLE_CONTENT:unknown namespace <"
                            + root.namespace + "> for root <"
                            + qualifiedName + "> (loaded "
                            + new ArrayList<String>(files.keySet()) + ")");
        }
        FileModel target = files.get(root.namespace);
        Map<String, Object> f = target.veins.get(root.name);
        if (f == null) {
            throw new IllegalArgumentException(
                    "E_EXAMPLE_CONTENT:missing vein <" + qualifiedName
                            + "> in <" + target.path + ">");
        }
        Object rawBlock = f.get("block");
        if (!(rawBlock instanceof String)
                || ((String) rawBlock).isEmpty()) {
            throw new IllegalArgumentException(
                    "E_EXAMPLE_CONTENT:bad block <" + root.name + "> in <"
                            + target.path + ">");
        }
        String block = substitute((String) rawBlock, aliases, root.name,
                target.path);
        return new VeinPlaceJob(root, block,
                intsOf(f.get("size"), root.name, target.path),
                seedOf(f.get("seed"), root.name, target.path),
                numOf(f.get("count"), root.name, target.path));
    }

    /**
     * Single-ref substitution: identity when the map is empty, strict
     * both ways otherwise (unmapped palette and unknown key both refuse —
     * the operator maps the content ref to the landable block, e.g.
     * {@code example1.content:my_ore} to {@code example1:my_ore}).
     */
    private static String substitute(String block, Map<String, String> aliases,
            String name, String path) {
        if (aliases.isEmpty()) {
            return block;
        }
        String to = aliases.get(block);
        if (to == null) {
            throw new IllegalArgumentException(
                    "E_EXAMPLE_CONTENT:unmapped block <" + block + "> in <"
                            + name + "> (<" + path + ">)");
        }
        if (to.isEmpty() || to.indexOf(':') < 0) {
            throw new IllegalArgumentException(
                    "E_EXAMPLE_CONTENT:bad alias <" + block + " -> " + to
                            + "> in <" + path + ">");
        }
        for (String key : aliases.keySet()) {
            if (!key.equals(block)) {
                throw new IllegalArgumentException(
                        "E_EXAMPLE_CONTENT:unknown alias <" + key + "> for <"
                                + name + "> (<" + path + ">)");
            }
        }
        return to;
    }

    /** Wiring error table: every bad field shares one shape message. */
    private static IllegalArgumentException bad(String field, String name,
            String path) {
        return new IllegalArgumentException("E_EXAMPLE_CONTENT:bad " + field
                + " <" + name + "> in <" + path + ">");
    }

    private static int[] intsOf(Object raw, String name, String path) {
        if (!(raw instanceof List)) {
            throw bad("size", name, path);
        }
        List<?> items = (List<?>) raw;
        if (items.size() != 3) {
            throw bad("size", name, path);
        }
        int[] out = new int[3];
        for (int i = 0; i < 3; i++) {
            Object e = items.get(i);
            if (!(e instanceof Number)) {
                throw bad("size", name, path);
            }
            long v = ((Number) e).longValue();
            if (v < Integer.MIN_VALUE || v > Integer.MAX_VALUE) {
                throw bad("size range", name, path);
            }
            out[i] = (int) v;
        }
        return out;
    }

    private static int seedOf(Object raw, String name, String path) {
        if (!(raw instanceof Number)) {
            throw bad("seed", name, path);
        }
        long v = ((Number) raw).longValue();
        if (v < Integer.MIN_VALUE || v > Integer.MAX_VALUE) {
            throw bad("seed range", name, path);
        }
        return (int) v;
    }

    private static int numOf(Object raw, String name, String path) {
        if (!(raw instanceof Number)) {
            throw bad("count", name, path);
        }
        long v = ((Number) raw).longValue();
        if (v <= 0 || v > Integer.MAX_VALUE) {
            throw bad("count", name, path);
        }
        return (int) v;
    }
}
