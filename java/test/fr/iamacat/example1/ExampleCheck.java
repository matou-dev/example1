package fr.iamacat.example1;

import fr.iamacat.spi.MatouId;
import fr.iamacat.spi.Snapshot;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * M2 content self-test (no JUnit on this gate): every violation prints
 * {@code FAIL example1 : ...} and exits 1. Run by tools/check.sh against the
 * {@code ../spi} sibling checkout.
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

    private static Snapshot snap(long tick) {
        Map<MatouId, Object> states = new HashMap<MatouId, Object>();
        states.put(OwnedVeinJob.VEIN, Long.valueOf(8L));
        states.put(AdditiveScatterJob.SCATTER, Long.valueOf(4L));
        return new Snapshot(tick, states);
    }

    public static void main(String[] args) {
        // --- ids live in the frozen example1 namespaces ---
        check(OwnedVeinJob.VEIN.equals(
                MatouId.of("example1.content", "my_vein")), "owned id");
        check(AdditiveScatterJob.SCATTER.toString().equals(
                "example1.overworld:scatter_additive"), "additive id");
        expectRefused(new Runnable() {
            public void run() {
                MatouId.parse("my_vein");
            }
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
        expectNullRefused(new Runnable() {
            public void run() {
                owned.decide(null);
            }
        }, "null snapshot");
        expectRefused(new Runnable() {
            public void run() {
                Map<MatouId, Object> states =
                        new HashMap<MatouId, Object>();
                states.put(AdditiveScatterJob.SCATTER, Long.valueOf(4L));
                owned.decide(new Snapshot(7L, states));
            }
        }, "missing count");
        expectRefused(new Runnable() {
            public void run() {
                Map<MatouId, Object> states =
                        new HashMap<MatouId, Object>();
                states.put(OwnedVeinJob.VEIN, "eight");
                states.put(AdditiveScatterJob.SCATTER, Long.valueOf(4L));
                owned.decide(new Snapshot(7L, states));
            }
        }, "bad count type");
        expectRefused(new Runnable() {
            public void run() {
                Map<MatouId, Object> states =
                        new HashMap<MatouId, Object>();
                states.put(OwnedVeinJob.VEIN, Long.valueOf(0L));
                states.put(AdditiveScatterJob.SCATTER, Long.valueOf(4L));
                owned.decide(new Snapshot(7L, states));
            }
        }, "count 0");

        // --- additive job: pure, distinct stream from owned ---
        final AdditiveScatterJob additive = new AdditiveScatterJob();
        List<String> a1 = additive.decide(snap(7L));
        check(a1.equals(additive.decide(snap(7L))), "additive pure");
        check(a1.size() == 4, "additive count 4");
        check(!a1.equals(d1.subList(0, 4)), "additive stream distinct");
        expectNullRefused(new Runnable() {
            public void run() {
                additive.decide(null);
            }
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
        expectNullRefused(new Runnable() {
            public void run() {
                AdditiveScatterJob.merge(null, Collections.<String>emptyList());
            }
        }, "merge null owned");
        expectNullRefused(new Runnable() {
            public void run() {
                AdditiveScatterJob.merge(
                        Collections.<String>emptyList(), null);
            }
        }, "merge null additive");

        System.out.println("ok example1 : all");
    }
}
