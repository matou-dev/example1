#!/bin/sh
# Gate zero-MC-import (Q2 strict) : aucun import Minecraft/Forge hors bridge.
set -eu
cd "$(dirname "$0")/.."
hits=$(rg -n --no-heading "net\.minecraft|cpw\.mods\.|net\.minecraftforge" \
  --glob '!tools/**' --glob '!.git/**' --glob '!*.md' . || true)
if [ -n "$hits" ]; then
  echo "FAIL zero-mc-import :"
  echo "$hits"
  exit 1
fi
echo "ok (zero-mc-import)"
