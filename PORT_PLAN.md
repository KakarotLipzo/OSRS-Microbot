# Port plan — OSRS-Main (DreamBot) → OSRS-Micro (Microbot)

The whole port hinges on one move: **route all game access through `game.GameApi`** so the logic stops
importing `org.dreambot.*`. Once that seam exists, each file falls into one of four buckets. There are
~110 source files and ~691 direct DreamBot call-sites in OSRS-Main; this maps them.

## The four buckets

| Bucket | Meaning | Effort |
|---|---|---|
| **A — Logic (carries over)** | Coords, item/NPC ids, tables, thresholds, state machines. Refactor to call `GameApi` instead of DreamBot; the *decisions* are unchanged. | Low (mechanical) |
| **B — Facade (becomes GameApi)** | The `core/` hardware helpers that ARE the DreamBot↔game bridge. Their behaviour becomes `GameApi` methods; callers switch to the facade. | Medium |
| **C — UI (rewrite for RuneLite)** | Swing HUD + Control Panel → RuneLite overlay + config panel. Data feeds carry over; rendering is new. | High (separate track) |
| **D — Infra (replace/drop)** | Script lifecycle, DreamBot canvas/widgets, launcher, watchdog. Replaced by the plugin shell (done) + Microbot equivalents. | Medium |

## File-by-file

### B — Facade (`core/`) → fold into `GameApi` / facade-backed helpers
| OSRS-Main | Disposition |
|---|---|
| `core/Nav.java` | → `GameApi.walkTo/arrived/distanceTo/openNearestBank`. |
| `core/Gather.java` (mineOre/pickFrom/smelt/mill/cook) | → facade-backed helper calling `nearestObject/interactObject/waitUntil/invContains`. Keep the class; swap its innards. |
| `core/MakeInterface.java` (widget 270/anvil 312) | → `GameApi.interactWidget` + a Microbot make-screen helper. **Verify widget ids on RuneLite.** |
| `core/GeManager.java` / `core/GeBuy.java` | → `GameApi.geBuy/geSell/geCollectAll/openGe`. Re-verify buy/sell signatures (id vs name). |
| `core/BankMemory.java` | → `GameApi` bank reads. |
| `core/Provision.java` / `core/SupplyBuy.java` / `core/SupplyShop.java` | → `GameApi.shopBuy` + geBuy fallback. |
| `core/PriceLookup.java` | **Carries over UNCHANGED** — pure HTTP to the wiki API, zero DreamBot. |
| `core/LampClaimer.java` | → widget/interface facade calls. |

### A — Logic (carries over; refactor imports to `GameApi`)
| Group | OSRS-Main files | Notes |
|---|---|---|
| Skilling trainers | `tasks/mining/*`, `woodcutting/*`, `fishing/*`, `cooking/*`, `crafting/*`, `firemaking/*`, `smithing/*`, `runecraft/*` | Tables/areas/methods unchanged. Each becomes a `*Slice`-shaped class on the facade. Woodcutting is done as the proof. |
| Combat | `tasks/combat/*` (MonsterType, MeleeCombat, CombatEngine, RangedCombat, MagicCombat, GearManager, LootManager, FoodManager, PotionManager, PrayerManager, Dungeon, DungeonNav, safespots) | Biggest logic block. MonsterType tables + tiles carry over; combat/interact/prayer/loot verbs go through the facade (add `attackNpc`, `activatePrayer`, loot methods to `GameApi`). |
| Quests | `tasks/quests/*` + `core/QuestEngine.java` | State machines carry over. Needs `GameApi` dialogue option strings + selection (currently `TODO`). Quest varp reads → `Microbot.getVarbitValue`/config. `StrongholdSolver` keyword matcher needs `dialogueOptions()`. |
| Money | `money/*` (MoneyEngine, MoneyMethod, FlipMethod, FlipMath, FlipQuote, ProcessingMethod, SellLootMethod, CuratedWatchlist, PriceLookup layer) | FlipMath + price layer carry over; GE actions via facade. |
| Engines/registries | `core/GoalEngine`, `PlanEngine`, `ActivityRegistry`, `AreaRegistry`, `SkillCatalog`, `SkillOutput`, `XpTracker`, `RunLog`, `AccountState`, `TaskContext` | Mostly plain Java. `AccountState` (skill levels) → `GameApi.skillLevel`. `XpTracker`/`RunLog` → `GameApi.skillXp`. |
| Config | `core/ConfigStore.java` | Properties I/O is plain Java and carries over; swap DreamBot `Logger` for `Microbot.log` and re-point the config file path. |
| Anti-ban | `antiban/WorldGuard`, `ContentionHopper`, `DeathRecovery`, `LevelUpWatcher` | Logic carries over; world-hop/idle verbs → facade + Microbot antiban (`Rs2Antiban`, `Rs2Random`). |

