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
 * B2 content pack: exposes the M2/B4/V4 jobs plus counts as bridge-digestible
 * states. Counts come from content files parsed ONCE via the SPI reference parser
 * ({@link #fromFiles} / {@link #configure}) — never on the tick path. Pure,
 * Java 8, zero deps beyond matou-spi.
 *
 * <p>Structure wiring (B4/B6) and vein wiring (V4) are optional and additive:
 * {@code structureFile} / {@code structureFiles} wire structures; {@code veinFile}
 * wires the registered ore vein. Jobs live in one registry ({@link #jobs()}
 * in order, {@link #job} by id). Policy is delegated to {@link ExamplePolicy}.
 */
public final class ExamplePack implements ConfigurablePack, PolicyPack {
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
    private ExamplePolicy policy;
    private boolean configured;

    /** No-arg for reflective loading; {@link #configure} must follow. */
    public ExamplePack() {
        this.structures = Collections.<StructurePlaceJob>emptyList();
        this.vein = null;
        this.policy = ExamplePolicy.unwired();
        this.configured = false;
    }

    public ExamplePack(int ownedCount, int scatterCount) {
        this(ownedCount, scatterCount, Collections.<StructurePlaceJob>emptyList());
    }

    public ExamplePack(int ownedCount, int scatterCount, StructurePlaceJob structure) {
        this(ownedCount, scatterCount, Collections.singletonList(structure));
    }

    public ExamplePack(int ownedCount, int scatterCount, List<StructurePlaceJob> structures) {
        this(ownedCount, scatterCount, structures, null, true);
    }

    public ExamplePack(int ownedCount, int scatterCount, List<StructurePlaceJob> structures,
            VeinPlaceJob vein) {
        this(ownedCount, scatterCount, structures, vein, false);
    }

    private ExamplePack(int ownedCount, int scatterCount, List<StructurePlaceJob> structures,
            VeinPlaceJob vein, boolean allowNoVein) {
        this.ownedCount = OwnedVeinJob.countOf(Integer.valueOf(ownedCount));
        this.scatterCount = OwnedVeinJob.countOf(Integer.valueOf(scatterCount));
        this.structures = checked(structures);
        if (!allowNoVein && vein == null) {
            throw new NullPointerException("E_EXAMPLE_VEIN:null job");
        }
        this.vein = vein;
        this.policy = ExamplePolicy.unwired();
        this.configured = true;
    }

    private static List<StructurePlaceJob> checked(List<StructurePlaceJob> structures) {
        if (structures == null) {
            throw new NullPointerException("E_EXAMPLE_STRUCT:null jobs");
        }
        List<StructurePlaceJob> copy = new ArrayList<StructurePlaceJob>(structures.size());
        Set<MatouId> seen = new HashSet<MatouId>();
        for (StructurePlaceJob job : structures) {
            if (job == null) {
                throw new NullPointerException("E_EXAMPLE_STRUCT:null job");
            }
            if (!seen.add(job.id())) {
                throw new IllegalArgumentException("E_EXAMPLE_STRUCT:dup <" + job.id() + ">");
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
            throw new IllegalArgumentException("E_EXAMPLE_CONTENT:missing key <ownedFile>");
        }
        String scatter = args.get(SCATTER_KEY);
        if (scatter == null) {
            throw new IllegalArgumentException("E_EXAMPLE_CONTENT:missing key <scatterFile>");
        }
        ExamplePack ready;
        List<String> structPaths = wiredPaths(args, STRUCTURE_KEY,
                STRUCTURE_FILES_KEY, STRUCTURE_ROOT_KEY, "structure");
        if (structPaths == null) {
            ready = fromFiles(owned, scatter);
        } else {
            String root = args.get(STRUCTURE_ROOT_KEY);
            ready = fromFiles(owned, scatter, structPaths,
                    root != null ? root : StructurePlaceJob.HUT.toString(),
                    prefixedArgs(args, BLOCK_ALIAS_PREFIX));
        }
        List<String> veinPaths = wiredPaths(args, VEIN_KEY,
                VEIN_FILES_KEY, VEIN_ROOT_KEY, "vein");
        if (veinPaths != null) {
            String root = args.get(VEIN_ROOT_KEY);
            ready = fromFiles(ready, VeinPlaceJob.fromFiles(veinPaths,
                    root != null ? root : VeinPlaceJob.ORE.toString(),
                    prefixedArgs(args, VEIN_BLOCK_PREFIX)));
        }
        this.ownedCount = ready.ownedCount;
        this.scatterCount = ready.scatterCount;
        this.structures = ready.structures;
        this.vein = ready.vein;
        this.policy = ready.policy;
        this.configured = true;
    }

    private static List<String> wiredPaths(Map<String, String> args,
            String single, String plural, String root, String kind) {
        String one = args.get(single);
        String extra = args.get(plural);
        if (one == null && extra == null) {
            if (args.get(root) != null) {
                throw new IllegalArgumentException("E_EXAMPLE_CONTENT:"
                        + kind + "Root without " + kind + " file(s)");
            }
            return null;
        }
        List<String> paths = new ArrayList<String>();
        if (one != null) paths.add(one);
        paths.addAll(splitPaths(extra, plural));
        return paths;
    }

    public static ExamplePack fromFiles(String ownedPath, String scatterPath) {
        if (ownedPath == null) throw new NullPointerException("E_EXAMPLE_CONTENT:null owned path");
        if (scatterPath == null) throw new NullPointerException("E_EXAMPLE_CONTENT:null scatter path");
        ExamplePack pack = new ExamplePack(
                featureCount(parse(ownedPath), "my_vein", ownedPath),
                featureCount(parse(scatterPath), "scatter_additive", scatterPath));
        pack.policy = ExamplePolicy.fromFile(ownedPath);
        return pack;
    }

    public static ExamplePack fromFiles(String ownedPath, String scatterPath, String structurePath) {
        return fromFiles(ownedPath, scatterPath, structurePath, Collections.<String, String>emptyMap());
    }

    public static ExamplePack fromFiles(String ownedPath, String scatterPath,
            String structurePath, Map<String, String> aliases) {
        if (structurePath == null) throw new NullPointerException("E_EXAMPLE_CONTENT:null structure path");
        if (aliases == null) throw new NullPointerException("E_EXAMPLE_CONTENT:null aliases");
        return fromFiles(ownedPath, scatterPath, Collections.singletonList(structurePath),
                StructurePlaceJob.HUT.toString(), aliases);
    }

    public static ExamplePack fromFiles(String ownedPath, String scatterPath,
            List<String> structurePaths, String structureRoot, Map<String, String> aliases) {
        if (structurePaths == null) throw new NullPointerException("E_EXAMPLE_CONTENT:null structure files");
        if (structureRoot == null) throw new NullPointerException("E_EXAMPLE_CONTENT:null structure root");
        if (aliases == null) throw new NullPointerException("E_EXAMPLE_CONTENT:null aliases");
        return withStructures(fromFiles(ownedPath, scatterPath), Collections.singletonList(
                StructurePlaceJob.fromFiles(structurePaths, structureRoot, aliases)));
    }

    public static ExamplePack fromFiles(String ownedPath, String scatterPath,
            List<StructurePlaceJob> structures) {
        if (structures == null) throw new NullPointerException("E_EXAMPLE_CONTENT:null structures");
        return withStructures(fromFiles(ownedPath, scatterPath), structures);
    }

    private static ExamplePack withStructures(ExamplePack legacy, List<StructurePlaceJob> structures) {
        ExamplePack pack = new ExamplePack(legacy.ownedCount, legacy.scatterCount, structures);
        pack.policy = legacy.policy;
        return pack;
    }

    public static ExamplePack fromFiles(ExamplePack pack, VeinPlaceJob vein) {
        if (pack == null) throw new NullPointerException("E_EXAMPLE_PACK:null pack");
        if (vein == null) throw new NullPointerException("E_EXAMPLE_VEIN:null job");
        ExamplePack out = new ExamplePack(pack.ownedCount, pack.scatterCount, pack.structures, vein);
        out.policy = pack.policy;
        return out;
    }

    static List<String> splitPaths(String raw, String key) {
        List<String> out = new ArrayList<String>();
        if (raw == null) return out;
        for (String token : raw.split(",", -1)) {
            String path = token.trim();
            if (path.isEmpty()) {
                throw new IllegalArgumentException(
                        "E_EXAMPLE_CONTENT:bad " + key + " <" + raw + "> (blank entry)");
            }
            out.add(path);
        }
        return out;
    }

    static Map<String, String> prefixedArgs(Map<String, String> args, String prefix) {
        Map<String, String> aliases = new LinkedHashMap<String, String>();
        for (Map.Entry<String, String> e : args.entrySet()) {
            if (e.getKey() != null && e.getKey().startsWith(prefix)) {
                aliases.put(e.getKey().substring(prefix.length()), e.getValue());
            }
        }
        return aliases;
    }

    private static Map<String, Object> parse(String path) {
        try {
            return MatouParse.parseFile(path);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "E_EXAMPLE_CONTENT:unreadable <" + path + "> (" + e.getMessage() + ")");
        }
    }

    @SuppressWarnings("unchecked")
    private static int featureCount(Map<String, Object> tree, String feature, String path) {
        Object instances = tree.get("instances");
        if (instances instanceof List) {
            for (Object o : (List<Object>) instances) {
                if (!(o instanceof Map)) continue;
                Map<String, Object> inst = (Map<String, Object>) o;
                if ("Feature".equals(inst.get("decl")) && feature.equals(inst.get("name"))) {
                    Object fields = inst.get("fields");
                    if (fields instanceof Map) {
                        Object count = ((Map<String, Object>) fields).get("count");
                        if (count instanceof Number) return OwnedVeinJob.countOf(count);
                    }
                    throw new IllegalArgumentException(
                            "E_EXAMPLE_CONTENT:bad count <" + feature + "> in <" + path + ">");
                }
            }
        }
        throw new IllegalArgumentException(
                "E_EXAMPLE_CONTENT:missing feature <" + feature + "> in <" + path + ">");
    }

    public String namespace() { return NAMESPACE; }

    public Map<MatouId, Object> states(long tick) {
        if (!configured) throw new IllegalStateException("E_EXAMPLE_PACK:unconfigured");
        if (tick < 0) {
            throw new IllegalArgumentException("E_EXAMPLE_TICK:range <" + tick + "> (want >= 0)");
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

    private static List<Long> sizeState(int[] size) {
        List<Long> out = new ArrayList<Long>(size.length);
        for (int c : size) out.add(Long.valueOf(c));
        return out;
    }

    public List<MatouJob<List<String>>> jobs() {
        List<MatouJob<List<String>>> jobs = new ArrayList<MatouJob<List<String>>>();
        jobs.add(new OwnedVeinJob());
        jobs.add(new AdditiveScatterJob());
        jobs.addAll(structures);
        if (vein != null) jobs.add(vein);
        return Collections.unmodifiableList(jobs);
    }

    public MatouJob<List<String>> job(MatouId id) {
        if (id == null) throw new NullPointerException("E_EXAMPLE_JOB:null id");
        for (MatouJob<List<String>> job : jobs()) {
            if (idOf(job).equals(id)) return job;
        }
        throw new IllegalArgumentException("E_EXAMPLE_JOB:unknown <" + id + ">");
    }

    private static MatouId idOf(MatouJob<List<String>> job) {
        if (job instanceof StructurePlaceJob) return ((StructurePlaceJob) job).id();
        if (job instanceof VeinPlaceJob) return ((VeinPlaceJob) job).id();
        if (job instanceof AdditiveScatterJob) return AdditiveScatterJob.SCATTER;
        if (job instanceof OwnedVeinJob) return OwnedVeinJob.VEIN;
        throw new IllegalStateException("E_EXAMPLE_JOB:unknown <"
                + job.getClass().getName() + "> (pack jobs only)");
    }

    public boolean hasVein() { return vein != null; }

    public StateVocabulary vocabulary(String scope) {
        if (scope == null) throw new NullPointerException("E_EXAMPLE_VOCAB:null scope");
        if (SpawnStates.SCOPE.equals(scope)) return SpawnStates.vocabulary(ExampleIds.SPAWN_NS);
        if (LootStates.SCOPE.equals(scope)) return LootStates.vocabulary(ExampleIds.LOOT_NS);
        throw new IllegalArgumentException("E_EXAMPLE_VOCAB:unknown <" + scope + "> (want spawn/loot)");
    }

    public Map<String, String> lootDrops() { return policy.lootDrops(); }
    public String lootOreKind() { return policy.lootOreKind(); }
    public String lootBeastKind() { return policy.lootBeastKind(); }
    public long lootCount() { return policy.lootCount(); }
    public String spawnMob() { return policy.spawnMob(); }
    public long spawnHp() { return policy.spawnHp(); }
    public long spawnCap() { return policy.spawnCap(); }
    public long spawnBudget() { return policy.spawnBudget(); }
    public long spawnYMin() { return policy.spawnYMin(); }
    public long spawnYMax() { return policy.spawnYMax(); }
    public Map<String, Float> combatWeakspots() {
        return policy.combatWeakspots();
    }
    public double combatReach() { return policy.combatReach(); }
    public Set<String> combatMobs() { return policy.combatMobs(); }
    public Map<String, Float> combatWeakspots(String mob) {
        return policy.combatWeakspots(mob);
    }
    public double combatReach(String mob) {
        return policy.combatReach(mob);
    }
    public MatouJob<List<String>> lootJob() { return policy.lootJob(); }
    public MatouJob<List<String>> spawnJob() { return policy.spawnJob(); }
}
