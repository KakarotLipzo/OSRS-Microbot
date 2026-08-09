package net.runelite.client.plugins.microbot.quinnmain.bot.core;

import net.runelite.client.plugins.microbot.quinnmain.game.Sk;
import net.runelite.client.plugins.microbot.quinnmain.game.Pos;
import net.runelite.client.plugins.microbot.quinnmain.bot.util.Log;


import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * Live Grand Exchange price lookup via the OSRS Wiki Real-time Prices API
 * ({@code prices.runescape.wiki}) — the public, documented source that powers price sites like
 * osrs.exchange and RuneLite. Returns the latest insta-buy ("high") price for an item id.
 *
 * <p>Results are cached ~5 minutes and calls are short-timeout + fully guarded, so a slow or down
 * API never stalls or breaks the script — callers fall back to a static price when this returns -1.
 * The API asks for a descriptive User-Agent, which we provide.
 */
public final class PriceLookup {

    private PriceLookup() {}

    private static final String ENDPOINT = "https://prices.runescape.wiki/api/v1/osrs/latest?id=";
    private static final String TS_ENDPOINT =
            "https://prices.runescape.wiki/api/v1/osrs/timeseries?timestep=24h&id=";
    private static final String USER_AGENT =
            "QuinnMain DreamBot script - axe price check (contact: quinnconsolidatedqc@gmail.com)";
    private static final long TTL_MS = 5 * 60 * 1000L;
    /** Historical series moves once a day, so cache it far longer than the live price. */
    private static final long TS_TTL_MS = 60 * 60 * 1000L;

    // id -> {high, low, fetchedAtMs}
    private static final Map<Integer, long[]> CACHE = new HashMap<>();
    // id -> daily price series (oldest→newest), with the time it was fetched.
    private static final Map<Integer, int[]> TS_CACHE = new HashMap<>();
    private static final Map<Integer, Long> TS_AT = new HashMap<>();

    // ------------------------------------------------------------------
    // Flip data layer (feature #130): a fast "tier 2" cache on top of the per-item 5-min cache above.
    // One BULK fetch of each endpoint serves the whole flip watchlist, so ~30 items cost one request,
    // not thirty — and far fresher than 5 min. /latest = live insta prices; /5m = trade volume; /mapping
    // = the static 4-hour buy limit + members flag. Politeness: ~1-2 requests/min, descriptive UA.
    // ------------------------------------------------------------------
    private static final String LATEST_ALL = "https://prices.runescape.wiki/api/v1/osrs/latest";
    private static final String FIVE_MIN   = "https://prices.runescape.wiki/api/v1/osrs/5m";
    private static final String MAPPING    = "https://prices.runescape.wiki/api/v1/osrs/mapping";
    private static final long FAST_TTL_MS = 60_000L;         // live flip prices: refresh each minute
    private static final long VOL_TTL_MS  = 180_000L;        // /5m volume updates every 5 min
    private static final long MAP_TTL_MS  = 6 * 3_600_000L;  // /mapping is static item metadata

    private static Map<Integer, int[]> latestBulk;  private static long latestBulkAt; // id -> {high, low}
    private static Map<Integer, int[]> fiveMin;      private static long fiveMinAt;    // id -> {avgHigh, hVol, avgLow, lVol}
    private static Map<Integer, int[]> mapping;      private static long mappingAt;    // id -> {buyLimit, membersFlag}

    private static final java.util.regex.Pattern KEYED =
            java.util.regex.Pattern.compile("\"(\\d+)\":\\{");

    /** Fresh (≤{@value #FAST_TTL_MS}ms) insta-buy price for flip decisions; falls back to the 5-min tier. */
    public static synchronized int fastHigh(int itemId) {
        int[] e = latest().get(itemId);
        return (e != null && e[0] > 0) ? e[0] : high(itemId);
    }

    /** Fresh insta-<b>sell</b> price for flip decisions; falls back to the 5-min tier. */
    public static synchronized int fastLow(int itemId) {
        int[] e = latest().get(itemId);
        return (e != null && e[1] > 0) ? e[1] : low(itemId);
    }

    /** Units traded in the last 5-min window (high+low volume) — a liquidity proxy; -1 if unknown. */
    public static synchronized int recentVolume(int itemId) {
        int[] e = fiveMin().get(itemId);
        if (e == null) return -1;
        int hv = Math.max(0, e[1]), lv = Math.max(0, e[3]);
        return hv + lv;
    }

