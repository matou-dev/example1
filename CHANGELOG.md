# Changelog — matou-dev/example1

Notable changes to this repo. This content proof has no FML wiring: it is
not a loadable Forge mod alone and ships inside the `matou-dev/bridge-1710`
versioned server drop; store listings stay DRAFT (see hub `NAMES.md`).
Full notes per tag: https://github.com/matou-dev/example1/releases.

## [Unreleased]

- Docs: README rewritten in English (R3 hygiene).

## [1.0.0] - 2026-09-09

Source release: https://github.com/matou-dev/example1/releases/tag/v1.0.0

- Content proof over `matou-spi` snapshots: `content/owned.matou` (block +
  item + mob + feature, namespace `example1.content`) plus
  `content/additive.matou` (late overworld feature referencing owned
  content without replacing it).
- Pure jobs (`OwnedVeinJob`, `AdditiveScatterJob` + late `merge`) with
  py/java content parity; bare-ident refusal identical in both parsers.
