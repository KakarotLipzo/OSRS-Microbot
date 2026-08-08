#!/usr/bin/env bash
# Compiles the CLIENT-NEUTRAL logic layer with a real JDK, with no Microbot/RuneLite on the classpath.
# This is the only local verification available for the port: it proves the ported logic + facade type-
# check (catches signature drift, bad refactors), even though the Rs2* adapter can only be built in a
# Microbot fork. A file is "neutral" unless it imports RuneLite/Rs2/Microbot/inject types.
set -euo pipefail

JAVAC="/c/Users/quinn/Desktop/OSRS/jdk/jdk-11.0.31+11/bin/javac.exe"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/src/main/java"
OUT="$ROOT/build/neutral-check"

CLIENT_IMPORT='import (net\.runelite\.api|net\.runelite\.client\.config|net\.runelite\.client\.plugins\.Plugin|net\.runelite\.client\.plugins\.microbot\.util|net\.runelite\.client\.plugins\.microbot\.Microbot|net\.runelite\.client\.plugins\.microbot\.Script|net\.runelite\.client\.plugins\.microbot\.Global|net\.runelite\.client\.plugins\.microbot\.PluginConstants|com\.google\.inject|javax\.inject)'

rm -rf "$OUT"; mkdir -p "$OUT"

NEUTRAL=()
TOTAL=0
while IFS= read -r f; do
  TOTAL=$((TOTAL+1))
  if grep -Eq "$CLIENT_IMPORT" "$f"; then continue; fi
  NEUTRAL+=("$f")
done < <(find "$SRC" -name '*.java')

echo "Neutral files: ${#NEUTRAL[@]} / $TOTAL   (client-coupled files excluded, verified only in a fork)"
"$JAVAC" -d "$OUT" "${NEUTRAL[@]}"
echo "✅ neutral logic layer compiles clean"
