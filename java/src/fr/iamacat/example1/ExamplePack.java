package fr.iamacat.example1;

import fr.iamacat.spi.ConfigurablePack;
import fr.iamacat.spi.LootStates;
import fr.iamacat.spi.MatouId;
import fr.iamacat.spi.MatouJob;
import fr.iamacat.spi.MatouParse;
import fr.iamacat.spi.PolicyPack;
import fr.iamacat.spi.SpawnStates;
import fr.iamacat.spi.StateVocabulary;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * B2 content pack: exposes the M2 jobs plus their counts as
 * bridge-digestible states. Counts come from content files parsed ONCE via
 * the SPI reference parser ({@link #fromFiles} / {@link #configure}) —
 * never read on the tick path, never hardcoded. Pure, Java 8, zero deps
 * beyond matou-spi.
 *
 * <p>Structure wiring is optional and additive: without
 * {@code structureFile} (nor {@code structureFiles}) the pack stays the
 * legacy 2-job pack (owned + additive), so existing operators and the
 * bridge template keep working byte for byte; with it the pack also seals
 * the root structure count and exposes its {@link StructurePlaceJob} as
 * third job (default root is the composite {@code hut}: own volume plus
 * the {@code well} part tree). The root may live in another file:
 * {@code structureFiles} lists extra content files (comma-separated) and
 * {@code structureRoot} names the qualified {@code namespace:name} root
 * (cross-file parts resolve through {@code fromFiles}, import strictness
 * parser-enforced, file-set completeness wiring-enforced). Palette aliases come from
 * {@code block.<ref>} operator args ({@code content-ref -> landable
 * block}, strict both ways, empty = identity): content decides
 * <i>where</i>, the operator decides <i>what</i>, as with the wire block
 * of plane cells. A vein (SYNTAX-V4, {@link VeinPlaceJob}) is optional
 * and additive: {@code veinFile} (plus optional {@code veinFiles} /
 * {@code veinRoot}, default {@code example1.veins:ore_vein}) wires it as
 * fourth job, its single block mapped through {@code veinblock.<ref>}
 * operator args (strict both ways, empty = identity) — a separate
 * prefix so structure palettes keep their own strictness. The decide path is fully wired (pure, shape-agnostic
 * merge); landing {@code x,y,z:block} cells needs a 3D-capable sink
 * (bridge {@code WorldCellSink}). Jobs live in one registry:
 * {@link #jobs()} in owned-first order, {@link #job} by id — adding a job
 * extends the registry, never the factory overloads.
 */
public final class ExamplePack implements ConfigurablePack,
        PolicyPack {
    public static final String NAMESPACE = ExampleIds.NAMESPACE;
    static final String OWNED_KEY = "ownedFile";
    static final String SCATTER_KEY = "scatterFile";
    static final String STRUCTURE_KEY = "structureFile";
    static final String STRUCTURE_FILES_KEY = "structureFiles";
    static final String STRUCTURE_ROOT_KEY = "structureRoot";
    static final String BLOCK_ALIAS_PREFIX = "block.";
    static final String VEIN_KEY = "veinFile";
    static final String VEIN_FILES_KEY = "veinFiles";
    static final String VEIN_ROOT_KEY = "veinRoot";
    static final String VEIN_BLOCK_PREFIX = "veinblock.";

    private int ownedCount;
    private int scatterCount;
    private List<StructurePlaceJob> structures;
    private VeinPlaceJob vein;
    private LootTable loot;
    private SpawnTable spawn;
    private boolean configured;

    /** No-arg for the reflective loader; {@link #configure} must follow. */
    public ExamplePack() {
        this.structures = Collections.<StructurePlaceJob>emptyList();
        this.vein = null;
        this.loot = null;
        this.spawn = null;
        this.configured = false;
    }

    public ExamplePack(int ownedCount, int scatterCount) {
        this(ownedCount, scatterCount,
                Collections.<StructurePlaceJob>emptyList());
    }

    /** Wired pack: {@code structure} is shared (immutable, pure). */
    public ExamplePack(int ownedCount, int scatterCount,
            StructurePlaceJob structure) {
        this(ownedCount, scatterCount, singleton(structure));
    }

    private static List<StructurePlaceJob> singleton(
            StructurePlaceJob structure) {
        if (structure == null) {
            throw new NullPointerException("E_EXAMPLE_STRUCT:null job");
        }
        return Collections.singletonList(structure);
    }

    /**
     * Wired pack over several structures (each wired separately, e.g. via
     * {@link StructurePlaceJob#fromFiles}, so per-tree alias coverage stays
     * strict). Duplicate structure ids are refused.
     */
    public ExamplePack(int ownedCount, int scatterCount,
            List<StructurePlaceJob> structures) {
        this.ownedCount =
                OwnedVeinJob.countOf(Integer.valueOf(ownedCount));
        this.scatterCount =
                OwnedVeinJob.countOf(Integer.valueOf(scatterCount));
        this.structures = checked(structures);
        this.vein = null;
        this.loot = null;
        this.spawn = null;
        this.configured = true;
    }

    /**
     * Wired pack with a vein (fourth job, decided after structures).
     * No null-means-absent: callers wanting no vein use the 3-arg
     * overload instead of passing null here.
     */
    public ExamplePack(int ownedCount, int scatterCount,
            List<StructurePlaceJob> structures, VeinPlaceJob vein) {
        this(ownedCount, scatterCount, structures);
        if (vein == null) {
            throw new NullPointerException("E_EXAMPLE_VEIN:null job");
        }
        this.vein = vein;
    }

    private static List<StructurePlaceJob> checked(
            List<StructurePlaceJob> structures) {
        if (structures == null) {
            throw new NullPointerException("E_EXAMPLE_STRUCT:null jobs");
        }
        List<StructurePlaceJob> copy =
                new ArrayList<StructurePlaceJob>(structures.size());
        Set<MatouId> seen = new HashSet<MatouId>();
        for (StructurePlaceJob job : structures) {
            if (job == null) {
                throw new NullPointerException("E_EXAMPLE_STRUCT:null job");
            }
            if (!seen.add(job.id())) {
                throw new IllegalArgumentException(
                        "E_EXAMPLE_STRUCT:dup <" + job.id() + ">");
            }
            copy.add(job);
        }
        return Collections.unmodifiableList(copy);
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
        String extraPaths = args.get(STRUCTURE_FILES_KEY);
        String structureRoot = args.get(STRUCTURE_ROOT_KEY);
        if (structurePath == null && extraPaths == null) {
            if (structureRoot != null) {
                throw new IllegalArgumentException(
                        "E_EXAMPLE_CONTENT:structureRoot without"
                                + " structure file(s)");
            }
            ready = fromFiles(owned, scatter);
        } else {
            List<String> structPaths = new ArrayList<String>();
            if (structurePath != null) {
                structPaths.add(structurePath);
            }
            structPaths.addAll(splitPaths(extraPaths, STRUCTURE_FILES_KEY));
            String root = structureRoot != null ? structureRoot
                    : StructurePlaceJob.HUT.toString();
            ready = fromFiles(owned, scatter, structPaths, root,
                    prefixedArgs(args, BLOCK_ALIAS_PREFIX));
        }
        String veinPath = args.get(VEIN_KEY);
        String veinExtra = args.get(VEIN_FILES_KEY);
        String veinRoot = args.get(VEIN_ROOT_KEY);
        if (veinPath == null && veinExtra == null) {
            if (veinRoot != null) {
                throw new IllegalArgumentException(
                        "E_EXAMPLE_CONTENT:veinRoot without vein file(s)");
            }
        } else {
            List<String> veinPaths = new ArrayList<String>();
            if (veinPath != null) {
                veinPaths.add(veinPath);
            }
            veinPaths.addAll(splitPaths(veinExtra, VEIN_FILES_KEY));
            String root = veinRoot != null ? veinRoot
                    : VeinPlaceJob.ORE.toString();
            ready = fromFiles(ready, VeinPlaceJob.fromFiles(veinPaths,
                    root, prefixedArgs(args, VEIN_BLOCK_PREFIX)));
        }
        this.ownedCount = ready.ownedCount;
        this.scatterCount = ready.scatterCount;
        this.structures = ready.structures;
        this.vein = ready.vein;
        this.loot = ready.loot;
        this.spawn = ready.spawn;
        this.configured = true;
    }

    /**
      * Wires both content files through the SPI reference parser at wire
      * time (counts plus the T4 loot/spawn policy from the owned file —
      * never on the tick path). Loud on unreadable / unparsable / missing
      * feature — never defaulted. Legacy 2-job pack: no structure wired.
      * The owned file also seals the T4 loot/spawn policy (single mob
      * funds both tables — the tables' own multi/empty refusals propagate
      * untouched).
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
        ExamplePack pack = new ExamplePack(
                featureCount(parse(ownedPath), "my_vein", ownedPath),
                featureCount(parse(scatterPath), "scatter_additive",
                        scatterPath));
        pack.loot = LootTable.fromFile(ownedPath);
        pack.spawn = SpawnTable.fromFile(ownedPath);
        return pack;
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
        return fromFiles(ownedPath, scatterPath,
                Collections.singletonList(structurePath),
                StructurePlaceJob.HUT.toString(), aliases);
    }

    /**
     * Parses owned/scatter plus a set of structure files once and wires
     * the qualified {@code namespace:name} root with palette aliases
     * (strict both ways over the whole cross-file tree). Loud on
     * unreadable / unknown namespace / missing structure / unmapped
     * palette / unknown alias — never defaulted.
     */
    public static ExamplePack fromFiles(String ownedPath,
            String scatterPath, List<String> structurePaths,
            String structureRoot, Map<String, String> aliases) {
        if (structurePaths == null) {
            throw new NullPointerException(
                    "E_EXAMPLE_CONTENT:null structure files");
        }
        if (structureRoot == null) {
            throw new NullPointerException(
                    "E_EXAMPLE_CONTENT:null structure root");
        }
        if (aliases == null) {
            throw new NullPointerException(
                    "E_EXAMPLE_CONTENT:null aliases");
        }
        ExamplePack legacy = fromFiles(ownedPath, scatterPath);
        return new ExamplePack(legacy.ownedCount, legacy.scatterCount,
                Collections.singletonList(StructurePlaceJob.fromFiles(
                        structurePaths, structureRoot, aliases)));
    }

    /**
     * Parses owned/scatter once and registers already-wired structures
     * (each wired separately, e.g. via {@link StructurePlaceJob#fromFiles},
     * so per-tree alias coverage stays strict). This is the growth path:
     * a further job joins the registry here, no new overload needed.
     */
    public static ExamplePack fromFiles(String ownedPath,
            String scatterPath, List<StructurePlaceJob> structures) {
        if (structures == null) {
            throw new NullPointerException(
                    "E_EXAMPLE_CONTENT:null structures");
        }
        ExamplePack legacy = fromFiles(ownedPath, scatterPath);
        return new ExamplePack(legacy.ownedCount, legacy.scatterCount,
                structures);
    }

    /**
      * Adds an already-wired vein to a wired pack (wired separately via
      * {@link VeinPlaceJob#fromFiles}, so its alias coverage stays
      * strict). The vein decides after every other job. The T4 policy
      * rides along (vein files never fund tables).
      */
    public static ExamplePack fromFiles(ExamplePack pack,
            VeinPlaceJob vein) {
        if (pack == null) {
            throw new NullPointerException("E_EXAMPLE_PACK:null pack");
        }
        if (vein == null) {
            throw new NullPointerException("E_EXAMPLE_VEIN:null job");
        }
        ExamplePack out = new ExamplePack(pack.ownedCount,
                pack.scatterCount, pack.structures, vein);
        out.loot = pack.loot;
        out.spawn = pack.spawn;
        return out;
    }

    /** Comma-separated path list; blank entries fail loudly, never skipped. */
    static List<String> splitPaths(String raw, String key) {
        List<String> out = new ArrayList<String>();
        if (raw == null) {
            return out;
        }
        for (String token : raw.split(",", -1)) {
            String path = token.trim();
            if (path.isEmpty()) {
                throw new IllegalArgumentException(
                        "E_EXAMPLE_CONTENT:bad " + key + " <" + raw
                                + "> (blank entry)");
            }
            out.add(path);
        }
        return out;
    }

    /**
     * Operator {@code <prefix><ref>} args, prefix stripped, order kept.
     * One derivation point for both alias families ({@code block.} for
     * structure palettes, {@code veinblock.} for the vein block) — a
     * second prefix is one call, never a copied loop.
     */
    static Map<String, String> prefixedArgs(Map<String, String> args,
            String prefix) {
        Map<String, String> aliases = new LinkedHashMap<String, String>();
        for (Map.Entry<String, String> e : args.entrySet()) {
            if (e.getKey() != null && e.getKey().startsWith(prefix)) {
                aliases.put(e.getKey().substring(prefix.length()),
                        e.getValue());
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
        for (StructurePlaceJob job : structures) {
            states.put(job.id(), Long.valueOf(job.contentCount()));
        }
        if (vein != null) {
            states.put(vein.id(), Long.valueOf(vein.contentCount()));
            states.put(vein.sizeId(), sizeState(vein.size()));
        }
        return Collections.unmodifiableMap(states);
    }

    /** Seals wired extents as the snapshot size vector (Long trio). */
    private static List<Long> sizeState(int[] size) {
        List<Long> out = new ArrayList<Long>(size.length);
        for (int c : size) {
            out.add(Long.valueOf(c));
        }
        return out;
    }

    public List<MatouJob<List<String>>> jobs() {
        List<MatouJob<List<String>>> jobs =
                new ArrayList<MatouJob<List<String>>>();
        jobs.add(new OwnedVeinJob());
        jobs.add(new AdditiveScatterJob());
        jobs.addAll(structures);
        if (vein != null) {
            jobs.add(vein);
        }
        return Collections.unmodifiableList(jobs);
    }

    /**
     * Registry lookup by id: owned and additive jobs resolve to fresh
     * equivalents (they are stateless), structures and the vein to the
     * wired instance. Unknown ids are refused loudly — callers never index
     * {@link #jobs()} positionally.
     */
    public MatouJob<List<String>> job(MatouId id) {
        if (id == null) {
            throw new NullPointerException("E_EXAMPLE_JOB:null id");
        }
        if (id.equals(OwnedVeinJob.VEIN)) {
            return new OwnedVeinJob();
        }
        if (id.equals(AdditiveScatterJob.SCATTER)) {
            return new AdditiveScatterJob();
        }
        for (StructurePlaceJob job : structures) {
            if (id.equals(job.id())) {
                return job;
            }
        }
        if (vein != null && id.equals(vein.id())) {
            return vein;
        }
        throw new IllegalArgumentException(
                "E_EXAMPLE_JOB:unknown <" + id + ">");
    }

    /** True once a vein file was wired (legacy packs stay vein-free). */
    public boolean hasVein() {
        return vein != null;
    }

    /**
     * T3 vocabulary provision (hub
     * {@code decisions/SPI_STATE_VOCABULARY.md}): serves the sealed-spawn
     * and sealed-loot vocabularies the bridge seals resolve at wire time
     * (parse-once, beside the tables — never on the tick path), so job
     * and seal share ids with no bridge-to-content compile edge.
     * Config-independent: vocabularies name states, never content.
     * Unknown or null scopes are refused loudly, never defaulted.
     */
    public StateVocabulary vocabulary(String scope) {
        if (scope == null) {
            throw new NullPointerException("E_EXAMPLE_VOCAB:null scope");
        }
        if (SpawnStates.SCOPE.equals(scope)) {
            return SpawnStates.vocabulary(ExampleIds.SPAWN_NS);
        }
        if (LootStates.SCOPE.equals(scope)) {
            return LootStates.vocabulary(ExampleIds.LOOT_NS);
        }
        throw new IllegalArgumentException("E_EXAMPLE_VOCAB:unknown <"
                + scope + "> (want spawn/loot)");
    }

    /**
      * T4 pack-driven policy (hub
      * {@code decisions/SPI_STATE_VOCABULARY.md}): the owned file seals
      * both tables at wire time ({@link #fromFiles}), so the forge wire
      * reads plain data plus fresh jobs off this interface and drops its
      * content imports. Config-independent packs (count fixtures built by
      * the int constructors, never wired to a file) serve no policy: the
      * accessors refuse loudly instead of guessing numbers.
      */
    private void requirePolicy() {
        if (loot == null || spawn == null) {
            throw new IllegalStateException("E_EXAMPLE_POLICY:unwired "
                    + "(pack never wired to an owned file — want fromFiles)");
        }
    }

    public Map<String, String> lootDrops() {
        requirePolicy();
        return loot.drops();
    }

    public String lootOreKind() {
        requirePolicy();
        return LootJob.ORE;
    }

    public String lootBeastKind() {
        requirePolicy();
        return LootJob.BEAST;
    }

    public long lootCount() {
        requirePolicy();
        return loot.count();
    }

    public String spawnMob() {
        requirePolicy();
        return spawn.mob();
    }

    public long spawnHp() {
        requirePolicy();
        return spawn.hp();
    }

    public long spawnCap() {
        requirePolicy();
        return spawn.cap();
    }

    public long spawnBudget() {
        requirePolicy();
        return spawn.budget();
    }

    public long spawnYMin() {
        requirePolicy();
        return spawn.yMin();
    }

    public long spawnYMax() {
        requirePolicy();
        return spawn.yMax();
    }

    public MatouJob<List<String>> lootJob() {
        requirePolicy();
        return new LootJob();
    }

    public MatouJob<List<String>> spawnJob() {
        requirePolicy();
        return new SpawnJob();
    }
}
