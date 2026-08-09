package net.runelite.client.plugins.microbot.quinnmain.bot.core;

import net.runelite.client.plugins.microbot.quinnmain.game.Game;
import net.runelite.client.plugins.microbot.quinnmain.game.GameApi;

/**
 * Aggression safety helper. In OSRS a standard aggressive monster attacks a player whose combat level
 * is ≤ 2×(monster level) + 1; above that it ignores them. Non-combat skilling next to aggressive mobs
 * (Al Kharid warriors, Draynor dark wizards, Barbarian Village barbarians) will get a low-level,
 * unattended, foodless account killed — so each skilling location tags the worst nearby aggressive mob
 * level and only uses that spot once we're safe. Ported from OSRS-Main; combat level now via the facade.
 */
public final class Aggression {

    private Aggression() {}

    public static final int AL_KHARID_WARRIOR = 9;   // safe at combat ≥ 20
    public static final int DRAYNOR_DARK_WIZARD = 7; // safe at combat ≥ 16
    public static final int BARBARIAN = 10;          // safe at combat ≥ 22

    public static boolean safeFrom(int mobLevel, int myCombat) {
        return mobLevel <= 0 || myCombat > (2 * mobLevel) + 1;
    }

    public static boolean safeFrom(int mobLevel) {
        return safeFrom(mobLevel, myCombatLevel());
    }

    /** Local player's combat level, defensively (falls back to the level-3 minimum on any hiccup). */
    public static int myCombatLevel() {
        try { GameApi g = Game.api(); return g == null ? 3 : g.combatLevel(); }
        catch (Throwable e) { return 3; }
    }
}
