// Checks that facebook.js covers Facebook's feeds and nothing else, on fake
// pages served under www.facebook.com. Needs Playwright with Chromium:
//   node tests/facebook-feed.test.js
const { chromium } = require('playwright');
const fs = require('fs');
const ext = require('path').join(__dirname, '..', 'extension');
(async () => {
  const b = await chromium.launch();
  const ctx = await b.newContext();
  await ctx.addInitScript({ content: fs.readFileSync(ext + '/facebook.js', 'utf8') });
  const css = fs.readFileSync(ext + '/facebook.css', 'utf8');
  await ctx.route('https://www.facebook.com/**', r => {
    const login = r.request().url().includes('login=1');
    r.fulfill({ contentType: 'text/html', body: `<html><head><style>${css}</style></head><body><div role="feed">posts</div>${login ? '<input name="pass" type="password">' : ''}<a id="spa" href="#">x</a></body></html>` });
  });
  const page = await ctx.newPage();
  const cases = [
    ['/', true], ['/?sk=h_chr', true], ['/home.php', true], ['/watch/', true], ['/watch/?v=123', false],
    ['/reel/', true], ['/reel/12345', false], ['/reels/', true], ['/groups/feed/', true], ['/groups/123/', false],
    ['/messages/t/1', false], ['/zuck', false], ['/notifications/', false], ['/marketplace/', false], ['/?login=1', false],
  ];
  let fail = 0;
  for (const [path, want] of cases) {
    await page.goto('https://www.facebook.com' + path);
    await page.waitForTimeout(50);
    const got = await page.evaluate(() => !!document.querySelector('#rnb-fb-cover') && document.documentElement.classList.contains('rnb-fb-nofeed'));
    if (got !== want) fail++;
    console.log(got === want ? 'ok  ' : 'FAIL', path, got);
  }
  // In-page navigation: from the feed to messages and back.
  await page.goto('https://www.facebook.com/');
  await page.evaluate(() => history.pushState({}, '', '/messages/'));
  await page.waitForTimeout(700);
  let c = await page.evaluate(() => !!document.querySelector('#rnb-fb-cover'));
  console.log(!c ? 'ok  ' : 'FAIL', 'pushState to /messages/ removes cover'); if (c) fail++;
  await page.evaluate(() => history.pushState({}, '', '/'));
  await page.waitForTimeout(700);
  c = await page.evaluate(() => !!document.querySelector('#rnb-fb-cover'));
  console.log(c ? 'ok  ' : 'FAIL', 'pushState back to / restores cover'); if (!c) fail++;
  // Removed by the page: comes back.
  await page.evaluate(() => document.querySelector('#rnb-fb-cover').remove());
  await page.waitForTimeout(700);
  c = await page.evaluate(() => !!document.querySelector('#rnb-fb-cover'));
  console.log(c ? 'ok  ' : 'FAIL', 'cover put back after removal'); if (!c) fail++;
  // Search form.
  await page.fill('#rnb-fb-cover input', 'Ana Souza');
  await Promise.all([page.waitForURL('**/search/**'), page.press('#rnb-fb-cover input', 'Enter')]);
  const u = page.url(); const ok = u.endsWith('/search/people/?q=Ana%20Souza');
  console.log(ok ? 'ok  ' : 'FAIL', 'search goes to', u); if (!ok) fail++;
  await page.goto('https://www.facebook.com/');
  if (process.argv[2]) await page.screenshot({ path: process.argv[2] });
  await b.close();
  process.exit(fail ? 1 : 0);
})();
