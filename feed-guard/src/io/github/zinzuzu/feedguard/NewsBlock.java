package io.github.zinzuzu.feedguard;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.os.SystemClock;
import android.text.format.DateFormat;

import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The morning news block on the phone (NewsRules says what it does and holds
 * its settings). GuardService tells it when the screen goes off and when the
 * phone is unlocked; it decides whether that unlock is waking up, and keeps
 * count of the block's time.
 */
final class NewsBlock {
    private NewsBlock() {}

    // Stored state, in Guard.prefs.
    static final String K_IN_USE = "newsInUse";             // unlocked since the screen last went off
    static final String K_IN_USE_BOOT = "newsInUseBoot";     // the start-up count of that unlock
    static final String K_PUT_DOWN_BOOT = "newsDownBoot";    // when the phone was last put down: start-up count,
    static final String K_PUT_DOWN_AT = "newsDownAt";        // ... SystemClock.elapsedRealtime() ms,
    static final String K_PUT_DOWN_WALL = "newsDownWall";    // ... and wall-clock ms
    static final String K_ON = "newsOn";                     // a block is running
    static final String K_LENGTH = "newsLength";             // its length, ms, fixed when it started
    static final String K_COUNTED = "newsCounted";           // block time counted up to the base below, ms
    static final String K_BASE_BOOT = "newsBaseBoot";
    static final String K_BASE_AT = "newsBaseAt";            // SystemClock.elapsedRealtime() ms of that count
    static final String K_STARTED_WALL = "newsStartedWall";  // wall-clock ms it started, for what's shown

    static final int N_NEWS = 8;

    /** The screen went off. If the phone was in use, that's when it was put down. */
    static synchronized void screenOff(Context c) {
        SharedPreferences p = Guard.prefs(c);
        if (!p.getBoolean(K_IN_USE, false)) return; // lit up for a notification only
        putDown(c, p.edit().putBoolean(K_IN_USE, false)).commit();
    }

    /** The phone is shutting down: it was put down now, whatever the screen did. */
    static synchronized void shuttingDown(Context c) {
        SharedPreferences p = Guard.prefs(c);
        putDown(c, p.edit().putBoolean(K_IN_USE, false)).commit();
    }

    private static SharedPreferences.Editor putDown(Context c, SharedPreferences.Editor e) {
        return e.putLong(K_PUT_DOWN_BOOT, BankMode.bootCount(c))
                .putLong(K_PUT_DOWN_AT, SystemClock.elapsedRealtime())
                .putLong(K_PUT_DOWN_WALL, System.currentTimeMillis());
    }

    /**
     * The phone was unlocked. Returns true if that started a block (the caller
     * then pauses the news apps and checks Firefox straight away).
     */
    static synchronized boolean unlocked(Context c) {
        SharedPreferences p = Guard.prefs(c);
        long nowBoot = BankMode.bootCount(c);
        // Already counted this unlock. Unless it was before a restart: then the phone
        // went off without Feed Guard seeing it put down, and this is a new unlock.
        // The put-down stored is then older than the real one, which errs towards blocking.
        if (p.getBoolean(K_IN_USE, false) && p.getLong(K_IN_USE_BOOT, nowBoot) == nowBoot) return false;
        SharedPreferences.Editor e = p.edit().putBoolean(K_IN_USE, true).putLong(K_IN_USE_BOOT, nowBoot);
        Config cfg = Config.read(c);
        long now = SystemClock.elapsedRealtime();
        long wall = System.currentTimeMillis();
        long gap = NewsRules.gapMs(p.getLong(K_PUT_DOWN_BOOT, NewsRules.UNKNOWN_BOOT), p.getLong(K_PUT_DOWN_AT, 0),
                p.getLong(K_PUT_DOWN_WALL, 0), nowBoot, now, wall);
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        boolean start = cfg.newsBlock && !active(c)
                && NewsRules.wokeUp(gap, cfg.newsGapHours, hour, cfg.newsFromHour, cfg.newsUntilHour);
        if (start) {
            e.putBoolean(K_ON, true)
                    .putLong(K_LENGTH, cfg.newsMinutes * 60_000L)
                    .putLong(K_COUNTED, 0)
                    .putLong(K_BASE_BOOT, nowBoot)
                    .putLong(K_BASE_AT, now)
                    .putLong(K_STARTED_WALL, wall);
        }
        e.commit();
        if (start) {
            Guard.notify(c, N_NEWS, "Morning: news is off until " + time(c, endsAt(c)),
                    "Good morning. News sites, news apps and YouTube ReVanced open again in " + cfg.newsMinutes + " minutes.",
                    new Intent(c, MainActivity.class));
            WatchdogJob.newsEndCheck(c, timeLeft(c));
        }
        return start;
    }

    /** Whether a block is running right now. Also stores the time counted, so a restart loses little of it. */
    static synchronized boolean active(Context c) {
        SharedPreferences p = Guard.prefs(c);
        if (!p.getBoolean(K_ON, false)) return false;
        long nowBoot = BankMode.bootCount(c);
        long now = SystemClock.elapsedRealtime();
        long counted = NewsRules.counted(p.getLong(K_COUNTED, 0), p.getLong(K_BASE_BOOT, NewsRules.UNKNOWN_BOOT),
                p.getLong(K_BASE_AT, 0), nowBoot, now);
        long length = p.getLong(K_LENGTH, NewsRules.DEFAULT_MINUTES * 60_000L);
        // Ends when its time is up, or when news_block is turned off in OwnDroid (behind its lock).
        if (counted >= length || !Config.read(c).newsBlock) {
            p.edit().putBoolean(K_ON, false).commit();
            Guard.cancel(c, N_NEWS);
            return false;
        }
        p.edit().putLong(K_COUNTED, counted).putLong(K_BASE_BOOT, nowBoot).putLong(K_BASE_AT, now).apply();
        return true;
    }

    /** Block time left, in ms; 0 if none is running. */
    static long timeLeft(Context c) {
        if (!active(c)) return 0;
        SharedPreferences p = Guard.prefs(c);
        return Math.max(0, p.getLong(K_LENGTH, 0) - p.getLong(K_COUNTED, 0));
    }

    /** When the block ends, as a wall-clock time, if the phone stays on; 0 if none is running. */
    static long endsAt(Context c) {
        long left = timeLeft(c);
        return left > 0 ? System.currentTimeMillis() + left : 0;
    }

    /** The news apps installed now: the news_apps setting, and apps in Android's news category. */
    static Set<String> installedNewsApps(Config cfg, List<ApplicationInfo> apps) {
        Set<String> out = new HashSet<>();
        for (ApplicationInfo ai : apps) {
            if (cfg.newsApps.contains(ai.packageName) || ai.category == ApplicationInfo.CATEGORY_NEWS) {
                out.add(ai.packageName);
            }
        }
        return out;
    }

    static String time(Context c, long wall) {
        return DateFormat.getTimeFormat(c).format(new Date(wall));
    }
}
