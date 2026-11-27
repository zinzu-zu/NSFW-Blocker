# Publishing updates

Notes for whoever changes the extension next.

- The add-on ID is `reddit-nsfw-blocker@self-hosted` (in `extension/manifest.json`). Never change it: the Mac lock and every installed copy are tied to it.
- From 1.1.1 on, the add-on is **listed** on addons.mozilla.org. Listed add-ons update themselves in Firefox on the Mac (even while it's locked) and on Android.
- To ship a change:
  1. Raise `version` in `extension/manifest.json`.
  2. Run `bash publish.sh`. It needs the `AMO_JWT_ISSUER` and `AMO_JWT_SECRET` secrets and network access to `addons.mozilla.org`.
  3. Mozilla reviews the version, and devices pick it up within about a day.
- If those secrets aren't available, upload `extension-upload.zip` by hand on the add-on's page with **Upload New Version**, and choose **On this site**.
- The Mac profile must point at the listed add-on. To set that up, run `lockdown.sh` with the add-on's short name instead of the `.xpi`. Until that's done, the Mac only updates through `lockdown/update.sh`.
- From 1.2.0, `guard-marker.js` and `guard-marker.css` add an invisible "feedguard <time>" marker to Instagram and Reddit pages for the Feed Guard app on Android (`feed-guard/`). Keep the element id `rnb-guard-marker`, the label format and the stylesheet trick (hidden inline, shown by the add-on's CSS) unchanged, or Feed Guard will cover those pages on the phone.
- From 1.3.0, `facebook.js` and `facebook.css` cover Facebook's feeds in the same way `instagram.js` covers Instagram's. Facebook gets no Feed Guard marker, because the phone setup doesn't cover Facebook. `node tests/facebook-feed.test.js` checks which pages get the cover.
- From 1.4.0, `feed-pass.js` and `feed-pass.css` add the 10-minute feed pass to the Instagram and Facebook panels. Only `background.js` (`startFeedPass`) writes the start time, so the 14-day rule lives in one place; `feed-pass.js` repeats the two durations to draw the panel. From 1.4.1 the Instagram panel links to Instagram's own search page (`/explore/search/`) instead of calling its search API, which Instagram removed; `instagram.css` hides the post grid there. `node tests/feed-pass.test.js` checks both. Keep the panel id `rnb-ig-cover`: Feed Guard on Android looks for it.
- From 1.4.2 the add-on is called NSFW Blocker, and the Mac lock uses `nsfw-blocker` names (`/Library/Application Support/nsfw-blocker/nsfw-blocker.xpi`, `nsfw-blocker.mobileconfig`). Two names stay old on purpose: the add-on ID above, and the profile identifier `local.redditnsfwblocker`, so a new profile replaces the installed one instead of adding a second. `lockdown/newest-xpi.sh` picks the signed `.xpi` with the highest version, whatever it's called.
