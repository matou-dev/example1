package fr.iamacat.example1;

import fr.iamacat.spi.MatouId;
import fr.iamacat.spi.Snapshot;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * M2 content self-test (no JUnit on this gate): every violation prints
 * {@code FAIL example1 : ...} and exits 1. Refusal batteries that only
 * differ by input go in {@link Refusal} tables with one runner per refusal
 * kind, so a new refusal is one row — never a new anonymous class
 * (lambdas are legal: the gate compiles {@code --release 8}). Run by
 * tools/check.sh against the {@code ../spi} sibling checkout.
 */
public final class ExampleCheck {
    private ExampleCheck() {}

    private static void check(boolean cond, String what) {
        if (!cond) {
            System.out.println("FAIL example1 : " + what);
            System.exit(1);
        }
        System.out.println("ok example1 : " + what);
    }

    private static void expectRefused(Runnable r, String what) {
        try {
            r.run();
        } catch (IllegalArgumentException e) {
            System.out.println("ok example1 : refused " + what
                    + " (" + e.getMessage() + ")");
            return;
        }
        System.out.println("FAIL example1 : accepted " + what);
        System.exit(1);
    }

    private static void expectNullRefused(Runnable r, String what) {
        try {
            r.run();
        } catch (NullPointerException e) {
            System.out.println("ok example1 : refused " + what
                    + " (" + e.getMessage() + ")");
            return;
        }
        System.out.println("FAIL example1 : accepted " + what);
        System.exit(1);
    }

    /** One refusal case: runnable plus the name its refusal prints. */
    private static final class Refusal {
        final Runnable run;
        final String what;
        Refusal(Runnable run, String what) {
            this.run = run;
            this.what = what;
        }
    }

    private static void checkRefused(Refusal[] cases) {
        for (Refusal c : cases) {
            expectRefused(c.run, c.what);
        }
    }

    private static void checkNullRefused(Refusal[] cases) {
        for (Refusal c : cases) {
            expectNullRefused(c.run, c.what);
        }
    }

    private static Snapshot snap(long tick) {
        Map<MatouId, Object> states = new HashMap<MatouId, Object>();
        states.put(OwnedVeinJob.VEIN, Long.valueOf(8L));
        states.put(AdditiveScatterJob.SCATTER, Long.valueOf(4L));
        return new Snapshot(tick, states);
    }

    private static Snapshot snapOf(MatouId id, long count, long tick) {
        Map<MatouId, Object> states = new HashMap<MatouId, Object>();
        states.put(id, Long.valueOf(count));
        return new Snapshot(tick, states);
    }

    /**
     * Writes a temp content file with the owned header plus the given
     * block stanza (bad-block refusal fixtures must not live in
     * {@code content/}: every file there must stay dual-parsable).
     */
    private static String tmpMatou(String stanza) {
        String body = "syntax 1\nnamespace example1.content\n\n"
                + "genre Block : Data\nfield hardness : f32\n"
                + "field opaque : bool\n\n" + stanza;
        try {
            java.nio.file.Path p =
                    Files.createTempFile("blockspec", ".matou");
            Files.write(p, body.getBytes(StandardCharsets.UTF_8));
            p.toFile().deleteOnExit();
            return p.toString();
        } catch (Exception e) {
            throw new RuntimeException("tmpMatou: " + e.getMessage(), e);
        }
    }

    /**
     * Writes a temp content file with a mob header plus the given mob
     * stanza (loot refusal fixtures must not live in {@code content/}:
     * every file there must stay dual-parsable and single-mob).
     */
    private static String tmpLoot(String stanza) {
        String body = "syntax 1\nnamespace example1.content\n\n"
                + "genre Item : Data\nfield stack : u32\n"
                + "field label : string\n\n"
                + "genre Mob : Data\nfield hp : u32\n"
                + "field drop : item_ref\n\n"
                + "item my_gem\n  stack = 64\n  label = \"shiny\"\n\n"
                + stanza;
        try {
            java.nio.file.Path p =
                    Files.createTempFile("loottable", ".matou");
            Files.write(p, body.getBytes(StandardCharsets.UTF_8));
            p.toFile().deleteOnExit();
            return p.toString();
        } catch (Exception e) {
            throw new RuntimeException("tmpLoot: " + e.getMessage(), e);
        }
    }

