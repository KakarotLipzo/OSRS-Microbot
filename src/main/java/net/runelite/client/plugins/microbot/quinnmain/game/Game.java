package net.runelite.client.plugins.microbot.quinnmain.game;

/**
 * Static holder for the active {@link GameApi}. The plugin sets it once at startup
 * ({@code Game.set(new MicrobotGameApi())}); ported logic that used DreamBot's static accessors
 * ({@code Players.}, {@code Inventory.}, {@code Bank.}, …) reaches the facade via {@code Game.api().…}
 * with minimal structural change. Neutral (no client import), so it compiles in the neutral-layer check.
 *
 * <p>{@link #api()} may be null before init or in a headless unit test — callers that run outside the
 * game loop should null-check (most logic only runs after startup, so it's safe there).
 */
public final class Game {
    private Game() {}
    private static volatile GameApi api;
    public static void set(GameApi a) { api = a; }
    public static GameApi api() { return api; }
}
