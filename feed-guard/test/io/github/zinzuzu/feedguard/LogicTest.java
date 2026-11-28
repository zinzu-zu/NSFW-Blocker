package io.github.zinzuzu.feedguard;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Tests for the parts of Feed Guard that don't need a phone: reading the
 * address bar, the add-on's marker, the cover decision, the app rules (with
 * "Only apps on the list"), when colour is allowed, the accessibility
 * services list, banking mode, signature pins and the morning news block.
 * Run with test/run.sh. No test framework, so it runs on a plain JDK.
 */
public final class LogicTest {
    private static int passed, failed;

    public static void main(String[] args) {
        urlText();
        markerText();
        coverDecision();
        feedPanelRule();
        appRules();
        listRule();
        reinstalls();
        colourRules();
        serviceList();
        bankServices();
        bankRules();
        bankTexts();
        pins();
        newsRules();
        newsAppRule();
        newsWhy();
        newsReceiver();
        torRule();
        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    // --- Address bar -------------------------------------------------------

    static void urlText() {
        // Compose toolbar: "{title} {url}. {hint}"
        eq("compose, feed", "instagram.com", UrlText.host("Instagram instagram.com/. Search or enter address"));
        eq("compose, full url", "www.reddit.com",
                UrlText.host("Reddit - Dive into anything https://www.reddit.com/. Search or enter address"));
        eq("compose, no title", "www.instagram.com", UrlText.host(" https://www.instagram.com/direct/inbox/. Search or enter address"));
        eq("compose, home screen shows only the hint", null, UrlText.host("Search or enter address"));
        eq("title mentioning another site doesn't win", "example.org",
                UrlText.host("Why I quit reddit.com example.org/post. Search or enter address"));
        eq("German hint with z.B.", "instagram.com", UrlText.host("Instagram instagram.com. Suche z.B. oder Adresse eingeben"));
        // View-based toolbar: plain text
        eq("plain host", "instagram.com", UrlText.host("instagram.com"));
        eq("plain url", "www.instagram.com", UrlText.host("https://www.instagram.com/direct/inbox/"));
        eq("view-source", "www.instagram.com", UrlText.host("view-source:https://www.instagram.com/"));
        eq("port", "instagram.com", UrlText.host("instagram.com:443/explore/"));
        eq("user info", "www.reddit.com", UrlText.host("https://user@www.reddit.com/r/pics"));
        eq("quoted", "instagram.com", UrlText.host("“instagram.com”"));
        eq("about page", null, UrlText.host("about:blank"));
        eq("add-on page", null, UrlText.host("moz-extension://4f1c2d3e-aaaa-bbbb-cccc-1234567890ab/blocked.html"));
        eq("search words", null, UrlText.host("reddit nsfw"));
        eq("null", null, UrlText.host(null));

        check("guarded: instagram.com", UrlText.isGuardedHost("instagram.com"));
        check("guarded: www.instagram.com", UrlText.isGuardedHost("www.instagram.com"));
        check("guarded: l.instagram.com", UrlText.isGuardedHost("l.instagram.com"));
        check("guarded: reddit.com", UrlText.isGuardedHost("reddit.com"));
        check("guarded: old.reddit.com", UrlText.isGuardedHost("old.reddit.com"));
        check("not guarded: notinstagram.com", !UrlText.isGuardedHost("notinstagram.com"));
        check("not guarded: ddinstagram.com", !UrlText.isGuardedHost("ddinstagram.com"));
        check("not guarded: instagram.com.evil.net", !UrlText.isGuardedHost("instagram.com.evil.net"));
        check("not guarded: redditmedia.com", !UrlText.isGuardedHost("redditmedia.com"));
        check("not guarded: null", !UrlText.isGuardedHost(null));

        check("title: Instagram", UrlText.titleLooksGuarded("Instagram"));
        check("title: Reddit home", UrlText.titleLooksGuarded("Reddit - Dive into anything"));
        check("title: profile", UrlText.titleLooksGuarded("Ana (@ana) • Instagram photos and videos"));
        check("title: AskReddit isn't a match", !UrlText.titleLooksGuarded("AskReddit"));
        check("title: inbox", !UrlText.titleLooksGuarded("Inbox • Direct"));
        check("title: null", !UrlText.titleLooksGuarded(null));
    }

    // --- Marker ------------------------------------------------------------

    static void markerText() {
        long now = 1_800_000_000_000L; // ms
        long s = now / 1000;
        check("fresh: now", MarkerText.isFresh("feedguard " + s, now));
        check("fresh: 12 s old", MarkerText.isFresh("feedguard " + (s - 12), now));
        check("stale: 13 s old", !MarkerText.isFresh("feedguard " + (s - 13), now));
        check("fresh: 5 s ahead", MarkerText.isFresh("feedguard " + (s + 5), now));
        check("stale: 6 s ahead", !MarkerText.isFresh("feedguard " + (s + 6), now));
        check("not a marker: text", !MarkerText.isFresh("feedguard abc", now));
        check("not a marker: empty", !MarkerText.isFresh("feedguard ", now));
        check("not a marker: other", !MarkerText.isFresh("Instagram", now));
        check("not a marker: null", !MarkerText.isFresh(null, now));
        check("not a marker: negative", !MarkerText.isFresh("feedguard -5", now));
    }

    // --- Cover decision ----------------------------------------------------

    static final long WALL = 1_800_000_000_000L;

    static CoverDecision.Input page(String url, boolean marker, boolean armed, long t) {
        CoverDecision.Input in = new CoverDecision.Input();
        in.firefoxVisible = true;
        in.urlBoxFound = url != null;
        in.urlText = url;
        in.markerLabel = marker ? "feedguard " + ((WALL + t) / 1000) : null;
        in.armed = armed;
        in.nowElapsedMs = 10_000 + t;
        in.nowWallMs = WALL + t;
        return in;
    }

    static final String IG = "Instagram instagram.com/. Search or enter address";
    static final String IG_DM = "Inbox • Direct instagram.com/direct/inbox/. Search or enter address";
    static final String OTHER = "Example example.org/. Search or enter address";

    static void coverDecision() {
        CoverDecision d = new CoverDecision();
        CoverDecision.Outcome o = d.decide(page(IG, false, false, 0));
        check("not armed: no cover", !o.cover && !o.arm && o.guardedPage);
        o = d.decide(page(IG, false, false, 60_000));
        check("not armed: still no cover later", !o.cover);

        d = new CoverDecision();
        o = d.decide(page(IG, true, false, 0));
        check("first fresh marker arms", o.arm && !o.cover && o.markerFresh);

        d = new CoverDecision();
        check("armed, marker missing: grace at 0 s", !d.decide(page(IG, false, true, 0)).cover);
        check("armed, marker missing: grace at 2.4 s", !d.decide(page(IG, false, true, 2400)).cover);
        check("armed, marker missing: covered at 2.5 s", d.decide(page(IG, false, true, 2500)).cover);
        check("covered while marker stays missing", d.decide(page(IG_DM, false, true, 5000)).cover);
        check("marker back: uncovered", !d.decide(page(IG, true, true, 6000)).cover);

        d = new CoverDecision();
        CoverDecision.Input in = page(IG, false, true, 0);
        in.loading = true;
        d.decide(in);
        in = page(IG, false, true, 7900);
        in.loading = true;
        check("loading: longer grace", !d.decide(in).cover);
        in = page(IG, false, true, 8000);
        in.loading = true;
        check("loading: covered after 8 s", d.decide(in).cover);
        check("covered stays covered when loading starts again", d.decide(page(IG, false, true, 8100)).cover);

        d = new CoverDecision();
        d.decide(page(OTHER, false, true, 0));
        check("other site: never covered", !d.decide(page(OTHER, false, true, 30_000)).cover);

        d = new CoverDecision();
        d.decide(page(IG, false, true, 0));
        d.decide(page(IG, false, true, 3000));
        in = page(IG, false, true, 4000);
        in.firefoxVisible = false;
        check("Firefox off screen: no cover", !d.decide(in).cover);
        check("same page again: covered at once", d.decide(page(IG, false, true, 5000)).cover);
        check("a different guarded page gets the grace", !new CoverDecision().decide(page(IG_DM, false, true, 0)).cover);

        // Address bar briefly missing (full-screen video): the last reading holds for 15 s.
        d = new CoverDecision();
        d.decide(page(IG, false, true, 0));
        check("covered", d.decide(page(IG, false, true, 3000)).cover);
        in = page(null, false, true, 10_000);
        in.pageTitle = "Inbox • Direct";
        check("address bar gone: memory keeps it covered", d.decide(in).cover);
        in = page(null, false, true, 19_000);
        in.pageTitle = "Instagram";
        check("memory expired: title says Instagram", d.decide(in).cover);
        in = page(null, false, true, 20_000);
        in.pageTitle = "Inbox • Direct";
        check("memory expired: other title isn't covered", !d.decide(in).cover);

        // Address bar never readable: title fallback.
        d = new CoverDecision();
        in = page(null, false, true, 0);
        in.pageTitle = "Reddit - Dive into anything";
        d.decide(in);
        in = page(null, false, true, 3000);
        in.pageTitle = "Reddit - Dive into anything";
        check("no address bar: Reddit title covered", d.decide(in).cover);

        // Home screen: the address bar only shows the hint.
        d = new CoverDecision();
        d.decide(page(IG, false, true, 0));
        d.decide(page(IG, false, true, 3000));
        check("new tab: uncovered", !d.decide(page("Search or enter address", false, true, 3500)).cover);

        // Stale marker counts as missing.
        d = new CoverDecision();
        in = page(IG, true, true, 0);
        d.decide(in);
        in = page(IG, false, true, 1000);
        in.markerLabel = "feedguard " + ((WALL - 20_000) / 1000);
        d.decide(in);
        in = page(IG, false, true, 3600);
        in.markerLabel = "feedguard " + ((WALL - 20_000) / 1000);
        check("stale marker: covered", d.decide(in).cover);
    }

    // --- Add-on 1.1.x: the feed panel -------------------------------------

    static CoverDecision.Input panelPage(String url, boolean panel, boolean panelArmed, long t) {
        CoverDecision.Input in = page(url, false, false, t);
        in.feedPanel = panel;
        in.panelArmed = panelArmed;
        return in;
    }

    static void feedPanelRule() {
        check("path: home", "/".equals(UrlText.path("Instagram instagram.com/. Search or enter address")));
        check("path: with query", "/explore/".equals(UrlText.path("https://www.instagram.com/explore/?hl=en")));
        check("path: bare host is unknown", UrlText.path("Instagram instagram.com. Search or enter address") == null);
        check("path: none for hint only", UrlText.path("Search or enter address") == null);
        check("feed: Instagram home", UrlText.isFeedPage("www.instagram.com", "/"));
        check("feed: Instagram Explore", UrlText.isFeedPage("instagram.com", "/explore/"));
        check("feed: Instagram Reels tab", UrlText.isFeedPage("instagram.com", "/reels/"));
        check("not feed: a reel", !UrlText.isFeedPage("instagram.com", "/reels/abc123/"));
        check("not feed: a profile", !UrlText.isFeedPage("instagram.com", "/someone/"));
        check("not feed: messages", !UrlText.isFeedPage("instagram.com", "/direct/inbox/"));
        check("feed: Reddit front page", UrlText.isFeedPage("www.reddit.com", "/"));
        check("feed: Reddit best", UrlText.isFeedPage("reddit.com", "/best/"));
        check("feed: r/popular", UrlText.isFeedPage("reddit.com", "/r/popular/"));
        check("feed: r/all/top", UrlText.isFeedPage("old.reddit.com", "/r/all/top"));
        check("not feed: a subreddit", !UrlText.isFeedPage("reddit.com", "/r/askreddit/"));
        check("not feed: unknown path", !UrlText.isFeedPage("reddit.com", null));
        check("not feed: other site", !UrlText.isFeedPage("example.org", "/"));
        check("panel by id", MarkerText.looksLikeFeedPanel("rnb-ig-cover", null, null));
        check("panel by text", MarkerText.looksLikeFeedPanel(null, "The home feed is turned off.", null));
        check("not a panel", !MarkerText.looksLikeFeedPanel("main", "The feed", null));

        final String REDDIT = "Reddit reddit.com/. Search or enter address";
        final String SUB = "r/askreddit reddit.com/r/askreddit/. Search or enter address";
        final String IG_BARE = "Instagram instagram.com. Search or enter address";

        CoverDecision d = new CoverDecision();
        CoverDecision.Outcome o = d.decide(panelPage(IG, false, false, 0));
        d.decide(panelPage(IG, false, false, 0));
        o = d.decide(panelPage(IG, false, false, 60_000));
        check("1.1.x: not armed, no panel: no cover", !o.cover && !o.armPanel);

        d = new CoverDecision();
        o = d.decide(panelPage(IG, true, false, 0));
        check("1.1.x: first panel arms the panel rule", o.armPanel && !o.arm && !o.cover);

        d = new CoverDecision();
        check("1.1.x: panel there: no cover", !d.decide(panelPage(IG, true, true, 0)).cover
                && !d.decide(panelPage(IG, true, true, 30_000)).cover);

        d = new CoverDecision();
        d.decide(panelPage(IG, false, true, 0));
        check("1.1.x: panel missing: grace", !d.decide(panelPage(IG, false, true, 2400)).cover);
        check("1.1.x: panel missing on the feed: covered", d.decide(panelPage(IG, false, true, 2500)).cover);
        check("1.1.x: leaving for messages uncovers", !d.decide(panelPage(IG_DM, false, true, 4000)).cover);
        check("1.1.x: messages never covered", !d.decide(panelPage(IG_DM, false, true, 60_000)).cover);

        d = new CoverDecision();
        d.decide(panelPage(REDDIT, false, true, 0));
        check("1.1.x: Reddit front page without panel: covered", d.decide(panelPage(REDDIT, false, true, 3000)).cover);
        d = new CoverDecision();
        d.decide(panelPage(SUB, false, true, 0));
        check("1.1.x: a subreddit isn't checked", !d.decide(panelPage(SUB, false, true, 30_000)).cover);

        d = new CoverDecision();
        d.decide(panelPage(IG_BARE, false, true, 0));
        check("1.1.x: bare host, page unknown: not covered", !d.decide(panelPage(IG_BARE, false, true, 30_000)).cover);

        d = new CoverDecision();
        d.decide(panelPage(IG, false, true, 0));
        d.decide(panelPage(IG, false, true, 3000));
        CoverDecision.Input in = panelPage(null, false, true, 5000);
        in.pageTitle = "Instagram";
        check("1.1.x: no address bar: not judged", !d.decide(in).cover);

        // With 1.2.0's marker, the marker rule wins.
        d = new CoverDecision();
        in = page(IG, true, true, 0);
        in.panelArmed = true;
        check("marker fresh, no panel: no cover", !d.decide(in).cover);
        in = page(IG, true, true, 30_000);
        in.panelArmed = true;
        check("still no cover", !d.decide(in).cover);
    }

    // --- App rules ---------------------------------------------------------

    static void appRules() {
        Set<String> installed = set("org.mozilla.firefox", "com.android.chrome", "org.mozilla.focus",
                "com.instagram.android", "com.instagram.barcelona", "com.instagram.lite", "com.whatsapp",
                "io.github.zinzuzu.feedguard", "com.reddit.frontpage", "ml.docilealligator.infinityforreddit",
                "org.quantumbadger.redreader", "com.android.vending", "com.bintianqi.owndroid", "com.google.android.webview");
        Set<String> browsers = set("org.mozilla.firefox", "com.android.chrome", "org.mozilla.focus", "com.android.vending");
        Set<String> none = Collections.emptySet();
        eq("pause list", new TreeSet<>(Arrays.asList("com.android.chrome", "com.instagram.android", "com.instagram.lite",
                        "com.reddit.frontpage", "ml.docilealligator.infinityforreddit", "org.mozilla.focus", "org.quantumbadger.redreader")),
                AppRules.toPause(installed, browsers, none, none, "io.github.zinzuzu.feedguard"));
        check("allowed app is left alone",
                !AppRules.toPause(installed, browsers, set("com.android.chrome"), none, "io.github.zinzuzu.feedguard").contains("com.android.chrome"));
        check("extra app is paused",
                AppRules.toPause(installed, browsers, none, set("com.whatsapp"), "io.github.zinzuzu.feedguard").contains("com.whatsapp"));
        check("Firefox is never paused, even if listed",
                !AppRules.toPause(installed, browsers, none, set("org.mozilla.firefox"), "io.github.zinzuzu.feedguard").contains("org.mozilla.firefox"));
        check("Threads is left alone", !AppRules.isFeedApp("com.instagram.barcelona"));
        eq("parse list", set("a.b", "c.d", "e.f", "g.h"), AppRules.parseList(" a.b, c.d;e.f  g.h "));
        eq("parse empty", Collections.<String>emptySet(), AppRules.parseList("  "));
    }

    // --- Only apps on the list ---------------------------------------------

    static final String SELF = "io.github.zinzuzu.feedguard";

    /** A phone like the user's Pixel: what's installed, what came with it, what's in the app drawer. */
    static AppRules.Inputs phone() {
        AppRules.Inputs in = new AppRules.Inputs();
        in.installed = set("org.mozilla.firefox", "com.android.chrome", "org.mozilla.focus", "com.instagram.android",
                "com.whatsapp", "org.telegram.messenger", "com.anthropic.claude", "com.google.android.apps.maps",
                "com.google.android.youtube", "com.google.android.calculator", "com.android.vending",
                "com.google.android.googlequicksearchbox", "com.android.settings", "com.bintianqi.owndroid",
                "io.github.zinzuzu.watchlimit", SELF, "com.google.android.gms", "com.example.sharetarget",
                "com.google.android.apps.nexuslauncher", "com.google.android.dialer", "com.google.android.apps.messaging",
                "com.google.android.contacts", "com.google.android.deskclock", "com.google.android.inputmethod.latin",
                "com.google.android.documentsui", "com.android.providers.media", "helium314.keyboard",
                "com.google.android.apps.photos", "com.google.android.GoogleCamera");
        // Came with the phone: everything except these.
        in.userInstalled = set("org.mozilla.firefox", "org.mozilla.focus", "com.instagram.android", "com.whatsapp",
                "org.telegram.messenger", "com.anthropic.claude", "com.bintianqi.owndroid", "io.github.zinzuzu.watchlimit",
                SELF, "com.example.sharetarget", "helium314.keyboard");
        in.launchable = set("org.mozilla.firefox", "com.android.chrome", "org.mozilla.focus", "com.instagram.android",
                "com.whatsapp", "org.telegram.messenger", "com.anthropic.claude", "com.google.android.apps.maps",
                "com.google.android.youtube", "com.google.android.calculator", "com.android.vending",
                "com.google.android.googlequicksearchbox", "com.android.settings", "com.bintianqi.owndroid",
                "io.github.zinzuzu.watchlimit", SELF, "com.google.android.dialer", "com.google.android.apps.messaging",
                "com.google.android.contacts", "com.google.android.deskclock", "com.google.android.apps.photos",
                "com.google.android.GoogleCamera", "helium314.keyboard");
        in.browsers = set("org.mozilla.firefox", "com.android.chrome", "org.mozilla.focus");
        // HeliBoard is the keyboard in use, so AppGate counts it as a basic.
        in.essential = set(SELF, "helium314.keyboard");
        return in;
    }

    static void listRule() {
        AppRules.Inputs in = phone();
        AppRules.Plan plan = AppRules.plan(in);
        eq("list off: only the browser rule", set("com.android.chrome", "org.mozilla.focus", "com.instagram.android"),
                new HashSet<>(plan.pause));
        check("list off: nothing for the list rule", plan.listRule.isEmpty());

        in.listMode = true;
        in.allowed = set("com.whatsapp", "org.telegram.messenger", "com.anthropic.claude", "com.google.android.apps.maps",
                "com.google.android.calculator");
        plan = AppRules.plan(in);
        eq("list on: everything else in the drawer, and what you installed",
                set("org.mozilla.firefox", "com.android.chrome", "org.mozilla.focus", "com.instagram.android",
                        "com.google.android.youtube", "com.android.vending", "com.google.android.googlequicksearchbox",
                        "com.example.sharetarget", "com.google.android.apps.photos", "com.google.android.GoogleCamera"),
                new HashSet<>(plan.pause));
        check("list on: listed apps open", !plan.pause.contains("com.whatsapp") && !plan.pause.contains("com.google.android.apps.maps"));
        check("list on: the basics stay", !plan.pause.contains("com.google.android.dialer")
                && !plan.pause.contains("com.google.android.apps.messaging") && !plan.pause.contains("com.google.android.contacts")
                && !plan.pause.contains("com.google.android.deskclock") && !plan.pause.contains("com.android.settings")
                && !plan.pause.contains("com.google.android.documentsui"));
        check("list on: the lock stays", !plan.pause.contains("com.bintianqi.owndroid")
                && !plan.pause.contains("io.github.zinzuzu.watchlimit") && !plan.pause.contains(SELF));
        check("list on: the keyboard in use stays", !plan.pause.contains("helium314.keyboard"));
        check("list on: system parts without an icon stay", !plan.pause.contains("com.google.android.gms")
                && !plan.pause.contains("com.android.providers.media"));
        check("list on: Firefox is paused unless listed", plan.pause.contains("org.mozilla.firefox"));
        check("list on: Play Store is paused unless listed", plan.listRule.contains("com.android.vending"));

        in.allowed = set("com.whatsapp", "org.mozilla.firefox");
        check("list on: listed Firefox opens", !AppRules.plan(in).pause.contains("org.mozilla.firefox"));

        in.allowed = set("com.whatsapp", "com.bintianqi.owndroid");
        in.extraBlocked = set("com.bintianqi.owndroid", "com.google.android.dialer");
        plan = AppRules.plan(in);
        check("basics can't be paused, even as extra apps", !plan.pause.contains("com.bintianqi.owndroid")
                && !plan.pause.contains("com.google.android.dialer"));
        in.extraBlocked = Collections.emptySet();

        // The browser rule pauses once; the list rule keeps pausing.
        in = phone();
        in.handled = set("com.android.chrome");
        check("list off: Chrome unpaused in OwnDroid stays unpaused", !AppRules.plan(in).pause.contains("com.android.chrome"));
        in.listMode = true;
        check("list on: Chrome is paused again", AppRules.plan(in).pause.contains("com.android.chrome"));

        // Lifting Feed Guard's own pauses.
        in = phone();
        in.listMode = true;
        in.allowed = set("com.whatsapp");
        in.ours = set("com.whatsapp", "com.google.android.youtube", "com.android.chrome", "gone.app");
        plan = AppRules.plan(in);
        eq("list on: lifts only what's now listed", set("com.whatsapp"), new HashSet<>(plan.release));
        in.listMode = false;
        plan = AppRules.plan(in);
        eq("list off: lifts list pauses, keeps the browser rule's", set("com.whatsapp", "com.google.android.youtube"),
                new HashSet<>(plan.release));
        check("never lifts an uninstalled app", !plan.release.contains("gone.app"));

        in = phone();
        in.ours = set("com.whatsapp");
        in.extraBlocked = set("com.whatsapp");
        check("extra app stays paused", AppRules.plan(in).release.isEmpty());
        in.extraBlocked = Collections.emptySet();
        check("extra app taken off: lifted", AppRules.plan(in).release.contains("com.whatsapp"));

        // The dead-man switch holds Firefox: neither paused nor lifted by the rules.
        in = phone();
        in.listMode = true;
        in.ours = set("org.mozilla.firefox");
        in.allowed = set("org.mozilla.firefox");
        in.skip = set("org.mozilla.firefox");
        plan = AppRules.plan(in);
        check("dead-man Firefox isn't lifted", !plan.release.contains("org.mozilla.firefox"));
        in.allowed = Collections.emptySet();
        check("dead-man Firefox isn't paused twice", !AppRules.plan(in).pause.contains("org.mozilla.firefox"));

        // Signature pins take an app off the list (AppGate removes it from allowed).
        in = phone();
        in.listMode = true;
        in.allowed = set("com.google.android.apps.maps");
        check("a repackaged WhatsApp is paused", AppRules.plan(in).pause.contains("com.whatsapp"));
    }

    // --- Reinstalled apps --------------------------------------------------

    static void reinstalls() {
        Map<String, Long> before = new HashMap<>();
        before.put("org.mozilla.focus", 1_000L);
        before.put("com.android.chrome", 2_000L);
        before.put("com.instagram.android", 3_000L);
        before.put("com.example.gone", 4_000L);
        Map<String, Long> now = new HashMap<>();
        now.put("org.mozilla.focus", 9_000L); // uninstalled and installed again
        now.put("com.android.chrome", 2_000L); // only updated: same time
        now.put("com.instagram.android", 0L); // Android didn't say
        now.put("com.whatsapp", 5_000L); // not seen before
        Set<String> again = AppRules.reinstalled(before, now);
        eq("reinstalled: only the app with a new first-install time", set("org.mozilla.focus"), new HashSet<>(again));
        eq("reinstalled: nothing stored yet", 0, AppRules.reinstalled(new HashMap<String, Long>(), now).size());

        // The gap it closes: a browser the rule paused once (handled), reinstalled
        // while the service was off and didn't hear it, keeps its pause only if it
        // counts as new again.
        AppRules.Inputs in = phone();
        in.handled = set("com.android.chrome", "org.mozilla.focus", "com.instagram.android");
        in.ours = set("com.android.chrome", "org.mozilla.focus", "com.instagram.android");
        check("a handled browser isn't paused a second time", !AppRules.plan(in).pause.contains("org.mozilla.focus"));
        in.handled.removeAll(again);
        in.ours.removeAll(again);
        AppRules.Plan plan = AppRules.plan(in);
        check("a reinstalled browser is paused again", plan.pause.contains("org.mozilla.focus"));
        check("an updated one isn't", !plan.pause.contains("com.android.chrome"));
        check("and no pause is lifted", plan.release.isEmpty());

        Map<String, Long> parsed = AppRules.parseTimes(
                Arrays.asList("org.mozilla.focus=9000", "broken", "=5", "a.b=", "a.c=x", "a.d=-3", "a.e=0", null));
        Map<String, Long> want = new HashMap<>();
        want.put("org.mozilla.focus", 9_000L);
        eq("times: good entries only", want, new HashMap<>(parsed));
        eq("times: round trip", parsed, AppRules.parseTimes(AppRules.formatTimes(parsed)));
        eq("times: nothing stored", 0, AppRules.parseTimes(null).size());
        eq("times: unknown ones aren't stored", 0, AppRules.formatTimes(Collections.singletonMap("a.b", 0L)).size());
    }

    // --- Colour ------------------------------------------------------------

    static final long SCREEN = 1080L * 2400;
    static final long BAR = 1080L * 80;
    static final Set<String> MAPS = ColorRules.DEFAULT_COLOUR_APPS;

    static ColorRules.Win app(String pkg) {
        return new ColorRules.Win(ColorRules.APP, pkg, SCREEN);
    }

    static ColorRules.Win win(int kind, long area) {
        return new ColorRules.Win(kind, null, area);
    }

    static boolean colour(ColorRules.Win... windows) {
        return ColorRules.colourAllowed(new ArrayList<>(Arrays.asList(windows)), SCREEN, MAPS);
    }

    static void colourRules() {
        ColorRules.Win statusBar = win(ColorRules.SYSTEM, BAR);
        ColorRules.Win navBar = win(ColorRules.SYSTEM, BAR);
        check("Maps alone: colour", colour(app("com.google.android.apps.maps"), statusBar, navBar));
        check("CoMaps alone: colour", colour(app("app.comaps.google"), statusBar));
        check("Organic Maps alone: colour", colour(app("app.organicmaps")));
        check("WhatsApp: black and white", !colour(app("com.whatsapp"), statusBar, navBar));
        check("home screen with Maps in a corner: black and white",
                !colour(new ColorRules.Win(ColorRules.APP, "com.google.android.apps.maps", SCREEN / 9),
                        app("com.google.android.apps.nexuslauncher")));
        check("Maps next to WhatsApp: black and white", !colour(app("com.google.android.apps.maps"), app("com.whatsapp")));
        check("Maps next to CoMaps: colour", colour(app("com.google.android.apps.maps"), app("app.comaps.fdroid")));
        check("keyboard over Maps: black and white",
                !colour(app("com.google.android.apps.maps"), win(ColorRules.KEYBOARD, SCREEN / 3)));
        check("shade over Maps: black and white", !colour(app("com.google.android.apps.maps"), win(ColorRules.SYSTEM, SCREEN)));
        check("half-screen overlay: black and white",
                !colour(app("com.google.android.apps.maps"), win(ColorRules.SYSTEM, SCREEN / 2)));
        check("small chat bubble: still colour",
                colour(app("com.google.android.apps.maps"), win(ColorRules.SYSTEM, 200L * 200)));
        check("Feed Guard's cover ignored", colour(app("com.google.android.apps.maps"), win(ColorRules.IGNORED, SCREEN)));
        check("lock screen: black and white", !colour(win(ColorRules.SYSTEM, SCREEN)));
        check("app unreadable: black and white", !colour(new ColorRules.Win(ColorRules.APP, null, SCREEN)));
        check("nothing on screen: black and white", !colour());
        check("windows unreadable: black and white", !ColorRules.colourAllowed(null, SCREEN, MAPS));
        check("no colour apps: black and white",
                !ColorRules.colourAllowed(Collections.singletonList(app("com.google.android.apps.maps")), SCREEN,
                        Collections.<String>emptySet()));
        check("screen size unknown: black and white",
                !ColorRules.colourAllowed(Collections.singletonList(app("com.google.android.apps.maps")), 0, MAPS));

        eq("colour apps unset: maps", MAPS, ColorRules.colourApps(null));
        check("default includes Google Maps", MAPS.contains("com.google.android.apps.maps"));
        check("default includes CoMaps from Play and F-Droid",
                MAPS.contains("app.comaps.google") && MAPS.contains("app.comaps.fdroid"));
        eq("colour apps cleared: none", Collections.<String>emptySet(), ColorRules.colourApps("  "));
        eq("colour apps \"none\": none", Collections.<String>emptySet(), ColorRules.colourApps(" None "));
        eq("colour apps set", set("net.osmand", "com.google.android.apps.maps"),
                ColorRules.colourApps("net.osmand, com.google.android.apps.maps"));
    }

    // --- Accessibility services list ----------------------------------------

    static void serviceList() {
        String me = "io.github.zinzuzu.feedguard/io.github.zinzuzu.feedguard.GuardService";
        String other = "io.github.zinzuzu.watchlimit/io.github.zinzuzu.watchlimit.WatchService";
        eq("empty list: just Feed Guard", me, ServiceList.withService(null, me));
        eq("blank list: just Feed Guard", me, ServiceList.withService("", me));
        eq("added after the others", other + ":" + me, ServiceList.withService(other, me));
        eq("stray colons dropped", other + ":" + me, ServiceList.withService(":" + other + ": :", me));
        eq("already there: nothing to do", null, ServiceList.withService(other + ":" + me, me));
        eq("short form counts", null, ServiceList.withService("io.github.zinzuzu.feedguard/.GuardService", me));
        eq("spaces around it count", null, ServiceList.withService(" " + other + " : " + me + " ", me));
        check("a similar name isn't Feed Guard", !ServiceList.contains("io.github.zinzuzu.feedguard/.GuardServiceX", me));
        check("another package isn't Feed Guard", !ServiceList.contains("io.github.zinzuzu.feedguardx/.GuardService", me));
        check("garbage isn't Feed Guard", !ServiceList.contains("garbage", me));
        check("null isn't Feed Guard", !ServiceList.contains(null, me));
        eq("package of a keyboard", "helium314.keyboard", ServiceList.packageOf("helium314.keyboard/.latin.LatinIME"));
        eq("package of nothing", null, ServiceList.packageOf(null));
        eq("package of garbage", null, ServiceList.packageOf("nonsense"));
    }

    // --- Signature pins -----------------------------------------------------

    // --- Banking mode ------------------------------------------------------

    static final String FG = "io.github.zinzuzu.feedguard/io.github.zinzuzu.feedguard.GuardService";
    static final String WL = "io.github.zinzuzu.watchlimit/io.github.zinzuzu.watchlimit.WatchService";
    static final String TALKBACK = "com.google.android.marvin.talkback/.TalkBackService";

    /** Switching only Feed Guard and Watch Limit off, and putting back exactly what was there. */
    static void bankServices() {
        Set<String> ours = BankRules.GUARD_PACKAGES;
        String[] s = ServiceList.without(TALKBACK + ":" + FG + ":" + WL, ours);
        eq("bank: others stay on", TALKBACK, s[0]);
        eq("bank: both guards taken off, as written", FG + ":" + WL, s[1]);

        String[] shortForm = ServiceList.without("io.github.zinzuzu.feedguard/.GuardService", ours);
        eq("bank: short form taken off too", "", shortForm[0]);
        eq("bank: short form kept as written", "io.github.zinzuzu.feedguard/.GuardService", shortForm[1]);

        String[] none = ServiceList.without(TALKBACK, ours);
        eq("bank: nothing of ours: list unchanged", TALKBACK, none[0]);
        eq("bank: nothing of ours: nothing taken", "", none[1]);
        eq("bank: empty list", "", ServiceList.without(null, ours)[0]);
        eq("bank: stray colons dropped", TALKBACK, ServiceList.without(":" + TALKBACK + "::" + FG + ":", ours)[0]);
        eq("bank: a similar package isn't ours", "io.github.zinzuzu.feedguardx/.X",
                ServiceList.without("io.github.zinzuzu.feedguardx/.X", ours)[0]);
        eq("bank: garbage is left alone", "garbage", ServiceList.without("garbage", ours)[0]);

        // Round trip: off, then back on, gives every service back.
        String before = TALKBACK + ":" + FG + ":" + WL;
        String[] off = ServiceList.without(before, ours);
        String back = ServiceList.withAll(off[0], off[1]);
        check("bank: round trip keeps TalkBack", ServiceList.contains(back, TALKBACK));
        check("bank: round trip brings Feed Guard back", ServiceList.contains(back, FG));
        check("bank: round trip brings Watch Limit back", ServiceList.contains(back, WL));
        eq("bank: putting back onto nothing", FG + ":" + WL, ServiceList.withAll("", FG + ":" + WL));
        eq("bank: putting back onto null", FG, ServiceList.withAll(null, FG));
        eq("bank: already back: no duplicate", TALKBACK + ":" + FG, ServiceList.withAll(TALKBACK + ":" + FG, FG));
        eq("bank: nothing to put back", TALKBACK, ServiceList.withAll(TALKBACK, ""));
        eq("bank: null to put back", TALKBACK, ServiceList.withAll(TALKBACK, null));
    }

    static void bankRules() {
        eq("bank apps: Nubank unless set", set("com.nu.production"), BankRules.bankApps(null));
        eq("bank apps: none", 0, BankRules.bankApps("none").size());
        eq("bank apps: None, any case", 0, BankRules.bankApps(" NONE ").size());
        eq("bank apps: blank means none", 0, BankRules.bankApps("  ").size());
        eq("bank apps: a list", set("com.nu.production", "com.revolut.revolut"),
                BankRules.bankApps("com.nu.production, com.revolut.revolut"));
        // Listing these must never switch the guards off.
        eq("bank apps: never Firefox", 0, BankRules.bankApps("org.mozilla.firefox").size());
        eq("bank apps: never Instagram", 0, BankRules.bankApps("com.instagram.android").size());
        eq("bank apps: never a Reddit app", 0, BankRules.bankApps("com.reddit.frontpage").size());
        eq("bank apps: never a feed app by name", 0, BankRules.bankApps("org.quantumbadger.redreader").size());
        eq("bank apps: never the Play Store", 0, BankRules.bankApps("com.android.vending").size());
        eq("bank apps: never OwnDroid", 0, BankRules.bankApps("com.bintianqi.owndroid").size());
        eq("bank apps: never Watch Limit", 0, BankRules.bankApps("io.github.zinzuzu.watchlimit").size());
        eq("bank apps: never Feed Guard", 0, BankRules.bankApps("io.github.zinzuzu.feedguard").size());
        eq("bank apps: never Settings", 0, BankRules.bankApps("com.android.settings").size());
        eq("bank apps: the bad one dropped, the bank kept", set("com.nu.production"),
                BankRules.bankApps("org.mozilla.firefox, com.nu.production"));
        check("may be bank: null", !BankRules.mayBeBank(null));
        check("may be bank: empty", !BankRules.mayBeBank(""));
        check("may be bank: Wise", BankRules.mayBeBank("com.transferwise.android"));

        long w = BankRules.WINDOW_MS;
        long t = 5_000_000L;
        long until = BankRules.deadline(t);
        eq("deadline: one window on", t + w, until);
        check("active: just started", BankRules.active(7, until, 7, t));
        check("active: a second before the end", BankRules.active(7, until, 7, until - 1000));
        check("over: at the end", !BankRules.active(7, until, 7, until));
        check("over: after the end", !BankRules.active(7, until, 7, until + 1));
        check("over: after a restart", !BankRules.active(7, until, 8, 1000));
        // The case only the start-up count catches: banking mode began a minute after
        // start-up, the phone restarted, and two minutes later the old end time still
        // looks less than one window away.
        long early = BankRules.deadline(60_000L);
        check("over: after a quick restart, though the old end time looks near",
                !BankRules.active(7, early, 8, 120_000L));
        check("active: the same start-up, a minute in", BankRules.active(7, early, 7, 120_000L));
        check("over: start-up count unknown then", !BankRules.active(BankRules.UNKNOWN_BOOT, until, 7, t));
        check("over: start-up count unknown now", !BankRules.active(7, until, BankRules.UNKNOWN_BOOT, t));
        check("over: never started", !BankRules.active(7, 0, 7, t));
        check("over: an end too far away to be ours", !BankRules.active(7, t + w + 1, 7, t));
        check("over: an end a whole day away", !BankRules.active(7, t + 86_400_000L, 7, t));
        check("active: an end exactly one window away", BankRules.active(7, t + w, 7, t));
    }

    /** The texts that give banking mode's times in words say what BankRules does. */
    static void bankTexts() {
        String dir = System.getProperty("feedguard.dir");
        check("texts: feedguard.dir is set (run test/run.sh)", dir != null);
        if (dir == null) return;
        String minutes = (BankRules.WINDOW_MS / 60_000) + " minutes";
        String seconds = (BankRules.CHECK_EVERY_MS / 1000) + " seconds";
        String strings = read(dir + "/res/values/strings.xml");
        String readme = read(dir + "/README.md");
        check("texts: the window is whole minutes", BankRules.WINDOW_MS % 60_000 == 0);
        check("texts: strings.xml says " + minutes, strings.contains("for " + minutes));
        check("texts: the README says " + minutes, readme.contains("for " + minutes));
        check("texts: the README says every " + seconds, readme.contains("every " + seconds));
    }

    static String read(String path) {
        try {
            return new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(path)),
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            return "";
        }
    }