    public static void main(String[] args) {
        // --- ids live in the frozen example1 namespaces ---
        check(OwnedVeinJob.VEIN.equals(
                MatouId.of("example1.content", "my_vein")), "owned id");
        check(AdditiveScatterJob.SCATTER.toString().equals(
                "example1.overworld:scatter_additive"), "additive id");
        expectRefused(() -> {
            MatouId.parse("my_vein");
        }, "bare ident");

        // --- owned job: pure, deterministic, addressed by tick ---
        final OwnedVeinJob owned = new OwnedVeinJob();
        List<String> d1 = owned.decide(snap(7L));
        List<String> d2 = owned.decide(snap(7L));
        check(d1.equals(d2), "owned pure");
        check(d1.size() == 8, "owned count 8");
        boolean shaped = true;
        for (String cell : d1) {
            shaped &= cell.matches("[0-9]+,[0-9]+");
        }
        check(shaped, "owned cells shaped");
        try {
            d1.add("0,0");
            check(false, "owned decision immutable");
        } catch (UnsupportedOperationException e) {
            System.out.println("ok example1 : owned decision immutable");
        }
        expectNullRefused(() -> {
            owned.decide(null);
        }, "null snapshot");
        expectRefused(() -> {
            Map<MatouId, Object> states =
                    new HashMap<MatouId, Object>();
            states.put(AdditiveScatterJob.SCATTER, Long.valueOf(4L));
            owned.decide(new Snapshot(7L, states));
        }, "missing count");
        expectRefused(() -> {
            Map<MatouId, Object> states =
                    new HashMap<MatouId, Object>();
            states.put(OwnedVeinJob.VEIN, "eight");
            states.put(AdditiveScatterJob.SCATTER, Long.valueOf(4L));
            owned.decide(new Snapshot(7L, states));
        }, "bad count type");
        expectRefused(() -> {
            Map<MatouId, Object> states =
                    new HashMap<MatouId, Object>();
            states.put(OwnedVeinJob.VEIN, Long.valueOf(0L));
            states.put(AdditiveScatterJob.SCATTER, Long.valueOf(4L));
            owned.decide(new Snapshot(7L, states));
        }, "count 0");

        // --- additive job: pure, distinct stream from owned ---
        final AdditiveScatterJob additive = new AdditiveScatterJob();
        List<String> a1 = additive.decide(snap(7L));
        check(a1.equals(additive.decide(snap(7L))), "additive pure");
        check(a1.size() == 4, "additive count 4");
        check(!a1.equals(d1.subList(0, 4)), "additive stream distinct");
        expectNullRefused(() -> {
            additive.decide(null);
        }, "additive null snapshot");

        // --- late merge: owned never replaced, no duplicates ---
        List<String> merged = AdditiveScatterJob.merge(
                Arrays.asList("1,2", "3,4"), Arrays.asList("3,4", "5,6"));
        check(merged.equals(Arrays.asList("1,2", "3,4", "5,6")),
                "merge additive");
        check(merged.subList(0, 2).equals(Arrays.asList("1,2", "3,4")),
                "merge owned first");
        List<String> full = AdditiveScatterJob.merge(d1, a1);
        check(full.size() >= d1.size()
                && full.subList(0, d1.size()).equals(d1),
                "merge keeps owned");
        try {
            full.add("0,0");
            check(false, "merged immutable");
        } catch (UnsupportedOperationException e) {
            System.out.println("ok example1 : merged immutable");
        }
        checkNullRefused(new Refusal[]{
            new Refusal(() -> {
                AdditiveScatterJob.merge(null,
                        Collections.<String>emptyList());
            }, "merge null owned"),
            new Refusal(() -> {
                AdditiveScatterJob.merge(
                        Collections.<String>emptyList(), null);
            }, "merge null additive"),
        });

        // --- B2 pack: counts from source files, jobs exposed, loud setup ---
        final ExamplePack pack = ExamplePack.fromFiles(
                "content/owned.matou", "content/additive.matou");
        check(pack.namespace().equals("example1"), "pack namespace");
        Map<MatouId, Object> packStates = pack.states(7L);
        check(packStates.get(OwnedVeinJob.VEIN).equals(Long.valueOf(8L)),
                "pack owned count 8");
        check(packStates.get(AdditiveScatterJob.SCATTER)
                .equals(Long.valueOf(4L)), "pack scatter count 4");
        check(pack.jobs().size() == 2, "pack 2 jobs");
        check(pack.jobs().get(0) instanceof OwnedVeinJob
                && pack.jobs().get(1) instanceof AdditiveScatterJob,
                "pack owned first");
        Snapshot packSnap = new Snapshot(7L, packStates);
        check(pack.job(OwnedVeinJob.VEIN).decide(packSnap).size() == 8,
                "pack owned decides 8");
        check(pack.job(AdditiveScatterJob.SCATTER).decide(packSnap)
                .size() == 4, "pack additive decides 4");
        try {
            pack.states(7L).put(OwnedVeinJob.VEIN, Long.valueOf(1L));
            check(false, "pack states immutable");
        } catch (UnsupportedOperationException e) {
            System.out.println("ok example1 : pack states immutable");
        }
        try {
            new ExamplePack().states(7L);
            check(false, "pack unconfigured");
        } catch (IllegalStateException e) {
            System.out.println("ok example1 : refused unconfigured ("
                    + e.getMessage() + ")");
        }
        expectRefused(() -> {
            new ExamplePack(0, 4);
        }, "pack count 0");
        expectRefused(() -> {
            pack.states(-1L);
        }, "pack negative tick");
        checkNullRefused(new Refusal[]{
            new Refusal(() -> {
                ExamplePack.fromFiles(null, "content/additive.matou");
            }, "pack null path"),
            new Refusal(() -> {
                new ExamplePack().configure(null);
            }, "pack null args"),
        });
        expectRefused(() -> {
            Map<String, String> op = new HashMap<String, String>();
            op.put("ownedFile", "content/owned.matou");
            new ExamplePack().configure(op);
        }, "pack missing key");
        expectRefused(() -> {
            Map<String, String> op = new HashMap<String, String>();
            op.put("ownedFile", "content/nope.matou");
            op.put("scatterFile", "content/additive.matou");
            new ExamplePack().configure(op);
        }, "pack bad path");
        Map<String, String> cfg = new HashMap<String, String>();
        cfg.put("ownedFile", "content/owned.matou");
        cfg.put("scatterFile", "content/additive.matou");
        ExamplePack viaCfg = new ExamplePack();
        viaCfg.configure(cfg);
        check(viaCfg.states(7L).equals(packStates), "pack configure wires");

        // --- wired pack: composite hut + aliases, legacy path intact ---
        final ExamplePack wired = ExamplePack.fromFiles(
                "content/owned.matou", "content/additive.matou",
                "content/structure.matou");
        check(wired.namespace().equals("example1"), "wired namespace");
        Map<MatouId, Object> wiredStates = wired.states(7L);
        check(wiredStates.size() == 3, "wired 3 states");
        check(wiredStates.get(StructurePlaceJob.HUT)
                .equals(Long.valueOf(2L)), "wired hut count 2");
        check(!packStates.containsKey(StructurePlaceJob.HUT),
                "legacy no hut state");
        check(!packStates.containsKey(StructurePlaceJob.WELL),
                "legacy no well state");
        check(wired.jobs().size() == 3, "wired 3 jobs");
        check(viaCfg.jobs().size() == 2, "pack configure legacy 2 jobs");
        Snapshot wiredSnap = new Snapshot(7L, wiredStates);
        check(wired.job(OwnedVeinJob.VEIN).decide(wiredSnap).size() == 8,
                "wired owned decides 8");
        check(wired.job(AdditiveScatterJob.SCATTER).decide(wiredSnap)
                .size() == 4, "wired additive decides 4");
        check(wired.job(StructurePlaceJob.HUT).decide(wiredSnap)
                .size() == 2 * (8 + 18),
                "wired hut decides 52");
        check(wired.jobs().get(2) instanceof StructurePlaceJob,
                "wired third is structure");
        check(wired.job(StructurePlaceJob.HUT) instanceof StructurePlaceJob,
                "wired registry hut resolves");
        final StructurePlaceJob hutJob = StructurePlaceJob.fromFile(
                "content/structure.matou", "hut");
        final ExamplePack viaCtor = new ExamplePack(8, 4, hutJob);
        check(viaCtor.states(7L).equals(wiredStates),
                "pack ctor wires structure");
        check(viaCtor.jobs().size() == 3, "pack ctor 3 jobs");
        checkNullRefused(new Refusal[]{
            new Refusal(() -> {
                new ExamplePack(8, 4, (StructurePlaceJob) null);
            }, "pack null structure job"),
            new Refusal(() -> {
                ExamplePack.fromFiles("content/owned.matou",
                        "content/additive.matou", (String) null);
            }, "pack null structure path"),
            new Refusal(() -> {
                ExamplePack.fromFiles("content/owned.matou",
                        "content/additive.matou",
                        "content/structure.matou", null);
            }, "pack null aliases"),
        });
        expectRefused(() -> {
            ExamplePack.fromFiles("content/owned.matou",
                    "content/additive.matou", "content/nope.matou");
        }, "pack bad structure path");
        Map<String, String> cfg3 = new HashMap<String, String>();
        cfg3.put("ownedFile", "content/owned.matou");
        cfg3.put("scatterFile", "content/additive.matou");
        cfg3.put("structureFile", "content/structure.matou");
        ExamplePack viaCfg3 = new ExamplePack();
        viaCfg3.configure(cfg3);
        check(viaCfg3.states(7L).equals(wiredStates),
                "pack configure wires structure");
        check(viaCfg3.jobs().size() == 3, "pack configure 3 jobs");
        expectRefused(() -> {
            Map<String, String> op = new HashMap<String, String>();
            op.put("ownedFile", "content/owned.matou");
            op.put("scatterFile", "content/additive.matou");
            op.put("structureFile", "content/nope.matou");
            new ExamplePack().configure(op);
        }, "pack bad structureFile");
        Map<String, String> cfgA = new HashMap<String, String>();
        cfgA.put("ownedFile", "content/owned.matou");
        cfgA.put("scatterFile", "content/additive.matou");
        cfgA.put("structureFile", "content/structure.matou");
        cfgA.put("block.example1.structures:hut_wall", "minecraft:stone");
        cfgA.put("block.example1.structures:hut_roof", "minecraft:stone");
        ExamplePack viaAlias = new ExamplePack();
        viaAlias.configure(cfgA);
        check(viaAlias.jobs().size() == 3, "pack alias 3 jobs");
        List<String> allied = viaAlias.job(StructurePlaceJob.HUT).decide(
                new Snapshot(7L, viaAlias.states(7L)));
        check(allied.size() == 52, "pack alias decides 52");
        boolean allStone = true;
        for (String cell : allied) {
            allStone &= cell.endsWith(":minecraft:stone");
        }
        check(allStone, "pack alias all stone");
        checkRefused(new Refusal[]{
            new Refusal(() -> {
                Map<String, String> op = new HashMap<String, String>();
                op.put("ownedFile", "content/owned.matou");
                op.put("scatterFile", "content/additive.matou");
                op.put("structureFile", "content/structure.matou");
                op.put("block.example1.structures:hut_roof",
                        "minecraft:stone");
                new ExamplePack().configure(op);
            }, "pack partial aliases"),
            new Refusal(() -> {
                Map<String, String> op = new HashMap<String, String>();
                op.put("ownedFile", "content/owned.matou");
                op.put("scatterFile", "content/additive.matou");
                op.put("structureFile", "content/structure.matou");
                op.put("block.example1.structures:hut_wall",
                        "minecraft:stone");
                op.put("block.example1.structures:hut_roof",
                        "minecraft:stone");
                op.put("block.bogus:thing", "minecraft:dirt");
                new ExamplePack().configure(op);
            }, "pack bogus alias"),
        });

        // --- structure job: pure leaf placement, named semantic refusals ---
        final StructurePlaceJob well = StructurePlaceJob.fromFile(
                "content/structure.matou", "well");
        check(well.id().equals(StructurePlaceJob.WELL), "structure id");
        check(well.id().equals(
                MatouId.of("example1.structures", "well")),
                "structure ns");
        check(well.contentCount() == 1, "structure content count 1");
        check(Arrays.equals(well.anchor(), new int[]{0, 64, 0}),
                "structure anchor");
        check(Arrays.equals(well.size(), new int[]{3, 2, 3}),
                "structure size");
        check(well.palette().equals(
                Arrays.asList("example1.structures:hut_wall")),
                "structure palette");
        try {
            well.palette().add("example1.structures:nope");
            check(false, "structure palette immutable");
        } catch (UnsupportedOperationException e) {
            System.out.println("ok example1 : structure palette immutable");
        }
        List<String> s1 = well.decide(
                snapOf(StructurePlaceJob.WELL, 2L, 7L));
        List<String> s2 = well.decide(
                snapOf(StructurePlaceJob.WELL, 2L, 7L));
        check(s1.equals(s2), "structure pure");
        check(s1.size() == 2 * 3 * 2 * 3, "structure count*volume 36");
        boolean sshaped = true;
        boolean sband = true;
        for (String cell : s1) {
            sshaped &= cell.matches(
                    "[0-9]+,[0-9]+,[0-9]+:example1\\.structures:hut_wall");
            int y = Integer.parseInt(
                    cell.split(":")[0].split(",")[1]);
            sband &= (y == 64 || y == 65);
        }
        check(sshaped, "structure cells shaped");
        check(sband, "structure y band");
        check(!well.decide(snapOf(StructurePlaceJob.WELL, 2L, 8L))
                .equals(s1), "structure tick addressed");
        try {
            s1.add("0,64,0:example1.structures:hut_wall");
            check(false, "structure decision immutable");
        } catch (UnsupportedOperationException e) {
            System.out.println("ok example1 : structure decision immutable");
        }
        expectNullRefused(() -> {
            well.decide(null);
        }, "structure null snapshot");
        expectRefused(() -> {
            well.decide(new Snapshot(7L,
                    new HashMap<MatouId, Object>()));
        }, "structure missing count");
        expectRefused(() -> {
            Map<MatouId, Object> states =
                    new HashMap<MatouId, Object>();
            states.put(StructurePlaceJob.WELL, Long.valueOf(0L));
            well.decide(new Snapshot(7L, states));
        }, "structure count 0");
        final int[] anchor = new int[]{0, 64, 0};
        final List<String> pal =
                Arrays.asList("example1.structures:hut_wall");
        final List<StructurePlaceJob> noParts = Collections.emptyList();
        checkRefused(new Refusal[]{
            new Refusal(() -> {
                new StructurePlaceJob(StructurePlaceJob.WELL, anchor,
                        new int[]{0, 2, 3}, pal, noParts, 1);
            }, "structure size 0"),
            new Refusal(() -> {
                new StructurePlaceJob(StructurePlaceJob.WELL, anchor,
                        new int[]{3, -1, 3}, pal, noParts, 1);
            }, "structure size negative"),
            new Refusal(() -> {
                new StructurePlaceJob(StructurePlaceJob.WELL, anchor,
                        new int[]{3, 2, 3}, Collections.<String>emptyList(),
                        noParts, 1);
            }, "structure palette empty"),
            new Refusal(() -> {
                new StructurePlaceJob(StructurePlaceJob.WELL, anchor,
                        new int[]{3, 2, 3}, pal, noParts, 0);
            }, "structure count 0"),
        });
        final StructurePlaceJob built = new StructurePlaceJob(
                StructurePlaceJob.HUT, new int[]{10, 64, -4},
                new int[]{2, 2, 2},
                Arrays.asList("example1.structures:hut_roof"),
                Arrays.asList(well), 2);
        check(built.parts().size() == 1
                && built.parts().get(0).equals(well),
                "ctor composite parts");
        try {
            built.parts().add(well);
            check(false, "structure parts immutable");
        } catch (UnsupportedOperationException e) {
            System.out.println("ok example1 : structure parts immutable");
        }
        checkNullRefused(new Refusal[]{
            new Refusal(() -> {
                new StructurePlaceJob(StructurePlaceJob.WELL, anchor,
                        new int[]{3, 2, 3}, pal, null, 1);
            }, "structure null parts"),
            new Refusal(() -> {
                new StructurePlaceJob(StructurePlaceJob.WELL, anchor,
                        new int[]{3, 2, 3}, pal,
                        Collections.<StructurePlaceJob>singletonList(null),
                        1);
            }, "structure null part"),
            new Refusal(() -> {
                new StructurePlaceJob(StructurePlaceJob.WELL, null,
                        new int[]{3, 2, 3}, pal, noParts, 1);
            }, "structure null anchor"),
            new Refusal(() -> {
                new StructurePlaceJob(StructurePlaceJob.WELL, anchor,
                        new int[]{3, 2, 3}, null, noParts, 1);
            }, "structure null palette"),
        });
        // --- composite: own volume first, parts in order, shared offset ---
        final StructurePlaceJob hut = StructurePlaceJob.fromFile(
                "content/structure.matou", "hut");
        check(hut.id().equals(StructurePlaceJob.HUT), "composite id");
        check(hut.contentCount() == 2, "composite content count 2");
        check(hut.palette().equals(
                Arrays.asList("example1.structures:hut_roof")),
                "composite palette");
        check(hut.parts().size() == 1
                && hut.parts().get(0).id().equals(
                        StructurePlaceJob.WELL),
                "composite part well");
        List<String> h1 = hut.decide(
                snapOf(StructurePlaceJob.HUT, 1L, 7L));
        check(h1.equals(hut.decide(
                snapOf(StructurePlaceJob.HUT, 1L, 7L))),
                "composite pure");
        check(h1.size() == 8 + 18, "composite 8 own + 18 part");
        boolean bordered = true;
        for (int i = 0; i < 8; i++) {
            bordered &= h1.get(i).endsWith(
                    ":example1.structures:hut_roof");
        }
        for (int i = 8; i < 26; i++) {
            bordered &= h1.get(i).endsWith(
                    ":example1.structures:hut_wall");
        }
        check(bordered, "composite own-then-parts");
        String[] own0 = h1.get(0).split(":")[0].split(",");
        String[] part0 = h1.get(8).split(":")[0].split(",");
        check(Integer.parseInt(own0[0]) - Integer.parseInt(part0[0]) == 10
                && Integer.parseInt(own0[1])
                        - Integer.parseInt(part0[1]) == 0
                && Integer.parseInt(own0[2])
                        - Integer.parseInt(part0[2]) == -4,
                "composite shared offset");
        List<String> h2 = hut.decide(
                snapOf(StructurePlaceJob.HUT, 2L, 7L));
        check(h2.size() == 2 * 26, "composite count 2 is 52");
        String[] own1 = h2.get(26).split(":")[0].split(",");
        String[] part1 = h2.get(34).split(":")[0].split(",");
        check(Integer.parseInt(own1[0]) - Integer.parseInt(part1[0]) == 10
                && Integer.parseInt(own1[2])
                        - Integer.parseInt(part1[2]) == -4,
                "composite second offset shared");

        // --- wiring refusals: cycles, external parts, alias strictness ---
        checkRefused(new Refusal[]{
            new Refusal(() -> {
                StructurePlaceJob.fromFile(
                        "content/structure_badparts.matou", "loop");
            }, "structure cycle"),
            new Refusal(() -> {
                StructurePlaceJob.fromFile(
                        "content/structure_badparts.matou", "ext");
            }, "structure external part"),
        });

        // --- cross-file parts: ext wires well from structure.matou ---
        final List<String> xfiles = Arrays.asList(
                "content/structure_badparts.matou",
                "content/structure.matou");
        final StructurePlaceJob ext = StructurePlaceJob.fromFiles(
                xfiles, "bad.parts:ext");
        check(ext.id().equals(MatouId.of("bad.parts", "ext")), "cross id");
        check(ext.contentCount() == 1, "cross content count 1");
        check(ext.palette().equals(Arrays.asList("bad.parts:b1")),
                "cross palette");
        check(ext.parts().size() == 1
                && ext.parts().get(0).id().equals(
                        StructurePlaceJob.WELL),
                "cross part well");
        List<String> x1 = ext.decide(
                snapOf(MatouId.of("bad.parts", "ext"), 1L, 7L));
        check(x1.equals(ext.decide(
                snapOf(MatouId.of("bad.parts", "ext"), 1L, 7L))),
                "cross pure");
        check(x1.size() == 1 + 18, "cross 1 own + 18 part");
        check(x1.get(0).endsWith(":bad.parts:b1"), "cross own first");
        boolean xwall = true;
        for (int i = 1; i < 19; i++) {
            xwall &= x1.get(i).endsWith(
                    ":example1.structures:hut_wall");
        }
        check(xwall, "cross part after own");
        String[] xown = x1.get(0).split(":")[0].split(",");
        String[] xpart = x1.get(1).split(":")[0].split(",");
        check(Integer.parseInt(xown[0]) - Integer.parseInt(xpart[0]) == 5
                && Integer.parseInt(xown[1])
                        - Integer.parseInt(xpart[1]) == 0
                && Integer.parseInt(xown[2])
                        - Integer.parseInt(xpart[2]) == 5,
                "cross shared offset");
        Map<String, String> xalias = new HashMap<String, String>();
        xalias.put("bad.parts:b1", "minecraft:stone");
        xalias.put("example1.structures:hut_wall", "minecraft:stone");
        final StructurePlaceJob xaliased = StructurePlaceJob.fromFiles(
                xfiles, "bad.parts:ext", xalias);
        List<String> xacells = xaliased.decide(
                snapOf(MatouId.of("bad.parts", "ext"), 1L, 7L));
        check(xacells.size() == 19, "cross alias decides 19");
        boolean xstone = true;
        for (String cell : xacells) {
            xstone &= cell.endsWith(":minecraft:stone");
        }
        check(xstone, "cross alias all stone");
        expectRefused(() -> {
            Map<String, String> partial = new HashMap<String, String>();
            partial.put("example1.structures:hut_wall",
                    "minecraft:stone");
            StructurePlaceJob.fromFiles(xfiles, "bad.parts:ext",
                    partial);
        }, "cross alias unmapped palette");

        // --- file-set refusals: unknown ns, dup ns, bad root, no files ---
        final List<String> cfiles = Arrays.asList(
                "content/structure_badparts.matou",
                "content/structure_cross.matou");
        checkRefused(new Refusal[]{
            new Refusal(() -> {
                StructurePlaceJob.fromFiles(
                        Collections.singletonList(
                                "content/structure_badparts.matou"),
                        "bad.parts:ext");
            }, "cross unknown namespace"),
            new Refusal(() -> {
                StructurePlaceJob.fromFiles(cfiles, "bad.parts:near");
            }, "cross cycle"),
            new Refusal(() -> {
                StructurePlaceJob.fromFiles(Arrays.asList(
                        "content/structure.matou",
                        "content/structure.matou"),
                        "example1.structures:hut");
            }, "cross duplicate namespace"),
            new Refusal(() -> {
                StructurePlaceJob.fromFiles(xfiles, "ext");
            }, "cross bare root"),
            new Refusal(() -> {
                StructurePlaceJob.fromFiles(xfiles, "bad.parts:nope");
            }, "cross missing root"),
            new Refusal(() -> {
                StructurePlaceJob.fromFiles(
                        Collections.<String>emptyList(),
                        "bad.parts:ext");
            }, "cross no files"),
        });
        checkNullRefused(new Refusal[]{
            new Refusal(() -> {
                StructurePlaceJob.fromFiles(null, "bad.parts:ext",
                        Collections.<String, String>emptyMap());
            }, "cross null files"),
            new Refusal(() -> {
                StructurePlaceJob.fromFiles(xfiles, null,
                        Collections.<String, String>emptyMap());
            }, "cross null root"),
        });

        // --- wired pack across files: ext root via operator keys ---
        Map<String, String> cfgX = new HashMap<String, String>();
        cfgX.put("ownedFile", "content/owned.matou");
        cfgX.put("scatterFile", "content/additive.matou");
        cfgX.put("structureFile", "content/structure_badparts.matou");
        cfgX.put("structureFiles", "content/structure.matou");
        cfgX.put("structureRoot", "bad.parts:ext");
        ExamplePack viaCross = new ExamplePack();
        viaCross.configure(cfgX);
        Map<MatouId, Object> crossStates = viaCross.states(7L);
        check(crossStates.size() == 3, "cross pack 3 states");
        check(crossStates.get(MatouId.of("bad.parts", "ext"))
                .equals(Long.valueOf(1L)), "cross pack ext count 1");
        check(viaCross.jobs().size() == 3, "cross pack 3 jobs");
        Snapshot crossSnap = new Snapshot(7L, crossStates);
        check(viaCross.job(MatouId.of("bad.parts", "ext"))
                .decide(crossSnap).size() == 19,
                "cross pack decides 19");
        final ExamplePack directCross = ExamplePack.fromFiles(
                "content/owned.matou", "content/additive.matou", xfiles,
                "bad.parts:ext",
                Collections.<String, String>emptyMap());
        check(directCross.states(7L).equals(crossStates),
                "cross pack overload wires");
        Map<String, String> cfgXS = new HashMap<String, String>(cfgX);
        cfgXS.put("block.bad.parts:b1", "minecraft:stone");
        cfgXS.put("block.example1.structures:hut_wall",
                "minecraft:stone");
        ExamplePack viaCrossStone = new ExamplePack();
        viaCrossStone.configure(cfgXS);
        List<String> xstoned = viaCrossStone.job(
                MatouId.of("bad.parts", "ext")).decide(
                new Snapshot(7L, viaCrossStone.states(7L)));
        check(xstoned.size() == 19, "cross pack alias decides 19");
        boolean xallstone = true;
        for (String cell : xstoned) {
            xallstone &= cell.endsWith(":minecraft:stone");
        }
        check(xallstone, "cross pack alias all stone");
        expectRefused(() -> {
            Map<String, String> op = new HashMap<String, String>();
            op.put("ownedFile", "content/owned.matou");
            op.put("scatterFile", "content/additive.matou");
            op.put("structureRoot", "bad.parts:ext");
            new ExamplePack().configure(op);
        }, "cross pack root without files");
        expectRefused(() -> {
            Map<String, String> op = new HashMap<String, String>();
            op.put("ownedFile", "content/owned.matou");
            op.put("scatterFile", "content/additive.matou");
            op.put("structureFile",
                    "content/structure_badparts.matou");
            op.put("structureFiles",
                    "content/structure.matou, ");
            op.put("structureRoot", "bad.parts:ext");
            new ExamplePack().configure(op);
        }, "cross pack blank structureFiles entry");
        checkNullRefused(new Refusal[]{
            new Refusal(() -> {
                ExamplePack.fromFiles("content/owned.matou",
                        "content/additive.matou", (List<String>) null,
                        "bad.parts:ext",
                        Collections.<String, String>emptyMap());
            }, "cross pack null files"),
            new Refusal(() -> {
                ExamplePack.fromFiles("content/owned.matou",
                        "content/additive.matou", xfiles, null,
                        Collections.<String, String>emptyMap());
            }, "cross pack null root"),
        });
        Map<String, String> stoneAlias = new HashMap<String, String>();
        stoneAlias.put("example1.structures:hut_wall", "minecraft:stone");
        final StructurePlaceJob aliased = StructurePlaceJob.fromFile(
                "content/structure.matou", "well", stoneAlias);
        check(aliased.palette().equals(Arrays.asList("minecraft:stone")),
                "alias palette");
        List<String> acells = aliased.decide(
                snapOf(StructurePlaceJob.WELL, 1L, 7L));
        check(acells.size() == 18, "alias decides 18");
        boolean aliasStone = true;
        for (String cell : acells) {
            aliasStone &= cell.endsWith(":minecraft:stone");
        }
        check(aliasStone, "alias all stone");
        checkRefused(new Refusal[]{
            new Refusal(() -> {
                Map<String, String> partial = new HashMap<String, String>();
                partial.put("example1.structures:hut_roof",
                        "minecraft:stone");
                StructurePlaceJob.fromFile(
                        "content/structure.matou", "hut", partial);
            }, "alias unmapped palette"),
            new Refusal(() -> {
                Map<String, String> bogus = new HashMap<String, String>();
                bogus.put("example1.structures:hut_wall",
                        "minecraft:stone");
                bogus.put("bogus:thing", "minecraft:dirt");
                StructurePlaceJob.fromFile(
                        "content/structure.matou", "well", bogus);
            }, "alias unknown key"),
            new Refusal(() -> {
                Map<String, String> bad = new HashMap<String, String>();
                bad.put("example1.structures:hut_wall", "stone");
                StructurePlaceJob.fromFile(
                        "content/structure.matou", "well", bad);
            }, "alias bad value"),
        });
        checkNullRefused(new Refusal[]{
            new Refusal(() -> {
                StructurePlaceJob.fromFile(
                        "content/structure.matou", "well", null);
            }, "alias null map"),
        });
        expectRefused(() -> {
            StructurePlaceJob.fromFile(
                    "content/structure.matou", "nope");
        }, "structure missing name");
        expectRefused(() -> {
            StructurePlaceJob.fromFile(
                    "content/nope.matou", "well");
        }, "structure bad path");
        expectNullRefused(() -> {
            StructurePlaceJob.fromFile(null, "well");
        }, "structure null path");

        // --- registry: jobs by id, list overload, dup loud ---
        check(wired.job(StructurePlaceJob.HUT).decide(wiredSnap)
                .size() == 52, "registry hut decides 52");
        final ExamplePack listed = ExamplePack.fromFiles(
                "content/owned.matou", "content/additive.matou",
                Collections.singletonList(hut));
        check(listed.states(7L).equals(wiredStates),
                "registry list overload wires");
        check(listed.job(StructurePlaceJob.HUT).decide(wiredSnap)
                .size() == 52, "registry list hut decides 52");
        expectRefused(() -> {
            wired.job(MatouId.of("example1", "nope"));
        }, "registry unknown job");
        expectNullRefused(() -> {
            wired.job(null);
        }, "registry null id");
        expectRefused(() -> {
            ExamplePack.fromFiles("content/owned.matou",
                    "content/additive.matou", Arrays.asList(hut, hut));
        }, "registry dup structure");
        checkNullRefused(new Refusal[]{
            new Refusal(() -> {
                ExamplePack.fromFiles("content/owned.matou",
                        "content/additive.matou",
                        (List<StructurePlaceJob>) null);
            }, "registry null structures"),
            new Refusal(() -> {
                ExamplePack.fromFiles("content/owned.matou",
                        "content/additive.matou",
                        Collections.singletonList(
                                (StructurePlaceJob) null));
            }, "registry null structure entry"),
        });

        // --- structure merge: owned never replaced, position-keyed ---
        List<String> smerged = StructurePlaceJob.merge(
                Arrays.asList("0,64,0:example1.structures:hut_wall",
                        "1,64,0:example1.structures:hut_wall"),
                Arrays.asList("1,64,0:example1.structures:other",
                        "2,64,0:example1.structures:other"));
        check(smerged.equals(Arrays.asList(
                "0,64,0:example1.structures:hut_wall",
                "1,64,0:example1.structures:hut_wall",
                "2,64,0:example1.structures:other")),
                "structure merge keeps owned block");
        List<String> sfull = StructurePlaceJob.merge(s1,
                well.decide(snapOf(StructurePlaceJob.WELL, 2L, 8L)));
        check(sfull.size() >= s1.size()
                && sfull.subList(0, s1.size()).equals(s1),
                "structure merge keeps owned");
        try {
            sfull.add("0,64,0:example1.structures:hut_wall");
            check(false, "structure merged immutable");
        } catch (UnsupportedOperationException e) {
            System.out.println("ok example1 : structure merged immutable");
        }
        checkNullRefused(new Refusal[]{
            new Refusal(() -> {
                StructurePlaceJob.merge(null,
                        Collections.<String>emptyList());
            }, "structure merge null owned"),
            new Refusal(() -> {
                StructurePlaceJob.merge(
                        Collections.<String>emptyList(), null);
            }, "structure merge null additive"),
        });
        expectRefused(() -> {
            StructurePlaceJob.merge(
                    Arrays.asList("badcell"),
                    Collections.<String>emptyList());
        }, "structure merge bad cell");

        // --- blockspec: registration source, parse-once from owned ---
        List<BlockSpec> specs =
                BlockSpec.fromFile("content/owned.matou");
        check(specs.size() == 1, "blockspec count 1");
        BlockSpec ore = specs.get(0);
        check(ore.namespace().equals("example1.content"),
                "blockspec namespace");
        check(ore.name().equals("my_ore"), "blockspec name");
        check(ore.hardness() == 3.0f, "blockspec hardness");
        check(ore.opaque(), "blockspec opaque");
        check(BlockSpec.fromFile("content/additive.matou").isEmpty(),
                "blockspec no blocks is empty");
        try {
            specs.add(ore);
            check(false, "blockspec list immutable");
        } catch (UnsupportedOperationException e) {
            System.out.println("ok example1 : blockspec list immutable");
        }
        // Refusal fixtures live in temp files: content/*.matou must stay
        // dual-parsable (tools/check_content.py), so bad blocks never land
        // next to the proof content.
        checkRefused(new Refusal[]{
            new Refusal(() -> {
                BlockSpec.fromFile("content/nope.matou");
            }, "blockspec unreadable"),
            new Refusal(() -> {
                BlockSpec.fromFile(tmpMatou("block my_ore\n"
                        + "  opaque = true\n"));
            }, "blockspec missing hardness"),
            new Refusal(() -> {
                BlockSpec.fromFile(tmpMatou("block my_ore\n"
                        + "  hardness = NaN\n"
                        + "  opaque = true\n"));
            }, "blockspec NaN hardness"),
            new Refusal(() -> {
                BlockSpec.fromFile(tmpMatou("block my_ore\n"
                        + "  hardness = 3.0\n"));
            }, "blockspec missing opaque"),
            new Refusal(() -> {
                BlockSpec.fromFile(tmpMatou("block my_ore\n"
                        + "  hardness = 3.0\n"
                        + "  opaque = yes\n"));
            }, "blockspec bad opaque"),
        });
        checkNullRefused(new Refusal[]{
            new Refusal(() -> {
                BlockSpec.fromFile(null);
            }, "blockspec null path"),
        });
        expectRefused(() -> {
            new BlockSpec("example1.content", "", 3.0f, true);
        }, "blockspec empty name");
        expectRefused(() -> {
            new BlockSpec("example1.content", "my_ore",
                    Float.POSITIVE_INFINITY, true);
        }, "blockspec infinite hardness");

        // --- vein job: seeded clusters, snapshot-sealed size, named refusals ---
        final VeinPlaceJob oreVein = VeinPlaceJob.fromFile(
                "content/vein.matou", "ore_vein");
        check(oreVein.id().equals(VeinPlaceJob.ORE), "vein id");
        check(oreVein.id().equals(
                MatouId.of("example1.veins", "ore_vein")), "vein ns");
        check(oreVein.sizeId().equals(
                MatouId.of("example1.veins", "ore_vein_size")),
                "vein size id");
        check(oreVein.contentCount() == 2, "vein content count 2");
        check(Arrays.equals(oreVein.size(), new int[]{3, 2, 3}),
                "vein size");
        check(oreVein.seed() == 7, "vein seed");
        check(oreVein.block().equals("example1.content:my_ore"),
                "vein block identity");
        final List<Long> veinSize = Arrays.asList(Long.valueOf(3L),
                Long.valueOf(2L), Long.valueOf(3L));
        Map<MatouId, Object> veinStates = new HashMap<MatouId, Object>();
        veinStates.put(VeinPlaceJob.ORE, Long.valueOf(2L));
        veinStates.put(oreVein.sizeId(), veinSize);
        List<String> v1 = oreVein.decide(new Snapshot(7L, veinStates));
        check(v1.equals(oreVein.decide(new Snapshot(7L, veinStates))),
                "vein pure");
        check(v1.size() == 2 * 3 * 2 * 3, "vein count*volume 36");
        boolean vshaped = true;
        boolean vband = true;
        for (String cell : v1) {
            vshaped &= cell.matches(
                    "[0-9]+,[0-9]+,[0-9]+:example1\\.content:my_ore");
            int y = Integer.parseInt(
                    cell.split(":")[0].split(",")[1]);
            vband &= (y == 60 || y == 61);
        }
        check(vshaped, "vein cells shaped");
        check(vband, "vein y band 60..61");
        check(!oreVein.decide(new Snapshot(8L, veinStates)).equals(v1),
                "vein tick addressed");
        final VeinPlaceJob ore9 = new VeinPlaceJob(VeinPlaceJob.ORE,
                "example1.content:my_ore", new int[]{3, 2, 3}, 9, 2);
        check(!ore9.decide(new Snapshot(7L, veinStates)).equals(v1),
                "vein seed addressed");
        try {
            v1.add("0,60,0:example1.content:my_ore");
            check(false, "vein decision immutable");
        } catch (UnsupportedOperationException e) {
            System.out.println("ok example1 : vein decision immutable");
        }
        expectNullRefused(() -> {
            oreVein.decide(null);
        }, "vein null snapshot");
        expectRefused(() -> {
            oreVein.decide(new Snapshot(7L,
                    new HashMap<MatouId, Object>()));
        }, "vein missing count");
        expectRefused(() -> {
            Map<MatouId, Object> states =
                    new HashMap<MatouId, Object>();
            states.put(VeinPlaceJob.ORE, "two");
            states.put(oreVein.sizeId(), veinSize);
            oreVein.decide(new Snapshot(7L, states));
        }, "vein bad count type");
        expectRefused(() -> {
            Map<MatouId, Object> states =
                    new HashMap<MatouId, Object>();
            states.put(VeinPlaceJob.ORE, Long.valueOf(0L));
            states.put(oreVein.sizeId(), veinSize);
            oreVein.decide(new Snapshot(7L, states));
        }, "vein count 0");
        final List<Long> flatSize = Arrays.asList(Long.valueOf(3L),
                Long.valueOf(0L), Long.valueOf(3L));
        checkRefused(new Refusal[]{
            new Refusal(() -> {
                Map<MatouId, Object> states =
                        new HashMap<MatouId, Object>();
                states.put(VeinPlaceJob.ORE, Long.valueOf(2L));
                oreVein.decide(new Snapshot(7L, states));
            }, "vein missing size"),
            new Refusal(() -> {
                Map<MatouId, Object> states =
                        new HashMap<MatouId, Object>();
                states.put(VeinPlaceJob.ORE, Long.valueOf(2L));
                states.put(oreVein.sizeId(), "3,2,3");
                oreVein.decide(new Snapshot(7L, states));
            }, "vein bad size type"),
            new Refusal(() -> {
                Map<MatouId, Object> states =
                        new HashMap<MatouId, Object>();
                states.put(VeinPlaceJob.ORE, Long.valueOf(2L));
                states.put(oreVein.sizeId(), Arrays.asList(
                        Long.valueOf(3L), Long.valueOf(2L)));
                oreVein.decide(new Snapshot(7L, states));
            }, "vein bad size shape"),
            new Refusal(() -> {
                Map<MatouId, Object> states =
                        new HashMap<MatouId, Object>();
                states.put(VeinPlaceJob.ORE, Long.valueOf(2L));
                states.put(oreVein.sizeId(), flatSize);
                oreVein.decide(new Snapshot(7L, states));
            }, "vein size 0"),
            new Refusal(() -> {
                new VeinPlaceJob(VeinPlaceJob.ORE,
                        "example1.content:my_ore",
                        new int[]{0, 2, 3}, 7, 2);
            }, "vein ctor size 0"),
            new Refusal(() -> {
                new VeinPlaceJob(VeinPlaceJob.ORE,
                        "example1.content:my_ore",
                        new int[]{3, -1, 3}, 7, 2);
            }, "vein ctor size negative"),
            new Refusal(() -> {
                new VeinPlaceJob(VeinPlaceJob.ORE,
                        "example1.content:my_ore",
                        new int[]{3, 2}, 7, 2);
            }, "vein ctor size shape"),
            new Refusal(() -> {
                new VeinPlaceJob(VeinPlaceJob.ORE, "",
                        new int[]{3, 2, 3}, 7, 2);
            }, "vein ctor empty block"),
            new Refusal(() -> {
                new VeinPlaceJob(VeinPlaceJob.ORE,
                        "example1.content:my_ore",
                        new int[]{3, 2, 3}, 7, 0);
            }, "vein ctor count 0"),
        });
        checkNullRefused(new Refusal[]{
            new Refusal(() -> {
                new VeinPlaceJob(null, "example1.content:my_ore",
                        new int[]{3, 2, 3}, 7, 2);
            }, "vein ctor null id"),
            new Refusal(() -> {
                new VeinPlaceJob(VeinPlaceJob.ORE,
                        "example1.content:my_ore", null, 7, 2);
            }, "vein ctor null size"),
        });
        // --- vein wiring: aliases strict both ways, file-set strict ---
        Map<String, String> oreAlias = new HashMap<String, String>();
        oreAlias.put("example1.content:my_ore", "example1:my_ore");
        final VeinPlaceJob aliasedOre = VeinPlaceJob.fromFile(
                "content/vein.matou", "ore_vein", oreAlias);
        check(aliasedOre.block().equals("example1:my_ore"),
                "vein alias block");
        List<String> acab = aliasedOre.decide(
                new Snapshot(7L, veinStates));
        check(acab.size() == 36, "vein alias decides 36");
        boolean aliasOre = true;
        for (String cell : acab) {
            aliasOre &= cell.endsWith(":example1:my_ore");
        }
        check(aliasOre, "vein alias all ore");
        Map<String, String> oreBoth = new HashMap<String, String>(oreAlias);
        oreBoth.put("bogus:thing", "minecraft:dirt");
        checkRefused(new Refusal[]{
            new Refusal(() -> {
                Map<String, String> wrong = new HashMap<String, String>();
                wrong.put("other:ref", "example1:my_ore");
                VeinPlaceJob.fromFile(
                        "content/vein.matou", "ore_vein", wrong);
            }, "vein alias unmapped block"),
            new Refusal(() -> {
                VeinPlaceJob.fromFile(
                        "content/vein.matou", "ore_vein", oreBoth);
            }, "vein alias unknown key"),
            new Refusal(() -> {
                Map<String, String> bad = new HashMap<String, String>();
                bad.put("example1.content:my_ore", "my_ore");
                VeinPlaceJob.fromFile(
                        "content/vein.matou", "ore_vein", bad);
            }, "vein alias bad value"),
            new Refusal(() -> {
                VeinPlaceJob.fromFile(
                        "content/vein.matou", "nope", oreAlias);
            }, "vein missing name"),
            new Refusal(() -> {
                VeinPlaceJob.fromFile(
                        "content/nope.matou", "ore_vein", oreAlias);
            }, "vein bad path"),
            new Refusal(() -> {
                VeinPlaceJob.fromFiles(
                        Collections.singletonList("content/vein.matou"),
                        "example1.veins:nope", oreAlias);
            }, "vein missing root"),
            new Refusal(() -> {
                VeinPlaceJob.fromFiles(
                        Collections.singletonList("content/vein.matou"),
                        "other.ns:ore_vein", oreAlias);
            }, "vein unknown namespace"),
            new Refusal(() -> {
                VeinPlaceJob.fromFiles(Arrays.asList(
                        "content/vein.matou", "content/vein.matou"),
                        "example1.veins:ore_vein", oreAlias);
            }, "vein duplicate namespace"),
            new Refusal(() -> {
                VeinPlaceJob.fromFiles(
                        Collections.<String>emptyList(),
                        "example1.veins:ore_vein", oreAlias);
            }, "vein no files"),
            new Refusal(() -> {
                VeinPlaceJob.fromFiles(
                        Collections.singletonList("content/vein.matou"),
                        "ore_vein", oreAlias);
            }, "vein bare root"),
        });
        checkNullRefused(new Refusal[]{
            new Refusal(() -> {
                VeinPlaceJob.fromFile(null, "ore_vein", oreAlias);
            }, "vein null path"),
            new Refusal(() -> {
                VeinPlaceJob.fromFile(
                        "content/vein.matou", "ore_vein", null);
            }, "vein null aliases"),
            new Refusal(() -> {
                VeinPlaceJob.fromFiles(null,
                        "example1.veins:ore_vein", oreAlias);
            }, "vein null files"),
            new Refusal(() -> {
                VeinPlaceJob.fromFiles(
                        Collections.singletonList("content/vein.matou"),
                        null, oreAlias);
            }, "vein null root"),
        });
        final VeinPlaceJob crossOre = VeinPlaceJob.fromFiles(
                Collections.singletonList("content/vein.matou"),
                "example1.veins:ore_vein", oreAlias);
        check(crossOre.decide(new Snapshot(7L, veinStates)).equals(acab),
                "vein files wires like file");
        // --- vein pack: fourth job, seal-vs-wire comparateur tick by tick ---
        check(!wired.hasVein() && !pack.hasVein(), "legacy packs vein-free");
        final ExamplePack veinPack = ExamplePack.fromFiles(
                wired, aliasedOre);
        check(veinPack.hasVein(), "vein pack has vein");
        Map<MatouId, Object> veinPackStates = veinPack.states(7L);
        check(veinPackStates.size() == 5, "vein pack 5 states");
        check(veinPackStates.get(VeinPlaceJob.ORE)
                .equals(Long.valueOf(2L)), "vein pack count 2");
        check(veinPackStates.get(aliasedOre.sizeId()).equals(veinSize),
                "vein pack seals size");
        check(veinPack.jobs().size() == 4, "vein pack 4 jobs");
        check(veinPack.jobs().get(3) instanceof VeinPlaceJob,
                "vein fourth is vein");
        check(veinPack.job(VeinPlaceJob.ORE) instanceof VeinPlaceJob,
                "vein registry resolves");
        Snapshot veinSnap = new Snapshot(7L, veinPackStates);
        check(veinPack.job(VeinPlaceJob.ORE).decide(veinSnap).size() == 36,
                "vein registry decides 36");
        boolean sealSame = true;
        for (long t = 0L; t < 40L; t++) {
            Map<MatouId, Object> hand = new HashMap<MatouId, Object>();
            hand.put(OwnedVeinJob.VEIN, Long.valueOf(8L));
            hand.put(AdditiveScatterJob.SCATTER, Long.valueOf(4L));
            hand.put(StructurePlaceJob.HUT, Long.valueOf(2L));
            hand.put(VeinPlaceJob.ORE, Long.valueOf(2L));
            hand.put(aliasedOre.sizeId(), veinSize);
            sealSame &= aliasedOre.decide(new Snapshot(t,
                    veinPack.states(t))).equals(
                    aliasedOre.decide(new Snapshot(t, hand)));
        }
        check(sealSame, "vein seal comparateur 40 ticks");
        Map<MatouId, Object> tampered = new HashMap<MatouId, Object>(
                veinPack.states(7L));
        tampered.put(aliasedOre.sizeId(), Arrays.asList(
                Long.valueOf(2L), Long.valueOf(2L), Long.valueOf(2L)));
        check(!aliasedOre.decide(new Snapshot(7L, tampered)).equals(
                aliasedOre.decide(veinSnap)), "vein size state is live");
        expectRefused(() -> {
            veinPack.job(MatouId.of("example1", "nope"));
        }, "vein pack registry unknown job");
        checkNullRefused(new Refusal[]{
            new Refusal(() -> {
                ExamplePack.fromFiles(wired, null);
            }, "vein pack null job"),
            new Refusal(() -> {
                ExamplePack.fromFiles(null, aliasedOre);
            }, "vein pack null pack"),
            new Refusal(() -> {
                new ExamplePack(8, 4,
                        Collections.singletonList(hutJob), null);
            }, "vein pack null vein ctor"),
        });
        // --- vein pack via operator keys: veinFile + veinblock alias ---
        Map<String, String> cfgV = new HashMap<String, String>();
        cfgV.put("ownedFile", "content/owned.matou");
        cfgV.put("scatterFile", "content/additive.matou");
        cfgV.put("structureFile", "content/structure.matou");
        cfgV.put("veinFile", "content/vein.matou");
        cfgV.put("block.example1.structures:hut_wall", "minecraft:stone");
        cfgV.put("block.example1.structures:hut_roof", "minecraft:stone");
        cfgV.put("veinblock.example1.content:my_ore", "example1:my_ore");
        ExamplePack viaVein = new ExamplePack();
        viaVein.configure(cfgV);
        check(viaVein.hasVein(), "vein configure wires");
        check(viaVein.jobs().size() == 4, "vein configure 4 jobs");
        check(viaVein.states(7L).equals(veinPack.states(7L)),
                "vein configure seals like direct");
        List<String> vdecided = viaVein.job(VeinPlaceJob.ORE).decide(
                new Snapshot(7L, viaVein.states(7L)));
        check(vdecided.size() == 36, "vein configure decides 36");
        boolean vore = true;
        for (String cell : vdecided) {
            vore &= cell.endsWith(":example1:my_ore");
        }
        check(vore, "vein configure all ore");
        expectRefused(() -> {
            Map<String, String> op = new HashMap<String, String>(cfgV);
            op.remove("veinFile");
            op.put("veinRoot", "example1.veins:ore_vein");
            new ExamplePack().configure(op);
        }, "vein root without files");
        expectRefused(() -> {
            Map<String, String> op = new HashMap<String, String>(cfgV);
            op.put("veinFiles", "content/vein.matou, ");
            op.remove("veinFile");
            new ExamplePack().configure(op);
        }, "vein blank veinFiles entry");

        lootSection();

        spawnSection();

        System.out.println("ok example1 : all");
    }

