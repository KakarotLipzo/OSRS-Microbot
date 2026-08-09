package net.runelite.client.plugins.microbot.quinnmain.bot.core;

import net.runelite.client.plugins.microbot.quinnmain.game.Sk;
import net.runelite.client.plugins.microbot.quinnmain.game.Pos;
import net.runelite.client.plugins.microbot.quinnmain.bot.util.Log;


import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import java.util.Properties;

/**
 * Per-account, on-disk settings: each skill's <b>target level</b> (default 99) and
 * <b>selection weight</b> (default 50), the cumulative lifetime playtime, and the QoL
 * manual-override flag. Persisted so it survives Stop&rarr;Start and can be edited later
 * by the GUI. Stored at {@code ~/DreamBot/QuinnMain/<username>.properties}.
 */
public class ConfigStore {

    public static final int DEFAULT_TARGET = 99;
    public static final int DEFAULT_WEIGHT = 50;

    private final Map<Sk, Integer> targets = new EnumMap<>(Sk.class);
    private final Map<Sk, Integer> weights = new EnumMap<>(Sk.class);
    private long lifetimePlaytimeMs = 0;

    // Economy settings (editable later via the GUI).
    private int goldReserve = 10_000;      // gp to keep untouched when buying gear (e.g. axes)
    private boolean geBuyAxes = true;      // allow dedicated GE trips to buy affordable axe upgrades
    private boolean geBuySupplies = true;  // allow GE trips to buy tools/consumables (nets, feathers…)
    private boolean fishingPowerDrop = false; // Fishing: drop fish for max XP instead of banking them
    private boolean fishingKaramja = false;   // Fishing: allow the Karamja tier (lobster/tuna/sword) — needs the boat; off until verified
    private boolean smithingBuyBars = false;  // Smithing: GE-buy bars to anvil the best affordable/level tier (anvil-only, no coal/furnace)
    private boolean flipEnabled = false;      // Money: GE flipping (buy low / sell high) — OFF by default (needs capital, has market risk)
    private String flipMode = "CONSERVATIVE"; // CONSERVATIVE (break-even stop-loss, tight caps) / BALANCED (accepts small losses, bigger caps)
    private boolean deathRecovery = true;     // after a death, run back and reclaim items from the grave
    private boolean miningPowerDrop = false;  // Mining: drop ore for max XP instead of banking it
    private String miningOre = "AUTO";        // which ore to mine: AUTO (best XP) / COPPER / TIN / IRON
    private int miningQuantity = 0;           // stop mining once we have this many of the chosen ore (0 = ∞)
    // Per-skill continuous-training cap is randomised in [min, max] active minutes each time a skill is
    // picked, so switches feel human rather than a fixed 4h clock. Defaults: 30 min .. 240 min (4h).
    private int skillCapMinutesMin = 30;
    private int skillCapMinutesMax = 240;

    // Quests: a global on/off, plus a per-quest flag (quest.<KEY>.enabled) so the user can choose which
    // quests to run. A quest runs only when BOTH the global switch and its own flag are on (default true).
    private boolean questsEnabled = true;
    private final java.util.Map<String, Boolean> questEnabled = new java.util.HashMap<>();
    /** Durable per-quest sub-progress, persisted as {@code questprogress.<KEY>=<int>}. For quests whose
     *  in-game varp doesn't track intermediate steps (e.g. Pirate's Treasure's crate smuggle), so a
     *  Stop/Start doesn't lose the milestone and redo it. Default 0. */
    private final java.util.Map<String, Integer> questProgress = new java.util.HashMap<>();
    // Quests run as random sessions interleaved with skilling, not as a block before it: a gap of
    // [gapMin, gapMax] active minutes between sessions, each session lasting [sessionMin, sessionMax].
    private int questGapMinutesMin = 45;
    private int questGapMinutesMax = 150;
    private int questSessionMinutesMin = 25;
    private int questSessionMinutesMax = 60;

    // Money making: a master switch, plus a per-method flag (money.<KEY>.enabled). A method runs only
    // when BOTH are on (default true), the same shape as quests.
    private boolean moneyEnabled = true;
    private final java.util.Map<String, Boolean> moneyMethodEnabled = new java.util.HashMap<>();

    // Per-skill EXCLUDED activities (skill.<S>.excluded = CSV of ActivityRegistry keys). Absent = the
    // activity is enabled, so an untouched config behaves exactly as before. Trainers filter their
    // chooser against this, which is what makes the control panel's activity chips actually bite.
    private final java.util.Map<Sk, java.util.Set<String>> excludedActivities = new EnumMap<>(Sk.class);

    // Per-skill "collect N then move on" target (skill.<S>.quantity, 0 = unlimited). Generalises the
    // older mining.quantity, which is still read/written for back-compat.
    private final java.util.Map<Sk, Integer> skillQuantity = new EnumMap<>(Sk.class);

    // Per-skill, per-activity EXCLUDED areas (area.<S>.<ACTIVITY>.excluded = CSV of AreaRegistry keys).
    // Absent = every area of that activity is enabled, so an untouched config behaves as before. The
    // area picker writes this; MiningTask reads it to pick where to mine.
    private final java.util.Map<Sk, java.util.Map<String, java.util.Set<String>>> excludedAreas =
            new EnumMap<>(Sk.class);

    // Per-skill EQUIPMENT LOADOUT (gear.<S>.<SLOT> = itemId). Sparse: only slots the user picked are
    // stored, so a skill with no loadout keeps the auto-gear behaviour untouched (opt-in). Read by the
    // combat gear managers, which equip the user's items over the auto-pick only when a slot is set.
    private final java.util.Map<Sk, java.util.Map<GearSlot, Integer>> gearLoadout =
            new EnumMap<>(Sk.class);

    // The ordered PLAN queue (design §2.3): runs top to bottom before the level goals. `planEnabled`
    // off preserves the steps but falls back to goals. Steps persist; `planNextId` keeps ids stable.
    private boolean planEnabled = false;
    private final java.util.List<PlanStep> plan = new java.util.ArrayList<>();
    private int planNextId = 1;
    // Transient (NOT persisted): while the PlanEngine runs a skill step it pins the trainer to one
    // activity by narrowing isActivityEnabled to just this (skill, activity). Cleared between steps.
    private transient Sk planFocusSkill;
    private transient String planFocusActivity;
    // Transient: true while the PlanEngine is driving a step. Loot caps (below) only bite while this
    // is set — in plain Goals/weighted mode looting behaves as the Setup thresholds say.
    private transient boolean planActive;

    // Per-item LOOT CAPS (lootcap.<itemId> = maxQty). While following the plan, stop looting an item
    // once we already hold this many (inventory + bank). Sparse: only capped items are stored, so an
    // empty map means "no caps" and looting is unchanged.
    private final java.util.Map<Integer, Integer> lootCaps = new java.util.TreeMap<>();

    // Breaks (design §2.6). The master on/off is breaksEnabled above; these shape the schedule. All
    // default so an untouched config behaves like before: no rules → BreakManager's built-in timings;
    // no daily budget / active-hours / sleep / days-off.
    private final java.util.List<BreakRule> breakRules = new java.util.ArrayList<>();
    private int breakNextRuleId = 1;
    private boolean breakLogout = true;       // a break logs out (vs a stay-online AFK for the rest duration)
    private boolean breakBankFirst = false;   // deposit at the nearest bank before a logout break
    private boolean breakResumeSame = true;   // keep the same skill after a break (off = re-roll a new one)
    private int breakHoursPerDay = 0;         // active-play budget per 24h (0 = unlimited)
    private int breakVaryPct = 0;             // vary the daily budget ± this %
    private int breakMaxSessionHours = 0;     // force a long break after this unbroken session (0 = off)
    private String breakActiveFrom = "";      // "HH:MM" active-hours window start (empty = always allowed)
    private String breakActiveTo = "";        // "HH:MM" window end
    private boolean breakSleepOn = false;     // one long offline stretch per 24h
    private int breakSleepHours = 7;
    private int breakDaysOff = 0;             // random rest days per week (0-6)
    private boolean breakStopWhenDone = true; // daily budget spent: stop the script (true) vs wait for next day

    // Global automation switches surfaced by the control panel's Settings tab.
    private boolean antibanEnabled = true;   // humanised timings / micro-behaviours
    private boolean breaksEnabled = true;    // scheduled logout breaks + micro-AFK
    private int stopAfterHours = 0;          // stop the script after N hours of session playtime (0 = never)
    // Layer-3 automation instrumentation (AUTOMATION_PLAN.md §3.4). OFF by default: the script runs
    // fully standalone unless this is set, at which point the heartbeat + ban/lock detection engage.
    private boolean instrumentationEnabled = false;
    private int hudOpacity = 96;             // in-canvas HUD opacity, 40..100
    // Where the HUD sits: CHAT (over the chatbox), TOP (top-left of the canvas), FLOAT (just above
    // the chatbox, leaving the game chat readable).
    private String hudDock = "CHAT";
    private boolean hudShowTargets = true;   // "/target" labels in the skill grid

