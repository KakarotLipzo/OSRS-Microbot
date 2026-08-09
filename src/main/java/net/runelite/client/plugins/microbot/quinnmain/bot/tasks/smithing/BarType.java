package net.runelite.client.plugins.microbot.quinnmain.bot.tasks.smithing;

import net.runelite.client.plugins.microbot.quinnmain.game.Sk;
import net.runelite.client.plugins.microbot.quinnmain.game.Pos;
import net.runelite.client.plugins.microbot.quinnmain.bot.core.BankLoc;
import net.runelite.client.plugins.microbot.quinnmain.bot.util.Log;

/**
 * Bars we can smelt at a furnace, with the bar item ID, the Smithing level, an XP-rate rank, and the
 * ore recipe (ore item IDs + how many of each per bar). The trainer smelts the best bar it has ore
 * for — which naturally consumes the ore the Mining trainer banks.
 *
 * <p>F2P from mined ore: BRONZE (copper + tin) and IRON (iron ore). Higher bars (steel, mithril…)
 * need coal, which the current Mining set doesn't gather yet — add coal mining + those bars later.
 * (Iron bars have a 50% smelt success on F2P without a ring of forging — expected, still XP on success.)
 */
public enum BarType {
    BRONZE ("Bronze bar", 2349, 1,  8,  new int[]{436, 438}, new int[]{1, 1}), // copper + tin
    IRON   ("Iron bar",   2351, 15, 20, new int[]{440},      new int[]{1});     // iron ore

    public final String barName;
    public final int barId;
    public final int smithLevel;
    public final int xpRank;
    public final int[] oreIds;
    public final int[] oreCounts;

    BarType(String barName, int barId, int smithLevel, int xpRank, int[] oreIds, int[] oreCounts) {
        this.barName = barName;
        this.barId = barId;
        this.smithLevel = smithLevel;
        this.xpRank = xpRank;
        this.oreIds = oreIds;
        this.oreCounts = oreCounts;
    }

    /** Ore items consumed per bar (sum of the recipe counts). */
    public int oresPerBar() {
        int s = 0;
        for (int c : oreCounts) s += c;
        return s;
    }
}
