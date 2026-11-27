// Reddit NSFW Blocker: Facebook news feed.
// Covers the endless feeds (the home feed, Watch, the Reels tab and the groups
// feed) with a full-screen panel, and leaves messages, groups, profiles, pages,
// notifications, search and single posts or videos working. Like instagram.js,
// the cover doesn't depend on Facebook's page layout, which changes often.
(() => {
  if (window.__rnbFbLoaded || window.top !== window) return;
  window.__rnbFbLoaded = true;
  const pass = window.__rnbFeedPass; // the 10-minute feed pass, see feed-pass.js

  // "/" and "/home.php" are the home feed (with or without "?sk=..." filters);
  // "/watch/" is the Watch feed; "/reel/" and "/reels/" the Reels tab;
  // "/groups/feed/" mixes posts from every group. A single video
  // ("/watch/?v=<id>"), reel ("/reel/<id>"), post or group page stays visible.
  const FEED_PATHS = /^\/(home\.php|watch|reels?|groups\/feed)?\/?$/i;
  const isFeed = () => FEED_PATHS.test(location.pathname) &&
    !(/^\/watch\/?$/i.test(location.pathname) && new URLSearchParams(location.search).has('v'));

  function buildCover() {
    const cover = document.createElement('div');
    cover.id = 'rnb-fb-cover';
    cover.innerHTML = '<div class="rnb-card">' +
      '<strong>The feed is turned off.</strong>' +
      '<span>Search for someone, or go straight to what you came for.</span>' +
      '<form><input type="text" name="q" placeholder="Search Facebook" aria-label="Search Facebook" autocapitalize="off" autocomplete="off"></form>' +
      '<a href="/messages/">Messages</a>' +
      '<a href="/groups/joins/">Your groups</a>' +
      '<a href="/marketplace/">Marketplace</a>' +
      '<a href="/me/">Your profile</a>' +
      '</div>';
    if (pass) cover.querySelector('.rnb-card').append(pass.panelBox());
    return cover;
  }

  // Listen on the document, so the form still works if Facebook copies the
  // cover while rebuilding the page.
  document.addEventListener('submit', e => {
    const form = e.target.closest && e.target.closest('#rnb-fb-cover form');
    if (!form) return;
    e.preventDefault();
    e.stopPropagation();
    const q = form.querySelector('input').value.trim();
    if (q) location.href = `/search/people/?q=${encodeURIComponent(q.slice(0, 100))}`;
  }, true);

  let cover = null;
  function setCover(on) {
    if (!document.documentElement) return; // too early; the next tick catches up
    // Drop stray copies left behind when Facebook rebuilds the page.
    for (const c of document.querySelectorAll('#rnb-fb-cover')) if (c !== cover) c.remove();
    document.documentElement.classList.toggle('rnb-fb-nofeed', on);
    if (on) {
      if (!cover) cover = buildCover();
      // Facebook rebuilds the page often, so put the cover back if it's removed.
      const host = document.body || document.documentElement;
      if (cover.parentNode !== host) host.appendChild(cover);
      const box = cover.querySelector('.rnb-pass');
      if (box) pass.renderPanel(box);
      // Stop feed videos playing sound behind the cover.
      for (const v of document.querySelectorAll('video')) { if (!v.paused) v.pause(); v.muted = true; }
    } else if (cover) {
      cover.remove();
    }
  }

  // Logged out, Facebook shows its login form at "/", so leave that visible.
  const loginForm = () => document.querySelector('input[type="password"], input[name="pass"]');
  const tick = () => {
    const feed = isFeed() && !loginForm();
    const passOpen = Boolean(pass && pass.open());
    setCover(feed && !passOpen);
    if (pass) pass.showPill(feed && passOpen);
  };
  tick();
  document.addEventListener('DOMContentLoaded', tick);
  // Facebook changes pages without reloading, so keep checking.
  setInterval(tick, 500);
})();
