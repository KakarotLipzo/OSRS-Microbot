package net.runelite.client.plugins.microbot.quinnmain.bot.tasks.combat;

import net.runelite.client.plugins.microbot.quinnmain.game.Sk;
import net.runelite.client.plugins.microbot.quinnmain.game.Pos;
import net.runelite.client.plugins.microbot.quinnmain.bot.core.BankLoc;
import net.runelite.client.plugins.microbot.quinnmain.bot.util.Log;


/**
 * F2P combat targets with a fixed spot + nearby bank, ordered by the player <b>combat level</b> at
 * which we progress to them. The engine fights the highest-tier monster whose {@link #minCombat} the
 * player has reached (see {@code CombatEngine.chooseMonster}).
 *
 * <h2>"Only fight what we can beat"</h2>
 * Every entry records the monster's own {@link #combatLevel}, and {@link #minCombat} is set to roughly
 * <b>2× that plus a margin</b> — the same ratio {@code core.Aggression} uses to decide when an
 * aggressive mob has stopped being a threat. So we only step up to a monster once we comfortably
 * out-level it, rather than the moment it becomes technically attackable. {@link #foodOptional} marks
 * the ones that genuinely cannot kill us, and the engine falls back to those when it has no food.
 *
 * <h2>Surface only — deliberately</h2>
 * The wiki's better F2P training spots (Hill Giants in Edgeville Dungeon, Moss Giants in Varrock
 * Sewers, Flesh Crawlers and Ankous in the Stronghold of Security) are all <b>underground</b>.
 * {@code Nav} web-walks the surface; it has no dungeon-entrance, ladder or Stronghold-door handling,
 * so sending the bot there would strand it. Every location below is reachable by walking. Hill Giants
 * appear via <b>Giants' Plateau</b>, the one surface hill-giant spot in F2P; <b>Giant frogs</b>
 * (Lumbridge Swamp) and <b>Hobgoblins</b> (Hobgoblin Peninsula, west of the Crafting Guild) are the
 * other two surface F2P spots we can reach, so those are in; everything better stays underground and out.
 *
 * <h2>Anchors</h2>
 * Anchors are a walk target, not a spawn tile — the engine walks here then searches for the NPC by
 * name, so being a few tiles out is harmless. Where an anchor is an estimate it says so; if none of
 * the monster is found on arrival the engine benches that entry and drops a tier rather than
 * standing there (see {@code MeleeCombat.attack}).
 *
 * <p>Sources: OSRS Wiki "Free-to-play Melee training", "Hill Giant", "Guard", "Giant frog",
 * "Hobgoblin", "Hobgoblin Peninsula".
 */
public enum MonsterType {
    //          npc names                                     cbLvl minCombat foodOptional dangerous anchor                     bank                     safeSpot
    /** Level 1, 3hp — the weakest thing in the game. Lumbridge chicken coop, north-east of the castle. */
    CHICKEN   (new String[]{"Chicken"},                        1,   0,        true,        false,    new Pos(3235, 3295, 0),  BankLoc.LUMBRIDGE,   null),

    /** Level 2, 8hp. Lumbridge cow field — verified live, the engine's reliable fallback. */
    COW       (new String[]{"Cow", "Cow calf"},                2,   0,        true,        false,    new Pos(3259, 3286, 0),  BankLoc.LUMBRIDGE,   null),

    /** Level 5, 15hp. Edgeville Monastery. Not aggressive. */
    MONK      (new String[]{"Monk"},                           5,   12,       false,       false,    new Pos(3050, 3487, 0),  BankLoc.EDGEVILLE,   null),

    /** Level 10, 20hp. Barbarian Village, between Edgeville and Varrock West. Preferred over the Al-Kharid
     *  warrior (same minCombat 25): no toll gate and a reliable open spawn, so a broke account trains here. */
    BARBARIAN (new String[]{"Barbarian"},                      10,  25,       false,       false,    new Pos(3080, 3420, 0),  BankLoc.EDGEVILLE,   null),

    /** Level 9, 19hp. Al Kharid palace — bank is right there. Nearby warriors join in once engaged. Listed
     *  AFTER Barbarian: reaching it needs the 10gp toll gate, so it's a poor pick while broke (it hiked here
     *  and found nothing, 2026-07-31). Revisit ordering / add toll handling once funded. */
    AL_KHARID_WARRIOR(new String[]{"Al-Kharid warrior"},       9,   25,       false,       false,    new Pos(3297, 3175, 0),  BankLoc.AL_KHARID,   null),

