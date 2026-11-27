// Checks the 10-minute feed pass (feed-pass.js with background.js) on fake
// Facebook and Instagram pages, and how the Instagram panel leads to search.
// A small stand-in for the browser's extension APIs routes messages to
// background.js and keeps storage in memory. Needs Playwright with Chromium:
//   node tests/feed-pass.test.js
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');
const ext = path.join(__dirname, '..', 'extension');
const read = f => fs.readFileSync(path.join(ext, f), 'utf8');

// Runs in the page before the add-on's files: fake chrome.* and a clock the
// test can move forward (window.__skew).
const STUB = `
  // The test passes the shared storage and clock offset in through
  // window.name, which survives going from one site to the other.
  const shared = JSON.parse(window.name || '{}');
  window.__skew = shared.skew || 0;
  const realNow = Date.now.bind(Date);
  Date.now = () => realNow() + window.__skew;
  const store = shared.store || {};
  const save = () => { window.name = JSON.stringify({ skew: window.__skew, store }); };
  save();
  const changed = [], msgListeners = [];
  const ev = () => ({ addListener() {} });
  window.chrome = {
    storage: {
      local: {
        get: async k => (k in store ? { [k]: store[k] } : {}),
        set: async o => { for (const [k, v] of Object.entries(o)) {
          const old = store[k]; store[k] = v; save();
          for (const f of changed) f({ [k]: { oldValue: old, newValue: v } }, 'local'); } },
      },
      onChanged: { addListener: f => changed.push(f) },
    },
    runtime: {
      onMessage: { addListener: f => msgListeners.push(f) },
      sendMessage: msg => new Promise(res => { for (const f of msgListeners) if (f(msg, {}, res) === true) return; res(undefined); }),
      onStartup: ev(), onInstalled: ev(), getURL: p => p,
    },
    tabs: { onUpdated: ev(), query: async () => [], update() {} },
  };`;

(async () => {
  const b = await chromium.launch();
  const ctx = await b.newContext();
  // Each site gets only its own script, as in manifest.json.
  await ctx.addInitScript({ content: STUB + read('background.js') + read('feed-pass.js') +
    `if (location.hostname.endsWith('facebook.com')) {${read('facebook.js')}} else {${read('instagram.js')}}` });
  const css = read('facebook.css') + read('instagram.css') + read('feed-pass.css');
  const page_ = body => `<html><head><style>${css}</style></head><body>${body}</body></html>`;
  await ctx.route('https://www.facebook.com/**', r => r.fulfill({ contentType: 'text/html', body: page_('<div role="feed">posts</div>') }));
  await ctx.route('https://www.instagram.com/**', r => {
    r.fulfill({ contentType: 'text/html', body: page_('<main><article>post</article><a href="/p/abc/">tile</a><a href="/natgeo/">natgeo</a></main>') });
  });

  const page = await ctx.newPage();
  let fail = 0;
  const check = (ok, what) => { console.log(ok ? 'ok  ' : 'FAIL', what); if (!ok) fail++; };
  const covered = sel => page.evaluate(s => !!document.querySelector(s), sel);
  const skew = async ms => { await page.evaluate(ms => { window.__skew += ms; window.name = JSON.stringify({ ...JSON.parse(window.name), skew: window.__skew }); }, ms); await page.waitForTimeout(700); };

  // Facebook: offer, confirm, open.
  await page.goto('https://www.facebook.com/');
  await page.waitForTimeout(300);
  check(await covered('#rnb-fb-cover'), 'facebook feed covered');
  await page.click('#rnb-fb-cover .rnb-pass button');
  check(await page.isVisible('text=Start 10 minutes now'), 'asks to confirm first');
  await page.click('text=Not now');
  check(await page.isVisible('text=Open the feed for 10 minutes'), '"Not now" goes back');
  await page.click('text=Open the feed for 10 minutes');
  await page.click('text=Start 10 minutes now');
  await page.waitForTimeout(700);
  check(!(await covered('#rnb-fb-cover')), 'pass opens the facebook feed');
  check(/10 min left/.test(await page.textContent('#rnb-pass-pill')), 'countdown shows 10 min left');

  // The same pass opens Instagram, and survives a reload.
  await page.goto('https://www.instagram.com/');
  await page.waitForTimeout(700);
  check(!(await covered('#rnb-ig-cover')), 'same pass opens the instagram feed');
  await skew(6 * 60 * 1000);
  check(/4 min left/.test(await page.textContent('#rnb-pass-pill')), 'countdown counts down');

  // After 10 minutes the cover comes back, and the next pass is two weeks out.
  await skew(5 * 60 * 1000);
  check(await covered('#rnb-ig-cover'), 'instagram covered again after 10 minutes');
  check(!(await covered('#rnb-pass-pill')), 'countdown gone');
  const wait = await page.textContent('#rnb-ig-cover .rnb-pass');
  check(/Your next 10 minutes of feed are on/.test(wait), 'shows when the next pass is: ' + wait.trim());
  check(!(await page.isVisible('#rnb-ig-cover .rnb-pass button')), 'no button before two weeks');
  // A page can't start a second pass early: background refuses.
  const r = await page.evaluate(() => chrome.runtime.sendMessage({ type: 'feedPassStart' }));
  check(r && r.refused, 'background refuses an early second pass');
  await page.waitForTimeout(700);
  check(await covered('#rnb-ig-cover'), 'still covered after refused start');

  // 14 days later the offer is back.
  await skew(14 * 24 * 60 * 60 * 1000);
  check(await page.isVisible('text=Open the feed for 10 minutes'), 'offer back after 14 days');
  // Clock set back before the last start: no pass.
  await skew(-30 * 24 * 60 * 60 * 1000);
  check(!(await page.isVisible('#rnb-ig-cover .rnb-pass button')), 'clock set back: no offer');
  const r2 = await page.evaluate(() => chrome.runtime.sendMessage({ type: 'feedPassStart' }));
  check(r2 && r2.refused, 'clock set back: background refuses');

  // Instagram's own search: the panel links to it, its grid is hidden, and
  // hashtag pages reached from it are covered.
  check(await page.getAttribute('#rnb-ig-cover a.rnb-search', 'href') === '/explore/search/', 'panel links to Instagram search');
  await page.goto('https://www.instagram.com/explore/search/');
  await page.waitForTimeout(700);
  check(!(await covered('#rnb-ig-cover')), 'search page not covered');
  check(await page.evaluate(() => document.documentElement.classList.contains('rnb-ig-search')), 'search page marked');
  check(!(await page.isVisible('main a[href="/p/abc/"]')) && await page.isVisible('main a[href="/natgeo/"]'), 'search page hides posts, keeps account results');
  await page.goto('https://www.instagram.com/explore/tags/cats/');
  await page.waitForTimeout(700);
  check(await covered('#rnb-ig-cover'), 'hashtag page covered');
  await page.goto('https://www.instagram.com/natgeo/');
  await page.waitForTimeout(700);
  check(!(await covered('#rnb-ig-cover')), 'profile not covered');
  await page.goto('https://www.instagram.com/');
  await page.waitForTimeout(700);
  await page.fill('#rnb-ig-cover input', 'ana.souza');
  await Promise.all([page.waitForURL('**/ana.souza/'), page.press('#rnb-ig-cover input', 'Enter')]);
  check(page.url().endsWith('/ana.souza/'), 'Enter opens the typed username');
  if (process.argv[2]) {
    await page.goto('https://www.instagram.com/');
    await page.screenshot({ path: process.argv[2] });
  }

  await b.close();
  process.exit(fail ? 1 : 0);
})();
