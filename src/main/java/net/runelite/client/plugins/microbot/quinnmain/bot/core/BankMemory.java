package net.runelite.client.plugins.microbot.quinnmain.bot.core;

import net.runelite.client.plugins.microbot.quinnmain.game.Game;
import net.runelite.client.plugins.microbot.quinnmain.game.GameApi;
import net.runelite.client.plugins.microbot.quinnmain.bot.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Persistent memory of the bank's contents (item id → quantity), so tasks and the goal engine can
 * decide what's trainable without opening the bank. Ported from OSRS-Main; bank reads go through the
 * {@link GameApi} facade, and the per-account file now lives under {@code ~/.runelite/quinnmain/}.
 */
public class BankMemory {

    private final Map<Integer, Integer> contents = new HashMap<>();
    private File file;
    private boolean loaded = false;
    private boolean seenOnce = false;
    private long lastSnapshot = 0;
    private long savedAt = 0;

    private static GameApi g() { return Game.api(); }

    public synchronized void loadFor(String username) {
        if (loaded) return;
        String u = (username == null || username.isEmpty())
                ? "default" : username.replaceAll("[^a-zA-Z0-9_-]", "_");
        File dir = new File(System.getProperty("user.home"),
                ".runelite" + File.separator + "quinnmain");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        file = new File(dir, u + "_bank.properties");
        if (file.exists()) {
            Properties p = new Properties();
            try (FileInputStream in = new FileInputStream(file)) {
                p.load(in);
            } catch (IOException e) {
                Log.log("[bank] memory load failed: " + e);
            }
            seenOnce = Boolean.parseBoolean(p.getProperty("_seen", "false"));
            try { savedAt = Long.parseLong(p.getProperty("_savedAt", "0")); } catch (Exception ignore) { }
            for (String k : p.stringPropertyNames()) {
                if (k.startsWith("_")) continue;
                try { contents.put(Integer.parseInt(k), Integer.parseInt(p.getProperty(k))); }
                catch (NumberFormatException ignore) { }
            }
            Log.log("[bank] loaded memory: " + contents.size() + " item type(s), seen=" + seenOnce);
        }
        loaded = true;
    }

    /** If the bank is open, refresh the snapshot (throttled). Call each loop. */
    public synchronized void maybeSnapshot() {
        GameApi a = g();
        if (a == null || !a.bankIsOpen()) return;
        long now = System.currentTimeMillis();
        if (now - lastSnapshot < 1500) return;
        lastSnapshot = now;
        Map<Integer, Integer> fresh;
        try { fresh = a.bankSnapshot(); } catch (Throwable e) { return; }
        if (fresh == null) return;
        contents.clear();
        contents.putAll(fresh);
        seenOnce = true;
        savedAt = now;
        save();
    }

    /** Force an immediate snapshot, bypassing the throttle. */
    public synchronized void snapshotNow() {
        GameApi a = g();
        if (a == null || !a.bankIsOpen()) return;
        lastSnapshot = 0;
        maybeSnapshot();
    }

    public synchronized boolean has(int itemId) { return contents.getOrDefault(itemId, 0) > 0; }
    public synchronized int count(int itemId) { return contents.getOrDefault(itemId, 0); }
    public synchronized Map<Integer, Integer> contentsCopy() { return new HashMap<>(contents); }

    public synchronized boolean hasAny(int... itemIds) {
        for (int id : itemIds) if (has(id)) return true;
        return false;
    }

    public synchronized boolean hasSeenBank() { return seenOnce; }
    public synchronized long lastSavedAt() { return savedAt; }

    private void save() {
        if (file == null) return;
        Properties p = new Properties();
        p.setProperty("_seen", String.valueOf(seenOnce));
        p.setProperty("_savedAt", String.valueOf(savedAt));
        for (Map.Entry<Integer, Integer> e : contents.entrySet()) {
            p.setProperty(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
        }
        try (FileOutputStream out = new FileOutputStream(file)) {
            p.store(out, "QuinnMain bank memory (item id = quantity).");
        } catch (IOException e) {
            Log.log("[bank] memory save failed: " + e);
        }
    }
}
