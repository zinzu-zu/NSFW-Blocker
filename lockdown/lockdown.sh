#!/bin/bash
# Reddit NSFW Blocker lock-down for macOS.
# Run from Terminal:  sudo bash lockdown.sh
# Builds one configuration profile that force-installs the extension in
# Firefox (and Edge, if published there), blocks Reddit, Instagram and
# Facebook in Safari and the other Chromium browsers, and turns
# off extra profiles. Firefox keeps its private windows, with the extension
# running in them; the other browsers lose theirs. DNS settings are left alone.
# A system profile can only be removed with an administrator password.
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(dirname "$HERE")"
LIB="/Library/Application Support/RedditNSFWBlocker"
OUT="$HERE/RedditNSFWBlocker.mobileconfig"
FF_ID="reddit-nsfw-blocker@self-hosted"

if [[ $EUID -ne 0 && -z "${DRY_RUN:-}" ]]; then
  echo "Run this with sudo:  sudo bash \"$0\""; exit 1
fi

# --- Firefox: listed add-on on addons.mozilla.org, or a self-signed .xpi ---
read -r -p "Firefox add-on slug on addons.mozilla.org (Enter to use reddit-nsfw-blocker.xpi from this folder): " SLUG
if [[ -n "$SLUG" && ! "$SLUG" =~ ^[A-Za-z0-9_-]{1,100}$ ]]; then
  echo "That doesn't look like an add-on name (letters, numbers, - and _ only)."; exit 1
fi
if [[ -n "$SLUG" ]]; then
  FF_URL="https://addons.mozilla.org/firefox/downloads/latest/$SLUG/latest.xpi"
elif [[ -f "$ROOT/reddit-nsfw-blocker.xpi" ]]; then
  if [[ -z "${DRY_RUN:-}" ]]; then
    mkdir -p "$LIB"; cp "$ROOT/reddit-nsfw-blocker.xpi" "$LIB/"; chown -R root:wheel "$LIB"; chmod -R go-w "$LIB"
  fi
  FF_URL="file:///Library/Application%20Support/RedditNSFWBlocker/reddit-nsfw-blocker.xpi"
else
  echo "No slug and no reddit-nsfw-blocker.xpi next to README.md. See step 1 in the README."; exit 1
fi

# --- Chromium browsers ---
# Chrome, Brave and Chromium only force-install extensions from the paid Chrome
# Web Store on a Mac that isn't MDM-managed, so they get Reddit blocked outright
# instead. Edge can force-install from Microsoft's free Edge Add-ons store.
read -r -p "Edge Add-ons extension ID (Enter to skip and block Reddit in Edge too): " EDGE_ID
# Instagram and Facebook are always blocked there too (owner's choice,
# 2026-11-27), so their feeds only open in Firefox, where the extension hides
# them. messenger.com stays open in every browser.
BLOCK_REDDIT="<key>URLBlocklist</key><array><string>reddit.com</string><string>redd.it</string><string>redditmedia.com</string><string>instagram.com</string><string>facebook.com</string></array>"
if [[ -n "$EDGE_ID" && ! "$EDGE_ID" =~ ^[a-p]{32}$ ]]; then
  echo "That doesn't look like an extension ID (32 letters a-p)."; exit 1
fi
if [[ -n "$EDGE_ID" ]]; then
  EDGE_EXTRA="<key>ExtensionInstallForcelist</key><array><string>$EDGE_ID;https://edge.microsoft.com/extensionwebstorebase/v1/crx</string></array>"
else
  EDGE_EXTRA="$BLOCK_REDDIT"
fi

u() { uuidgen; }

# --- Safari ---
# Safari can't run the extension under lock, so Reddit is blocked there
# outright, like in Chrome. Apple's content filter (the one Screen Time uses)
# also blocks adult sites it recognises and turns off Safari's private
# windows; Firefox's private windows stay. The adult media hosts come from the
# extension's rules.json, so the two lists can't drift apart.
# The old key names (filterBlacklist, whitelistEnabled) are used on purpose:
# Apple renamed them in macOS 15.2, but the old ones still work on every
# version and the new ones are reported to block less reliably.
SAFARI_DOMAINS="reddit.com redd.it redditmedia.com instagram.com facebook.com"
RULES="$ROOT/extension/rules.json"
if [[ -f "$RULES" ]]; then
  SAFARI_DOMAINS="$SAFARI_DOMAINS $(sed -n '/"requestDomains"/,/]/p' "$RULES" | grep -oE '"[a-z0-9.-]+\.[a-z0-9-]+"' | tr -d '"' | sort -u | tr '\n' ' ')"