    // Consumables: which foods / potions the bot may use, and the thresholds that trigger them.
    // Stored as EXCLUSIONS (food.excluded / potion.excluded) so an untouched config keeps the old
    // "use whatever's best" behaviour and new catalogue entries are enabled by default.
    private final java.util.Set<String> excludedFood = new java.util.HashSet<>();
    private final java.util.Set<String> excludedPotions = new java.util.HashSet<>();
    private int eatAtPercent = 50;        // eat below this HP %
    private int prayerSipBelow = 20;      // sip a prayer restore below this many prayer points
    private boolean potionReboost = true; // re-sip a combat boost once it wears off

    // Combat settings.
    private String combatWeaponType = "SLASH"; // melee attack type to gear for: STAB / SLASH / CRUSH
    private boolean geBuyGear = true;          // allow GE trips to buy affordable weapon/armor upgrades
    private boolean lootEnabled = true;        // pick up drops while fighting
    private int lootWealthGate = 100_000;      // bank value (gp) below which we loot everything; above → valuable-only
    private int lootValuableMinValue = 1_000;  // min per-item GE value to bother looting (always applies)
    // Opt-in: below lootWealthGate, take EVERYTHING. Off by default — on a poor account it hoovered up
    // jugs/pots/bowls, which looks botlike and starved the food restock that runs after looting.
    private boolean lootAllWhenPoor = false;
    private boolean combatUsePotions = false;  // sip combat-boost (and P2P prayer-restore) potions
    private boolean prayerEnabled = true;      // use prayers in combat at all (master switch)
    private boolean prayerUseProtection = true;// activate a protection prayer vs dangerous monsters
    private boolean prayerUseOffensive = false;// activate offensive stat prayers (drains prayer fast)
    private boolean combatSafeSpot = true;     // ranged/magic: fight from a monster's safe tile when one is defined
    private boolean combatUseDungeons = false; // allow underground (dungeon) combat spots — off until their routes are confirmed
    private String craftingMethod = "AUTO"; // AUTO (best F2P job by level, all methods) / GOLD_JEWELRY / LEATHER / GEMS
    private String firemakingArea = "VARROCK_WEST"; // VARROCK_WEST / GRAND_EXCHANGE / FALADOR
    // Which shape the anvil hammers out: AUTO (most bars per action) or a shape key (PLATEBODY…).
    // Shape doesn't change XP rate — XP is per bar, set by the bar's tier — so this is for making a
    // specific item to wear/sell, not for training speed.
    private String anvilShape = "AUTO";

    // QoL override: when on, the goal engine ignores its weighted plan and trains qolSkill only.
    private boolean qolOverride = false;
    private Sk qolSkill = null;

    // Account safety (design §3.6): which world TYPES / REGIONS the bot may join. Allowlist semantics —
    // an unticked type is never entered and is hopped away from if landed on. Only the types the client
    // can actually classify are modelled (Members/Free, the 5 total-level tiers, PvP, High-risk);
    // Deadman/LMS/etc. have no client getter and population isn't exposed, so those controls are omitted
    // rather than shipped inert. Defaults: the 7 normal world types, all regions, follow-hop off.
    private final java.util.Set<String> worldTypes = new java.util.HashSet<>(java.util.Arrays.asList(
            "MEMBERS", "FREE", "T500", "T750", "T1250", "T1500", "T2000"));
    private final java.util.Set<String> worldRegions = new java.util.HashSet<>(java.util.Arrays.asList(
            "UK", "GERMANY", "USA_EAST", "USA_WEST", "AUSTRALIA"));
    private boolean hopOnFollow = false;   // leave the world after sustained attention from a player
    private boolean busyHop = false;       // hop worlds when a COMPETITIVE spot is too busy (contested resource)
    private int busyHopMinPlayers = 3;     // other players within a few tiles that counts as "too busy"

    // Membership (design §3.6): buy a bond from the GE when membership runs low. Gated by money-making
    // being enabled (the same HOLD that blocks every GE spend), and by the price cap + cash reserve.
    private boolean bondBuy = false;
    private int bondBuyBelowDays = 3;
    private long bondMaxPriceGp = 8_500_000L;

    // Withdraw-per-trip (design §3.6): per-item portions/flasks pulled from the bank each trip. Sparse —
    // absent means "use the manager's default". foodQty.<key> = portions, potQty.<key> = flasks.
    private final java.util.Map<String, Integer> foodQty = new java.util.HashMap<>();
    private final java.util.Map<String, Integer> potionQty = new java.util.HashMap<>();

    private File file;
    private boolean loaded = false;

    public ConfigStore() {
        for (Sk s : SkillCatalog.TRAINABLE) {
            targets.put(s, DEFAULT_TARGET);
            weights.put(s, DEFAULT_WEIGHT);
        }
    }