    /** Level 13, 23hp. Lumbridge Swamp, south of the castle (added for F2P in 2015). Non-aggressive, so
     *  you fight one at a time, and the Lumbridge bank is close for food — a calm step between Barbarian
     *  and Guard. <b>Anchor is an estimate</b> (the wiki gives no tile); the not-found fallback benches it
     *  and drops a tier if it's wrong, so a live run confirms it harmlessly. */
    GIANT_FROG(new String[]{"Giant frog"},                     13,  29,       false,       false,    new Pos(3230, 3184, 0),  BankLoc.LUMBRIDGE,   null),

    /** Level 21, 22hp. Varrock castle courtyard. Not aggressive unless attacked. */
    GUARD     (new String[]{"Guard"},                          21,  45,       false,       false,    new Pos(3212, 3428, 0),  BankLoc.VARROCK_WEST, null),

    /**
     * Level 28, 35hp. <b>Giants' Plateau</b>, east of Al Kharid past the gate — the only F2P hill
     * giants above ground. Aggressive below combat 57, which is why {@link #minCombat} is 57.
     * <b>Anchor is an estimate</b> — the wiki gives directions ("east of the Al Kharid general store,
     * past the gap in the gate") but no tile. Needs one live confirmation; until then the
     * not-found fallback keeps it harmless.
     */
    HILL_GIANT(new String[]{"Hill Giant", "Hill giant"},       28,  57,       false,       true,     new Pos(3350, 3150, 0),  BankLoc.AL_KHARID,   null),

    /**
     * Level 28, 29hp. <b>Hobgoblin Peninsula</b> — the strip of land west of the Crafting Guild, between
     * the Dark Wizards' Tower (north) and Melzar's Maze (south); the one surface F2P hobgoblin group Nav
     * can reach (the others are underground — Edgeville Dungeon, Asgarnian Ice Dungeon — or Wilderness).
     * Aggressive below combat 57, hence {@link #minCombat} 57. Listed AFTER Hill Giant, so the engine
     * prefers hill giants (more hp = more XP, better drops) and only falls to hobgoblins when the giant
     * spot is benched or excluded. Bank is far (Falador), so food trips are long. <b>Anchor is an
     * estimate</b> — needs one live confirmation; until then the not-found fallback keeps it harmless.
     */
    HOBGOBLIN (new String[]{"Hobgoblin"},                      28,  57,       false,       false,    new Pos(2907, 3302, 0),  BankLoc.FALADOR_WEST, null),

    // ---- Underground F2P spots (Wilderness-free) — descent handled by DungeonNav + Dungeon ----------

    /** Level 27, 26hp. <b>Stronghold of Security</b>, floor 1 (Vault of War) — straight down the ladder
     *  in the middle of Barbarian Village, no security door on this floor. Not aggressive, not Wilderness.
     *  A niche step at combat 40–44 and a proof that the dungeon descent works; the real Stronghold prize
     *  (Flesh Crawlers, floor 2) waits on the inter-floor quiz-door handling. <b>Interior anchor is an
     *  estimate</b> — the not-found fallback benches it. */
    MINOTAUR  (new String[]{"Minotaur"},                       27,  40,       false,       false,    new Pos(1866, 5238, 0),  BankLoc.EDGEVILLE,    null, Dungeon.STRONGHOLD),

    /** Level 41, 25hp. <b>Stronghold floor 2 (Catacomb of Famine)</b> — the best F2P melee XP: aggressive
     *  to any level (AFK) and deals <b>max 1 damage</b>, so food-optional. Behind the floor-1→2 maze + quiz
     *  doors, which {@link com.quinn.osrs.main.antiban.StrongholdSolver} answers; minCombat 51 matches the
     *  floor-2 portal shortcut. <b>Interior anchor is an estimate</b> — the combat reach-timeout benches it
     *  if the maze can't be threaded, so it falls back to the surface safely. */
    FLESH_CRAWLER(new String[]{"Flesh Crawler", "Flesh crawler"}, 41, 51,   true,        false,    new Pos(1890, 5275, 0),  BankLoc.EDGEVILLE,    new Pos(1885, 5270, 0), Dungeon.STRONGHOLD),

