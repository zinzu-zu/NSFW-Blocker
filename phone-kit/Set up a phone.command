#!/bin/bash
# Double-click this in Finder to set up the phone that's plugged in. It opens
# in Terminal and runs phone-kit setup for you:
#   - if this Mac has no adb, the tool that talks to the phone over USB, it
#     offers to download Android's platform tools from Google into this folder
#   - the first time, it makes your settings file and opens it in TextEdit
#   - when the kit stops for something you need to do, Enter goes on from there
# The first time, macOS may not open it because it was downloaded: README.md
# says what to click.

cd "$(dirname "$0")" || exit 1
HERE=$(pwd)
# The kit is next to this file, or one folder up if this file was moved into
# a subfolder. my-phone.conf is used from either place, this folder first.
KIT=$HERE
[ -x "$KIT/phone-kit" ] || [ ! -x "$HERE/../phone-kit" ] || KIT=$(cd "$HERE/.." && pwd)
if [ ! -x "$KIT/phone-kit" ]; then
  printf '%s\n' "phone-kit isn't next to this file or in the folder above it." \
    "Put \"Set up a phone.command\" back in the phone-kit folder, or in a folder inside it."
  printf 'Press Enter to close this window. '; read -r _; exit 1
fi
CONF=${PHONE_KIT_CONF:-}
if [ -z "$CONF" ]; then
  CONF=$HERE/my-phone.conf
  [ -f "$CONF" ] || [ ! -f "$KIT/my-phone.conf" ] || CONF=$KIT/my-phone.conf
fi
export PHONE_KIT_CONF=$CONF
PLATFORM_TOOLS=https://dl.google.com/android/repository/platform-tools-latest-darwin.zip
umask 077
export PHONE_KIT_AGAIN="press Enter in this window"
# Ctrl-C stops the kit, not this window: the kit says where it stopped.
trap ':' INT

say() { printf '%s\n' "$*"; }
ask() { printf '%s ' "$1"; REPLY=""; IFS= read -r REPLY; }   # fails at the end of input
bye() { say ""; ask "Press Enter to close this window."; exit "$1"; }

say "phone-kit: setting up the phone that's plugged in."
say ""

# adb: this Mac's own, or the platform tools downloaded into this folder before.
adb=${PHONE_KIT_ADB:-}
if [ -z "$adb" ]; then
  for a in "$(command -v adb 2>/dev/null)" ${PHONE_KIT_ADB_PLACES-/opt/homebrew/bin/adb /usr/local/bin/adb} \
      "$KIT/platform-tools/adb"; do
    if [ -n "$a" ] && [ -x "$a" ]; then adb=$a; break; fi
  done
fi
if [ -z "$adb" ]; then
  say "This Mac doesn't have adb, the tool that talks to the phone over USB. The kit can"
  say "download it, as Android's platform tools from Google, into this folder."
  ask "Download it now? [y/n]" || exit 1
  case "$REPLY" in
    y|Y|yes|Yes) ;;
    *) say "Then install adb yourself (brew install android-platform-tools), and open this again."
       bye 1 ;;
  esac
  tmp=$(mktemp -d) || bye 1
  if curl -fL --progress-bar -o "$tmp/platform-tools.zip" "$PLATFORM_TOOLS" \
      && unzip -q -o "$tmp/platform-tools.zip" -d "$KIT" && [ -x "$KIT/platform-tools/adb" ]; then
    rm -rf "$tmp"
    adb=$KIT/platform-tools/adb
    say "Got it, in $KIT/platform-tools."
    say ""
  else
    rm -rf "$tmp"
    say "The download didn't work. Check the internet connection, and open this again."
    bye 1
  fi
fi
export PHONE_KIT_ADB=$adb

if [ ! -f "$CONF" ]; then
  cp "$KIT/my-phone.conf.example" "$CONF" || bye 1
  say "Your settings go in my-phone.conf, which opens in TextEdit now. Put in your"
  say "NextDNS address and your apps, and save it (Cmd-S)."
  open -e "$CONF"
  ask "Press Enter here once it's saved." || exit 1
fi

while :; do
  say ""
  if "$KIT/phone-kit" setup; then break; fi
  say ""
  say "Enter goes on. To change your settings first, type s and press Enter."
  ask "To stop, close this window." || exit 1
  case "$REPLY" in
    s|S) open -e "$CONF"
         ask "Press Enter here once it's saved." || exit 1 ;;
  esac
done
bye 0