    /** The item's GE 4-hour buy limit, or -1 if unknown (no limit listed). */
    public static synchronized int buyLimit(int itemId) {
        int[] e = mapping().get(itemId);
        return (e != null && e[0] > 0) ? e[0] : -1;
    }

    /** True if the item is members-only (so it can't be traded on an F2P world). Unknown → false. */
    public static synchronized boolean isMembers(int itemId) {
        int[] e = mapping().get(itemId);
        return e != null && e[1] == 1;
    }

    private static Map<Integer, int[]> latest() {
        long now = System.currentTimeMillis();
        if (latestBulk != null && now - latestBulkAt < FAST_TTL_MS) return latestBulk;
        Map<Integer, int[]> fresh = parseKeyed(fetchJson(LATEST_ALL, 6000), new String[]{"\"high\"", "\"low\""});
        if (!fresh.isEmpty()) { latestBulk = fresh; latestBulkAt = now; }
        return latestBulk != null ? latestBulk : java.util.Collections.emptyMap();
    }

    private static Map<Integer, int[]> fiveMin() {
        long now = System.currentTimeMillis();
        if (fiveMin != null && now - fiveMinAt < VOL_TTL_MS) return fiveMin;
        Map<Integer, int[]> fresh = parseKeyed(fetchJson(FIVE_MIN, 6000),
                new String[]{"\"avgHighPrice\"", "\"highPriceVolume\"", "\"avgLowPrice\"", "\"lowPriceVolume\""});
        if (!fresh.isEmpty()) { fiveMin = fresh; fiveMinAt = now; }
        return fiveMin != null ? fiveMin : java.util.Collections.emptyMap();
    }

    private static Map<Integer, int[]> mapping() {
        long now = System.currentTimeMillis();
        if (mapping != null && now - mappingAt < MAP_TTL_MS) return mapping;
        Map<Integer, int[]> fresh = parseMapping(fetchJson(MAPPING, 6000));
        if (!fresh.isEmpty()) { mapping = fresh; mappingAt = now; }
        return mapping != null ? mapping : java.util.Collections.emptyMap();
    }

    /** Parse an id-keyed bulk object ({@code {"data":{"561":{"high":..},..}}}) into id → the named fields. */
    private static Map<Integer, int[]> parseKeyed(String json, String[] fields) {
        Map<Integer, int[]> out = new HashMap<>();
        if (json == null) return out;
        java.util.regex.Matcher m = KEYED.matcher(json);
        while (m.find()) {
            try {
                int id = Integer.parseInt(m.group(1));
                int bs = m.end() - 1;                 // the '{' of this item's object
                int be = json.indexOf('}', bs);       // no nested objects → next '}' closes it
                if (be < 0) break;
                String obj = json.substring(bs, be);
                int[] v = new int[fields.length];
                for (int i = 0; i < fields.length; i++) v[i] = parseFieldFrom(obj, fields[i], 0);
                out.put(id, v);
            } catch (Throwable ignored) { }
        }
        return out;
    }

    /** Parse the {@code /mapping} array into id → {buyLimit, membersFlag}. Objects are flat. */
    private static Map<Integer, int[]> parseMapping(String json) {
        Map<Integer, int[]> out = new HashMap<>();
        if (json == null) return out;
        for (String obj : json.split("\\},\\{")) {
            int id = parseFieldFrom(obj, "\"id\"", 0);
            if (id <= 0) continue;
            int limit = parseFieldFrom(obj, "\"limit\"", 0);      // -1 when no limit is listed
            boolean members = obj.contains("\"members\":true");
            out.put(id, new int[]{limit, members ? 1 : 0});
        }
        return out;
    }

    /** Latest insta-buy (high) price for an item id, or -1 if unavailable. Cached ~5 min. */
    public static synchronized int high(int itemId) {
        long[] e = entry(itemId);
        return e == null ? -1 : (int) e[0];
    }

    /**
     * Latest insta-<b>sell</b> (low) price — what buyers are currently paying.
     *
     * <p>This is the right number to list a sale at: pricing at {@link #high} is how an offer sits
     * unsold for an hour. Same response and same cache entry as {@code high}, so asking for both
     * costs one request.
     */
    public static synchronized int low(int itemId) {
        long[] e = entry(itemId);
        return e == null ? -1 : (int) e[1];
    }

