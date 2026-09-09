package fr.iamacat.example1;

import fr.iamacat.spi.MatouId;
import fr.iamacat.spi.MatouJob;
import fr.iamacat.spi.MatouParse;
import fr.iamacat.spi.MatouRng;
import fr.iamacat.spi.Snapshot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * V3 structure job: one volume plus recursive {@code parts} (SYNTAX-V3).
 * Pure (no IO, no Minecraft, no clock). Java 8, zero deps beyond matou-spi.
 *
 * <p>Parser accepts any integers/lists; the job refuses non-positive extents
 * ({@code E_EXAMPLE_SIZE}) and empty palettes ({@code E_EXAMPLE_PALETTE}).
 * Each occurrence places own volume first, then parts depth-first, sharing
 * one plane offset. Cross-file parts ({@link #fromFiles}) are
 * parser-strict on imports, wiring-strict on file-set completeness, and
 * cycle-refusing — never defaulted. Palette aliases are identity when empty,
 * strict both ways otherwise. Cells are {@code "x,y,z:ns:block"}.
 */
public final class StructurePlaceJob implements MatouJob<List<String>> {
    public static final MatouId WELL =
            MatouId.parse("example1.structures:well");
    public static final MatouId HUT =
            MatouId.parse("example1.structures:hut");
    static final int CELLS = 16;

    private final MatouId id;
    private final int[] anchor;
    private final int[] size;
    private final List<String> palette;
    private final List<StructurePlaceJob> parts;
    private final int contentCount;

    public StructurePlaceJob(MatouId id, int[] anchor, int[] size,
            List<String> palette, List<StructurePlaceJob> parts,
            int count) {
        reqArg(id, "E_EXAMPLE_STRUCT:null id");
        this.anchor = vecOf(anchor, "anchor", id);
        this.size = vecOf(size, "size", id);
        this.palette = paletteOf(palette, id);
        this.parts = partsOf(parts, id);
        this.contentCount = OwnedVeinJob.countOf(Integer.valueOf(count));
        this.id = id;
    }

    public MatouId id() { return id; }
    public int[] anchor() { return anchor.clone(); }
    public int[] size() { return size.clone(); }
    public List<String> palette() { return palette; }
    public List<StructurePlaceJob> parts() { return parts; }

    /** Count from the content file (tick counts come from snapshots). */
    public int contentCount() { return contentCount; }

    /** Own volume plus every part volume, recursively (capacity hint). */
    int treeVolume() {
        int volume = size[0] * size[1] * size[2];
        for (StructurePlaceJob part : parts) {
            volume += part.treeVolume();
        }
        return volume;
    }

    public List<String> decide(Snapshot snap) {
        reqArg(snap, "E_EXAMPLE_SNAPSHOT:null");
        int count = OwnedVeinJob.countOf(snap.get(id), id);
        MatouRng rng = MatouRng.forAddress(id.namespace, id.name,
                Long.toString(snap.tick()));
        List<String> out = new ArrayList<String>(count * treeVolume());
        for (int o = 0; o < count; o++) {
            placeTree(this, rng.nextInt(CELLS), rng.nextInt(CELLS), rng, out);
        }
        return Collections.unmodifiableList(out);
    }

    private static void placeTree(StructurePlaceJob job, int ox, int oz,
            MatouRng rng, List<String> out) {
        for (int dx = 0; dx < job.size[0]; dx++) {
            for (int dy = 0; dy < job.size[1]; dy++) {
                for (int dz = 0; dz < job.size[2]; dz++) {
                    String block = job.palette.get(
                            rng.nextInt(job.palette.size()));
                    out.add((job.anchor[0] + ox + dx) + ","
                            + (job.anchor[1] + dy) + ","
                            + (job.anchor[2] + oz + dz) + ":" + block);
                }
            }
        }
        for (StructurePlaceJob part : job.parts) {
            placeTree(part, ox, oz, rng, out);
        }
    }

    /** Position-keyed merge: owned first verbatim, additive only on new pos. */
    public static List<String> merge(List<String> owned,
            List<String> additive) {
        reqArg(owned, "E_EXAMPLE_MERGE:null owned");
        reqArg(additive, "E_EXAMPLE_MERGE:null additive");
        Set<String> seen = new HashSet<String>();
        for (String cell : owned) {
            seen.add(posOf(cell));
        }
        List<String> out = new ArrayList<String>(owned);
        for (String cell : additive) {
            if (seen.add(posOf(cell))) {
                out.add(cell);
            }
        }
        return Collections.unmodifiableList(out);
    }

    /** Single-file wiring, identity palette (convenience over fromFiles). */
    public static StructurePlaceJob fromFile(String path, String name) {
        return fromFile(path, name,
                Collections.<String, String>emptyMap());
    }

    /** Single-file wiring with palette aliases (strict both ways). */
    public static StructurePlaceJob fromFile(String path, String name,
            Map<String, String> aliases) {
        reqArg(path, "E_EXAMPLE_CONTENT:null structure path");
        reqArg(name, "E_EXAMPLE_CONTENT:null structure name");
        reqArg(aliases, "E_EXAMPLE_CONTENT:null aliases");
        Map<String, FileModel> files = loadFiles(
                Collections.singletonList(path));
        return wireRoot(files,
                files.keySet().iterator().next() + ":" + name, aliases);
    }

    /** Multi-file wiring, identity palette (root is qualified ns:name). */
    public static StructurePlaceJob fromFiles(List<String> paths,
            String qualifiedName) {
        return fromFiles(paths, qualifiedName,
                Collections.<String, String>emptyMap());
    }

    /** Multi-file wiring with palette aliases (strict over the tree). */
    public static StructurePlaceJob fromFiles(List<String> paths,
            String qualifiedName, Map<String, String> aliases) {
        reqArg(paths, "E_EXAMPLE_CONTENT:null structure files");
        reqArg(qualifiedName, "E_EXAMPLE_CONTENT:null structure name");
        reqArg(aliases, "E_EXAMPLE_CONTENT:null aliases");
        if (paths.isEmpty()) {
            throw new IllegalArgumentException(
                    "E_EXAMPLE_CONTENT:no structure files");
        }
        return wireRoot(loadFiles(paths), qualifiedName, aliases);
    }

    /** Shared root wiring: root check, recursive resolve, alias coverage. */
    private static StructurePlaceJob wireRoot(Map<String, FileModel> files,
            String qualifiedName, Map<String, String> aliases) {
        MatouId root = MatouId.parse(qualifiedName);
        if (!files.containsKey(root.namespace)) {
            throw new IllegalArgumentException(
                    "E_EXAMPLE_CONTENT:unknown namespace <"
                            + root.namespace + "> for root <"
                            + qualifiedName + "> (loaded "
                            + new ArrayList<String>(files.keySet()) + ")");
        }
        Set<String> used = new HashSet<String>();
        StructurePlaceJob job = resolveRef(files, null, root.namespace,
                root.name, new ArrayList<String>(), aliases, used);
        for (String key : aliases.keySet()) {
            if (!used.contains(key)) {
                throw new IllegalArgumentException(
                        "E_EXAMPLE_CONTENT:unknown alias <" + key
                                + "> for <" + qualifiedName + ">");
            }
        }
        return job;
    }

    /** One parsed content file: namespace plus structures by local name. */
    private static final class FileModel {
        final String path;
        final String namespace;
        final Map<String, Map<String, Object>> structures;
        FileModel(String path, String namespace,
                Map<String, Map<String, Object>> structures) {
            this.path = path;
            this.namespace = namespace;
            this.structures = structures;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, FileModel> loadFiles(List<String> paths) {
        Map<String, FileModel> files = new LinkedHashMap<String, FileModel>();
        for (String path : paths) {
            reqArg(path, "E_EXAMPLE_CONTENT:null structure path");
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
                    if (!"Structure".equals(inst.get("decl"))) continue;
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

    private static StructurePlaceJob resolveRef(
            Map<String, FileModel> files, FileModel referrer, String refNs,
            String refName, List<String> chain,
            Map<String, String> aliases, Set<String> used) {
        String qualified = refNs + ":" + refName;
        if (chain.contains(qualified)) {
            List<String> cycle = new ArrayList<String>(chain);
            cycle.add(qualified);
            throw new IllegalArgumentException("E_EXAMPLE_PARTS:cycle <"
                    + String.join(" -> ", cycle) + ">");
        }
        FileModel target = files.get(refNs);
        if (target == null) {
            String where = referrer == null ? "root <" + qualified + ">"
                    : "in <" + referrer.path + ">";
            throw new IllegalArgumentException(
                    "E_EXAMPLE_CONTENT:unknown namespace <" + refNs
                            + "> for <" + qualified + "> " + where
                            + " (no loaded file provides it — pass every"
                            + " providing file to fromFiles)");
        }
        Map<String, Object> f = target.structures.get(refName);
        if (f == null) {
            throw new IllegalArgumentException(
                    "E_EXAMPLE_CONTENT:missing structure <" + qualified
                            + "> in <" + target.path + ">");
        }
        List<String> rawParts =
                refsOf(f.get("parts"), "parts", refName, target.path);
        List<String> visiting = new ArrayList<String>(chain);
        visiting.add(qualified);
        List<StructurePlaceJob> parts =
                new ArrayList<StructurePlaceJob>(rawParts.size());
        for (String ref : rawParts) {
            int cut = ref.indexOf(':');
            if (cut < 0) {
                throw bad("parts", refName, target.path);
            }
            parts.add(resolveRef(files, target, ref.substring(0, cut),
                    ref.substring(cut + 1), visiting, aliases, used));
        }
        return new StructurePlaceJob(MatouId.of(refNs, refName),
                intsOf(f.get("anchor"), "anchor", refName, target.path),
                intsOf(f.get("size"), "size", refName, target.path),
                substitute(refsOf(f.get("palette"), "palette", refName,
                        target.path), aliases, used, refName, target.path),
                Collections.unmodifiableList(parts),
                numOf(f.get("count"), "count", refName, target.path));
    }

    private static List<String> substitute(List<String> palette,
            Map<String, String> aliases, Set<String> used, String name,
            String path) {
        if (aliases.isEmpty()) return palette;
        List<String> out = new ArrayList<String>(palette.size());
        for (String entry : palette) {
            String to = aliases.get(entry);
            if (to == null) {
                throw new IllegalArgumentException(
                        "E_EXAMPLE_CONTENT:unmapped palette <" + entry
                                + "> in <" + name + "> (<" + path + ">)");
            }
            if (to.isEmpty() || to.indexOf(':') < 0) {
                throw new IllegalArgumentException(
                        "E_EXAMPLE_CONTENT:bad alias <" + entry + " -> "
                                + to + "> in <" + path + ">");
            }
            used.add(entry);
            out.add(to);
        }
        return out;
    }

    private static String posOf(String cell) {
        reqArg(cell, "E_EXAMPLE_MERGE:null cell");
        int cut = cell.indexOf(':');
        if (cut < 0) {
            throw new IllegalArgumentException(
                    "E_EXAMPLE_MERGE:bad cell <" + cell + ">");
        }
        return cell.substring(0, cut);
    }

    /**
     * Ctor vec table: {@code anchor} wants any 3-vector, {@code size} wants
     * all components positive — one shape check, one range rule by name.
     */
    private static int[] vecOf(int[] v, String what, MatouId id) {
        reqArg(v, "E_EXAMPLE_STRUCT:null " + what + " <" + id + ">");
        if (v.length != 3) {
            throw new IllegalArgumentException("E_EXAMPLE_SIZE:shape <"
                    + what + " len " + v.length + "> (want 3)");
        }
        if ("size".equals(what)) {
            for (int c : v) {
                if (c <= 0) {
                    throw new IllegalArgumentException(
                            "E_EXAMPLE_SIZE:range <" + v[0] + "," + v[1]
                                    + "," + v[2] + "> (want all > 0)");
                }
            }
        }
        return v.clone();
    }

    private static List<String> paletteOf(List<String> palette, MatouId id) {
        reqArg(palette, "E_EXAMPLE_STRUCT:null palette <" + id + ">");
        if (palette.isEmpty()) {
            throw new IllegalArgumentException(
                    "E_EXAMPLE_PALETTE:empty <" + id + ">");
        }
        List<String> copy = new ArrayList<String>(palette);
        for (String block : copy) {
            if (block == null || block.isEmpty()) {
                throw new IllegalArgumentException(
                        "E_EXAMPLE_PALETTE:bad entry <" + id + ">");
            }
        }
        return Collections.unmodifiableList(copy);
    }

    private static List<StructurePlaceJob> partsOf(
            List<StructurePlaceJob> parts, MatouId id) {
        reqArg(parts, "E_EXAMPLE_STRUCT:null parts <" + id + ">");
        List<StructurePlaceJob> copy = new ArrayList<StructurePlaceJob>(parts);
        for (StructurePlaceJob part : copy) {
            reqArg(part, "E_EXAMPLE_STRUCT:null part <" + id + ">");
        }
        return Collections.unmodifiableList(copy);
    }

    /** Null table: every null arg shares one throw shape (code varies). */
    private static void reqArg(Object v, String code) {
        if (v == null) {
            throw new NullPointerException(code);
        }
    }

    /** Wiring error table: every bad field shares one shape message. */
    private static IllegalArgumentException bad(String field, String name,
            String path) {
        return new IllegalArgumentException("E_EXAMPLE_CONTENT:bad " + field
                + " <" + name + "> in <" + path + ">");
    }

    /** Wiring list table: every list field flows through one shape check. */
    private static List<?> reqList(Object raw, String field, String name,
            String path) {
        if (!(raw instanceof List)) {
            throw bad(field, name, path);
        }
        return (List<?>) raw;
    }

    /** Wiring number table: every number field flows through one check. */
    private static long reqNum(Object raw, String field, String name,
            String path) {
        if (!(raw instanceof Number)) {
            throw bad(field, name, path);
        }
        return ((Number) raw).longValue();
    }

    private static int[] intsOf(Object raw, String field, String name,
            String path) {
        List<?> items = reqList(raw, field, name, path);
        if (items.size() != 3) {
            throw bad(field, name, path);
        }
        int[] out = new int[3];
        for (int i = 0; i < 3; i++) {
            long v = reqNum(items.get(i), field, name, path);
            if (v < Integer.MIN_VALUE || v > Integer.MAX_VALUE) {
                throw bad(field + " range", name, path);
            }
            out[i] = (int) v;
        }
        return out;
    }

    private static List<String> refsOf(Object raw, String field, String name,
            String path) {
        List<?> items = reqList(raw, field, name, path);
        List<String> out = new ArrayList<String>(items.size());
        for (Object e : items) {
            if (!(e instanceof String)) {
                throw bad(field, name, path);
            }
            out.add((String) e);
        }
        return out;
    }

    private static int numOf(Object raw, String field, String name,
            String path) {
        long v = reqNum(raw, field, name, path);
        if (v <= 0 || v > Integer.MAX_VALUE) {
            throw bad(field, name, path);
        }
        return (int) v;
    }
}
