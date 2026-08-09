package net.runelite.client.plugins.microbot.quinnmain.bot.tasks.combat;

import net.runelite.client.plugins.microbot.quinnmain.game.Sk;
import net.runelite.client.plugins.microbot.quinnmain.game.Pos;
import net.runelite.client.plugins.microbot.quinnmain.bot.core.BankLoc;
import net.runelite.client.plugins.microbot.quinnmain.bot.util.Log;

/**
 * The three melee attack types. The trainer gears the weapon line matching the configured type
 * ({@code combat.weaponType}) so the player can match a monster's weakness — swords (STAB),
 * scimitars (SLASH, the best all-round F2P training weapon), maces (CRUSH). All three still support
 * the Accurate/Aggressive/Defensive styles, so any of Attack/Strength/Defence can be trained on them.
 */
public enum WeaponType {
    STAB, SLASH, CRUSH;

    /** Parse a config string; defaults to SLASH on anything unrecognised. */
    public static WeaponType parse(String s) {
        if (s != null) {
            try { return valueOf(s.trim().toUpperCase()); } catch (IllegalArgumentException ignored) { }
        }
        return SLASH;
    }
}
