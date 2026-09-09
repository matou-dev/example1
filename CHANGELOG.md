# Changelog — matou-dev/example1

Notable changes to this repo. This content proof has no FML wiring: it is
not a loadable Forge mod alone and ships inside the `matou-dev/bridge-1710`
versioned server drop; store listings stay DRAFT (see hub `NAMES.md`).
Full notes per tag: https://github.com/matou-dev/example1/releases.

## [Unreleased]

- Recursive `parts`: `StructurePlaceJob` places composites (own volume
  first, then parts depth-first in listed order, one shared plane offset
  per occurrence so the composed shape survives addressing; a part's own
  `count` is standalone-only). Cycles fail at wiring
  (`E_EXAMPLE_PARTS:cycle`), cross-file parts fail
  (`E_EXAMPLE_CONTENT:external part`) — never skipped. The pack now wires
  the composite `hut` (own `hut_roof` palette + `well` part).
- Palette aliases for the live run: `fromFile` 3-arg overload and
  `block.<ref>` pack args (`content-ref -> landable block`, empty =
  identity, otherwise strict both ways: unmapped entry, unknown key and
  malformed value all fail at wiring). Content decides *where*, the
  operator decides *what*.
- Structure proof over SYNTAX-V3: `content/structure.matou` (2 blocks + 1
  leaf structure + 1 composite, namespace `example1.structures`) wired once
  through the SPI reference parser to the pure `StructurePlaceJob` (cells
  `x,y,z:ns:block` from anchor/size/palette, count per tick from the
  snapshot). Semantic refusals live in the job, as with `feature.count`:
  `E_EXAMPLE_SIZE` (non-positive extents), `E_EXAMPLE_PALETTE` (empty),
  `E_EXAMPLE_PARTS` (non-leaf refused at wiring until recursive placement
  lands). Position-keyed owned/additive `merge` (owned block never
  replaced). `ExamplePack` wires the proof (additive, backward compatible):
  optional `structureFile` key in `configure`, `fromFiles` 3-arg overload
  and `ExamplePack(int, int, StructurePlaceJob)` ctor; wired packs seal the
  leaf `well` count in `states` and expose the job as third entry of
  `jobs()` (owned first, per contract). Without `structureFile` the pack
  stays the legacy 2-job pack (bridge template untouched). Decide path
  fully wired; landing `x,y,z:block` cells on a Forge sink is bridge
  follow-up (`ForgeCells` only parses `"x,z"`, loudly).

- CI: runner pinned (`ubuntu-24.04`), JDK 21 via `setup-java` (temurin),
  actions pinned by SHA with Dependabot, missing `spi` sibling checkout
  added (the gate compiles against it).
- Docs: README rewritten in English (R3 hygiene).

## [1.0.0] - 2026-09-09

Source release: https://github.com/matou-dev/example1/releases/tag/v1.0.0

- Content proof over `matou-spi` snapshots: `content/owned.matou` (block +
  item + mob + feature, namespace `example1.content`) plus
  `content/additive.matou` (late overworld feature referencing owned
  content without replacing it).
- Pure jobs (`OwnedVeinJob`, `AdditiveScatterJob` + late `merge`) with
  py/java content parity; bare-ident refusal identical in both parsers.