    /** Level 28, 35hp. <b>Edgeville Dungeon</b>, the SOUTHERN (F2P, non-Wilderness) end — down the
     *  Edgeville trapdoor. Denser hill-giant spawns than the surface Giants' Plateau; same tier, so it's
     *  the fallback after the surface giants + hobgoblins. Anchor kept safely south of the Wilderness line.
     *  <b>Interior anchor is an estimate</b> — the not-found fallback benches it. */
    EDGEVILLE_HILL_GIANT(new String[]{"Hill Giant", "Hill giant"}, 28, 57,    false,       true,     new Pos(3117, 9853, 0),  BankLoc.EDGEVILLE,    new Pos(3120, 9851, 0), Dungeon.EDGEVILLE_DUNGEON),

    /** Level 42, 60hp. <b>Varrock Sewers</b> (under Misthalin, NOT Wilderness) — down the manhole east of
     *  the palace. Good F2P XP with rune/herb drops. Aggressive, so food-gated. <b>Interior anchor is an
     *  estimate</b> — the not-found fallback benches it. */
    MOSS_GIANT(new String[]{"Moss giant", "Moss Giant"},       42,  80,       false,       true,     new Pos(3155, 9905, 0),  BankLoc.VARROCK_EAST, new Pos(3158, 9907, 0), Dungeon.VARROCK_SEWERS),

    /** Level 53, 70hp. <b>Asgarnian Ice Dungeon</b> (F2P section, not Wilderness) — down the trapdoor south
     *  of Port Sarim. The top F2P melee tier; they hit hard, so it's food-heavy and gated high (minCombat
     *  90). <b>Interior anchor is an estimate</b> — the not-found fallback benches it. */
    ICE_GIANT (new String[]{"Ice giant", "Ice Giant"},         53,  90,       false,       true,     new Pos(3040, 9560, 0),  BankLoc.DRAYNOR,      new Pos(3037, 9556, 0), Dungeon.ICE_DUNGEON);

    public final String[] npcNames;
    /** The monster's own combat level — what {@link #minCombat} is derived from. */
    public final int combatLevel;
    /** Player combat level required before the engine will pick this monster. */
    public final int minCombat;
    public final boolean foodOptional;
    /** Hits hard enough to warrant a protection prayer. */
    public final boolean dangerous;
    public final Pos anchor;
    public final BankLoc bank;
    /**
     * Optional tile to fight from with ranged/magic where the monster can't reach us (null = none). Only
     * the aggressive/hard-hitting dungeon targets (hill/moss/ice giants, flesh crawlers) define one; weak
     * melee mobs don't need it. These tiles are best-effort <b>estimates</b> — safespot tiles aren't
     * published as coordinates — so the combat engine abandons an unreachable one after a short timeout
     * (a wrong tile just means fighting normally, never a stall). Tune from a live run.
     */
    public final Pos safeSpot;
    /** The underground area this monster is in (null = a surface spot the walker reaches directly). */
    public final Dungeon dungeon;

    /** Surface monster (no dungeon). */
    MonsterType(String[] npcNames, int combatLevel, int minCombat, boolean foodOptional,
                boolean dangerous, Pos anchor, BankLoc bank, Pos safeSpot) {
        this(npcNames, combatLevel, minCombat, foodOptional, dangerous, anchor, bank, safeSpot, null);
    }

    /** Underground monster — {@code dungeon} tells {@link DungeonNav} how to descend to {@code anchor}. */
    MonsterType(String[] npcNames, int combatLevel, int minCombat, boolean foodOptional,
                boolean dangerous, Pos anchor, BankLoc bank, Pos safeSpot, Dungeon dungeon) {
        this.npcNames = npcNames;
        this.combatLevel = combatLevel;
        this.minCombat = minCombat;
        this.foodOptional = foodOptional;
        this.dangerous = dangerous;
        this.anchor = anchor;
        this.bank = bank;
        this.safeSpot = safeSpot;
        this.dungeon = dungeon;
    }

    public boolean matches(String name) {
        if (name == null) return false;
        for (String n : npcNames) if (n.equals(name)) return true;
        return false;
    }

    /** Human label for logs. */
    public String label() {
        return npcNames[0] + " (lvl " + combatLevel + ")";
    }
}