    static void lootSection() {
        // --- loot table: single mob funds both kinds from owned content ---
        final LootTable table =
                LootTable.fromFile("content/owned.matou");
        check(table.drops().size() == 2, "loot table 2 kinds");
        check("example1.content:my_gem".equals(
                table.drops().get(LootJob.ORE)), "loot ore pays gem");
        check("example1.content:my_gem".equals(
                table.drops().get(LootJob.BEAST)), "loot beast pays gem");
        try {
            table.drops().put("ore", "example1.content:nope");
            check(false, "loot table immutable");
        } catch (UnsupportedOperationException e) {
            System.out.println("ok example1 : loot table immutable");
        }
        final String oneMob = tmpLoot("mob my_beast\n  hp = 20\n"
                + "  drop = example1.content:my_gem\n");
        check(LootTable.fromFile(oneMob).drops().equals(table.drops()),
                "loot table wires like owned");
        expectRefused(() -> {
            LootTable.fromFile(tmpLoot(""));
        }, "loot table empty mob");
        expectRefused(() -> {
            LootTable.fromFile(tmpLoot("mob a\n  hp = 1\n"
                    + "  drop = example1.content:my_gem\n"
                    + "mob b\n  hp = 2\n"
                    + "  drop = example1.content:my_gem\n"));
        }, "loot table multi mob");
        expectRefused(() -> {
            LootTable.fromFile("content/no-such.matou");
        }, "loot table missing file");
        checkNullRefused(new Refusal[]{
            new Refusal(() -> {
                LootTable.fromFile(null);
            }, "loot table null path"),
        });

        // --- loot job: pure, immediate, content refs out ---
        final LootJob loot = new LootJob();
        Map<String, Long> harvest = new LinkedHashMap<String, Long>();
        harvest.put("8,10,8:" + LootJob.ORE, Long.valueOf(7L));
        harvest.put("12,10,8:" + LootJob.BEAST, Long.valueOf(7L));
        Map<MatouId, Object> lootStates = new HashMap<MatouId, Object>();
        lootStates.put(LootJob.HARVESTED, harvest);
        lootStates.put(LootJob.TABLE, table.drops());
        lootStates.put(LootJob.COUNT, Long.valueOf(1L));
        final Snapshot lsnap = new Snapshot(7L, lootStates);
        List<String> first = loot.decide(lsnap);
        check(first.equals(loot.decide(lsnap)), "loot pure");
        check(first.equals(Arrays.asList("8,10,8:example1.content:my_gem",
                "12,10,8:example1.content:my_gem")), "loot pays gem twice");
        try {
            first.add("0,0,0:example1.content:my_gem");
            check(false, "loot decision immutable");
        } catch (UnsupportedOperationException e) {
            System.out.println("ok example1 : loot decision immutable");
        }
        Map<String, Long> future = new LinkedHashMap<String, Long>();
        future.put("8,10,8:" + LootJob.ORE, Long.valueOf(9L));
        Map<MatouId, Object> futureStates =
                new HashMap<MatouId, Object>(lootStates);
        futureStates.put(LootJob.HARVESTED, future);
        check(loot.decide(new Snapshot(7L, futureStates)).isEmpty(),
                "loot future harvest not due");
        Map<MatouId, Object> doubleStates =
                new HashMap<MatouId, Object>(lootStates);
        doubleStates.put(LootJob.COUNT, Long.valueOf(2L));
        check(loot.decide(new Snapshot(7L, doubleStates)).size() == 4,
                "loot count state is live");
        expectNullRefused(() -> {
            loot.decide(null);
        }, "loot null snapshot");
        checkRefused(new Refusal[]{
            new Refusal(() -> {
                Map<MatouId, Object> s =
                        new HashMap<MatouId, Object>(lootStates);
                s.remove(LootJob.HARVESTED);
                loot.decide(new Snapshot(7L, s));
            }, "loot missing harvested"),
            new Refusal(() -> {
                Map<MatouId, Object> s =
                        new HashMap<MatouId, Object>(lootStates);
                s.remove(LootJob.TABLE);
                loot.decide(new Snapshot(7L, s));
            }, "loot missing table"),
            new Refusal(() -> {
                Map<MatouId, Object> s =
                        new HashMap<MatouId, Object>(lootStates);
                s.remove(LootJob.COUNT);
                loot.decide(new Snapshot(7L, s));
            }, "loot missing count"),
            new Refusal(() -> {
                Map<MatouId, Object> s =
                        new HashMap<MatouId, Object>(lootStates);
                s.put(LootJob.COUNT, "one");
                loot.decide(new Snapshot(7L, s));
            }, "loot bad count type"),
            new Refusal(() -> {
                Map<MatouId, Object> s =
                        new HashMap<MatouId, Object>(lootStates);
                s.put(LootJob.COUNT, Long.valueOf(0L));
                loot.decide(new Snapshot(7L, s));
            }, "loot zero count"),
            new Refusal(() -> {
                Map<MatouId, Object> s =
                        new HashMap<MatouId, Object>(lootStates);
                Map<String, String> half =
                        new HashMap<String, String>(table.drops());
                half.remove(LootJob.BEAST);
                s.put(LootJob.TABLE, half);
                loot.decide(new Snapshot(7L, s));
            }, "loot half table"),
            new Refusal(() -> {
                Map<MatouId, Object> s =
                        new HashMap<MatouId, Object>(lootStates);
                Map<String, Long> strange =
                        new LinkedHashMap<String, Long>();
                strange.put("8,10,8:fish", Long.valueOf(7L));
                s.put(LootJob.HARVESTED, strange);
                loot.decide(new Snapshot(7L, s));
            }, "loot unknown kind"),
            new Refusal(() -> {
                Map<MatouId, Object> s =
                        new HashMap<MatouId, Object>(lootStates);
                Map<String, Long> shapeless =
                        new LinkedHashMap<String, Long>();
                shapeless.put("8,10:ore", Long.valueOf(7L));
                s.put(LootJob.HARVESTED, shapeless);
                loot.decide(new Snapshot(7L, s));
            }, "loot bad harvest shape"),
            new Refusal(() -> {
                Map<MatouId, Object> s =
                        new HashMap<MatouId, Object>(lootStates);
                Map<String, Long> neg = new LinkedHashMap<String, Long>();
                neg.put("8,10,8:ore", Long.valueOf(-1L));
                s.put(LootJob.HARVESTED, neg);
                loot.decide(new Snapshot(7L, s));
            }, "loot negative harvest tick"),
        });
    }

