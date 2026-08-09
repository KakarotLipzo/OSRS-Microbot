package net.runelite.client.plugins.microbot.quinnmain.bot.core;

import net.runelite.client.plugins.microbot.quinnmain.game.Sk;
import net.runelite.client.plugins.microbot.quinnmain.game.Pos;
import net.runelite.client.plugins.microbot.quinnmain.bot.util.Log;

/**
 * One AFK/break rule (design §2.6): play for {@code playMin} minutes, then break for {@code restMin}
 * minutes, with each duration varied by ± {@code jitterPct}% per cycle so no two breaks match. The
 * {@link com.quinn.osrs.main.antiban.BreakManager} picks a random <b>enabled</b> rule each cycle.
 */
public class BreakRule {
    public final int id;
    public int playMin;
    public int restMin;
    public int jitterPct;
    public boolean on;

    public BreakRule(int id, int playMin, int restMin, int jitterPct, boolean on) {
        this.id = id;
        this.playMin = playMin;
        this.restMin = restMin;
        this.jitterPct = jitterPct;
        this.on = on;
    }
}
