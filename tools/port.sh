#!/usr/bin/env bash
# Mechanical port transform for one OSRS-Main source file.
# Usage: tools/port.sh core/ConfigStore.java   (path relative to OSRS-Main/src/com/quinn/osrs/main)
# Copies the file into the mirrored OSRS-Micro package and applies the common rewrites:
#   package/imports com.quinn.osrs.main.* -> quinnmain.bot.* (ui -> quinnmain.ui)
#   drop DreamBot Skill/Logger/Tile imports; Skill->Sk, Logger.->Log., Tile->Pos
#   inject neutral imports (Sk, Pos, Log)
# Residual DreamBot method calls (facade routing) are fixed by hand afterwards; compile-check.sh verifies.
set -euo pipefail

SRCBASE=/c/Users/quinn/Desktop/OSRS-Main/src/com/quinn/osrs/main
DSTBASE=/c/Users/quinn/Desktop/OSRS-Micro/src/main/java/net/runelite/client/plugins/microbot/quinnmain
NEW=net.runelite.client.plugins.microbot.quinnmain

rel="$1"
case "$rel" in
  ui/*) dst="$DSTBASE/$rel" ;;
  *)    dst="$DSTBASE/bot/$rel" ;;
esac
mkdir -p "$(dirname "$dst")"
cp "$SRCBASE/$rel" "$dst"

# package + internal cross-package imports
sed -i -E \
  -e "s/package com\.quinn\.osrs\.main\.ui/package $NEW.ui/" \
  -e "s/package com\.quinn\.osrs\.main/package $NEW.bot/" \
  -e "s/import com\.quinn\.osrs\.main\.ui/import $NEW.ui/g" \
  -e "s/import static com\.quinn\.osrs\.main/import static $NEW.bot/g" \
  -e "s/import com\.quinn\.osrs\.main/import $NEW.bot/g" \
  "$dst"

# drop the trivially-neutralised DreamBot type imports
sed -i -E \
  -e "/import org\.dreambot\.api\.methods\.skills\.Skill;/d" \
  -e "/import org\.dreambot\.api\.utilities\.Logger;/d" \
  -e "/import org\.dreambot\.api\.methods\.map\.Tile;/d" \
  -e "/import org\.dreambot\.api\.methods\.container\.impl\.bank\.BankLocation;/d" \
  "$dst"

# type / accessor rewrites (word-boundaried so Skills/SkillTask/getTile are untouched)
sed -i -E \
  -e "s/\bSkill\b/Sk/g" \
  -e "s/\bLogger\./Log./g" \
  -e "s/\bTile\b/Pos/g" \
  -e "s/\bBankLocation\b/BankLoc/g" \
  "$dst"

# inject neutral imports right after the package line
sed -i -E "0,/^package .*/s//&\n\nimport $NEW.game.Sk;\nimport $NEW.game.Pos;\nimport $NEW.bot.core.BankLoc;\nimport $NEW.bot.util.Log;/" "$dst"

echo "ported $rel"
