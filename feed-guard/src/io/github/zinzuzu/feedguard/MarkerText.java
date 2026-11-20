package io.github.zinzuzu.feedguard;

/**
 * The add-on puts a tiny invisible element on every Instagram and Reddit page,
 * labelled "feedguard {unix seconds}", and rewrites the time every two seconds.
 * The element only shows while the add-on's stylesheet is applied, and the time
 * only moves while the add-on's script runs. Turning the add-on off therefore
 * makes the label disappear or go stale.
 *
 * Pure Java, so it can be tested off the phone.
 */
final class MarkerText {
    private MarkerText() {}

    static final String ELEMENT_ID = "rnb-guard-marker";
    static final String PREFIX = "feedguard ";
    /** How old the label may be. The add-on writes it every 2 s, and right away when a tab comes back. */
    static final long MAX_AGE_SECONDS = 12;
    /** Allowance for a label slightly ahead of the phone's clock. */
    static final long MAX_AHEAD_SECONDS = 5;

    /**
     * Add-on 1.1.x has no marker, but on the feed pages it shows its own panel:
     * a div with one of these ids, headed by one of these lines.
     */
    static final String[] PANEL_IDS = {"rnb-ig-cover", "rnb-feed-notice"};
    static final String[] PANEL_TEXTS = {"The feed is turned off.", "The home feed is turned off."};

    static boolean looksLikeFeedPanel(String resourceId, CharSequence text, CharSequence desc) {
        if (resourceId != null) {
            for (String id : PANEL_IDS) if (resourceId.equals(id)) return true;
        }
        for (String t : PANEL_TEXTS) {
            if (text != null && text.toString().trim().startsWith(t)) return true;
            if (desc != null && desc.toString().trim().startsWith(t)) return true;
        }
        return false;
    }

    static boolean looksLikeMarker(CharSequence text) {
        return text != null && text.toString().trim().startsWith(PREFIX);
    }

    /** The label's time in unix seconds, or -1 if it isn't a marker label. */
    static long seconds(CharSequence label) {
        if (!looksLikeMarker(label)) return -1;
        String digits = label.toString().trim().substring(PREFIX.length()).trim();
        if (digits.isEmpty() || digits.length() > 12) return -1;
        for (int i = 0; i < digits.length(); i++) {
            if (!Character.isDigit(digits.charAt(i))) return -1;
        }
        return Long.parseLong(digits);
    }

    /** True if the label is a marker written within the last MAX_AGE_SECONDS. */
    static boolean isFresh(CharSequence label, long nowMillis) {
        long s = seconds(label);
        if (s < 0) return false;
        long now = nowMillis / 1000;
        return s <= now + MAX_AHEAD_SECONDS && now - s <= MAX_AGE_SECONDS;
    }
}
