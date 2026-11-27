// Reddit NSFW Blocker: Instagram home feed.
// Covers the endless feeds (home, Explore, the Reels tab, hashtag and place
// grids) with a full-screen panel and leaves profiles, single posts and reels,
// stories, search and messages working. The cover doesn't depend on
// Instagram's page layout, which differs between phone and desktop and changes
// often. The panel links to Instagram's own search page, where the post grid
// under the search box is hidden, and offers the feed pass (feed-pass.js).
(() => {
  if (window.__rnbIgLoaded || window.top !== window) return;
  window.__rnbIgLoaded = true;
  const pass = window.__rnbFeedPass;

  // "/" is the home feed; "/explore/" the Explore grid; "/reels/" the Reels tab.
  // A single reel ("/reel/<id>/") or post ("/p/<id>/") opened from a message
  // stays visible. Feed Guard (feed-guard/UrlText.java) keeps the same list.
  const FEED_PATHS = /^\/(explore|reels)?\/?$/i;
  // Hashtag and place pages, reached from search results, are post grids too.
  const GRID_PATHS = /^\/explore\/(tags|locations)\//i;
  // Instagram's own search page: a search box, with the Explore grid under it
  // until you type. instagram.css hides the grid there; the results stay.
  const SEARCH_PATH = /^\/explore\/search\/?$/i;
  const USERNAME = /^[A-Za-z0-9._]{1,30}$/;

  function buildCover() {
    const cover = document.createElement('div');
    cover.id = 'rnb-ig-cover';
    cover.innerHTML = '<div class="rnb-card">' +
      '<strong>The feed is turned off.</strong>' +
      '<span>Search for someone, type a username, or open your messages.</span>' +
      '<a class="rnb-search" href="/explore/search/">Search Instagram</a>' +
      '<form><input type="text" name="u" placeholder="Username" aria-label="Instagram username" autocapitalize="off" autocomplete="off"></form>' +
      '<a href="/direct/inbox/">Open messages</a>' +
      '<a href="/accounts/login/">Log in</a>' +
      '</div>';
    if (pass) cover.querySelector('.rnb-card').append(pass.panelBox());
    return cover;
  }

  // Listen on the document, so the form still works if Instagram copies the
  // cover while rebuilding the page.
  document.addEventListener('submit', e => {
    const form = e.target.closest && e.target.closest('#rnb-ig-cover form');
    if (!form) return;
    e.preventDefault();
    e.stopPropagation();
    const name = form.querySelector('input').value.trim().replace(/^@/, '');
    if (USERNAME.test(name)) location.href = `/${name}/`;
  }, true);

  let cover = null;
  function setCover(on) {
    // Drop stray copies left behind when Instagram rebuilds the page.
    for (const c of document.querySelectorAll('#rnb-ig-cover')) if (c !== cover) c.remove();
    document.documentElement.classList.toggle('rnb-ig-nofeed', on);
    if (on) {
      if (!cover) cover = buildCover();
      // Instagram rebuilds the page often, so put the cover back if it's removed.
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

  // Logged out, Instagram shows its login form at "/", so leave that visible.
  const loginForm = () => document.querySelector('input[type="password"], input[name="password"]');
  const tick = () => {
    if (!document.documentElement) return; // too early; the next tick catches up
    const p = location.pathname;
    const feed = (FEED_PATHS.test(p) || GRID_PATHS.test(p)) && !loginForm();
    const passOpen = Boolean(pass && pass.open());
    setCover(feed && !passOpen);
    document.documentElement.classList.toggle('rnb-ig-search', SEARCH_PATH.test(p) && !passOpen);
    if (pass) pass.showPill((feed || SEARCH_PATH.test(p)) && passOpen);
  };
  tick();
  document.addEventListener('DOMContentLoaded', tick);
  // Instagram changes pages without reloading, so keep checking.
  setInterval(tick, 500);
})();