    static void pins() {
        Map<String, String> parsed = Pins.parse(Arrays.asList("com.whatsapp=0A1b", "broken", "=00", "org.x=", "org.y=zz", "org.z=abc"));
        Map<String, String> want = new HashMap<>();
        want.put("com.whatsapp", "0a1b");
        eq("pins: good entries only", want, new HashMap<>(parsed));
        eq("pins: round trip", parsed, Pins.parse(Pins.format(parsed)));
        eq("pins: nothing stored", 0, Pins.parse(null).size());
        eq("hex", "00ff10", Pins.hex(new byte[] {0, (byte) 0xff, 0x10}));
        eq("bytes", "00ff10", Pins.hex(Pins.bytes("00ff10")));
    }

    // --- Morning news block ------------------------------------------------

    static final long H = 3_600_000L;

    static void newsRules() {
        Set<String> sites = NewsRules.DEFAULT_SITES;
        check("g1 is news", NewsRules.isNewsHost("g1.globo.com", sites));
        check("Folha (under UOL) is news", NewsRules.isNewsHost("www1.folha.uol.com.br", sites));
        check("bbc.com is news", NewsRules.isNewsHost("www.bbc.com", sites));
        check("Google News is news", NewsRules.isNewsHost("news.google.com", sites));
        check("google.com isn't news", !NewsRules.isNewsHost("www.google.com", sites));
        check("a lookalike isn't news", !NewsRules.isNewsHost("notglobo.com", sites));
        check("no host isn't news", !NewsRules.isNewsHost(null, sites));
        check("address bar to host to news",
                NewsRules.isNewsHost(UrlText.host("g1 g1.globo.com/politica/. Search or enter address"), sites));

        eq("sites setting, cleaned", set("g1.globo.com", "example.org", "folha.com.br"),
                NewsRules.listOrDefault(" https://g1.globo.com/x, www.Example.org;folha.com.br ", sites));
        eq("sites unset is the default", sites, NewsRules.listOrDefault(null, sites));
        eq("sites none", Collections.<String>emptySet(), NewsRules.listOrDefault("none", sites));
        eq("apps unset is the default", NewsRules.DEFAULT_APPS, NewsRules.appsOrDefault(null));
        check("YouTube ReVanced is in the morning block", NewsRules.DEFAULT_APPS.contains("app.revanced.android.youtube"));
        eq("apps keep their case", set("mnn.Android"), NewsRules.appsOrDefault("mnn.Android"));

        // The gap: same start-up uses time since start-up; the clock can't fake it.
        eq("gap, same start-up", 7 * H, NewsRules.gapMs(3, 1 * H, 1_000, 3, 8 * H, 1_000 + 1));
        eq("never put down", -1L, NewsRules.gapMs(-1, 0, 0, 3, 8 * H, 5 * H));
        eq("gap across a restart: the clock", 7 * H, NewsRules.gapMs(3, 20 * H, 10 * H, 4, 1 * H, 17 * H));
        eq("gap across a restart: never less than time since start-up", 2 * H,
                NewsRules.gapMs(3, 20 * H, 10 * H, 4, 2 * H, 10 * H));
        eq("time since start-up went back: a restart even without a boot count", 7 * H,
                NewsRules.gapMs(-1, 20 * H, 10 * H, -1, 1 * H, 17 * H));

        check("6 AM is in 6-12", NewsRules.inWindow(6, 6, 12));
        check("11 AM is in 6-12", NewsRules.inWindow(11, 6, 12));
        check("noon isn't in 6-12", !NewsRules.inWindow(12, 6, 12));
        check("5 AM isn't in 6-12", !NewsRules.inWindow(5, 6, 12));
        check("window across midnight", NewsRules.inWindow(1, 22, 3) && !NewsRules.inWindow(4, 22, 3));
        check("same hours: all day", NewsRules.inWindow(17, 0, 0));

        check("7 hours, at 7 AM: woke up", NewsRules.wokeUp(7 * H, 6, 7, 6, 12));
        check("exactly 6 hours: woke up", NewsRules.wokeUp(6 * H, 6, 7, 6, 12));
        check("5 hours: not yet", !NewsRules.wokeUp(5 * H, 6, 7, 6, 12));
        check("8 hours, at 3 PM: not the morning", !NewsRules.wokeUp(8 * H, 6, 15, 6, 12));
        check("unknown gap: no", !NewsRules.wokeUp(-1, 6, 7, 6, 12));

        // Counting the block's hour.
        eq("counted, same start-up", 25 * 60_000L, NewsRules.counted(10 * 60_000L, 3, 100_000, 3, 100_000 + 15 * 60_000L));
        eq("counted, after a restart: what was stored", 10 * 60_000L, NewsRules.counted(10 * 60_000L, 3, 50 * H, 4, 60_000));
        eq("counted, time went back: a restart", 10 * 60_000L, NewsRules.counted(10 * 60_000L, -1, 50 * H, -1, 60_000));
        eq("clamp", 240, NewsRules.clamp(9999, 1, 240));
    }