    /** Load this account's config file once the username is known. Idempotent. */
    public synchronized void loadFor(String username) {
        if (loaded) return;
        String u = (username == null || username.isEmpty())
                ? "default" : username.replaceAll("[^a-zA-Z0-9_-]", "_");
        File dir = new File(System.getProperty("user.home"),
                "DreamBot" + File.separator + "QuinnMain");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        file = new File(dir, u + ".properties");

        if (file.exists()) {
            Properties p = new Properties();
            try (FileInputStream in = new FileInputStream(file)) {
                p.load(in);
            } catch (IOException e) {
                Log.log("[config] load failed: " + e);
            }
            for (Sk s : SkillCatalog.TRAINABLE) {
                targets.put(s, parseInt(p.getProperty("skill." + s.name() + ".target"), DEFAULT_TARGET));
                weights.put(s, parseInt(p.getProperty("skill." + s.name() + ".weight"), DEFAULT_WEIGHT));
            }
            lifetimePlaytimeMs = parseLong(p.getProperty("playtime.lifetimeMs"), 0L);
            goldReserve = parseInt(p.getProperty("gold.reserve"), 10_000);
            geBuyAxes = Boolean.parseBoolean(p.getProperty("ge.buyAxes", "true"));
            geBuySupplies = Boolean.parseBoolean(p.getProperty("ge.buySupplies", "true"));
            fishingPowerDrop = Boolean.parseBoolean(p.getProperty("fishing.powerDrop", "false"));
            fishingKaramja = Boolean.parseBoolean(p.getProperty("fishing.karamja", "false"));
            smithingBuyBars = Boolean.parseBoolean(p.getProperty("smithing.buyBars", "false"));
            flipEnabled = Boolean.parseBoolean(p.getProperty("flip.enabled", "false"));
            flipMode = p.getProperty("flip.mode", "CONSERVATIVE");
            deathRecovery = Boolean.parseBoolean(p.getProperty("death.recovery", "true"));
            miningPowerDrop = Boolean.parseBoolean(p.getProperty("mining.powerDrop", "false"));
            miningOre = p.getProperty("mining.ore", "AUTO");
            miningQuantity = parseInt(p.getProperty("mining.quantity"), 0);
            questsEnabled = Boolean.parseBoolean(p.getProperty("quest.enabled", "true"));
            // Reload any per-quest flags present in the file (quest.<KEY>.enabled, excluding the global one).
            questEnabled.clear();
            for (String pn : p.stringPropertyNames()) {
                if (pn.startsWith("quest.") && pn.endsWith(".enabled") && !pn.equals("quest.enabled")) {
                    String k = pn.substring("quest.".length(), pn.length() - ".enabled".length());
                    if (!k.isEmpty()) questEnabled.put(k, Boolean.parseBoolean(p.getProperty(pn)));
                }
            }
            // Durable per-quest sub-progress (questprogress.<KEY>=<int>).
            questProgress.clear();
            for (String pn : p.stringPropertyNames()) {
                if (pn.startsWith("questprogress.")) {
                    String k = pn.substring("questprogress.".length());
                    if (!k.isEmpty()) questProgress.put(k, parseInt(p.getProperty(pn), 0));
                }
            }
            questGapMinutesMin = parseInt(p.getProperty("quest.gapMinutesMin"), 45);
            questGapMinutesMax = parseInt(p.getProperty("quest.gapMinutesMax"), 150);
            questSessionMinutesMin = parseInt(p.getProperty("quest.sessionMinutesMin"), 25);
            questSessionMinutesMax = parseInt(p.getProperty("quest.sessionMinutesMax"), 60);
            moneyEnabled = Boolean.parseBoolean(p.getProperty("money.enabled", "true"));
            moneyMethodEnabled.clear();
            for (String pn : p.stringPropertyNames()) {
                if (pn.startsWith("money.") && pn.endsWith(".enabled") && !pn.equals("money.enabled")) {
                    String k = pn.substring("money.".length(), pn.length() - ".enabled".length());
                    if (!k.isEmpty()) moneyMethodEnabled.put(k, Boolean.parseBoolean(p.getProperty(pn)));
                }
            }
            // Back-compat: an old single skill.capMinutes seeds the max if the new keys are absent.
            int legacyCap = parseInt(p.getProperty("skill.capMinutes"), 240);
            skillCapMinutesMin = parseInt(p.getProperty("skill.capMinutesMin"), 30);
            skillCapMinutesMax = parseInt(p.getProperty("skill.capMinutesMax"), legacyCap);
            combatWeaponType = p.getProperty("combat.weaponType", "SLASH");
            geBuyGear = Boolean.parseBoolean(p.getProperty("ge.buyGear", "true"));
            lootEnabled = Boolean.parseBoolean(p.getProperty("loot.enabled", "true"));
            lootWealthGate = parseInt(p.getProperty("loot.wealthGate"), 100_000);
            lootValuableMinValue = parseInt(p.getProperty("loot.valuableMinValue"), 1_000);
            lootAllWhenPoor = Boolean.parseBoolean(p.getProperty("loot.allWhenPoor", "false"));
            combatUsePotions = Boolean.parseBoolean(p.getProperty("combat.usePotions", "false"));
            prayerEnabled = Boolean.parseBoolean(p.getProperty("prayer.enabled", "true"));
            prayerUseProtection = Boolean.parseBoolean(p.getProperty("prayer.useProtection", "true"));
            prayerUseOffensive = Boolean.parseBoolean(p.getProperty("prayer.useOffensive", "false"));
            combatSafeSpot = Boolean.parseBoolean(p.getProperty("combat.safeSpot", "true"));
            combatUseDungeons = Boolean.parseBoolean(p.getProperty("combat.useDungeons", "false"));
            craftingMethod = p.getProperty("crafting.method", "AUTO");
            firemakingArea = p.getProperty("firemaking.area", "VARROCK_WEST");
            anvilShape = p.getProperty("smithing.anvilShape", "AUTO");
            antibanEnabled = Boolean.parseBoolean(p.getProperty("antiban.enabled", "true"));
            breaksEnabled = Boolean.parseBoolean(p.getProperty("breaks.enabled", "true"));
            stopAfterHours = parseInt(p.getProperty("session.stopAfterHours"), 0);
            instrumentationEnabled = Boolean.parseBoolean(p.getProperty("instrumentation.enabled", "false"));
            hudOpacity = clamp(parseInt(p.getProperty("hud.opacity"), 96), 40, 100);
            hudDock = p.getProperty("hud.dock", "CHAT");
            loadCsv(p.getProperty("food.excluded", ""), excludedFood);
            loadCsv(p.getProperty("potion.excluded", ""), excludedPotions);
            eatAtPercent = clamp(parseInt(p.getProperty("food.eatAtPercent"), 50), 5, 95);
            prayerSipBelow = Math.max(1, parseInt(p.getProperty("potion.prayerSipBelow"), 20));
            potionReboost = Boolean.parseBoolean(p.getProperty("potion.reboost", "true"));
            hudShowTargets = Boolean.parseBoolean(p.getProperty("hud.showTargets", "true"));
            excludedActivities.clear();
            skillQuantity.clear();
            for (Sk s : SkillCatalog.TRAINABLE) {
                String csv = p.getProperty("skill." + s.name() + ".excluded", "");
                if (!csv.trim().isEmpty()) {
                    java.util.Set<String> set = new java.util.HashSet<>();
                    for (String k : csv.split(",")) if (!k.trim().isEmpty()) set.add(k.trim());
                    if (!set.isEmpty()) excludedActivities.put(s, set);
                }
                int q = parseInt(p.getProperty("skill." + s.name() + ".quantity"), 0);
                if (q > 0) skillQuantity.put(s, q);
            }
            // Per-activity excluded areas: area.<S>.<ACTIVITY>.excluded = CSV.
            excludedAreas.clear();
            for (String pn : p.stringPropertyNames()) {
                if (!pn.startsWith("area.") || !pn.endsWith(".excluded")) continue;
                String mid = pn.substring("area.".length(), pn.length() - ".excluded".length());
                int dot = mid.indexOf('.');
                if (dot <= 0 || dot >= mid.length() - 1) continue;
                Sk sk = safeSkill(mid.substring(0, dot));
                String act = mid.substring(dot + 1);
                if (sk == null || act.isEmpty()) continue;
                java.util.Set<String> set = new java.util.HashSet<>();
                for (String k : p.getProperty(pn, "").split(",")) if (!k.trim().isEmpty()) set.add(k.trim());
                if (!set.isEmpty()) excludedAreas.computeIfAbsent(sk, k -> new java.util.HashMap<>()).put(act, set);
            }
            // Per-skill equipment loadout: gear.<S>.<SLOT> = itemId.
            gearLoadout.clear();
            for (String pn : p.stringPropertyNames()) {
                if (!pn.startsWith("gear.")) continue;
                String mid = pn.substring("gear.".length());
                int dot = mid.indexOf('.');
                if (dot <= 0 || dot >= mid.length() - 1) continue;
                Sk sk = safeSkill(mid.substring(0, dot));
                GearSlot slot = GearSlot.parse(mid.substring(dot + 1));
                if (sk == null || slot == null) continue;
                int itemId = parseInt(p.getProperty(pn), 0);
                if (itemId > 0) gearLoadout.computeIfAbsent(sk, k -> new EnumMap<>(GearSlot.class)).put(slot, itemId);
            }
            // The ordered plan queue: plan.enabled + plan.count + plan.<i>.{kind,name,activity,cond,value,elapsed}.
            plan.clear();
            planEnabled = Boolean.parseBoolean(p.getProperty("plan.enabled", "false"));
            planNextId = parseInt(p.getProperty("plan.nextId"), 1);
            int planCount = parseInt(p.getProperty("plan.count"), 0);
            for (int i = 0; i < planCount; i++) {
                String pre = "plan." + i + ".";
                PlanStep.Kind kind = parsePlanKind(p.getProperty(pre + "kind"));
                PlanStep.Cond cond = parsePlanCond(p.getProperty(pre + "cond"));
                String nm = p.getProperty(pre + "name", "");
                if (kind == null || cond == null || nm.isEmpty()) continue;
                int id = parseInt(p.getProperty(pre + "id"), planNextId + i);
                String act = p.getProperty(pre + "activity", "");
                PlanStep st = new PlanStep(id, kind, nm, act.isEmpty() ? null : act, cond,
                        parseInt(p.getProperty(pre + "value"), 0));
                st.elapsedMs = parseLong(p.getProperty(pre + "elapsed"), 0L);
                plan.add(st);
                if (id >= planNextId) planNextId = id + 1;
            }
            // Breaks: rule list + the schedule scalars.
            breakRules.clear();
            breakNextRuleId = parseInt(p.getProperty("breaks.nextRuleId"), 1);
            int ruleCount = parseInt(p.getProperty("breaks.rule.count"), 0);
            for (int i = 0; i < ruleCount; i++) {
                String pre = "breaks.rule." + i + ".";
                int id = parseInt(p.getProperty(pre + "id"), breakNextRuleId + i);
                int play = parseInt(p.getProperty(pre + "play"), 45);
                int rest = parseInt(p.getProperty(pre + "rest"), 7);
                int jit = parseInt(p.getProperty(pre + "jitter"), 20);
                boolean on = Boolean.parseBoolean(p.getProperty(pre + "on", "true"));
                breakRules.add(new BreakRule(id, play, rest, jit, on));
                if (id >= breakNextRuleId) breakNextRuleId = id + 1;
            }
            breakLogout = Boolean.parseBoolean(p.getProperty("breaks.behaviour.logout", "true"));
            breakBankFirst = Boolean.parseBoolean(p.getProperty("breaks.behaviour.bank", "false"));
            breakResumeSame = Boolean.parseBoolean(p.getProperty("breaks.behaviour.resume", "true"));
            breakHoursPerDay = parseInt(p.getProperty("breaks.hoursPerDay"), 0);
            breakVaryPct = clamp(parseInt(p.getProperty("breaks.varyPct"), 0), 0, 60);
            breakMaxSessionHours = parseInt(p.getProperty("breaks.maxSessionHours"), 0);
            breakActiveFrom = p.getProperty("breaks.activeFrom", "");
            breakActiveTo = p.getProperty("breaks.activeTo", "");
            breakSleepOn = Boolean.parseBoolean(p.getProperty("breaks.sleepOn", "false"));
            breakSleepHours = clamp(parseInt(p.getProperty("breaks.sleepHours"), 7), 1, 16);
            breakDaysOff = clamp(parseInt(p.getProperty("breaks.daysOff"), 0), 0, 6);
            breakStopWhenDone = Boolean.parseBoolean(p.getProperty("breaks.stopWhenDone", "true"));
            // Per-item loot caps: lootcap.<itemId> = maxQty.
            lootCaps.clear();
            for (String pn : p.stringPropertyNames()) {
                if (!pn.startsWith("lootcap.")) continue;
                int id = parseInt(pn.substring("lootcap.".length()), -1);
                int qty = parseInt(p.getProperty(pn), 0);
                if (id > 0 && qty > 0) lootCaps.put(id, qty);
            }
            // Back-compat: the old mining.quantity seeds Mining's generic quantity if that's unset.
            if (miningQuantity > 0 && !skillQuantity.containsKey(Sk.MINING)) {
                skillQuantity.put(Sk.MINING, miningQuantity);
            }
            qolOverride = Boolean.parseBoolean(p.getProperty("qol.override", "false"));
            qolSkill = safeSkill(p.getProperty("qol.skill", ""));
            // Account safety — only overwrite the defaults if the key is present (absent = keep defaults).
            String wtypes = p.getProperty("worlds.types");
            if (wtypes != null) { worldTypes.clear(); for (String k : wtypes.split(",")) if (!k.trim().isEmpty()) worldTypes.add(k.trim()); }
            String wregs = p.getProperty("worlds.regions");
            if (wregs != null) { worldRegions.clear(); for (String k : wregs.split(",")) if (!k.trim().isEmpty()) worldRegions.add(k.trim()); }
            hopOnFollow = Boolean.parseBoolean(p.getProperty("worlds.hopOnFollow", "false"));
            busyHop = Boolean.parseBoolean(p.getProperty("worlds.busyHop", "false"));
            busyHopMinPlayers = parseInt(p.getProperty("worlds.busyHopMinPlayers"), 3);
            bondBuy = Boolean.parseBoolean(p.getProperty("bond.buy", "false"));
            bondBuyBelowDays = parseInt(p.getProperty("bond.buyBelowDays"), 3);
            bondMaxPriceGp = parseLong(p.getProperty("bond.maxPriceGp"), 8_500_000L);
            foodQty.clear();
            potionQty.clear();
            for (String pn : p.stringPropertyNames()) {
                if (pn.startsWith("foodQty.")) { int q = parseInt(p.getProperty(pn), 0); if (q > 0) foodQty.put(pn.substring("foodQty.".length()), q); }
                else if (pn.startsWith("potQty.")) { int q = parseInt(p.getProperty(pn), 0); if (q > 0) potionQty.put(pn.substring("potQty.".length()), q); }
            }
            Log.log("[config] loaded " + file.getName()
                    + " (lifetime playtime " + (lifetimePlaytimeMs / 60000) + " min)");
        } else {
            Log.log("[config] new config for '" + u + "' — writing defaults (target 99, weight 50).");
        }
        loaded = true;
        save();
    }

