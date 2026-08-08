package net.runelite.client.plugins.microbot.quinnmain;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.quinnmain.game.GameApi;
import net.runelite.client.plugins.microbot.quinnmain.game.MicrobotGameApi;
import net.runelite.client.plugins.microbot.quinnmain.slice.WoodcuttingSlice;

import java.util.concurrent.TimeUnit;

/**
 * The Microbot runner: owns the scheduled loop and drives the current task through the {@link GameApi}
 * facade. Today it runs the woodcutting vertical slice; once the engine is ported, this loop instead
 * calls into the ported {@code GoalEngine}/{@code PlanEngine} — the loop shell stays the same.
 *
 * <p>Extends Microbot's {@link Script}: {@code super.run()} is the built-in guard (paused / not logged
 * in / blocking event), and {@code mainScheduledFuture} is the loop handle {@code shutdown()} cancels.
 */
public class QuinnMainScript extends Script {

    private final GameApi game = new MicrobotGameApi();
    private WoodcuttingSlice slice;

    public boolean run(QuinnMainConfig config) {
        this.slice = new WoodcuttingSlice(game, config);
        Microbot.enableAutoRunOn = true;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;          // Microbot's global run-guard
                slice.tick();
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
