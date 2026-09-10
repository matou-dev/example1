# Changelog — matou-dev/example1

Notable changes to this repo. This content proof has no FML wiring: it is
not a loadable Forge mod alone and ships inside the `matou-dev/bridge-1710`
versioned server drop; store listings stay DRAFT (see hub `NAMES.md`).
Full notes per tag: https://github.com/matou-dev/example1/releases.

## [Unreleased]

- Extract `ExamplePolicy` holder (structural pass, hub
  `decisions/SPI_STATE_VOCABULARY.md`): the sealed loot/spawn tables
  plus the twelve `PolicyPack` accessors move out of `ExamplePack`
  (637 to 606 lines), which delegates; wiring paths carry the holder
  by reference (structure/vein files never fund tables), count
  fixtures hold it unwired. Public API, sealed values and `E_*`
  codes unchanged — E0-locked by the provision battery and the
  policy comparateur over every wiring path.

- Vein V4 (`content/vein.matou`, SYNTAX-V4): pure `VeinPlaceJob`
  (seeded solid clusters of one block on the `BASE_Y = 60` band, count +
  size from snapshot states with `Counts`-style refusals, `veinblock.`
  operator aliases strict both ways); `ExamplePack` wires it as an
  optional fourth job (`veinFile`/`veinFiles`/`veinRoot` keys, legacy
  packs vein-free and behaviour-identical); gate battery (purity,
  tick/seed addressing, y band, seal-vs-wire comparateur over 40
  ticks, wiring refusals); `check_content.py` pins the vein shape.

- Authoring refactor (test-mod scaling audit): one `ExampleIds` table
  (every `namespace:name` once, shared grid, count-code prefix — jobs
  delegate, RNG addresses derive from the ids); snapshot counts via SPI
  `Counts`, cell rendering via SPI `Cell` (decided bytes and RNG streams
  unchanged, bridge comparateur still green); `ExamplePack` job registry
  (`job(MatouId)` beside `jobs()`, new `fromFiles` overload over
  pre-wired structures, duplicate ids refused); `ExampleCheck` lambdas +
  `Refusal[]` batteries + registry coverage (same assertions plus 10,
  gate output otherwise byte-identical). `tools/check.sh` honours
  `MATOU_SPI_SRC`. Behaviour and error codes unchanged.
- Refactor `StructurePlaceJob` table-driven in place (580 to 437 lines,
  back under the 450 design alert, no satellite split): single-parse
  `fromFile` delegating to a shared `wireRoot`, `String.join` for cycle
  chains, `HashSet` position merge, one `vecOf` size/anchor table, one
  `bad`/`reqList`/`reqNum`/`reqArg` wiring-error table. Behaviour and
  error codes unchanged; gate still 153 oks including the 19-cell
  cross-file `ext`.
- Cross-file `parts`: `StructurePlaceJob.fromFiles` wires a qualified
  `namespace:name` root across a set of content files (one shared plane
  offset per occurrence, own volume first, depth-first listed order —
  unchanged). Import strictness stays parser-enforced
  (`E_MATOU_UNKNOWN_REF` unless same namespace or declared via `from`);
  file-set completeness is wiring-enforced (`E_EXAMPLE_CONTENT:unknown
  namespace` when an imported namespace has no loaded file,
  `E_EXAMPLE_CONTENT:duplicate namespace` on ambiguous files).
  Same-file `fromFile` now delegates to it (identical behaviour for
  same-file trees). Cycles carry qualified chains, same-file or
  cross-file (`E_EXAMPLE_PARTS:cycle <bad.parts:near -> cross.far:far ->
  bad.parts:near>`). Fixtures: `content/structure_cross.matou`
  (`cross.far:far`, 1 block + 1 cycle half), `structure_badparts.matou`
  gains `near` and wires `ext` across files in the gate (19 cells: 1 own
  + 18 `well`, shared offset `(5,0,5)`).
- Pack operator path for cross-file roots: `ExamplePack.fromFiles`
  5-arg overload `(owned, scatter, structurePaths, structureRoot,
  aliases)` plus `configure` keys `structureFiles` (comma-separated,
  blank entries refused) and `structureRoot` (default stays the `hut`
  qualified id, never recopied). `structureRoot` without file(s) fails
  loudly. Single `structureFile` behaviour is byte-for-byte unchanged.
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