    /** Snapshot every persisted key to a Properties object — what {@link #save()} writes to disk. */
    public synchronized Properties toProperties() {
        Properties p = new Properties();
        for (Sk s : SkillCatalog.TRAINABLE) {
            p.setProperty("skill." + s.name() + ".target", String.valueOf(targets.get(s)));
            p.setProperty("skill." + s.name() + ".weight", String.valueOf(weights.get(s)));
        }
        p.setProperty("playtime.lifetimeMs", String.valueOf(lifetimePlaytimeMs));
        p.setProperty("gold.reserve", String.valueOf(goldReserve));
        p.setProperty("ge.buyAxes", String.valueOf(geBuyAxes));
        p.setProperty("ge.buySupplies", String.valueOf(geBuySupplies));
        p.setProperty("fishing.powerDrop", String.valueOf(fishingPowerDrop));
        p.setProperty("fishing.karamja", String.valueOf(fishingKaramja));
        p.setProperty("smithing.buyBars", String.valueOf(smithingBuyBars));
        p.setProperty("flip.enabled", String.valueOf(flipEnabled));
        p.setProperty("flip.mode", flipMode);
        p.setProperty("death.recovery", String.valueOf(deathRecovery));
        p.setProperty("mining.powerDrop", String.valueOf(miningPowerDrop));
        p.setProperty("mining.ore", miningOre);
        p.setProperty("mining.quantity", String.valueOf(miningQuantity));
        p.setProperty("quest.enabled", String.valueOf(questsEnabled));
        for (java.util.Map.Entry<String, Boolean> e : questEnabled.entrySet()) {
            p.setProperty("quest." + e.getKey() + ".enabled", String.valueOf(e.getValue()));
        }
        for (java.util.Map.Entry<String, Integer> e : questProgress.entrySet()) {
            p.setProperty("questprogress." + e.getKey(), String.valueOf(e.getValue()));
        }
        p.setProperty("quest.gapMinutesMin", String.valueOf(questGapMinutesMin));
        p.setProperty("quest.gapMinutesMax", String.valueOf(questGapMinutesMax));
        p.setProperty("quest.sessionMinutesMin", String.valueOf(questSessionMinutesMin));
        p.setProperty("quest.sessionMinutesMax", String.valueOf(questSessionMinutesMax));
        p.setProperty("money.enabled", String.valueOf(moneyEnabled));
        for (java.util.Map.Entry<String, Boolean> e : moneyMethodEnabled.entrySet()) {
            p.setProperty("money." + e.getKey() + ".enabled", String.valueOf(e.getValue()));
        }
        p.setProperty("skill.capMinutesMin", String.valueOf(skillCapMinutesMin));
        p.setProperty("skill.capMinutesMax", String.valueOf(skillCapMinutesMax));
        p.setProperty("antiban.enabled", String.valueOf(antibanEnabled));
        p.setProperty("breaks.enabled", String.valueOf(breaksEnabled));
        p.setProperty("session.stopAfterHours", String.valueOf(stopAfterHours));
        p.setProperty("instrumentation.enabled", String.valueOf(instrumentationEnabled));
        p.setProperty("hud.opacity", String.valueOf(hudOpacity));
        p.setProperty("hud.dock", hudDock);
        p.setProperty("food.excluded", String.join(",", excludedFood));
        p.setProperty("potion.excluded", String.join(",", excludedPotions));
        p.setProperty("food.eatAtPercent", String.valueOf(eatAtPercent));
        p.setProperty("potion.prayerSipBelow", String.valueOf(prayerSipBelow));
        p.setProperty("potion.reboost", String.valueOf(potionReboost));
        p.setProperty("hud.showTargets", String.valueOf(hudShowTargets));
        for (java.util.Map.Entry<Sk, java.util.Set<String>> e : excludedActivities.entrySet()) {
            if (e.getValue() == null || e.getValue().isEmpty()) continue;
            p.setProperty("skill." + e.getKey().name() + ".excluded", String.join(",", e.getValue()));
        }
        for (java.util.Map.Entry<Sk, Integer> e : skillQuantity.entrySet()) {
            if (e.getValue() != null && e.getValue() > 0) {
                p.setProperty("skill." + e.getKey().name() + ".quantity", String.valueOf(e.getValue()));
            }
        }
        for (java.util.Map.Entry<Sk, java.util.Map<String, java.util.Set<String>>> se : excludedAreas.entrySet()) {
            for (java.util.Map.Entry<String, java.util.Set<String>> ae : se.getValue().entrySet()) {
                if (ae.getValue() == null || ae.getValue().isEmpty()) continue;
                p.setProperty("area." + se.getKey().name() + "." + ae.getKey() + ".excluded",
                        String.join(",", ae.getValue()));
            }
        }
        for (java.util.Map.Entry<Sk, java.util.Map<GearSlot, Integer>> se : gearLoadout.entrySet()) {
            for (java.util.Map.Entry<GearSlot, Integer> ge : se.getValue().entrySet()) {
                if (ge.getValue() == null || ge.getValue() <= 0) continue;
                p.setProperty("gear." + se.getKey().name() + "." + ge.getKey().name(), String.valueOf(ge.getValue()));
            }
        }
        p.setProperty("plan.enabled", String.valueOf(planEnabled));
        p.setProperty("plan.nextId", String.valueOf(planNextId));
        p.setProperty("plan.count", String.valueOf(plan.size()));
        for (int i = 0; i < plan.size(); i++) {
            PlanStep st = plan.get(i);
            String pre = "plan." + i + ".";
            p.setProperty(pre + "id", String.valueOf(st.id));
            p.setProperty(pre + "kind", st.kind.name());
            p.setProperty(pre + "name", st.name);
            p.setProperty(pre + "activity", st.activity == null ? "" : st.activity);
            p.setProperty(pre + "cond", st.cond.name());
            p.setProperty(pre + "value", String.valueOf(st.value));
            p.setProperty(pre + "elapsed", String.valueOf(st.elapsedMs));
        }
        p.setProperty("breaks.nextRuleId", String.valueOf(breakNextRuleId));
        p.setProperty("breaks.rule.count", String.valueOf(breakRules.size()));
        for (int i = 0; i < breakRules.size(); i++) {
            BreakRule r = breakRules.get(i);
            String pre = "breaks.rule." + i + ".";
            p.setProperty(pre + "id", String.valueOf(r.id));
            p.setProperty(pre + "play", String.valueOf(r.playMin));
            p.setProperty(pre + "rest", String.valueOf(r.restMin));
            p.setProperty(pre + "jitter", String.valueOf(r.jitterPct));
            p.setProperty(pre + "on", String.valueOf(r.on));
        }
        p.setProperty("breaks.behaviour.logout", String.valueOf(breakLogout));
        p.setProperty("breaks.behaviour.bank", String.valueOf(breakBankFirst));
        p.setProperty("breaks.behaviour.resume", String.valueOf(breakResumeSame));
        p.setProperty("breaks.hoursPerDay", String.valueOf(breakHoursPerDay));
        p.setProperty("breaks.varyPct", String.valueOf(breakVaryPct));
        p.setProperty("breaks.maxSessionHours", String.valueOf(breakMaxSessionHours));
        p.setProperty("breaks.activeFrom", breakActiveFrom == null ? "" : breakActiveFrom);
        p.setProperty("breaks.activeTo", breakActiveTo == null ? "" : breakActiveTo);
        p.setProperty("breaks.sleepOn", String.valueOf(breakSleepOn));
        p.setProperty("breaks.sleepHours", String.valueOf(breakSleepHours));
        p.setProperty("breaks.daysOff", String.valueOf(breakDaysOff));
        p.setProperty("breaks.stopWhenDone", String.valueOf(breakStopWhenDone));
        for (java.util.Map.Entry<Integer, Integer> e : lootCaps.entrySet()) {
            if (e.getValue() != null && e.getValue() > 0) p.setProperty("lootcap." + e.getKey(), String.valueOf(e.getValue()));
        }
        p.setProperty("combat.weaponType", combatWeaponType);
        p.setProperty("ge.buyGear", String.valueOf(geBuyGear));
        p.setProperty("loot.enabled", String.valueOf(lootEnabled));
        p.setProperty("loot.wealthGate", String.valueOf(lootWealthGate));
        p.setProperty("loot.valuableMinValue", String.valueOf(lootValuableMinValue));
        p.setProperty("loot.allWhenPoor", String.valueOf(lootAllWhenPoor));
        p.setProperty("combat.usePotions", String.valueOf(combatUsePotions));
        p.setProperty("prayer.enabled", String.valueOf(prayerEnabled));
        p.setProperty("prayer.useProtection", String.valueOf(prayerUseProtection));
        p.setProperty("prayer.useOffensive", String.valueOf(prayerUseOffensive));
        p.setProperty("combat.safeSpot", String.valueOf(combatSafeSpot));
        p.setProperty("combat.useDungeons", String.valueOf(combatUseDungeons));
        p.setProperty("crafting.method", craftingMethod);
        p.setProperty("firemaking.area", firemakingArea);
        p.setProperty("smithing.anvilShape", anvilShape);
        p.setProperty("qol.override", String.valueOf(qolOverride));
        p.setProperty("qol.skill", qolSkill == null ? "" : qolSkill.name());
        p.setProperty("worlds.types", String.join(",", worldTypes));
        p.setProperty("worlds.regions", String.join(",", worldRegions));
        p.setProperty("worlds.hopOnFollow", String.valueOf(hopOnFollow));
        p.setProperty("worlds.busyHop", String.valueOf(busyHop));
        p.setProperty("worlds.busyHopMinPlayers", String.valueOf(busyHopMinPlayers));
        p.setProperty("bond.buy", String.valueOf(bondBuy));
        p.setProperty("bond.buyBelowDays", String.valueOf(bondBuyBelowDays));
        p.setProperty("bond.maxPriceGp", String.valueOf(bondMaxPriceGp));
        for (Map.Entry<String, Integer> e : foodQty.entrySet())
            if (e.getValue() != null && e.getValue() > 0) p.setProperty("foodQty." + e.getKey(), String.valueOf(e.getValue()));
        for (Map.Entry<String, Integer> e : potionQty.entrySet())
            if (e.getValue() != null && e.getValue() > 0) p.setProperty("potQty." + e.getKey(), String.valueOf(e.getValue()));
        return p;
    }

