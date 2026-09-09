# matou-dev/example1 — SPI proof on the content side

Minimal content (1 block / 1 item / 1 mob / 1 owned worldgen feature + 1
overworld additive) proving on the content side what the minimap proves on
the client side, over the same SPI.
**Zero Minecraft import** (`check` gate). Modid `example1`, namespace
`example1:` (see `NAMES.md`).

M2 proof: `content/owned.matou` (block + item + mob + feature, namespace
`example1.content`) + `content/additive.matou` (late feature, namespace
`example1.overworld`, references owned without replacing it). Structure
proof: `content/structure.matou` (syntax 3, namespace
`example1.structures`, 2 blocks + 1 leaf + 1 composite) wired to the pure
`StructurePlaceJob` (recursive parts: own volume first, shared occurrence
offset; named semantic refusals incl. cycles; position-keyed
owned/additive merge; `block.<ref>` palette aliases for landable blocks),
sealed by `ExamplePack` as an optional third job (`structureFile` key; without it the legacy
2-job pack is untouched). Pure jobs
(`OwnedVeinJob`, `AdditiveScatterJob` + late `merge`) over `matou-spi`
snapshots; self-test `java/test`, gate `tools/check.sh` (zero-MC +
sibling-SPI compile + py/java content parity).
