package net.runelite.client.plugins.microbot.quinnmain.bot.tasks.runecraft;

import net.runelite.client.plugins.microbot.quinnmain.game.Sk;
import net.runelite.client.plugins.microbot.quinnmain.game.Pos;
import net.runelite.client.plugins.microbot.quinnmain.bot.core.BankLoc;
import net.runelite.client.plugins.microbot.quinnmain.bot.util.Log;


/**
 * The F2P Runecraft runes, worst → best XP (auto-progression picks the highest {@link #xpRank} the level
 * allows). Each is crafted from essence at its own altar, reached by walking to the surface
 * <b>mysterious ruins</b> and entering with the matching <b>talisman</b> (or a worn tiara). Body runes
 * are the highest F2P runecraft (7.5 xp/essence); Mind is skipped because Earth (same level 9) beats it.
 *
 * <p>Essence: <b>pure essence</b> (id 7936) — F2P can't mine it but can buy it cheaply on the GE, and it
 * crafts these runes for the same XP as rune essence (per the wiki F2P guide).
 *
 * <p><b>Ruins tiles are best-effort estimates</b> (the wiki gives directions, not coordinates); the
 * trainer's nearby-object diagnostic logs the real ruins/altar names + ids on arrival so a wrong anchor
 * is corrected from one live run, and it simply skips a method it can't reach rather than hanging.
 *
 * <p>Sources: OSRS Wiki "Free-to-play Runecraft training", "Air/Water/Earth/Fire/Body Altar".
 */
public enum RuneMethod {
    //     rune name    runeId talisman name     talId rcLvl xpRank  ruins tile                 bank
    AIR   ("Air rune",   556,  "Air talisman",   1438, 1,   50, new Pos(2841, 3376, 0), BankLoc.FALADOR_EAST),
    WATER ("Water rune", 555,  "Water talisman", 1444, 5,   60, new Pos(3183, 3164, 0), BankLoc.DRAYNOR),
    EARTH ("Earth rune", 557,  "Earth talisman", 1440, 9,   65, new Pos(3306, 3474, 0), BankLoc.VARROCK_EAST),
    FIRE  ("Fire rune",  554,  "Fire talisman",  1442, 14,  70, new Pos(3311, 3253, 0), BankLoc.AL_KHARID),
    BODY  ("Body rune",  559,  "Body talisman",  1446, 20,  75, new Pos(3053, 3445, 0), BankLoc.EDGEVILLE);

    /** Pure essence — the F2P-buyable essence used for every rune here. */
    public static final int PURE_ESSENCE = 7936;

    public final String runeName;
    public final int runeId;
    public final String talismanName;
    public final int talismanId;
    public final int rcLevel;
    public final int xpRank;
    public final Pos ruins;
    public final BankLoc bank;

    RuneMethod(String runeName, int runeId, String talismanName, int talismanId, int rcLevel,
               int xpRank, Pos ruins, BankLoc bank) {
        this.runeName = runeName;
        this.runeId = runeId;
        this.talismanName = talismanName;
        this.talismanId = talismanId;
        this.rcLevel = rcLevel;
        this.xpRank = xpRank;
        this.ruins = ruins;
        this.bank = bank;
    }
}
