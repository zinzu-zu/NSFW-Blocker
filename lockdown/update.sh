#!/bin/bash
# Installs a newer signed version of the add-on on a Mac that's already locked.
#   sudo bash update.sh /path/to/signed.xpi
# Tip: type "sudo bash ", drag update.sh into Terminal, type a space, then drag
# the signed .xpi in too. Without a path, it uses the signed .xpi in the
# blocker folder with the highest version, whatever it's called.
# Firefox notices the newer version in the locked folder and swaps it in on
# its next start. Needs the admin password, like any change to the lock.
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(dirname "$HERE")"
LIB="/Library/Application Support/nsfw-blocker"

if [[ $EUID -ne 0 ]]; then
  echo "Run this with sudo:  sudo bash \"$0\" /path/to/signed.xpi"; exit 1
fi
if [[ ! -d "$LIB" ]]; then
  if [[ -d "/Library/Application Support/RedditNSFWBlocker" ]]; then
    # Locks installed before the rename to NSFW Blocker (1.4.2) used that folder.
    echo "The lock still uses its old name. Run lockdown.sh once (and install the profile) instead."
  else
    echo "The lock isn't installed yet. Run lockdown.sh instead."
  fi
  exit 1
fi

# shellcheck source=newest-xpi.sh
. "$HERE/newest-xpi.sh"
SRC="${1:-}"
if [[ -z "$SRC" ]]; then
  SRC="$(newest_xpi "$ROOT")"
  if [[ -z "$SRC" ]]; then
    echo "Couldn't find an add-on signed by Mozilla (.xpi) in $ROOT."
    echo "Pass it directly:  sudo bash \"$0\" /path/to/signed.xpi"
    echo "Files there:"; ls -1 "$ROOT"
    exit 1
  fi
fi
if [[ ! -f "$SRC" ]]; then
  echo "No file at: $SRC"; exit 1
fi

# A signed add-on carries Mozilla's signature files; an unsigned zip doesn't.
if ! xpi_signed "$SRC"; then
  echo "This file isn't signed by Mozilla: $SRC"
  echo "Download the signed .xpi from your add-on's version page instead."; exit 1
fi
VERSION="$(xpi_version "$SRC")"

cp "$SRC" "$LIB/nsfw-blocker.xpi"
chown root:wheel "$LIB/nsfw-blocker.xpi"
chmod 644 "$LIB/nsfw-blocker.xpi"
echo "Installed version ${VERSION:-unknown} from $SRC"
echo "Quit Firefox with Cmd+Q and open it again."