    static void newsAppRule() {
        AppRules.Inputs in = phone();
        in.installed = new HashSet<>(in.installed);
        in.installed.add("com.globo.g1.app");
        in.newsApps = set("com.globo.g1.app", "com.android.vending");
        in.allowed = set("com.globo.g1.app");
        AppRules.Plan off = AppRules.plan(in);
        check("no block: listed news app open", !off.pause.contains("com.globo.g1.app"));

        in.newsBlock = true;
        AppRules.Plan on = AppRules.plan(in);
        check("block: news app paused even when listed", on.pause.contains("com.globo.g1.app"));
        check("block: the Play Store is never a news app", !on.pause.contains("com.android.vending"));

        in.newsBlock = false;
        in.ours = set("com.globo.g1.app");
        check("block over: its pause is lifted", AppRules.plan(in).release.contains("com.globo.g1.app"));
        in.newsBlock = true;
        check("block on: its pause stays", !AppRules.plan(in).release.contains("com.globo.g1.app"));
    }

    static void newsWhy() {
        eq("why: started", NewsRules.STARTED, NewsRules.decide(true, false, 7 * H, 6, 7, 6, 12));
        eq("why: turned off", NewsRules.TURNED_OFF, NewsRules.decide(false, false, 7 * H, 6, 7, 6, 12));
        eq("why: already on", NewsRules.ALREADY_ON, NewsRules.decide(true, true, 7 * H, 6, 7, 6, 12));
        eq("why: never put down", NewsRules.NEVER_PUT_DOWN, NewsRules.decide(true, false, -1, 6, 7, 6, 12));
        eq("why: too short", NewsRules.TOO_SHORT, NewsRules.decide(true, false, 5 * H, 6, 7, 6, 12));
        eq("why: not the morning", NewsRules.NOT_MORNING, NewsRules.decide(true, false, 8 * H, 6, 14, 6, 12));
        eq("duration, hours and minutes", "7 h 31 min", NewsRules.duration(7 * H + 31 * 60_000L));
        eq("duration, whole hours", "6 h", NewsRules.duration(6 * H));
        eq("duration, minutes", "45 min", NewsRules.duration(45 * 60_000L));
        eq("why text: too short", "after 3 h 55 min untouched: too short to count as a night (it needs 6 h).",
                NewsRules.whyText(NewsRules.TOO_SHORT, 3 * H + 55 * 60_000L, 6, "6:00", "12:00"));
        check("why text: every result has one", !NewsRules.whyText(NewsRules.NOT_MORNING, H, 6, "6:00", "12:00").isEmpty()
                && !NewsRules.whyText(NewsRules.STARTED, H, 6, "a", "b").isEmpty()
                && !NewsRules.whyText(NewsRules.NEVER_PUT_DOWN, -1, 6, "a", "b").isEmpty());
    }

