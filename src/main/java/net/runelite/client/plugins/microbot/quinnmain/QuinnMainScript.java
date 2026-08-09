package net.runelite.client.plugins.microbot.quinnmain;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.quinnmain.bot.antiban.BreakManager;
import net.runelite.client.plugins.microbot.quinnmain.bot.antiban.Humanizer;
import net.runelite.client.plugins.microbot.quinnmain.bot.core.AccountState;
import net.runelite.client.plugins.microbot.quinnmain.bot.core.BankMemory;
import net.runelite.client.plugins.microbot.quinnmain.bot.core.ConfigStore;
import net.runelite.client.plugins.microbot.quinnmain.bot.core.GoalEngine;
import net.runelite.client.plugins.microbot.quinnmain.bot.core.PlaytimeTracker;
import net.runelite.client.plugins.microbot.quinnmain.bot.core.TaskContext;
import net.runelite.client.plugins.microbot.quinnmain.bot.tasks.cooking.CookingTask;
import net.runelite.client.plugins.microbot.quinnmain.bot.tasks.crafting.CraftingTask;
import net.runelite.client.plugins.microbot.quinnmain.bot.tasks.firemaking.FiremakingTask;
import net.runelite.client.plugins.microbot.quinnmain.bot.tasks.fishing.FishingTask;
import net.runelite.client.plugins.microbot.quinnmain.bot.tasks.mining.MiningTask;
import net.runelite.client.plugins.microbot.quinnmain.bot.tasks.runecraft.RunecraftTask;
import net.runelite.client.plugins.microbot.quinnmain.bot.tasks.smithing.SmithingTask;
import net.runelite.client.plugins.microbot.quinnmain.bot.tasks.woodcutting.WoodcuttingTask;
import net.runelite.client.plugins.microbot.quinnmain.bot.util.Log;
import net.runelite.client.plugins.microbot.quinnmain.game.Game;
import net.runelite.client.plugins.microbot.quinnmain.game.GameApi;
import net.runelite.client.plugins.microbot.quinnmain.game.MicrobotGameApi;
import net.runelite.client.plugins.microbot.quinnmain.game.Sk;

import java.util.concurrent.TimeUnit;

/**
 * The Microbot runner: drives Quinn Main's weighted-skilling loop through the {@link GameApi} facade.
 * On startup it installs the Microbot adapter into the {@link Game} holder and routes {@link Log} to
 * Microbot's console, builds the config + services bundle ({@link TaskContext}), and registers the eight
 * ported skilling trainers with the {@link GoalEngine}. Each tick it accrues playtime, runs the break
 * scheduler, then picks and executes the next skill — the same shape as OSRS-Main's onLoop.
 *
 * <p>Combat/quest/money task registration lands as those subsystems are ported (see PORT_PLAN.md).
 */
public class QuinnMainScript extends Script {

    private final GameApi game = new MicrobotGameApi();
    private ConfigStore config;
    private PlaytimeTracker playtime;
    private AccountState account;
    private Humanizer humanizer;
    private BreakManager breaks;
    private BankMemory bankMemory;
    private GoalEngine engine;
    private TaskContext ctx;
    private boolean loadedForAccount = false;

    public boolean run(QuinnMainConfig ignoredRuneliteConfig) {
        // Install the facade + logging bridge before any bot logic runs.
        Game.set(game);
        try { Log.setSink(Microbot::log); } catch (Throwable ignored) { }

        config = new ConfigStore();
        playtime = new PlaytimeTracker();
        account = new AccountState();
        humanizer = new Humanizer();
        breaks = new BreakManager();
        bankMemory = new BankMemory();
        engine = new GoalEngine();

        engine.register(new WoodcuttingTask());
        engine.register(new MiningTask());
        engine.register(new FishingTask());
        engine.register(new CookingTask());
        engine.register(new FiremakingTask());
        engine.register(new SmithingTask());
        engine.register(new CraftingTask());
        engine.register(new RunecraftTask());

        ctx = new TaskContext(config, playtime, account, humanizer, breaks, bankMemory);
        Microbot.enableAutoRunOn = true;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(this::tick, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    private void tick() {
        try {
            if (!Microbot.isLoggedIn() || !super.run()) return;

            if (!loadedForAccount) {
                String user = account.username();
                if (user == null) return;                 // wait for the player to load
                config.loadFor(user);
                bankMemory.loadFor(user);
                loadedForAccount = true;
                Log.log("[quinnmain] loaded config for " + user + "; " + (engine.hasAnyTasks() ? "8 trainers registered." : "no trainers?!"));
            }

            bankMemory.maybeSnapshot();

            long delta = playtime.update(!breaks.isOnBreak());
            engine.accrue(delta);
            humanizer.setEnabled(config.isAntibanEnabled());

            if (breaks.handle(ctx, playtime.sessionMs())) return;   // on break / AFK — idle this tick
            if (breaks.consumeRerollAfterBreak()) engine.requestSkip();

            Sk target = engine.select(ctx);
            if (target == null) return;                             // nothing eligible right now
            int delay = engine.taskFor(target).execute(ctx);
            if (delay > 0) sleep(Math.min(delay, 5000));            // honour the task's pacing hint
        } catch (Exception ex) {
            Microbot.log("[quinnmain] loop error: " + ex.getMessage());
        }
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }
}
