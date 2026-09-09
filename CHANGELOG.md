# Changelog — matou-dev/example1

Notable changes to this repo. This content proof has no FML wiring: it is
not a loadable Forge mod alone and ships inside the `matou-dev/bridge-1710`
versioned server drop; store listings stay DRAFT (see hub `NAMES.md`).
Full notes per tag: https://github.com/matou-dev/example1/releases.

## [Unreleased]

- Structure proof over SYNTAX-V3: `content/structure.matou` (1 block + 1
  leaf structure + 1 composite, namespace `example1.structures`) wired once
  through the SPI reference parser to the pure `StructurePlaceJob` (cells
  `x,y,z:ns:block` from anchor/size/palette, count per tick from the
  snapshot). Semantic refusals live in the job, as with `feature.count`:
  `E_EXAMPLE_SIZE` (non-positive extents), `E_EXAMPLE_PALETTE` (empty),
  `E_EXAMPLE_PARTS` (non-leaf refused at wiring until recursive placement
  lands). Position-keyed owned/additive `merge` (owned block never
  replaced). `ExamplePack` untouched on purpose (bridge calls its 2-arg
  `fromFiles`); pack wiring for structures is follow-up.

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
