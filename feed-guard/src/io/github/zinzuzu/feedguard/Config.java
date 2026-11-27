package io.github.zinzuzu.feedguard;

import android.content.Context;
import android.content.RestrictionsManager;
import android.os.Bundle;

import java.util.Set;

/**
 * Feed Guard's managed configuration, set in OwnDroid (Applications › Feed
 * Guard › Managed configuration). OwnDroid only stores the keys someone set,
 * so a missing key means the default below.
 */
final class Config {
    static final String ALLOWED = "allowed_apps";
    static final String BLOCKED = "blocked_apps";
    static final String ONLY_LISTED = "only_listed_apps";
    static final String GRAYSCALE = "grayscale";
    static final String COLOR_APPS = "color_apps";
    static final String BANK_APPS = "bank_apps";
    static final String NEWS_BLOCK = "news_block";
    static final String NEWS_GAP_HOURS = "news_gap_hours";
    static final String NEWS_FROM_HOUR = "news_from_hour";
    static final String NEWS_UNTIL_HOUR = "news_until_hour";
    static final String NEWS_MINUTES = "news_minutes";
    static final String NEWS_SITES = "news_sites";
    static final String NEWS_APPS = "news_apps";

    /** The list: apps Feed Guard never pauses. Default: none. */
    final Set<String> allowed;
    /** More apps to pause like browsers. Default: none. */
    final Set<String> extraBlocked;
    /** Pause every app that isn't on the list. Default: off. */
    final boolean listMode;
    /** Keep the screen black and white. Default: on (it needs the adb permission anyway). */
    final boolean grayscale;
    /** Apps that may show colour. Default: the maps apps in ColorRules. */
    final Set<String> colourApps;
    /** Apps that switch Feed Guard and Watch Limit off while they open (banking mode). Default: Nubank. */
    final Set<String> bankApps;
    /** Block news for a while after waking up (see NewsRules). Default: on. */
    final boolean newsBlock;
    /** Hours left alone that count as a night's sleep. */
    final int newsGapHours;
    /** The morning window, in local hours: [from, until). */
    final int newsFromHour, newsUntilHour;
    /** How long the block lasts. */
    final int newsMinutes;
    /** News sites (hosts, subdomains included). */
    final Set<String> newsSites;
    /** News apps, on top of apps in Android's news category. */
    final Set<String> newsApps;

    private Config(Bundle b) {
        allowed = AppRules.parseList(string(b, ALLOWED));
        extraBlocked = AppRules.parseList(string(b, BLOCKED));
        listMode = bool(b, ONLY_LISTED, false);
        grayscale = bool(b, GRAYSCALE, true);
        colourApps = ColorRules.colourApps(b.containsKey(COLOR_APPS) ? string(b, COLOR_APPS) : null);
        bankApps = BankRules.bankApps(b.containsKey(BANK_APPS) ? string(b, BANK_APPS) : null);
        newsBlock = bool(b, NEWS_BLOCK, true);
        newsGapHours = NewsRules.clamp(integer(b, NEWS_GAP_HOURS, NewsRules.DEFAULT_GAP_HOURS), 1, 23);
        newsFromHour = NewsRules.clamp(integer(b, NEWS_FROM_HOUR, NewsRules.DEFAULT_FROM_HOUR), 0, 23);
        newsUntilHour = NewsRules.clamp(integer(b, NEWS_UNTIL_HOUR, NewsRules.DEFAULT_UNTIL_HOUR), 0, 24) % 24;
        newsMinutes = NewsRules.clamp(integer(b, NEWS_MINUTES, NewsRules.DEFAULT_MINUTES), 1, NewsRules.MAX_MINUTES);
        newsSites = NewsRules.listOrDefault(b.containsKey(NEWS_SITES) ? string(b, NEWS_SITES) : null, NewsRules.DEFAULT_SITES);
        newsApps = NewsRules.appsOrDefault(b.containsKey(NEWS_APPS) ? string(b, NEWS_APPS) : null);
    }

    static Config read(Context c) {
        Bundle b = null;
        try {
            RestrictionsManager rm = c.getSystemService(RestrictionsManager.class);
            b = rm == null ? null : rm.getApplicationRestrictions();
        } catch (RuntimeException ignored) {
            // No configuration readable: defaults.
        }
        return new Config(b == null ? new Bundle() : b);
    }

    @SuppressWarnings("deprecation")
    private static String string(Bundle b, String key) {
        Object v = b.get(key);
        return v == null ? "" : v.toString();
    }

    /** Accepts a real boolean, or "true"/"false" in case a tool stores text. */
    @SuppressWarnings("deprecation")
    private static boolean bool(Bundle b, String key, boolean fallback) {
        Object v = b.get(key);
        if (v instanceof Boolean) return (Boolean) v;
        if (v instanceof String) {
            String s = ((String) v).trim();
            if (s.equalsIgnoreCase("true")) return true;
            if (s.equalsIgnoreCase("false")) return false;
        }
        return fallback;
    }

    /** Accepts a real integer, or digits as text. */
    @SuppressWarnings("deprecation")
    private static int integer(Bundle b, String key, int fallback) {
        Object v = b.get(key);
        if (v instanceof Integer) return (Integer) v;
        if (v instanceof String) {
            try {
                return Integer.parseInt(((String) v).trim());
            } catch (NumberFormatException ignored) {
                // Not a number: the default.
            }
        }
        return fallback;
    }
}
