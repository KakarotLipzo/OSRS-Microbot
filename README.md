# OSRS-Micro — Quinn Main, ported to Microbot

A port of the DreamBot bot in `Desktop\OSRS-Main` to **Microbot** (the RuneLite botting fork). Same
game (official OSRS), same logic — different client API. The DreamBot bot in OSRS-Main is **untouched
and still the live one**; this is a parallel project.

## ⚠ Read this first — the compile boundary

This machine has **no Microbot toolchain**, so nothing here has been compiled or run. Microbot plugins
build **inside a fork of the Microbot repo**, not against a published jar. The code here is written
against Microbot's documented `Rs2*` API conventions, but exact signatures drift between revisions —
**expect the compiler in your fork to flag a handful, and fix them against your fork's actual
signatures.** Anything not yet exercised throws `UnsupportedOperationException` on purpose, so nothing
masquerades as working.

## How to build / run it

1. Clone/fork Microbot: `git clone https://github.com/chsami/Microbot`.
2. Copy the package `src/main/java/net/runelite/client/plugins/microbot/quinnmain/` from here into your
   fork at `runelite-client/src/main/java/net/runelite/client/plugins/microbot/quinnmain/`.
3. Build the client (the fork's Gradle build) and run it.
4. In the RuneLite plugin list, enable **“Quinn Main”**, set the woodcutting config, and start.
5. Fix any compiler errors the `MicrobotGameApi` mapping surfaces (see the file's header note).

## What's here now (increment 1 — the foundation + proof)

| File | Role |
|---|---|
| `game/GameApi.java` | **The facade.** Client-neutral interface all bot logic talks to. The seam that makes the port possible. |
| `game/MicrobotGameApi.java` | Microbot implementation of the facade (delegates to `Rs2*`). Slice methods implemented; rest are honest `TODO` throws. |
| `slice/WoodcuttingSlice.java` | **Vertical-slice proof** — a real woodcutting loop with zero client-specific code. The shape every ported trainer takes. |
| `QuinnMainPlugin.java` | RuneLite plugin entry (`@PluginDescriptor`). |
| `QuinnMainScript.java` | The scheduled loop (Microbot `Script`). |
| `QuinnMainConfig.java` | RuneLite config for the slice. |
| `PORT_PLAN.md` | The file-by-file map + ordering to port the remaining ~100 files. |

## The idea in one picture

```
   trainers / quests / combat / money          ← ported logic (coords, tables, state machines)
   (import ONLY game.GameApi)                     — client-neutral, carries over from OSRS-Main
                    │
              game.GameApi   ← the facade (the seam)
                    │
        ┌───────────┴────────────┐
   MicrobotGameApi          DreamBotGameApi
   (Rs2* — here)            (Nav/Bank/… — retrofit into OSRS-Main)
```

Prove `WoodcuttingSlice` runs on Microbot → the pattern holds → the rest is repetition. See
`PORT_PLAN.md` for what carries over vs. what gets rewritten, and in what order.