    public synchronized void save() {
        if (file == null) return;   // a draft (unbacked) copy never persists
        Properties p = toProperties();
        try (FileOutputStream out = new FileOutputStream(file)) {
            p.store(out, "QuinnMain per-account config (targets, weights, playtime).");
        } catch (IOException e) {
            Log.log("[config] save failed: " + e);
        }
    }

    // ------------------------------------------------------------------------------------------
    // Staged configuration (the Control Panel's save bar). The panel edits a DRAFT copy; the bot
    // keeps reading this APPLIED store until the user hits Save & apply. See ControlPanel.
    // ------------------------------------------------------------------------------------------

    /**
     * Deep-copy every <b>editable</b> field from {@code o} onto this store. Excluded on purpose:
     * the live runtime counters ({@code lifetimePlaytimeMs}) and the HUD-driven QoL pin
     * ({@code qolOverride}/{@code qolSkill}) — those are applied immediately, never staged, so a
     * stale draft must not clobber them. Nested collections are cloned so draft and applied never
     * share mutable state.
     */
    public synchronized void copyEditableFrom(ConfigStore o) {
        targets.clear(); targets.putAll(o.targets);
        weights.clear(); weights.putAll(o.weights);
        goldReserve = o.goldReserve;
        geBuyAxes = o.geBuyAxes; geBuySupplies = o.geBuySupplies; geBuyGear = o.geBuyGear;
        fishingPowerDrop = o.fishingPowerDrop; fishingKaramja = o.fishingKaramja; miningPowerDrop = o.miningPowerDrop;
        smithingBuyBars = o.smithingBuyBars; flipEnabled = o.flipEnabled; flipMode = o.flipMode;
        deathRecovery = o.deathRecovery;
        miningOre = o.miningOre; miningQuantity = o.miningQuantity;
        skillCapMinutesMin = o.skillCapMinutesMin; skillCapMinutesMax = o.skillCapMinutesMax;
        questsEnabled = o.questsEnabled;
        questEnabled.clear(); questEnabled.putAll(o.questEnabled);
        questProgress.clear(); questProgress.putAll(o.questProgress);
        questGapMinutesMin = o.questGapMinutesMin; questGapMinutesMax = o.questGapMinutesMax;
        questSessionMinutesMin = o.questSessionMinutesMin; questSessionMinutesMax = o.questSessionMinutesMax;
        moneyEnabled = o.moneyEnabled;
        moneyMethodEnabled.clear(); moneyMethodEnabled.putAll(o.moneyMethodEnabled);
        excludedActivities.clear();
        for (Map.Entry<Sk, java.util.Set<String>> e : o.excludedActivities.entrySet())
            excludedActivities.put(e.getKey(), new java.util.HashSet<>(e.getValue()));
        skillQuantity.clear(); skillQuantity.putAll(o.skillQuantity);
        excludedAreas.clear();
        for (Map.Entry<Sk, java.util.Map<String, java.util.Set<String>>> se : o.excludedAreas.entrySet()) {
            java.util.Map<String, java.util.Set<String>> m = new java.util.HashMap<>();
            for (Map.Entry<String, java.util.Set<String>> ae : se.getValue().entrySet())
                m.put(ae.getKey(), new java.util.HashSet<>(ae.getValue()));
            excludedAreas.put(se.getKey(), m);
        }
        gearLoadout.clear();
        for (Map.Entry<Sk, java.util.Map<GearSlot, Integer>> se : o.gearLoadout.entrySet())
            gearLoadout.put(se.getKey(), new EnumMap<>(se.getValue()));
        copyPlanFrom(o);
        lootCaps.clear(); lootCaps.putAll(o.lootCaps);
        breakRules.clear();
        for (BreakRule r : o.breakRules) breakRules.add(new BreakRule(r.id, r.playMin, r.restMin, r.jitterPct, r.on));
        breakNextRuleId = o.breakNextRuleId;
        breakLogout = o.breakLogout; breakBankFirst = o.breakBankFirst; breakResumeSame = o.breakResumeSame;
        breakHoursPerDay = o.breakHoursPerDay; breakVaryPct = o.breakVaryPct;
        breakMaxSessionHours = o.breakMaxSessionHours;
        breakActiveFrom = o.breakActiveFrom; breakActiveTo = o.breakActiveTo;
        breakSleepOn = o.breakSleepOn; breakSleepHours = o.breakSleepHours;
        breakDaysOff = o.breakDaysOff; breakStopWhenDone = o.breakStopWhenDone;
        antibanEnabled = o.antibanEnabled; breaksEnabled = o.breaksEnabled;
        instrumentationEnabled = o.instrumentationEnabled;
        stopAfterHours = o.stopAfterHours; hudOpacity = o.hudOpacity; hudDock = o.hudDock;
        hudShowTargets = o.hudShowTargets;
        excludedFood.clear(); excludedFood.addAll(o.excludedFood);
        excludedPotions.clear(); excludedPotions.addAll(o.excludedPotions);
        eatAtPercent = o.eatAtPercent; prayerSipBelow = o.prayerSipBelow; potionReboost = o.potionReboost;
        combatWeaponType = o.combatWeaponType;
        lootEnabled = o.lootEnabled; lootWealthGate = o.lootWealthGate;
        lootValuableMinValue = o.lootValuableMinValue; lootAllWhenPoor = o.lootAllWhenPoor;
        combatUsePotions = o.combatUsePotions; prayerEnabled = o.prayerEnabled;
        prayerUseProtection = o.prayerUseProtection; prayerUseOffensive = o.prayerUseOffensive;
        combatSafeSpot = o.combatSafeSpot; combatUseDungeons = o.combatUseDungeons;
        craftingMethod = o.craftingMethod; firemakingArea = o.firemakingArea; anvilShape = o.anvilShape;
        worldTypes.clear(); worldTypes.addAll(o.worldTypes);
        worldRegions.clear(); worldRegions.addAll(o.worldRegions);
        hopOnFollow = o.hopOnFollow;
        busyHop = o.busyHop; busyHopMinPlayers = o.busyHopMinPlayers;
        bondBuy = o.bondBuy; bondBuyBelowDays = o.bondBuyBelowDays; bondMaxPriceGp = o.bondMaxPriceGp;
        foodQty.clear(); foodQty.putAll(o.foodQty);
        potionQty.clear(); potionQty.putAll(o.potionQty);
    }