    static void spawnSection() {
        // --- spawn table: single mob ref from owned content ---
        final SpawnTable table =
                SpawnTable.fromFile("content/owned.matou");
        check("example1.content:my_beast".equals(table.mob()),
                "spawn table wires beast");
        final String oneMob = tmpLoot("mob my_beast\n  hp = 20\n"
                + "  drop = example1.content:my_gem\n");
        check(SpawnTable.fromFile(oneMob).mob().equals(table.mob()),
                "spawn table wires like owned");
        expectRefused(() -> {
            SpawnTable.fromFile(tmpLoot(""));
        }, "spawn table empty mob");
        expectRefused(() -> {
            SpawnTable.fromFile(tmpLoot("mob a\n  hp = 1\n"
                    + "  drop = example1.content:my_gem\n"
                    + "mob b\n  hp = 2\n"
                    + "  drop = example1.content:my_gem\n"));
        }, "spawn table multi mob");
        expectRefused(() -> {
            SpawnTable.fromFile("content/no-such.matou");
        }, "spawn table missing file");
        checkNullRefused(new Refusal[]{
            new Refusal(() -> {
                SpawnTable.fromFile(null);
            }, "spawn table null path"),
        });

        // --- spawn job: pure, budgeted, capped, y-banded ---
        final SpawnJob spawn = new SpawnJob();
        final String mob = table.mob();
        Map<String, String> empty = new LinkedHashMap<String, String>();
        Map<MatouId, Object> spawnStates = new HashMap<MatouId, Object>();
        spawnStates.put(SpawnJob.CENSUS, empty);
        spawnStates.put(SpawnJob.TABLE, mob);
        spawnStates.put(SpawnJob.CAP, Long.valueOf(4L));
        spawnStates.put(SpawnJob.BUDGET, Long.valueOf(1L));
        spawnStates.put(SpawnJob.Y, Arrays.asList(
                Long.valueOf(66L), Long.valueOf(68L)));
        final Snapshot ssnap = new Snapshot(7L, spawnStates);
        List<String> first = spawn.decide(ssnap);
        check(first.equals(spawn.decide(ssnap)), "spawn pure");
        check(first.size() == 1, "spawn budget 1 lands 1");
        boolean shaped = true;
        boolean banded = true;
        for (String cell : first) {
            shaped &= cell.matches(
                    "[0-9]+,[0-9]+,[0-9]+:example1\\.content:my_beast");
            String[] parts = cell.split(":")[0].split(",");
            int x = Integer.parseInt(parts[0]);
            int y = Integer.parseInt(parts[1]);
            int z = Integer.parseInt(parts[2]);
            banded &= (x >= 0 && x < 16 && z >= 0 && z < 16
                    && y >= 66 && y <= 68);
        }
        check(shaped, "spawn cells shaped");
        check(banded, "spawn pads in grid x banded y");
        check(!spawn.decide(new Snapshot(8L, spawnStates)).equals(first),
                "spawn tick addressed");
        try {
            first.add("0,66,0:" + mob);
            check(false, "spawn decision immutable");
        } catch (UnsupportedOperationException e) {
            System.out.println("ok example1 : spawn decision immutable");
        }
        Map<String, String> trio = new LinkedHashMap<String, String>();
        trio.put("11", "1,66,2:" + mob);
        trio.put("23", "3,67,4:" + mob);
        trio.put("37", "5,68,6:" + mob);
        Map<MatouId, Object> trioStates =
                new HashMap<MatouId, Object>(spawnStates);
        trioStates.put(SpawnJob.CENSUS, trio);
        check(spawn.decide(new Snapshot(7L, trioStates)).size() == 1,
                "spawn room 1 lands 1");
        Map<String, String> full = new LinkedHashMap<String, String>(trio);
        full.put("41", "7,66,8:" + mob);
        Map<MatouId, Object> fullStates =
                new HashMap<MatouId, Object>(spawnStates);
        fullStates.put(SpawnJob.CENSUS, full);
        check(spawn.decide(new Snapshot(7L, fullStates)).isEmpty(),
                "spawn cap reached lands none");
        Map<MatouId, Object> doubleStates =
                new HashMap<MatouId, Object>(spawnStates);
        doubleStates.put(SpawnJob.BUDGET, Long.valueOf(2L));
        check(spawn.decide(new Snapshot(7L, doubleStates)).size() == 2,
                "spawn budget state is live");
        expectNullRefused(() -> {
            spawn.decide(null);
        }, "spawn null snapshot");
        checkRefused(new Refusal[]{
            new Refusal(() -> {
                Map<MatouId, Object> s =
                        new HashMap<MatouId, Object>(spawnStates);
                s.remove(SpawnJob.CENSUS);
                spawn.decide(new Snapshot(7L, s));
            }, "spawn missing census"),
            new Refusal(() -> {
                Map<MatouId, Object> s =
                        new HashMap<MatouId, Object>(spawnStates);
                s.remove(SpawnJob.TABLE);
                spawn.decide(new Snapshot(7L, s));
            }, "spawn missing table"),
            new Refusal(() -> {
                Map<MatouId, Object> s =
                        new HashMap<MatouId, Object>(spawnStates);
                s.remove(SpawnJob.CAP);
                spawn.decide(new Snapshot(7L, s));
            }, "spawn missing cap"),
            new Refusal(() -> {
                Map<MatouId, Object> s =
                        new HashMap<MatouId, Object>(spawnStates);
                s.remove(SpawnJob.BUDGET);
                spawn.decide(new Snapshot(7L, s));
            }, "spawn missing budget"),
            new Refusal(() -> {
                Map<MatouId, Object> s =
                        new HashMap<MatouId, Object>(spawnStates);
                s.remove(SpawnJob.Y);
                spawn.decide(new Snapshot(7L, s));
            }, "spawn missing y"),
            new Refusal(() -> {
                Map<MatouId, Object> s =
                        new HashMap<MatouId, Object>(spawnStates);
                s.put(SpawnJob.CAP, "four");
                spawn.decide(new Snapshot(7L, s));
            }, "spawn bad cap type"),
            new Refusal(() -> {
                Map<MatouId, Object> s =
                        new HashMap<MatouId, Object>(spawnStates);
                s.put(SpawnJob.CAP, Long.valueOf(0L));
                spawn.decide(new Snapshot(7L, s));
            }, "spawn zero cap"),
            new Refusal(() -> {
                Map<MatouId, Object> s =
                        new HashMap<MatouId, Object>(spawnStates);
                s.put(SpawnJob.BUDGET, Long.valueOf(0L));
                spawn.decide(new Snapshot(7L, s));
            }, "spawn zero budget"),
            new Refusal(() -> {
                Map<MatouId, Object> s =
                        new HashMap<MatouId, Object>(spawnStates);
                s.put(SpawnJob.TABLE, "my_beast");
                spawn.decide(new Snapshot(7L, s));
            }, "spawn bare table ref"),
            new Refusal(() -> {
                Map<MatouId, Object> s =
                        new HashMap<MatouId, Object>(spawnStates);
                s.put(SpawnJob.Y, "66,68");
                spawn.decide(new Snapshot(7L, s));
            }, "spawn bad y type"),
            new Refusal(() -> {
                Map<MatouId, Object> s =
                        new HashMap<MatouId, Object>(spawnStates);
                s.put(SpawnJob.Y, Arrays.asList(
                        Long.valueOf(68L), Long.valueOf(66L)));
                spawn.decide(new Snapshot(7L, s));
            }, "spawn inverted y"),
            new Refusal(() -> {
                Map<MatouId, Object> s =
                        new HashMap<MatouId, Object>(spawnStates);
                Map<String, String> strange =
                        new LinkedHashMap<String, String>();
                strange.put("11", "1,66,2:example1.content:other");
                s.put(SpawnJob.CENSUS, strange);
                spawn.decide(new Snapshot(7L, s));
            }, "spawn foreign census"),
            new Refusal(() -> {
                Map<MatouId, Object> s =
                        new HashMap<MatouId, Object>(spawnStates);
                Map<String, String> shapeless =
                        new LinkedHashMap<String, String>();
                shapeless.put("11", "1,66:" + mob);
                s.put(SpawnJob.CENSUS, shapeless);
                spawn.decide(new Snapshot(7L, s));
            }, "spawn bad census shape"),
            new Refusal(() -> {
                Map<MatouId, Object> s =
                        new HashMap<MatouId, Object>(spawnStates);
                Map<String, String> badId =
                        new LinkedHashMap<String, String>();
                badId.put("-3", "1,66,2:" + mob);
                s.put(SpawnJob.CENSUS, badId);
                spawn.decide(new Snapshot(7L, s));
            }, "spawn negative census id"),
        });
    }
}
