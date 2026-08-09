package net.runelite.client.plugins.microbot.quinnmain.bot.tasks.combat;

import net.runelite.client.plugins.microbot.quinnmain.game.Sk;
import net.runelite.client.plugins.microbot.quinnmain.game.Pos;
import net.runelite.client.plugins.microbot.quinnmain.bot.core.BankLoc;
import net.runelite.client.plugins.microbot.quinnmain.bot.util.Log;


/**
 * An underground F2P area a {@link MonsterType} lives in, and how to get down there. The surface
 * {@code Nav} web-walks fine, but it does not reliably cross a dungeon <b>entrance</b> obstacle on its
 * own, so {@link DungeonNav} walks to the entrance and clicks it explicitly; this enum is the data for
 * that (entrance tile + object name) plus a bounding box used to tell "am I actually down there yet".
 *
 * <h2>Wilderness-free by construction</h2>
 * Quinn's rule: never enter the Wilderness. Every box below is a <b>non-Wilderness</b> F2P section:
 * <ul>
 *   <li><b>Stronghold of Security</b> — entirely safe; ladder in the middle of Barbarian Village.</li>
 *   <li><b>Edgeville Dungeon</b> — only the <i>southern</i> half (Hill Giants / Hobgoblins) is F2P and
 *       non-Wilderness; the northern half is members + Wilderness and its box is kept south of that line.</li>
 *   <li><b>Varrock Sewers</b> — under Misthalin, not Wilderness (the moss giants sit before the far end).</li>
 *   <li><b>Asgarnian Ice Dungeon</b> — safe; trapdoor south of Port Sarim.</li>
 * </ul>
 *
 * <p><b>Boxes are for plane-detection, not precision.</b> Surface coords for these areas are y≈3100–3500;
 * the interiors are mapped far away (y≈5200 for the Stronghold, y≈9500–9900 for the caves), so even a
 * loose box cleanly separates "on the surface" from "inside this dungeon". The monster {@code anchor}
 * (an estimate) is what we walk to once inside; a wrong anchor benches harmlessly (see {@link MonsterBench}).
 *
 * <p>Sources: OSRS Wiki "Stronghold of Security", "Edgeville Dungeon", "Varrock Sewers",
 * "Asgarnian Ice Dungeon".
 */
public enum Dungeon {

    //             label                        surface entrance          entrance object names          interior box (minX,maxX,minY,maxY)     surface bank
    STRONGHOLD        ("Stronghold of Security",  new Pos(3081, 3421, 0), new String[]{"Ladder", "Entrance"}, 1800, 2000, 5150, 5320, BankLoc.EDGEVILLE),
    EDGEVILLE_DUNGEON ("Edgeville Dungeon",       new Pos(3097, 3468, 0), new String[]{"Trapdoor"},           3070, 3170, 9820, 9910, BankLoc.EDGEVILLE),
    VARROCK_SEWERS    ("Varrock Sewers",          new Pos(3237, 3459, 0), new String[]{"Manhole"},            3090, 3270, 9840, 9940, BankLoc.VARROCK_EAST),
    ICE_DUNGEON       ("Asgarnian Ice Dungeon",   new Pos(3008, 3150, 0), new String[]{"Trapdoor", "Ladder"}, 2980, 3090, 9490, 9620, BankLoc.DRAYNOR);

    public final String label;
    /** Surface tile beside the entrance object — we web-walk here, then click the object to go down. */
    public final Pos surfaceEntrance;
    /** Names the entrance object may have (matched case-insensitively, contains). */
    public final String[] entranceNames;
    private final int minX, maxX, minY, maxY;
    /** Nearest surface bank for food restocks (the web-walker egresses up on the way there). */
    public final BankLoc bank;

    Dungeon(String label, Pos surfaceEntrance, String[] entranceNames,
            int minX, int maxX, int minY, int maxY, BankLoc bank) {
        this.label = label;
        this.surfaceEntrance = surfaceEntrance;
        this.entranceNames = entranceNames;
        this.minX = minX; this.maxX = maxX; this.minY = minY; this.maxY = maxY;
        this.bank = bank;
    }

    /** True when {@code t} is inside this dungeon's interior box (i.e. we've descended). */
    public boolean inside(Pos t) {
        return t != null && t.getX() >= minX && t.getX() <= maxX
                && t.getY() >= minY && t.getY() <= maxY;
    }

    /** True if {@code objectName} looks like this dungeon's entrance object. */
    public boolean matchesEntrance(String objectName) {
        if (objectName == null) return false;
        String n = objectName.toLowerCase();
        for (String e : entranceNames) if (n.contains(e.toLowerCase())) return true;
        return false;
    }
}