    /** Replace this store's plan queue with a clone of {@code o}'s (steps are cloned, not shared). */
    public synchronized void copyPlanFrom(ConfigStore o) {
        planEnabled = o.planEnabled;
        planNextId = o.planNextId;
        plan.clear();
        for (PlanStep st : o.plan) {
            PlanStep c = new PlanStep(st.id, st.kind, st.name, st.activity, st.cond, st.value);
            c.elapsedMs = st.elapsedMs;
            plan.add(c);
        }
    }

    /** A fresh unbacked (draft) copy of this store — {@code file==null}, so its save() is a no-op. */
    public synchronized ConfigStore deepCopyForDraft() {
        ConfigStore c = new ConfigStore();
        c.copyEditableFrom(this);
        c.loaded = true;
        return c;
    }

    /** Promote a draft to applied: copy its editable keys onto this store and persist. */
    public synchronized void applyEditableFrom(ConfigStore draft) {
        copyEditableFrom(draft);
        save();
    }

    /**
     * The rail section a config key belongs to, or {@code null} if it must be ignored for dirty
     * purposes (runtime counters, the HUD-driven QoL pin, and per-step elapsed progress the bot
     * mutates as it runs). Drives the save bar's "N unsaved changes · Goals, Setup" message.
     */
    public static String sectionFor(String key) {
        if (key == null) return null;
        if (key.equals("playtime.lifetimeMs") || key.startsWith("qol.")) return null;
        if (key.endsWith(".elapsed") || key.equals("plan.nextId") || key.equals("breaks.nextRuleId")) return null;
        if (key.equals("mining.quantity")) return null;   // legacy mirror of skill.MINING.quantity
        if (key.startsWith("skill.") && (key.endsWith(".target") || key.endsWith(".weight"))) return "Goals";
        if (key.startsWith("plan.") || key.startsWith("lootcap.")) return "Plan";
        if (key.startsWith("area.") || key.startsWith("gear.")) return "Library";
        if (key.startsWith("skill.") && (key.endsWith(".excluded") || key.endsWith(".quantity"))) return "Library";
        if (key.startsWith("quest.") && key.endsWith(".enabled") && !key.equals("quest.enabled")) return "Library";
        if (key.startsWith("money.") && key.endsWith(".enabled") && !key.equals("money.enabled")) return "Library";
        if (key.startsWith("breaks.")) return "Breaks";
        return "Setup";   // automation, combat, food/potions, worlds, bond, hud, thresholds…
    }

    public boolean isLoaded() { return loaded; }

    /** Layer-3 automation instrumentation (heartbeat + ban/lock detection). OFF keeps the script
     *  fully standalone — see AUTOMATION_PLAN.md §3.4. */
    public boolean isInstrumentationEnabled() { return instrumentationEnabled; }
    public void setInstrumentationEnabled(boolean on) { instrumentationEnabled = on; }

    public int getTarget(Sk s) { return targets.getOrDefault(s, DEFAULT_TARGET); }
    public void setTarget(Sk s, int lvl) { targets.put(s, clamp(lvl, 1, 99)); }

    public int getWeight(Sk s) { return weights.getOrDefault(s, DEFAULT_WEIGHT); }
    public void setWeight(Sk s, int w) { weights.put(s, Math.max(0, w)); }

    public long getLifetimePlaytimeMs() { return lifetimePlaytimeMs; }
    public void setLifetimePlaytimeMs(long ms) { lifetimePlaytimeMs = ms; }

    // ---- Account safety (world allowlist) ----
    public boolean isWorldTypeEnabled(String type) { return worldTypes.contains(type); }
    public void setWorldTypeEnabled(String type, boolean on) {
        if (on) worldTypes.add(type); else worldTypes.remove(type);
    }
    public int worldTypesEnabledCount() { return worldTypes.size(); }
    public boolean isRegionEnabled(String region) { return worldRegions.contains(region); }
    public void setRegionEnabled(String region, boolean on) {
        if (on) worldRegions.add(region); else worldRegions.remove(region);
    }
    public boolean isHopOnFollow() { return hopOnFollow; }
    public void setHopOnFollow(boolean b) { hopOnFollow = b; }
    public boolean isBusyHop() { return busyHop; }
    public void setBusyHop(boolean b) { busyHop = b; }
    public int getBusyHopMinPlayers() { return busyHopMinPlayers; }
    public void setBusyHopMinPlayers(int n) { busyHopMinPlayers = Math.max(1, n); }

    // ---- Membership / bond ----
    public boolean isBondBuy() { return bondBuy; }
    public void setBondBuy(boolean b) { bondBuy = b; }
    public int getBondBuyBelowDays() { return bondBuyBelowDays; }
    public void setBondBuyBelowDays(int d) { bondBuyBelowDays = Math.max(0, d); }
    public long getBondMaxPriceGp() { return bondMaxPriceGp; }
    public void setBondMaxPriceGp(long gp) { bondMaxPriceGp = Math.max(0, gp); }

    // ---- Withdraw per trip ----
    public int getFoodQty(String key, int def) { return foodQty.getOrDefault(key, def); }
    public void setFoodQty(String key, int qty) {
        if (qty > 0) foodQty.put(key, Math.min(28, qty)); else foodQty.remove(key);
    }
    public int getPotionQty(String key, int def) { return potionQty.getOrDefault(key, def); }
    public void setPotionQty(String key, int qty) {
        if (qty > 0) potionQty.put(key, Math.min(28, qty)); else potionQty.remove(key);
    }

    public int getGoldReserve() { return goldReserve; }
    public void setGoldReserve(int gp) { goldReserve = Math.max(0, gp); }

    public boolean isGeBuyAxes() { return geBuyAxes; }
    public void setGeBuyAxes(boolean on) { geBuyAxes = on; }

    public boolean isGeBuySupplies() { return geBuySupplies; }
    public void setGeBuySupplies(boolean on) { geBuySupplies = on; }

    public boolean isFishingPowerDrop() { return fishingPowerDrop; }
    public void setFishingPowerDrop(boolean on) { fishingPowerDrop = on; }
    public boolean isFishingKaramja() { return fishingKaramja; }
    public void setFishingKaramja(boolean on) { fishingKaramja = on; }
    public boolean isSmithingBuyBars() { return smithingBuyBars; }
    public void setSmithingBuyBars(boolean on) { smithingBuyBars = on; }
    public boolean isFlipEnabled() { return flipEnabled; }
    public void setFlipEnabled(boolean on) { flipEnabled = on; }
    public String getFlipMode() { return flipMode; }
    public void setFlipMode(String m) { if (m != null && !m.trim().isEmpty()) flipMode = m.trim().toUpperCase(); }
    public boolean isDeathRecovery() { return deathRecovery; }
    public void setDeathRecovery(boolean on) { deathRecovery = on; }

    public boolean isMiningPowerDrop() { return miningPowerDrop; }
    public void setMiningPowerDrop(boolean on) { miningPowerDrop = on; }

    public String getMiningOre() { return miningOre; }
    public void setMiningOre(String o) { if (o != null && !o.trim().isEmpty()) miningOre = o.trim().toUpperCase(); }
    public int getMiningQuantity() { return miningQuantity; }
    public void setMiningQuantity(int q) { miningQuantity = Math.max(0, q); }

    public boolean isQuestsEnabled() { return questsEnabled; }
    public void setQuestsEnabled(boolean on) { questsEnabled = on; }

    /** Per-quest flag (default true). This is the raw per-quest value — the global switch is separate. */
    public boolean isQuestEnabled(String key) { return questEnabled.getOrDefault(key, true); }
    public void setQuestEnabled(String key, boolean on) { questEnabled.put(key, on); }

    /** Durable per-quest sub-progress (default 0). Survives Stop/Start; see {@link #questProgress}. */
    public int getQuestProgress(String key) { return questProgress.getOrDefault(key, 0); }
    public void setQuestProgress(String key, int v) { questProgress.put(key, v); }

    /** Active minutes between quest sessions (a fresh value is rolled after each one). */
    public int getQuestGapMinutesMin() { return questGapMinutesMin; }
    public int getQuestGapMinutesMax() { return questGapMinutesMax; }
    public void setQuestGapMinutes(int min, int max) {
        questGapMinutesMin = Math.max(1, min);
        questGapMinutesMax = Math.max(questGapMinutesMin, max);
    }

