package net.runelite.client.plugins.microbot.quinnmain.bot.antiban;

import net.runelite.client.plugins.microbot.quinnmain.game.Game;
import net.runelite.client.plugins.microbot.quinnmain.game.GameApi;

import java.util.Random;

/**
 * Micro-behaviour randomisation — timing jitter + small decision randomness so there's no fixed,
 * detectable cadence. Ported from OSRS-Main; the only client touch (sleeping) goes through the facade.
 * (Mouse-curve humanisation is Microbot's own input layer.)
 */
public class Humanizer {

    private final Random rng = new Random();
    private volatile boolean enabled = true;

    public void setEnabled(boolean on) { enabled = on; }
    public boolean isEnabled() { return enabled; }

    public int reactionMs() {
        if (!enabled) return 120;
        double r = rng.nextDouble();
        if (r < 0.70) return 220 + rng.nextInt(360);
        if (r < 0.93) return 600 + rng.nextInt(700);
        return 1300 + rng.nextInt(1600);
    }

    public void reactionPause() {
        GameApi a = Game.api();
        if (a != null) a.sleep(reactionMs());
    }

    public int loopDelay() { return enabled ? 380 + rng.nextInt(520) : 300; }

    public boolean chance(double p) { return enabled && rng.nextDouble() < p; }

    public int between(int lo, int hi) { return lo + rng.nextInt(Math.max(1, hi - lo)); }

    public Random rng() { return rng; }
}