fi
safari_urls() {
  local d sub
  for d in $SAFARI_DOMAINS; do
    for sub in "" "www."; do
      printf '<string>https://%s%s</string><string>http://%s%s</string>' "$sub" "$d" "$sub" "$d"
    done
  done
  for sub in old new np i sh; do
    printf '<string>https://%s.reddit.com</string>' "$sub"
  done
  for sub in m web mbasic touch; do
    printf '<string>https://%s.facebook.com</string>' "$sub"
  done
}

chromium_payload() { # domain, private-key, add-profile-key, extra
  cat <<P
    <dict>
      <key>PayloadType</key><string>$1</string>
      <key>PayloadIdentifier</key><string>local.redditnsfwblocker.$1</string>
      <key>PayloadUUID</key><string>$(u)</string>
      <key>PayloadVersion</key><integer>1</integer>
      <key>$2</key><integer>1</integer>
      <key>BrowserGuestModeEnabled</key><false/>
      <key>$3</key><false/>
      $4
    </dict>
P
}

cat > "$OUT" <<EOF2
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>PayloadDisplayName</key><string>Reddit NSFW Blocker</string>
  <key>PayloadDescription</key><string>Keeps the Reddit NSFW Blocker installed in every browser.</string>
  <key>PayloadIdentifier</key><string>local.redditnsfwblocker</string>
  <key>PayloadUUID</key><string>$(u)</string>
  <key>PayloadType</key><string>Configuration</string>
  <key>PayloadScope</key><string>System</string>
  <key>PayloadVersion</key><integer>1</integer>
  <key>PayloadContent</key>
  <array>
    <dict>
      <key>PayloadType</key><string>org.mozilla.firefox</string>
      <key>PayloadIdentifier</key><string>local.redditnsfwblocker.firefox</string>
      <key>PayloadUUID</key><string>$(u)</string>
      <key>PayloadVersion</key><integer>1</integer>
      <key>EnterprisePoliciesEnabled</key><true/>
      <key>ExtensionSettings</key>
      <dict>
        <key>$FF_ID</key>
        <dict>
          <key>installation_mode</key><string>force_installed</string>
          <key>install_url</key><string>$FF_URL</string>
          <key>private_browsing</key><true/>
        </dict>
      </dict>
      <key>DisableSafeMode</key><true/>
      <key>BlockAboutProfiles</key><true/>
      <key>BlockAboutConfig</key><true/>
      <key>DisableDeveloperTools</key><true/>
    </dict>
    <dict>
      <key>PayloadType</key><string>com.apple.familycontrols.contentfilter</string>
      <key>PayloadIdentifier</key><string>local.redditnsfwblocker.safari</string>
      <key>PayloadUUID</key><string>$(u)</string>
      <key>PayloadVersion</key><integer>1</integer>
      <key>restrictWeb</key><true/>
      <key>useContentFilter</key><true/>
      <key>whitelistEnabled</key><false/>
      <key>filterBlacklist</key><array>$(safari_urls)</array>
    </dict>
$(chromium_payload com.google.Chrome IncognitoModeAvailability BrowserAddPersonEnabled "$BLOCK_REDDIT")
$(chromium_payload org.chromium.Chromium IncognitoModeAvailability BrowserAddPersonEnabled "$BLOCK_REDDIT")
$(chromium_payload com.brave.Browser IncognitoModeAvailability BrowserAddPersonEnabled "<key>TorDisabled</key><true/>$BLOCK_REDDIT")
$(chromium_payload com.microsoft.Edge InPrivateModeAvailability BrowserAddProfileEnabled "$EDGE_EXTRA")
  </array>
</dict>
</plist>
EOF2

if command -v plutil >/dev/null; then plutil -lint "$OUT"; fi
echo
echo "Profile written to $OUT"
echo "Opening it now. Then go to System Settings > General > Device Management"
echo "(or Privacy & Security > Profiles), click Reddit NSFW Blocker, and Install."
if [[ -z "${DRY_RUN:-}" ]]; then open "$OUT"; fi