    /** How long one quest session lasts, in active minutes. */
    public int getQuestSessionMinutesMin() { return questSessionMinutesMin; }
    public int getQuestSessionMinutesMax() { return questSessionMinutesMax; }
    public void setQuestSessionMinutes(int min, int max) {
        questSessionMinutesMin = Math.max(1, min);
        questSessionMinutesMax = Math.max(questSessionMinutesMin, max);
    }

    /** Master switch for money-making trips (Grand Exchange selling). */
    public boolean isMoneyEnabled() { return moneyEnabled; }
    public void setMoneyEnabled(boolean on) { moneyEnabled = on; }

    /** Per-method flag (default true). The master switch is checked separately. */
    public boolean isMoneyMethodEnabled(String key) { return moneyMethodEnabled.getOrDefault(key, true); }
    public void setMoneyMethodEnabled(String key, boolean on) { moneyMethodEnabled.put(key, on); }

    public int getSkillCapMinutesMin() { return skillCapMinutesMin; }
    public int getSkillCapMinutesMax() { return skillCapMinutesMax; }
    public void setSkillCapMinutesMin(int m) { skillCapMinutesMin = Math.max(1, m); }
    public void setSkillCapMinutesMax(int m) { skillCapMinutesMax = Math.max(1, m); }

    public String getCombatWeaponType() { return combatWeaponType; }
    public void setCombatWeaponType(String t) { if (t != null && !t.trim().isEmpty()) combatWeaponType = t.trim().toUpperCase(); }

    public boolean isGeBuyGear() { return geBuyGear; }
    public void setGeBuyGear(boolean on) { geBuyGear = on; }

    public boolean isLootEnabled() { return lootEnabled; }
    public void setLootEnabled(boolean on) { lootEnabled = on; }

    public int getLootWealthGate() { return lootWealthGate; }
    public void setLootWealthGate(int gp) { lootWealthGate = Math.max(0, gp); }

    // ---- Consumables -------------------------------------------------------------------------
    private static void loadCsv(String csv, java.util.Set<String> into) {
        into.clear();
        if (csv == null) return;
        for (String s : csv.split(",")) if (!s.trim().isEmpty()) into.add(s.trim());
    }

    /** True unless the user turned this food off in the Consumables tab. */
    public boolean isFoodEnabled(String key) { return !excludedFood.contains(key); }
    public void setFoodEnabled(String key, boolean on) {
        if (on) excludedFood.remove(key); else excludedFood.add(key);
    }

    /** True unless the user turned this potion off in the Consumables tab. */
    public boolean isPotionEnabled(String key) { return !excludedPotions.contains(key); }
    public void setPotionEnabled(String key, boolean on) {
        if (on) excludedPotions.remove(key); else excludedPotions.add(key);
    }

    public int enabledFoodCount(int total) { return Math.max(0, total - excludedFood.size()); }
    public int enabledPotionCount(int total) { return Math.max(0, total - excludedPotions.size()); }

    /** Eat below this health percentage. */
    public int getEatAtPercent() { return eatAtPercent; }
    public void setEatAtPercent(int pct) { eatAtPercent = clamp(pct, 5, 95); }

    /** Sip a prayer restore below this many prayer points. */
    public int getPrayerSipBelow() { return prayerSipBelow; }
    public void setPrayerSipBelow(int pts) { prayerSipBelow = Math.max(1, pts); }

    /** Re-sip a combat boost once it wears off. */
    public boolean isPotionReboost() { return potionReboost; }
    public void setPotionReboost(boolean on) { potionReboost = on; }

    public boolean isLootAllWhenPoor() { return lootAllWhenPoor; }
    public void setLootAllWhenPoor(boolean on) { lootAllWhenPoor = on; }

    public int getLootValuableMinValue() { return lootValuableMinValue; }
    public void setLootValuableMinValue(int gp) { lootValuableMinValue = Math.max(0, gp); }

    public boolean isCombatUsePotions() { return combatUsePotions; }
    public void setCombatUsePotions(boolean on) { combatUsePotions = on; }

    public boolean isPrayerEnabled() { return prayerEnabled; }
    public void setPrayerEnabled(boolean on) { prayerEnabled = on; }

    public boolean isPrayerUseProtection() { return prayerUseProtection; }
    public void setPrayerUseProtection(boolean on) { prayerUseProtection = on; }

    public boolean isPrayerUseOffensive() { return prayerUseOffensive; }
    public void setPrayerUseOffensive(boolean on) { prayerUseOffensive = on; }

    public boolean isCombatSafeSpot() { return combatSafeSpot; }
    public void setCombatSafeSpot(boolean on) { combatSafeSpot = on; }
    public boolean isCombatUseDungeons() { return combatUseDungeons; }
    public void setCombatUseDungeons(boolean on) { combatUseDungeons = on; }

    /** "AUTO", or an {@code AnvilProduct} shape key like "PLATEBODY". */
    public String getAnvilShape() { return anvilShape; }
    public void setAnvilShape(String s) {
        if (s != null && !s.trim().isEmpty()) anvilShape = s.trim().toUpperCase();
    }

    public String getCraftingMethod() { return craftingMethod; }
    public void setCraftingMethod(String m) { if (m != null && !m.trim().isEmpty()) craftingMethod = m.trim().toUpperCase(); }

    public String getFiremakingArea() { return firemakingArea; }
    public void setFiremakingArea(String a) { if (a != null && !a.trim().isEmpty()) firemakingArea = a.trim().toUpperCase(); }

    // ---- Per-skill activity exclusions -------------------------------------------------------
    // "Enabled" is the default: only explicit exclusions are stored, so an old config (or a skill the
    // user never touched) behaves exactly as it always has.

    /** True unless the user has explicitly excluded this activity for this skill. */
    public boolean isActivityEnabled(Sk s, String activityKey) {
        // While the plan pins this skill to one activity, only that activity is "enabled" — so the
        // trainer (or combat monster picker) runs exactly the planned activity, nothing else.
        if (planFocusActivity != null && planFocusSkill == s) {
            return planFocusActivity.equals(activityKey);
        }
        java.util.Set<String> ex = excludedActivities.get(s);
        return ex == null || !ex.contains(activityKey);
    }

    public void setActivityEnabled(Sk s, String activityKey, boolean on) {
        java.util.Set<String> ex = excludedActivities.computeIfAbsent(s, k -> new java.util.HashSet<>());
        if (on) ex.remove(activityKey); else ex.add(activityKey);
        if (ex.isEmpty()) excludedActivities.remove(s);
    }

    /** How many of this skill's activities the user has left enabled (of {@code total}). */
    public int enabledActivityCount(Sk s, int total) {
        java.util.Set<String> ex = excludedActivities.get(s);
        return ex == null ? total : Math.max(0, total - ex.size());
    }

    // ---- Per-activity area exclusions --------------------------------------------------------
    // Default is enabled: only explicit exclusions are stored, so an untouched activity uses every
    // area it has (which for a single-location activity is just its one spot).

    /** True unless the user has excluded this area for this skill's activity. */
    public boolean isAreaEnabled(Sk s, String activityKey, String areaKey) {
        java.util.Map<String, java.util.Set<String>> m = excludedAreas.get(s);
        if (m == null) return true;
        java.util.Set<String> set = m.get(activityKey);
        return set == null || !set.contains(areaKey);
    }

    public void setAreaEnabled(Sk s, String activityKey, String areaKey, boolean on) {
        java.util.Map<String, java.util.Set<String>> m =
                excludedAreas.computeIfAbsent(s, k -> new java.util.HashMap<>());
        java.util.Set<String> set = m.computeIfAbsent(activityKey, k -> new java.util.HashSet<>());
        if (on) set.remove(areaKey); else set.add(areaKey);
        if (set.isEmpty()) m.remove(activityKey);
        if (m.isEmpty()) excludedAreas.remove(s);
    }

    /** How many of this activity's areas the user has left enabled (of {@code total}). */
    public int enabledAreaCount(Sk s, String activityKey, int total) {
        java.util.Map<String, java.util.Set<String>> m = excludedAreas.get(s);
        if (m == null) return total;
        java.util.Set<String> set = m.get(activityKey);
        return set == null ? total : Math.max(0, total - set.size());
    }

    // ---- Per-skill equipment loadout ---------------------------------------------------------
    // Sparse and opt-in: a skill with no configured slot behaves exactly as before (auto-gear). The
    // combat gear managers equip these over the auto-pick only for the slots that are set.

    /** The item id the user set for this skill's slot, or 0 if none. */
    public int getGearItem(Sk s, GearSlot slot) {
        java.util.Map<GearSlot, Integer> m = gearLoadout.get(s);
        if (m == null) return 0;
        Integer id = m.get(slot);
        return id == null ? 0 : id;
    }

    /** Set (itemId &gt; 0) or clear (itemId &le; 0) this skill's slot. */
    public void setGearItem(Sk s, GearSlot slot, int itemId) {
        java.util.Map<GearSlot, Integer> m = gearLoadout.computeIfAbsent(s, k -> new EnumMap<>(GearSlot.class));
        if (itemId > 0) m.put(slot, itemId); else m.remove(slot);
        if (m.isEmpty()) gearLoadout.remove(s);
    }

