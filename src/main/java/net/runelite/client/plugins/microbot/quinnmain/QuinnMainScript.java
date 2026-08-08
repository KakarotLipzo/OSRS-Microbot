package net.runelite.client.plugins.microbot.quinnmain;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.quinnmain.game.GameApi;
import net.runelite.client.plugins.microbot.quinnmain.game.MicrobotGameApi;
import net.runelite.client.plugins.microbot.quinnmain.slice.MiningSlice;
import net.runelite.client.plugins.microbot.quinnmain.slice.WoodcuttingSlice;

import java.util.concurrent.TimeUnit;

/**
 * The Microbot runner: owns the scheduled loop and drives the selected trainer through the
 * {@link GameApi} facade. Today it picks one trainer from config; once the engine is ported this loop
 * instead calls the weighted {@code GoalEngine}/{@code PlanEngine} — the loop shell stays the same.
 *
 * <p>Extends Microbot's {@link Script}: {@code super.run()} is the built-in guard, and
 * {@code mainScheduledFuture} is the loop handle {@code shutdown()} cancels.
 */
public class QuinnMainScript extends Script {

    private final GameApi game = new MicrobotGameApi();
    private WoodcuttingSlice woodcutting;
    private MiningSlice mining;
    private QuinnMainConfig config;

    public boolean run(QuinnMainConfig config) {
        this.config = config;
        this.woodcutting = new WoodcuttingSlice(game, config);
        this.mining = new MiningSlice(game, config);
        Microbot.enableAutoRunOn = true;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;          // Microbot's global run-guard
                switch (config.task()) {
                    case MINING:      mining.tick(); break;
                    case WOODCUTTING:
                    default:          woodcutting.tick(); break;
                }
            } catch (Exception ex) {
                Microbot.log("[quinnmain] loop error: " + ex.getMessage());
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }
}
