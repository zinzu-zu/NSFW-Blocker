// NSFW Blocker: the feed pass, shared by instagram.js and facebook.js.
// Once every 14 days you can open the Instagram and Facebook feeds for 10
// minutes, whenever you choose. background.js keeps the rule and the start
// time (in the add-on's own storage, which the lock keeps); this file only
// reads it and draws the button and the countdown. One pass covers both sites.
(() => {
  if (window.__rnbFeedPass || window.top !== window) return;

  const PASS_MS = 10 * 60 * 1000;        // keep in step with background.js
  const EVERY_MS = 14 * 24 * 60 * 60 * 1000;

  let startedAt = 0;
  chrome.storage.local.get('feedPass').then(({ feedPass }) => {
    startedAt = (feedPass && feedPass.startedAt) || 0;
  }, () => {});
  chrome.storage.onChanged.addListener((changes, area) => {
    if (area === 'local' && changes.feedPass) startedAt = (changes.feedPass.newValue || {}).startedAt || 0;
  });

  // How much of the pass is left now, in ms (0 when closed).
  const leftMs = () => {
    const now = Date.now();
    return now >= startedAt && now - startedAt < PASS_MS ? PASS_MS - (now - startedAt) : 0;
  };
  // When the next pass can start (0 if it can start now). A clock set back
  // before the last start counts as "not yet".
  const nextAt = () => {
    const now = Date.now();
    if (!startedAt) return 0;
    if (now < startedAt) return startedAt + EVERY_MS;
    return now - startedAt >= EVERY_MS ? 0 : startedAt + EVERY_MS;
  };
  const start = () => chrome.runtime.sendMessage({ type: 'feedPassStart' }).then(r => {
    if (r && r.startedAt) startedAt = r.startedAt;
  }, () => {});

  // The part of the feed panel that offers the pass. Plain text only.
  function panelBox() {
    const box = document.createElement('div');
    box.className = 'rnb-pass';
    return box;
  }
  function renderPanel(box) {
    const next = nextAt();
    const key = next ? 'wait:' + next : box.dataset.confirm ? 'confirm' : 'offer';
    if (box.dataset.state === key) return;
    box.dataset.state = key;
    box.replaceChildren();
    if (next) {
      const p = document.createElement('small');
      p.textContent = 'Your next 10 minutes of feed are on ' +
        new Date(next).toLocaleString(undefined, { weekday: 'long', day: 'numeric', month: 'long', hour: '2-digit', minute: '2-digit' }) + '.';
      box.append(p);
    } else if (box.dataset.confirm) {
      const p = document.createElement('small');
      p.textContent = 'This uses your 10 minutes for the next two weeks, on Instagram and Facebook.';
      const yes = document.createElement('button');
      yes.type = 'button';
      yes.textContent = 'Start 10 minutes now';
      yes.addEventListener('click', () => { delete box.dataset.confirm; start(); });
      const no = document.createElement('button');
      no.type = 'button';
      no.className = 'rnb-quiet';
      no.textContent = 'Not now';
      no.addEventListener('click', () => { delete box.dataset.confirm; renderPanel(box); });
      box.append(p, yes, no);
    } else {
      const b = document.createElement('button');
      b.type = 'button';
      b.className = 'rnb-quiet';
      b.textContent = 'Open the feed for 10 minutes';
      b.addEventListener('click', () => { box.dataset.confirm = '1'; renderPanel(box); });
      box.append(b);
    }
  }

  // The countdown shown on feed pages while the pass is open.
  let pill = null;
  function showPill(on) {
    if (!on) { if (pill) pill.remove(); return; }
    if (!pill) { pill = document.createElement('div'); pill.id = 'rnb-pass-pill'; }
    const min = Math.ceil(leftMs() / 60000);
    pill.textContent = `Feed open: ${min} min left`;
    const host = document.body || document.documentElement;
    if (host && pill.parentNode !== host) host.appendChild(pill);
  }

  window.__rnbFeedPass = { open: () => leftMs() > 0, panelBox, renderPanel, showPill };
})();
