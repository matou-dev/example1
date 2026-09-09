package fr.iamacat.example1;

import fr.iamacat.spi.MatouId;
import fr.iamacat.spi.MatouJob;
import fr.iamacat.spi.MatouParse;
import fr.iamacat.spi.MatouRng;
import fr.iamacat.spi.Snapshot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * V3 structure job: decides the cells of one leaf structure volume from its
 * {@code anchor}/{@code size}/{@code palette} (spec
 * {@code SYNTAX-V3.md}). Pure (no IO, no Minecraft, no clock): same snapshot
 * in, equal decision out. Java 8, zero deps beyond matou-spi.
 *
 * <p>Split parser/job (same as {@code feature.count} refused by
 * {@link OwnedVeinJob}): the parser accepts any integers and any uniform
 * list, the job refuses non-positive extents ({@code E_EXAMPLE_SIZE}), an
 * empty palette ({@code E_EXAMPLE_PALETTE}) and non-leaf parts
 * ({@code E_EXAMPLE_PARTS}, recursive placement is not decided yet) loudly,
 * never defaulted.
 *
 * <p>Cells are {@code "x,y,z:ns:block"} strings. Per tick the job places
 * {@code count} occurrences (count read from the snapshot, as with
 * {@link OwnedVeinJob}); each occurrence offsets the volume in a 16x16 plane
 * and picks each cell block from the palette through the addressed RNG, so
 * the decision is deterministic per tick.
 */
public final class StructurePlaceJob implements MatouJob<List<String>> {
    public static final MatouId WELL =
            MatouId.parse("example1.structures:well");
    static final int CELLS = 16;

    private final MatouId id;
    private final int[] anchor;
    private final int[] size;
    private final List<String> palette;
    private final int contentCount;

    public StructurePlaceJob(MatouId id, int[] anchor, int[] size,
            List<String> palette, List<String> parts, int count) {
        if (id == null) {
            throw new NullPointerException("E_EXAMPLE_STRUCT:null id");
        }
        this.anchor = vecOf(anchor, "anchor", id);
        this.size = sizeOf(size, id);
        this.palette = paletteOf(palette, id);
        if (parts == null) {
            throw new NullPointerException(
                    "E_EXAMPLE_STRUCT:null parts <" + id + ">");
        }
        if (!parts.isEmpty()) {
            throw new IllegalArgumentException("E_EXAMPLE_PARTS:non-leaf <"
                    + id + "> (recursive placement not yet decided)");
        }
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

    /** Count parsed from the content file (tick counts come from snapshots). */
    public int contentCount() {
        return contentCount;
    }

    public List<String> decide(Snapshot snap) {
        if (snap == null) {
            throw new NullPointerException("E_EXAMPLE_SNAPSHOT:null");
        }
        int count = OwnedVeinJob.countOf(snap.get(id), id);
        MatouRng rng = MatouRng.forAddress(id.namespace, id.name,
                Long.toString(snap.tick()));
        int volume = size[0] * size[1] * size[2];
        List<String> out = new ArrayList<String>(count * volume);
        for (int o = 0; o < count; o++) {
            int ox = rng.nextInt(CELLS);
            int oz = rng.nextInt(CELLS);
            for (int dx = 0; dx < size[0]; dx++) {
                for (int dy = 0; dy < size[1]; dy++) {
                    for (int dz = 0; dz < size[2]; dz++) {
                        String block = palette.get(
                                rng.nextInt(palette.size()));
                        out.add((anchor[0] + ox + dx) + ","
                                + (anchor[1] + dy) + ","
                                + (anchor[2] + oz + dz) + ":" + block);
                    }
                }
            }
        }
        return Collections.unmodifiableList(out);
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
     * Wires one leaf structure from a content file through the SPI reference
     * parser, once (never on the tick path). Loud on unreadable / unparsable
     * / missing structure / malformed fields — never defaulted. Non-leaf
     * structures are refused ({@code E_EXAMPLE_PARTS}) at wiring time.
     */
    @SuppressWarnings("unchecked")
    public static StructurePlaceJob fromFile(String path, String name) {
        if (path == null) {
            throw new NullPointerException(
                    "E_EXAMPLE_CONTENT:null structure path");
        }
        if (name == null) {
            throw new NullPointerException(
                    "E_EXAMPLE_CONTENT:null structure name");
        }
        Map<String, Object> tree;
        try {
            tree = MatouParse.parseFile(path);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "E_EXAMPLE_CONTENT:unreadable <" + path + "> ("
                            + e.getMessage() + ")");
        }
        Object instances = tree.get("instances");
        String namespace = String.valueOf(tree.get("namespace"));
        if (instances instanceof List) {
            for (Object o : (List<Object>) instances) {
                if (!(o instanceof Map)) {
                    continue;
                }
                Map<String, Object> inst = (Map<String, Object>) o;
                if (!"Structure".equals(inst.get("decl"))
                        || !name.equals(inst.get("name"))) {
                    continue;
                }
                Object fields = inst.get("fields");
                if (!(fields instanceof Map)) {
                    throw new IllegalArgumentException(
                            "E_EXAMPLE_CONTENT:bad fields <" + name
                                    + "> in <" + path + ">");
                }
                Map<String, Object> f = (Map<String, Object>) fields;
                return new StructurePlaceJob(
                        MatouId.of(namespace, name),
                        intsOf(f.get("anchor"), "anchor", name, path),
                        intsOf(f.get("size"), "size", name, path),
                        refsOf(f.get("palette"), "palette", name, path),
                        refsOf(f.get("parts"), "parts", name, path),
                        numOf(f.get("count"), "count", name, path));
            }
        }
        throw new IllegalArgumentException(
                "E_EXAMPLE_CONTENT:missing structure <" + name + "> in <"
                        + path + ">");
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