### C — UI (rewrite for RuneLite)
| OSRS-Main | Disposition |
|---|---|
| `ui/Hud.java` + `HudTheme`, `HudSettingsPanel`, `XpPanel`, `BankPanel`, `SkillIcons` | → a **RuneLite overlay** (`Overlay`/`OverlayManager`). Data feeds carry over; Java2D drawing largely reusable (both are `Graphics2D`), but anchoring moves to RuneLite's overlay system, not the chat-box widget. |
| `ui/ControlPanel.java` + `GoalsTab/PlanTab/LibraryTab/SetupTab/BreaksTab/FlipTab`, `Nocturne` | → RuneLite **config panel** (`PluginPanel`) or a standalone Swing window kept as-is. The `UI_SPEC_FOR_REDESIGN.md` in OSRS-Main is the inventory to rebuild from. |
| `antiban/BreakManager`, `Humanizer` | Break scheduling logic carries over; login/logout + timing verbs → Microbot antiban. |

### D — Infra (replace/drop)
| OSRS-Main | Disposition |
|---|---|
| `QuinnMain.java` (`extends AbstractScript`, `onLoop`) | → **`QuinnMainPlugin` + `QuinnMainScript`** (done). Move the loop body onto the ported engines. |
| `Client.getCanvas()`, `Widgets.*` gotchas, `AbstractScript` | Dropped — RuneLite/Microbot equivalents. |
| `automation/*` (StatusFile, ProgressMonitor, LoginWatch, Automation) + `tools/watchdog/*` | The PowerShell watchdog is external and DreamBot-launcher-specific. Re-point later (status file + log dir + launch command all change) or run the Microbot client manually first. Out of scope until the bot itself runs. |

## Suggested order

1. **✅ Increment 1 (done):** facade + Microbot adapter core + woodcutting slice + plugin shell. **Compile-verify in a fork** and run the slice — this validates the whole chain.
2. **Grow `GameApi`** as needed and port the rest of the **skilling trainers** (they need the least new facade surface). Add `Gather` helpers on the facade.
3. **ConfigStore + engines + registries** (GoalEngine/PlanEngine) so the bot runs the weighted-goal loop, not just one slice.
4. **Combat** (add attack/prayer/loot to the facade). Largest logic block.
5. **Money + GE** (verify GE signatures) and **PriceLookup** (drops in unchanged).
6. **Quests** (needs dialogue-option facade methods) + `StrongholdSolver`.
7. **Anti-ban / breaks** on Microbot's antiban.
8. **UI** — overlay first (glanceable status), config panel second.
9. **Watchdog / automation** re-point last.

## Guardrails (carried from OSRS-Main)

- **Honesty rule:** no control/handle that looks live but does nothing. Unported facade methods throw, they don't no-op.
- **quest-helper repo first, wiki second** for any coord/id.
- Same OSRS ids/tiles → constants carry over verbatim; only the *access* changes.
- Keep logic files free of `Rs2*`/`org.dreambot.*` imports — that discipline is what keeps both clients buildable from one logic base.