    /**
     * The unlock broadcast comes from System UI, an ordinary app as far as Android
     * is concerned: a not-exported receiver never gets it (why 0.4.0 to 0.4.4 never
     * started the block). Checked in the source, since it can't run off the phone.
     */
    static void newsReceiver() {
        String dir = System.getProperty("feedguard.dir");
        if (dir == null) return;
        String src = read(dir + "/src/io/github/zinzuzu/feedguard/GuardService.java");
        check("unlock receiver is exported", src.contains("registerReceiver(screenReceiver, screen, Context.RECEIVER_EXPORTED)"));
        check("unlock receiver listens for USER_PRESENT", src.contains("screen.addAction(Intent.ACTION_USER_PRESENT)"));
        check("unlock has a safety net from accessibility events", src.contains("if (!unlockSeen) lookForUnlock();"));
    }

    static void torRule() {
        check("Tor Browser is Tor", AppRules.isTorBrowser("org.torproject.torbrowser"));
        check("Tor Browser alpha is Tor", AppRules.isTorBrowser("org.torproject.torbrowser_alpha"));
        check("Orbot isn't a browser", !AppRules.isTorBrowser("org.torproject.android"));
        AppRules.Inputs in = phone();
        in.installed = new HashSet<>(in.installed);
        in.installed.add("org.torproject.torbrowser");
        in.allowed = set("org.torproject.torbrowser");
        in.handled = set("org.torproject.torbrowser");
        AppRules.Plan plan = AppRules.plan(in);
        check("Tor: paused even when listed and already handled", plan.pause.contains("org.torproject.torbrowser"));
        in.ours = set("org.torproject.torbrowser");
        check("Tor: its pause is never lifted", !AppRules.plan(in).release.contains("org.torproject.torbrowser"));
        in.allowed = set("org.torproject.torbrowser", "com.whatsapp");
        in.extraBlocked = set("com.whatsapp");
        check("Tor: the allowlist still wins for other apps", !AppRules.plan(in).pause.contains("com.whatsapp"));
    }

    // --- Harness -----------------------------------------------------------

    static Set<String> set(String... items) {
        return new HashSet<>(Arrays.asList(items));
    }

    static void check(String name, boolean ok) {
        if (ok) {
            passed++;
        } else {
            failed++;
            System.out.println("FAIL: " + name);
        }
    }

    static void eq(String name, Object expected, Object actual) {
        boolean ok = expected == null ? actual == null : expected.equals(actual);
        if (!ok) System.out.println("  expected " + expected + ", got " + actual);
        check(name, ok);
    }
}
