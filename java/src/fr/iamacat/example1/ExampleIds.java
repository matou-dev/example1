package fr.iamacat.example1;

import fr.iamacat.spi.MatouId;

/**
 * One id table for the whole proof: every {@code namespace:name} once,
 * plus the shared scatter grid and count-code prefix. Job classes keep
 * their own public constants delegating here, and RNG addresses derive
 * from these ids — a rename touches one line, never a grep across
 * src+test+pack. Pure, Java 8, zero deps beyond matou-spi.
 */
public final class ExampleIds {
    private ExampleIds() {}

    /** Pack namespace, e.g. for {@link ExamplePack#namespace}. */
    public static final String NAMESPACE = "example1";

    /** Refusal prefix for every snapshot/content count in this pack. */
    public static final String COUNT_CODE = "E_EXAMPLE_COUNT";

    public static final MatouId VEIN =
            MatouId.parse("example1.content:my_vein");
    public static final MatouId SCATTER =
            MatouId.parse("example1.overworld:scatter_additive");
    public static final MatouId WELL =
            MatouId.parse("example1.structures:well");
    public static final MatouId HUT =
            MatouId.parse("example1.structures:hut");

    /** Shared scatter grid bound (worldgen cells per axis). */
    public static final int GRID = 16;
}