    /** How many slots this skill has an item in. */
    public int gearSlotsFilled(Sk s) {
        java.util.Map<GearSlot, Integer> m = gearLoadout.get(s);
        return m == null ? 0 : m.size();
    }

    /** True if this skill has at least one loadout slot set (i.e. the user opted in). */
    public boolean hasGearLoadout(Sk s) { return gearSlotsFilled(s) > 0; }

    /** Live view of this skill's slot→itemId map (empty if none); read by the gear managers. */
    public java.util.Map<GearSlot, Integer> getGearLoadout(Sk s) {
        java.util.Map<GearSlot, Integer> m = gearLoadout.get(s);
        return m == null ? java.util.Collections.emptyMap() : java.util.Collections.unmodifiableMap(m);
    }

    // ---- Plan queue --------------------------------------------------------------------------

    public boolean isPlanEnabled() { return planEnabled; }
    public void setPlanEnabled(boolean on) { planEnabled = on; }

    /** The live, ordered plan list (index 0 runs first). Edit steps in place, then call {@link #save}. */
    public java.util.List<PlanStep> getPlan() { return plan; }
    public boolean hasPlan() { return !plan.isEmpty(); }

    /** Append a new step and return it (id assigned automatically). */
    public PlanStep addPlanStep(PlanStep.Kind kind, String name, String activity, PlanStep.Cond cond, int value) {
        PlanStep st = new PlanStep(planNextId++, kind, name, activity, cond, value);
        plan.add(st);
        return st;
    }

    public void removePlanStep(int id) {
        for (java.util.Iterator<PlanStep> it = plan.iterator(); it.hasNext(); ) {
            if (it.next().id == id) { it.remove(); return; }
        }
    }

    /** Move the step with {@code id} by {@code delta} positions (clamped); no-op if not found. */
    public void movePlanStep(int id, int delta) {
        int i = indexOfPlanStep(id);
        if (i < 0) return;
        int j = clamp(i + delta, 0, plan.size() - 1);
        if (j == i) return;
        plan.add(j, plan.remove(i));
    }

    private int indexOfPlanStep(int id) {
        for (int i = 0; i < plan.size(); i++) if (plan.get(i).id == id) return i;
        return -1;
    }

    /** Transient: pin the trainer to one (skill, activity) while the plan runs a skill step. */
    public void setPlanFocus(Sk s, String activityKey) { planFocusSkill = s; planFocusActivity = activityKey; }
    public void clearPlanFocus() { planFocusSkill = null; planFocusActivity = null; }

    /** Transient: true while the PlanEngine is driving a step (gates the loot caps). */
    public boolean isPlanActive() { return planActive; }
    public void setPlanActive(boolean on) { planActive = on; }

    // ---- Per-item loot caps (only enforced while following the plan) --------------------------

    /** The live cap map (itemId → maxQty). Edit via {@link #setLootCap}; read-only iteration for the UI. */
    public java.util.Map<Integer, Integer> getLootCaps() { return lootCaps; }
    public boolean hasLootCaps() { return !lootCaps.isEmpty(); }
    /** Max of this item to hold before we stop looting it, or 0 if uncapped. */
    public int getLootCap(int itemId) { Integer v = lootCaps.get(itemId); return v == null ? 0 : v; }
    /** Set (qty &gt; 0) or clear (qty &le; 0) a cap for {@code itemId}. */
    public void setLootCap(int itemId, int qty) {
        if (itemId <= 0) return;
        if (qty > 0) lootCaps.put(itemId, qty); else lootCaps.remove(itemId);
    }

    // ---- Breaks ------------------------------------------------------------------------------

    /** The live break-rule list (edit in place, then {@link #save}). */
    public java.util.List<BreakRule> getBreakRules() { return breakRules; }
    public BreakRule addBreakRule(int playMin, int restMin, int jitterPct, boolean on) {
        BreakRule r = new BreakRule(breakNextRuleId++, playMin, restMin, jitterPct, on);
        breakRules.add(r);
        return r;
    }
    public void removeBreakRule(int id) {
        for (java.util.Iterator<BreakRule> it = breakRules.iterator(); it.hasNext(); ) if (it.next().id == id) { it.remove(); return; }
    }
    /** Enabled rules only — what the scheduler actually picks from. */
    public int enabledBreakRuleCount() {
        int n = 0; for (BreakRule r : breakRules) if (r.on) n++; return n;
    }

    public boolean isBreakLogout() { return breakLogout; }
    public void setBreakLogout(boolean on) { breakLogout = on; }
    public boolean isBreakBankFirst() { return breakBankFirst; }
    public void setBreakBankFirst(boolean on) { breakBankFirst = on; }
    public boolean isBreakResumeSame() { return breakResumeSame; }
    public void setBreakResumeSame(boolean on) { breakResumeSame = on; }

    public int getBreakHoursPerDay() { return breakHoursPerDay; }
    public void setBreakHoursPerDay(int h) { breakHoursPerDay = clamp(h, 0, 24); }
    public int getBreakVaryPct() { return breakVaryPct; }
    public void setBreakVaryPct(int p) { breakVaryPct = clamp(p, 0, 60); }
    public int getBreakMaxSessionHours() { return breakMaxSessionHours; }
    public void setBreakMaxSessionHours(int h) { breakMaxSessionHours = clamp(h, 0, 12); }

    /** "HH:MM" active-hours window (empty = always allowed). */
    public String getBreakActiveFrom() { return breakActiveFrom == null ? "" : breakActiveFrom; }
    public void setBreakActiveFrom(String s) { breakActiveFrom = s == null ? "" : s.trim(); }
    public String getBreakActiveTo() { return breakActiveTo == null ? "" : breakActiveTo; }
    public void setBreakActiveTo(String s) { breakActiveTo = s == null ? "" : s.trim(); }

    public boolean isBreakSleepOn() { return breakSleepOn; }
    public void setBreakSleepOn(boolean on) { breakSleepOn = on; }
    public int getBreakSleepHours() { return breakSleepHours; }
    public void setBreakSleepHours(int h) { breakSleepHours = clamp(h, 1, 16); }
    public int getBreakDaysOff() { return breakDaysOff; }
    public void setBreakDaysOff(int d) { breakDaysOff = clamp(d, 0, 6); }
    public boolean isBreakStopWhenDone() { return breakStopWhenDone; }
    public void setBreakStopWhenDone(boolean on) { breakStopWhenDone = on; }

    private static PlanStep.Kind parsePlanKind(String v) {
        if (v == null) return null;
        try { return PlanStep.Kind.valueOf(v.trim().toUpperCase()); } catch (Exception e) { return null; }
    }
    private static PlanStep.Cond parsePlanCond(String v) {
        if (v == null) return null;
        try { return PlanStep.Cond.valueOf(v.trim().toUpperCase()); } catch (Exception e) { return null; }
    }

    /** Per-skill "gather N then let the engine move on"; 0 = unlimited. */
    public int getSkillQuantity(Sk s) { return skillQuantity.getOrDefault(s, 0); }

    public void setSkillQuantity(Sk s, int q) {
        if (q <= 0) skillQuantity.remove(s); else skillQuantity.put(s, q);
        if (s == Sk.MINING) miningQuantity = Math.max(0, q); // keep the legacy key in step
    }

    // ---- Global automation ---------------------------------------------------------------------
    public boolean isAntibanEnabled() { return antibanEnabled; }
    public void setAntibanEnabled(boolean on) { antibanEnabled = on; }

    public boolean isBreaksEnabled() { return breaksEnabled; }
    public void setBreaksEnabled(boolean on) { breaksEnabled = on; }

    public int getStopAfterHours() { return stopAfterHours; }
    public void setStopAfterHours(int h) { stopAfterHours = Math.max(0, h); }

    public int getHudOpacity() { return hudOpacity; }
    public void setHudOpacity(int pct) { hudOpacity = clamp(pct, 40, 100); }

    /** "CHAT" | "TOP" | "FLOAT". */
    public String getHudDock() { return hudDock; }
    public void setHudDock(String d) {
        if (d != null && !d.trim().isEmpty()) hudDock = d.trim().toUpperCase();
    }

    public boolean isHudShowTargets() { return hudShowTargets; }
    public void setHudShowTargets(boolean on) { hudShowTargets = on; }

    public boolean isQolOverride() { return qolOverride; }
    public void setQolOverride(boolean on) { qolOverride = on; }
    public Sk getQolSkill() { return qolSkill; }
    public void setQolSkill(Sk s) { qolSkill = s; }

    // -- helpers --
    private static int parseInt(String v, int def) {
        try { return v == null ? def : Integer.parseInt(v.trim()); }
        catch (NumberFormatException e) { return def; }
    }
    private static long parseLong(String v, long def) {
        try { return v == null ? def : Long.parseLong(v.trim()); }
        catch (NumberFormatException e) { return def; }
    }
    private static Sk safeSkill(String name) {
        if (name == null || name.trim().isEmpty()) return null;
        try { return Sk.valueOf(name.trim()); }
        catch (IllegalArgumentException e) { return null; }
    }
    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
}