    /**
     * The item's daily GE price for roughly the last {@code days} days (oldest → newest), from the
     * wiki's real {@code timeseries} endpoint — genuine history, not a mock. Uses the daily average
     * high, falling back to the low on thin days and carrying the last known price over any gaps so
     * the series never dips to zero. Returns null if unavailable; cached ~1h (history moves daily).
     */
    public static synchronized int[] timeseries(int itemId, int days) {
        long now = System.currentTimeMillis();
        Long at = TS_AT.get(itemId);
        int[] cached = TS_CACHE.get(itemId);
        if (cached != null && at != null && now - at < TS_TTL_MS) return tail(cached, days);

        int[] fresh = fetchTimeseries(itemId);
        if (fresh == null || fresh.length == 0) return cached == null ? null : tail(cached, days);
        TS_CACHE.put(itemId, fresh);
        TS_AT.put(itemId, now);
        return tail(fresh, days);
    }

    private static int[] tail(int[] a, int n) {
        if (a == null) return null;
        if (n <= 0 || n >= a.length) return a;
        int[] out = new int[n];
        System.arraycopy(a, a.length - n, out, 0, n);
        return out;
    }

    /** Parse the timeseries array into daily prices (oldest→newest), gap-filled. */
    private static int[] fetchTimeseries(int itemId) {
        String json = fetchJson(TS_ENDPOINT + itemId);
        if (json == null) return null;
        java.util.List<Integer> out = new java.util.ArrayList<>();
        int last = 0;
        int p = 0;
        while ((p = json.indexOf("\"timestamp\"", p)) >= 0) {
            int hi = parseFieldFrom(json, "\"avgHighPrice\"", p);
            int lo = parseFieldFrom(json, "\"avgLowPrice\"", p);
            int v = hi > 0 ? hi : (lo > 0 ? lo : last);   // thin day → low, then carry the last price
            if (v > 0) last = v;
            out.add(v > 0 ? v : last);
            p += 11;
        }
        // Drop any leading zeros before the first real price so the chart starts clean.
        int start = 0;
        while (start < out.size() && out.get(start) <= 0) start++;
        if (start >= out.size()) return null;
        int[] arr = new int[out.size() - start];
        for (int i = start; i < out.size(); i++) arr[i - start] = out.get(i);
        return arr;
    }

    /** Cached {high, low, fetchedAt} for an item, fetching if absent or stale. */
    private static long[] entry(int itemId) {
        long now = System.currentTimeMillis();
        long[] cached = CACHE.get(itemId);
        if (cached != null && now - cached[2] < TTL_MS) return cached;

        int[] hl = fetchBoth(itemId);
        if (hl == null || (hl[0] <= 0 && hl[1] <= 0)) return cached; // keep any stale value we had
        long[] fresh = new long[]{hl[0], hl[1] > 0 ? hl[1] : hl[0], now};
        CACHE.put(itemId, fresh);
        return fresh;
    }

    /** @return {high, low}, or null if the request failed. */
    private static int[] fetchBoth(int itemId) {
        String json = fetchJson(ENDPOINT + itemId);
        if (json == null) return null;
        return new int[]{parseField(json, "\"high\""), parseField(json, "\"low\"")};
    }

    private static String fetchJson(String endpoint) { return fetchJson(endpoint, 2500); }

    private static String fetchJson(String endpoint, int timeoutMs) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(endpoint);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            int code = conn.getResponseCode();
            if (code != 200) {
                Log.log("[price] HTTP " + code + " for " + endpoint);
                return null;
            }
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
            }
            return sb.toString();
        } catch (Throwable e) {
            Log.log("[price] lookup failed for " + endpoint + ": " + e);
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /**
     * Minimal parse of {@code {"data":{"<id>":{"high":7118,"highTime":..,"low":7002,..}}}}.
     * {@code field} is the quoted key, e.g. {@code "\"low\""}. Note "high" is matched before
     * "highTime" only because we search for the quoted key including its closing quote.
     */
    private static int parseField(String json, String field) {
        return parseFieldFrom(json, field, 0);
    }

    /** As {@link #parseField} but searches for {@code field} starting at {@code from}. */
    private static int parseFieldFrom(String json, String field, int from) {
        if (json == null) return -1;
        int i = json.indexOf(field, from);
        if (i < 0) return -1;
        int colon = json.indexOf(':', i);
        if (colon < 0) return -1;
        int j = colon + 1;
        while (j < json.length() && !Character.isDigit(json.charAt(j)) && json.charAt(j) != '-') j++;
        int k = j;
        while (k < json.length() && Character.isDigit(json.charAt(k))) k++;
        if (k == j) return -1; // "high":null → no digits
        try {
            return Integer.parseInt(json.substring(j, k));
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
