# matou-dev/example1 — preuve SPI côté contenu

Contenu minime (1 bloc / 1 item / 1 mob / 1 feature worldgen owned + 1 additif
overworld) prouvant ce que la minimap prouve côté client, sur la même SPI.
**Zéro import Minecraft** (gate `check`). Modid `example1`, namespace
`example1:` (cf. `NAMES.md`).

M2 proof : `content/owned.matou` (block + item + mob + feature, namespace
`example1.content`) + `content/additive.matou` (late feature, namespace
`example1.overworld`, references owned without replacing it). Pure jobs
(`OwnedVeinJob`, `AdditiveScatterJob` + late `merge`) over `matou-spi`
snapshots; self-test `java/test`, gate `tools/check.sh` (zero-MC +
sibling-SPI compile + py/java content parity).
