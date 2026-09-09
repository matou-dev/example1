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
 * V3 structure job: decides the cells of one structure volume from its
 * {@code anchor}/{@code size}/{@code palette} plus, recursively, its
 * {@code parts} (spec {@code SYNTAX-V3.md}). Pure (no IO, no Minecraft, no
 * clock): same snapshot in, equal decision out. Java 8, zero deps beyond
 * matou-spi.
 *
 * <p>Split parser/job (same as {@code feature.count} refused by
 * {@link OwnedVeinJob}): the parser accepts any integers and any uniform
 * list, the job refuses non-positive extents ({@code E_EXAMPLE_SIZE}) and
 * an empty palette ({@code E_EXAMPLE_PALETTE}) loudly, never defaulted.
 *
 * <p>Composition: each occurrence places the own volume first, then parts
 * depth-first in listed order (landing order decides overlaps). Every
 * volume of the occurrence shares one plane offset, so the composed shape
 * survives the per-tick addressing: part anchors stay absolute, the offset
 * shifts the whole tree. A part's own {@code count} applies to standalone
 * wiring only; as a part it is placed once per parent occurrence. Parts
 * may live in other files ({@link #fromFiles}): import strictness is
 * parser-enforced ({@code E_MATOU_UNKNOWN_REF} unless the part namespace
 * is the file's own or declared via {@code from}), while file-set
 * completeness is wiring-enforced ({@code E_EXAMPLE_CONTENT:unknown
 * namespace} when an imported namespace has no loaded file). Cycles —
 * same-file or cross-file — are refused at wiring
 * ({@code E_EXAMPLE_PARTS:cycle}), never skipped silently.
 *
 * <p>Palette aliases ({@link #fromFile(String, String, Map)}): operator
 * bindings of content block refs to landable blocks (e.g. vanilla
 * names for the live run — content decides <i>where</i>, the operator
 * decides <i>what</i>, as with the wire block of plane cells). An empty
 * map is the identity; a non-empty map is strict both ways (unmapped
 * palette entry or unknown alias key both fail loudly at wiring).
 *
 * <p>Cells are {@code "x,y,z:ns:block"} strings. Per tick the job places
 * {@code count} occurrences (count read from the snapshot, as with
 * {@link OwnedVeinJob}); each occurrence offsets the tree in a 16x16 plane
 * and picks each cell block from the palette through the addressed RNG, so
 * the decision is deterministic per tick.
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
        if (id == null) {
            throw new NullPointerException("E_EXAMPLE_STRUCT:null id");
        }
        this.anchor = vecOf(anchor, "anchor", id);
        this.size = sizeOf(size, id);
        this.palette = paletteOf(palette, id);
        this.parts = partsOf(parts, id);
        this.contentCount = OwnedVeinJob.countOf(Integer.valueOf(count));
        this.id = id;
    }

    public MatouId id() {
        return id;
    }

    public int[] anchor() {
        return anchor.clone();
    }

    public int[] size() {
        return size.clone();
    }

    public List<String> palette() {
        return palette;
    }

    public List<StructurePlaceJob> parts() {
        return parts;
    }

    /** Count parsed from the content file (tick counts come from snapshots). */
    public int contentCount() {
        return contentCount;
    }

    /** Own volume plus every part volume, recursively (capacity hint). */
    int treeVolume() {
        int volume = size[0] * size[1] * size[2];
        for (StructurePlaceJob part : parts) {
            volume += part.treeVolume();
        }
        return volume;
    }

    public List<String> decide(Snapshot snap) {
        if (snap == null) {
            throw new NullPointerException("E_EXAMPLE_SNAPSHOT:null");
        }
        int count = OwnedVeinJob.countOf(snap.get(id), id);
        MatouRng rng = MatouRng.forAddress(id.namespace, id.name,
                Long.toString(snap.tick()));
        List<String> out = new ArrayList<String>(count * treeVolume());
        for (int o = 0; o < count; o++) {
            placeTree(this, rng.nextInt(CELLS), rng.nextInt(CELLS),
                    rng, out);
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

    /**
     * Owned/additif merge for structure cells (Q3-Q4, same rule as
     * {@link AdditiveScatterJob#merge} but position-keyed): owned order
     * preserved verbatim, additive cells appended only when their position
     * ({@code x,y,z} before the first {@code :}) is absent — an additive
     * block never replaces an owned one. Both inputs stay untouched.
     */
    public static List<String> merge(List<String> owned,
            List<String> additive) {
        if (owned == null) {
            throw new NullPointerException("E_EXAMPLE_MERGE:null owned");
        }
        if (additive == null) {
            throw new NullPointerException("E_EXAMPLE_MERGE:null additive");
        }
        List<String> seen = new ArrayList<String>(owned.size());
        for (String cell : owned) {
            seen.add(posOf(cell));
        }
        List<String> out = new ArrayList<String>(owned);
        for (String cell : additive) {
            String pos = posOf(cell);
            if (!seen.contains(pos)) {
                seen.add(pos);
                out.add(cell);
            }
        }
        return Collections.unmodifiableList(out);
    }

    /**
     * Wires one structure plus its part tree from a content file through
     * the SPI reference parser, once (never on the tick path). Identity
     * palette (content refs land as-is). Single-file convenience over
     * {@link #fromFiles}: parts outside this file are refused loudly
     * ({@code E_EXAMPLE_CONTENT:unknown namespace} — rewire through
     * {@code fromFiles} with every providing file). Loud on unreadable /
     * unparsable / missing structure / malformed fields / cycles — never
     * defaulted.
     */
    public static StructurePlaceJob fromFile(String path, String name) {
        return fromFile(path, name,
                Collections.<String, String>emptyMap());
    }

    /**
     * Wires one structure plus its part tree with palette aliases
     * ({@code content-ref -> landable block}). An empty map is the
     * identity; a non-empty map must cover every palette entry of the
     * tree and hold no foreign key, else wiring fails loudly.
     */
    @SuppressWarnings("unchecked")
    public static StructurePlaceJob fromFile(String path, String name,
            Map<String, String> aliases) {
        if (path == null) {
            throw new NullPointerException(
                    "E_EXAMPLE_CONTENT:null structure path");
        }
        if (name == null) {
            throw new NullPointerException(
                    "E_EXAMPLE_CONTENT:null structure name");
        }
        if (aliases == null) {
            throw new NullPointerException(
                    "E_EXAMPLE_CONTENT:null aliases");
        }
        String namespace =
                String.valueOf(parseTree(path).get("namespace"));
        return fromFiles(Collections.singletonList(path),
                namespace + ":" + name, aliases);
    }

    /**
     * Wires one structure plus its part tree from a set of content files,
     * parsed once each through the SPI reference parser (never on the
     * tick path). Identity palette (content refs land as-is). The root is
     * a qualified {@code namespace:name}; parts resolve in whichever
     * loaded file provides their namespace, depth-first in listed order.
     * Loud on unreadable / unparsable / unknown namespace / missing
     * structure / malformed fields / duplicate namespace / cycles — never
     * defaulted.
     */
    public static StructurePlaceJob fromFiles(List<String> paths,
            String qualifiedName) {
        return fromFiles(paths, qualifiedName,
                Collections.<String, String>emptyMap());
    }

    /**
     * Wires one structure plus its part tree from a set of content files
     * with palette aliases ({@code content-ref -> landable block}). An
     * empty map is the identity; a non-empty map must cover every palette
     * entry of the whole cross-file tree and hold no foreign key, else
     * wiring fails loudly.
     */
    @SuppressWarnings("unchecked")
    public static StructurePlaceJob fromFiles(List<String> paths,
            String qualifiedName, Map<String, String> aliases) {
        if (paths == null) {
            throw new NullPointerException(
                    "E_EXAMPLE_CONTENT:null structure files");
        }
        if (qualifiedName == null) {
            throw new NullPointerException(
                    "E_EXAMPLE_CONTENT:null structure name");
        }
        if (aliases == null) {
            throw new NullPointerException(
                    "E_EXAMPLE_CONTENT:null aliases");
        }
        if (paths.isEmpty()) {
            throw new IllegalArgumentException(
                    "E_EXAMPLE_CONTENT:no structure files");
        }
        Map<String, FileModel> files = loadFiles(paths);
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

    private static Map<String, Object> parseTree(String path) {
        try {
            return MatouParse.parseFile(path);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "E_EXAMPLE_CONTENT:unreadable <" + path + "> ("
                            + e.getMessage() + ")");
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, FileModel> loadFiles(List<String> paths) {
        Map<String, FileModel> files =
                new LinkedHashMap<String, FileModel>();
        for (String path : paths) {
            if (path == null) {
                throw new NullPointerException(
                        "E_EXAMPLE_CONTENT:null structure path");
            }
            Map<String, Object> tree = parseTree(path);
            String namespace = String.valueOf(tree.get("namespace"));
            if (files.containsKey(namespace)) {
                throw new IllegalArgumentException(
                        "E_EXAMPLE_CONTENT:duplicate namespace <"
                                + namespace + "> (<"
                                + files.get(namespace).path + "> vs <"
                                + path + ">)");
            }
            Map<String, Map<String, Object>> byName =
                    new HashMap<String, Map<String, Object>>();
            Object instances = tree.get("instances");
            if (instances instanceof List) {
                for (Object o : (List<Object>) instances) {
                    if (!(o instanceof Map)) {
                        continue;
                    }
                    Map<String, Object> inst = (Map<String, Object>) o;
                    if (!"Structure".equals(inst.get("decl"))) {
                        continue;
                    }
                    Object fields = inst.get("fields");
                    if (fields instanceof Map) {
                        byName.put(String.valueOf(inst.get("name")),
                                (Map<String, Object>) fields);
                    }
                }
            }
            files.put(namespace,
                    new FileModel(path, namespace, byName));
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
            throw new IllegalArgumentException(
                    "E_EXAMPLE_PARTS:cycle <" + join(cycle, " -> ") + ">");
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
                throw new IllegalArgumentException(
                        "E_EXAMPLE_CONTENT:bad parts <" + refName + "> in <"
                                + target.path + ">");
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
        if (aliases.isEmpty()) {
            return palette;
        }
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

    private static String join(List<String> items, String sep) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                sb.append(sep);
            }
            sb.append(items.get(i));
        }
        return sb.toString();
    }

    private static String posOf(String cell) {
        if (cell == null) {
            throw new NullPointerException("E_EXAMPLE_MERGE:null cell");
        }
        int cut = cell.indexOf(':');
        if (cut < 0) {
            throw new IllegalArgumentException(
                    "E_EXAMPLE_MERGE:bad cell <" + cell + ">");
        }
        return cell.substring(0, cut);
    }

    private static int[] vecOf(int[] v, String what, MatouId id) {
        if (v == null) {
            throw new NullPointerException(
                    "E_EXAMPLE_STRUCT:null " + what + " <" + id + ">");
        }
        if (v.length != 3) {
            throw new IllegalArgumentException("E_EXAMPLE_SIZE:shape <"
                    + what + " len " + v.length + "> (want 3)");
        }
        return v.clone();
    }

    private static int[] sizeOf(int[] v, MatouId id) {
        int[] size = vecOf(v, "size", id);
        for (int i = 0; i < 3; i++) {
            if (size[i] <= 0) {
                throw new IllegalArgumentException("E_EXAMPLE_SIZE:range <"
                        + size[0] + "," + size[1] + "," + size[2]
                        + "> (want all > 0)");
            }
        }
        return size;
    }

    private static List<String> paletteOf(List<String> palette, MatouId id) {
        if (palette == null) {
            throw new NullPointerException(
                    "E_EXAMPLE_STRUCT:null palette <" + id + ">");
        }
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
        if (parts == null) {
            throw new NullPointerException(
                    "E_EXAMPLE_STRUCT:null parts <" + id + ">");
        }
        List<StructurePlaceJob> copy =
                new ArrayList<StructurePlaceJob>(parts);
        for (StructurePlaceJob part : copy) {
            if (part == null) {
                throw new NullPointerException(
                        "E_EXAMPLE_STRUCT:null part <" + id + ">");
            }
        }
        return Collections.unmodifiableList(copy);
    }

    private static int[] intsOf(Object raw, String field, String name,
            String path) {
        if (!(raw instanceof List)) {
            throw new IllegalArgumentException(
                    "E_EXAMPLE_CONTENT:bad " + field + " <" + name
                            + "> in <" + path + ">");
        }
        List<?> items = (List<?>) raw;
        if (items.size() != 3) {
            throw new IllegalArgumentException(
                    "E_EXAMPLE_CONTENT:bad " + field + " <" + name
                            + "> in <" + path + ">");
        }
        int[] out = new int[3];
        for (int i = 0; i < 3; i++) {
            Object e = items.get(i);
            if (!(e instanceof Number)) {
                throw new IllegalArgumentException(
                        "E_EXAMPLE_CONTENT:bad " + field + " <" + name
                                + "> in <" + path + ">");
            }
            long v = ((Number) e).longValue();
            if (v < Integer.MIN_VALUE || v > Integer.MAX_VALUE) {
                throw new IllegalArgumentException(
                        "E_EXAMPLE_CONTENT:bad " + field + " range <" + name
                                + "> in <" + path + ">");
            }
            out[i] = (int) v;
        }
        return out;
    }

    private static List<String> refsOf(Object raw, String field, String name,
            String path) {
        if (!(raw instanceof List)) {
            throw new IllegalArgumentException(
                    "E_EXAMPLE_CONTENT:bad " + field + " <" + name
                            + "> in <" + path + ">");
        }
        List<String> out = new ArrayList<String>();
        for (Object e : (List<?>) raw) {
            if (!(e instanceof String)) {
                throw new IllegalArgumentException(
                        "E_EXAMPLE_CONTENT:bad " + field + " <" + name
                                + "> in <" + path + ">");
            }
            out.add((String) e);
        }
        return out;
    }

    private static int numOf(Object raw, String field, String name,
            String path) {
        if (!(raw instanceof Number)) {
            throw new IllegalArgumentException(
                    "E_EXAMPLE_CONTENT:bad " + field + " <" + name
                            + "> in <" + path + ">");
        }
        long v = ((Number) raw).longValue();
        if (v <= 0 || v > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "E_EXAMPLE_CONTENT:bad " + field + " <" + name
                            + "> in <" + path + ">");
        }
        return (int) v;
    }
}
