package fr.iamacat.example1;

import fr.iamacat.spi.MatouJob;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * T4 pack-driven policy holder (hub
 * {@code decisions/SPI_STATE_VOCABULARY.md}, combat policy hub
 * {@code decisions/VIRTUAL_HITBOXES.md}): the sealed loot/spawn/combat
 * tables plus the twenty-three {@code PolicyPack} accessors, out of
 * {@link ExamplePack}. The owned file seals all three tables at wire time
 * ({@link #fromFile} — combat and spawn seal per mob, loot seals the
 * agreed table, the tables' own empty/diverged refusals propagate
 * untouched); structure and vein files never fund tables, so wired packs
 * carry the holder by reference. Config-independent packs (count
 * fixtures, never wired to a file) hold the unwired singleton
 * ({@link #unwired}) and refuse loudly instead of guessing numbers.
 * Pure, Java 8, zero deps beyond matou-spi.
 */
public final class ExamplePolicy {
    private final LootTable loot;
    private final SpawnTable spawn;
    private final CombatTable combat;

    private ExamplePolicy(LootTable loot, SpawnTable spawn,
            CombatTable combat) {
        this.loot = loot;
        this.spawn = spawn;
        this.combat = combat;
    }

    /** Holder with no sealed tables: every accessor refuses loudly. */
    public static ExamplePolicy unwired() {
        return new ExamplePolicy(null, null, null);
    }

    /**
     * Seals all three tables from the owned content file once (parse-once,
     * beside the tables — never on the tick path). Loud on unreadable /
     * zero mobs / divergent loot — never defaulted.
     */
    public static ExamplePolicy fromFile(String ownedPath) {
        return new ExamplePolicy(LootTable.fromFile(ownedPath),
                SpawnTable.fromFile(ownedPath),
                CombatTable.fromFile(ownedPath));
    }

    /**
     * T4 pack-driven policy: the owned file seals all three tables at wire
     * time, so the forge wire reads plain data plus fresh jobs off this
     * holder and drops its content imports. Config-independent packs
     * (count fixtures built by the int constructors, never wired to a
     * file) serve no policy: the accessors refuse loudly instead of
     * guessing numbers.
     */
    private void requirePolicy() {
        if (loot == null || spawn == null || combat == null) {
            throw new IllegalStateException("E_EXAMPLE_POLICY:unwired "
                    + "(pack never wired to an owned file — want fromFiles)");
        }
    }

    /** Sealed loot table, or the loud unwired refusal (never a guess). */
    private LootTable loot() {
        requirePolicy();
        return loot;
    }

    /** Sealed spawn table, or the loud unwired refusal (never a guess). */
    private SpawnTable spawn() {
        requirePolicy();
        return spawn;
    }

    /** Sealed combat table, or the loud unwired refusal (never a guess). */
    private CombatTable combat() {
        requirePolicy();
        return combat;
    }

    public Map<String, String> lootDrops() {
        return loot().drops();
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
        return loot().count();
    }

    public String spawnMob() {
        return spawn().mob();
    }

    public long spawnHp() {
        return spawn().hp();
    }

    public long spawnCap() {
        return spawn().cap();
    }

    public long spawnBudget() {
        return spawn().budget();
    }

    public long spawnYMin() {
        return spawn().yMin();
    }

    public long spawnYMax() {
        return spawn().yMax();
    }

    public Set<String> spawnMobs() {
        return spawn().mobs();
    }

    public long spawnHp(String mob) {
        return spawn().hp(mob);
    }

    public long spawnCap(String mob) {
        return spawn().cap(mob);
    }

    public long spawnBudget(String mob) {
        return spawn().budget(mob);
    }

    public long spawnYMin(String mob) {
        return spawn().yMin(mob);
    }

    public long spawnYMax(String mob) {
        return spawn().yMax(mob);
    }

    public Map<String, Float> combatWeakspots() {
        return combat().weakspots();
    }

    public double combatReach() {
        return combat().reach();
    }

    public Set<String> combatMobs() {
        return combat().mobs();
    }

    public Map<String, Float> combatWeakspots(String mob) {
        return combat().weakspots(mob);
    }

    public double combatReach(String mob) {
        return combat().reach(mob);
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
