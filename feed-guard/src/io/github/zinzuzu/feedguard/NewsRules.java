package io.github.zinzuzu.feedguard;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * The morning news block. When the phone has been left alone for a long
 * stretch (a night's sleep) and is then unlocked in the morning, news sites
 * are covered in Firefox and news apps are paused for the next hour.
 *
 * "Left alone" means the screen stayed off, or only lit up for a notification
 * without being unlocked. The stretch is measured from the last time the
 * screen went off after an unlock, to the next unlock.
 *
 * The hour is counted in time the phone is switched on (Android's time since
 * start-up), not by the clock, so changing the clock doesn't end it early. A
 * restart in the middle doesn't end it either: the time already counted is
 * stored and the count carries on after the restart.
 *
 * Its settings and defaults live here, in one place. Pure Java, so it can be
 * tested off the phone.
 */
final class NewsRules {
    private NewsRules() {}

    /** How long the phone must be left alone to count as a night's sleep, when news_gap_hours isn't set. */
    static final int DEFAULT_GAP_HOURS = 6;
    /** How long the block lasts, when news_minutes isn't set. */
    static final int DEFAULT_MINUTES = 60;
    /** The first unlock counts as waking up only from this hour (local time) ... */
    static final int DEFAULT_FROM_HOUR = 6;
    /** ... until this hour. An afternoon away from the phone doesn't start the block. */
    static final int DEFAULT_UNTIL_HOUR = 12;
    /** Longest block news_minutes can ask for, so a typo can't block news for days. */
    static final int MAX_MINUTES = 240;

    /** What BootCount reads as when Android doesn't say. */
    static final long UNKNOWN_BOOT = BankRules.UNKNOWN_BOOT;

    /**
     * News sites, when news_sites isn't set. Each covers its subdomains too:
     * globo.com covers g1.globo.com and oglobo.globo.com (and Globoplay), uol.com.br
     * covers folha.uol.com.br (and UOL Mail).
     */
    static final Set<String> DEFAULT_SITES = AppRules.set(
            // Brazil
            "globo.com", "uol.com.br", "folha.com.br", "estadao.com.br", "cnnbrasil.com.br",
            "r7.com", "terra.com.br", "metropoles.com", "poder360.com.br", "veja.abril.com.br",
            "exame.com", "cartacapital.com.br", "gazetadopovo.com.br", "infomoney.com.br",
            "band.com.br", "sbtnews.sbt.com.br", "jovempan.com.br", "correiobraziliense.com.br",
            "istoe.com.br", "bbc.com", "bbc.co.uk", "g1.com.br",
            // International
            "news.google.com", "cnn.com", "nytimes.com", "theguardian.com", "reuters.com",
            "apnews.com", "washingtonpost.com", "aljazeera.com", "bloomberg.com", "foxnews.com",
            "nbcnews.com", "economist.com", "elpais.com", "lemonde.fr", "news.yahoo.com",
            "msn.com");

    /**
     * News apps (and YouTube ReVanced), when news_apps isn't set. Apps that call themselves news apps
     * (Android's "news" app category) are paused too, whatever this list says.
     */
    static final Set<String> DEFAULT_APPS = AppRules.set(
            "com.google.android.apps.magazines", // Google News
            "com.globo.g1.app",                  // g1
            "com.cnn.mobile.android.phone",      // CNN
            "mnn.Android",                       // AP News
            "bbc.mobile.news.ww",                // BBC News
            "bbc.mobile.news.uk",
            "com.nytimes.android",
            "com.guardian",
            "com.washingtonpost.rainbow",
            "com.foxnews.android",
            "com.zumobi.msnbc",                  // NBC News
            "com.bloomberg.android.plus",
            "com.microsoft.amp.apps.bingnews",   // Microsoft Start
            // Not news, but no better first hour either: YouTube ReVanced, and the
            // forks with their own package names (ReVanced Extended, Vanced).
            "app.revanced.android.youtube",
            "app.rvx.android.youtube",
            "com.vanced.android.youtube");

    /**
     * A managed-configuration list, or its default when it was never set.
     * "none" (or an empty value) means an empty list.
     */
    static Set<String> listOrDefault(String value, Set<String> fallback) {
        if (value == null) return fallback;
        String v = value.trim();
        if (v.isEmpty() || v.toLowerCase(Locale.ROOT).equals("none")) return Collections.emptySet();
        Set<String> out = new HashSet<>();
        for (String s : AppRules.parseList(v.toLowerCase(Locale.ROOT))) {
            String h = s.replaceAll("^[a-z]+://", "").replaceAll("^www\\.", "");
            int slash = h.indexOf('/');
            if (slash >= 0) h = h.substring(0, slash);
            if (!h.isEmpty()) out.add(h);
        }
        return out;
    }

    /** Package names keep their case, so apps are parsed without lower-casing. */
    static Set<String> appsOrDefault(String value) {
        if (value == null) return DEFAULT_APPS;
        String v = value.trim();
        if (v.isEmpty() || v.toLowerCase(Locale.ROOT).equals("none")) return Collections.emptySet();
        return AppRules.parseList(v);
    }

    /** True if the host is one of the sites or a subdomain of one. */
    static boolean isNewsHost(String host, Set<String> sites) {
        if (host == null || sites == null) return false;
        String h = host.toLowerCase(Locale.ROOT);
        while (h.endsWith(".")) h = h.substring(0, h.length() - 1);
        for (String s : sites) {
            if (h.equals(s) || h.endsWith("." + s)) return true;
        }
        return false;
    }

    /** Whether boot counts and elapsed times say "the same start-up". */
    static boolean sameBoot(long thenBoot, long thenElapsed, long nowBoot, long nowElapsed) {
        if (nowElapsed < thenElapsed) return false; // time since start-up went back: a restart
        if (thenBoot == UNKNOWN_BOOT || nowBoot == UNKNOWN_BOOT) return true;
        return thenBoot == nowBoot;
    }

    /**
     * How long the phone was left alone, in ms, or -1 if Feed Guard never saw
     * it put down. Within one start-up it's the time since start-up, which the
     * clock can't change. Across a restart it's the clock, but never less than
     * the time since this start-up.
     */
    static long gapMs(long lastBoot, long lastElapsed, long lastWall,
            long nowBoot, long nowElapsed, long nowWall) {
        if (lastWall <= 0) return -1;
        if (sameBoot(lastBoot, lastElapsed, nowBoot, nowElapsed)) return nowElapsed - lastElapsed;
        return Math.max(nowElapsed, nowWall - lastWall);
    }

    /** Whether an hour of the day (0-23) is in the morning window [from, until). from == until means all day. */
    static boolean inWindow(int hour, int from, int until) {
        if (from == until) return true;
        if (from < until) return hour >= from && hour < until;
        return hour >= from || hour < until; // a window across midnight
    }

    /** Whether this unlock is waking up: left alone long enough, and in the morning window. */
    static boolean wokeUp(long gapMs, int gapHours, int hour, int from, int until) {
        return gapMs >= 0 && gapMs >= gapHours * 3_600_000L && inWindow(hour, from, until);
    }

    /**
     * Block time counted so far, in ms: what was stored, plus the time since
     * it was stored if there was no restart in between.
     */
    static long counted(long storedMs, long baseBoot, long baseElapsed, long nowBoot, long nowElapsed) {
        if (sameBoot(baseBoot, baseElapsed, nowBoot, nowElapsed)) return storedMs + (nowElapsed - baseElapsed);
        return storedMs;
    }

    /** Clamps a whole-number setting into its range. */
    static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
