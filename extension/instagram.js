// Reddit NSFW Blocker: Instagram home feed.
// Covers the endless feeds (home, Explore, the Reels tab) with a full-screen
// panel and leaves profiles, single posts and reels, stories, search and
// messages working. The cover doesn't depend on Instagram's page layout, which
// differs between phone and desktop and changes often.
// The panel has Instagram's own account search, and the feed pass (feed-pass.js).
(() => {
  if (window.__rnbIgLoaded || window.top !== window) return;
  window.__rnbIgLoaded = true;
  const pass = window.__rnbFeedPass;

  // "/" is the home feed; "/explore/" the Explore grid; "/reels/" the Reels tab.
  // A single reel ("/reel/<id>/") or post ("/p/<id>/") opened from a message
  // stays visible. Feed Guard (feed-guard/UrlText.java) keeps the same list.
  const FEED_PATHS = /^\/(explore|reels)?\/?$/i;
  const USERNAME = /^[A-Za-z0-9._]{1,30}$/;

  function buildCover() {
    const cover = document.createElement('div');
    cover.id = 'rnb-ig-cover';
    cover.innerHTML = '<div class="rnb-card">' +
      '<strong>The feed is turned off.</strong>' +
      '<span>Search for an account, or open your messages.</span>' +
      '<form><input type="search" name="u" placeholder="Search" aria-label="Search Instagram accounts" autocapitalize="off" autocomplete="off"></form>' +
      '<ul class="rnb-results" aria-live="polite"></ul>' +
      '<a href="/direct/inbox/">Open messages</a>' +
      '<a href="/accounts/login/">Log in</a>' +
      '</div>';
    if (pass) cover.querySelector('.rnb-card').append(pass.panelBox());
    return cover;
  }

  // Instagram's own search, the one behind its Search panel. Only accounts are
  // listed: hashtag and place pages are feeds. Names are shown as plain text.
  async function searchAccounts(q) {
    const urls = [
      `/api/v1/web/search/topsearch/?context=blended&query=${encodeURIComponent(q)}&include_reel=false`,
      `/web/search/topsearch/?context=blended&query=${encodeURIComponent(q)}`,
    ];
    for (const url of urls) {
      try {
        const res = await fetch(url, {
          credentials: 'include',
          headers: { 'X-IG-App-ID': '936619743392459', 'X-Requested-With': 'XMLHttpRequest' },
          signal: AbortSignal.timeout(8000),
        });
        if (!res.ok) continue;
        const json = await res.json();
        if (json && Array.isArray(json.users)) {
          return json.users.map(u => u && u.user).filter(u => u && USERNAME.test(u.username || '')).slice(0, 8);
        }
      } catch { /* try the next address */ }
    }
    return null; // search unavailable: Enter still opens a username
  }

  let lastQuery = '';
  let searchTimer = null;
  function showResults(list, users, q) {
    list.replaceChildren();
    if (users === null) {
      const li = document.createElement('li');
      li.className = 'rnb-note';
      li.textContent = 'Search isn’t answering. Type the exact username and press Enter.';
      list.append(li);
      return;
    }
    if (!users.length) {
      const li = document.createElement('li');
      li.className = 'rnb-note';
      li.textContent = `No accounts found for “${q}”.`;
      list.append(li);
      return;
    }
    for (const u of users) {
      const li = document.createElement('li');
      const a = document.createElement('a');
      a.href = `/${u.username}/`;
      const name = document.createElement('b');
      name.textContent = u.username;
      a.append(name);
      if (u.full_name) {
        const full = document.createElement('small');
        full.textContent = String(u.full_name).slice(0, 60);
        a.append(full);
      }
      li.append(a);
      list.append(li);
    }
  }

  // Listen on the document, so the search still works if Instagram copies the
  // cover while rebuilding the page.
  document.addEventListener('input', e => {
    const input = e.target.closest && e.target.closest('#rnb-ig-cover input');
    if (!input) return;
    const list = input.closest('.rnb-card').querySelector('.rnb-results');
    const q = input.value.trim().replace(/^@/, '').slice(0, 50);
    clearTimeout(searchTimer);
    if (!q) { lastQuery = ''; list.replaceChildren(); return; }
    searchTimer = setTimeout(async () => {
      lastQuery = q;
      const users = await searchAccounts(q);
      if (lastQuery === q) showResults(list, users, q);
    }, 300);
  }, true);

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
    if (!document.documentElement) return; // too early; the next tick catches up
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
    const feed = FEED_PATHS.test(location.pathname) && !loginForm();
    const passOpen = Boolean(pass && pass.open());
    setCover(feed && !passOpen);
    if (pass) pass.showPill(feed && passOpen);
  };
  tick();
  document.addEventListener('DOMContentLoaded', tick);
  // Instagram changes pages without reloading, so keep checking.
  setInterval(tick, 500);
})();
