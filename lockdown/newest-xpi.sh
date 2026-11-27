# shellcheck shell=bash
# Sourced by lockdown.sh and update.sh. newest_xpi DIR prints the path of the
# Mozilla-signed add-on in DIR with the highest version, whatever the file is
# called (nsfw-blocker.xpi, "NSFW Blocker-1.4.1.xpi", ...). Files without
# Mozilla's signature are skipped. Prints nothing if there is none.
# The callers run with "set -euo pipefail", so nothing here pipes into a
# command that stops reading early (grep -q, head): with pipefail that counts
# as a failure when the listing is long, as it is for a signed add-on.

xpi_signed() { # file -> success if it carries Mozilla's signature files
  local list
  list="$(unzip -l "$1" 2>/dev/null)" || return 1
  [[ "$list" == *META-INF/* ]]
}
xpi_version() { # file -> version from its manifest.json, or nothing
  local manifest
  manifest="$(unzip -p "$1" manifest.json 2>/dev/null)" || return 0
  printf '%s\n' "$manifest" | sed -n 's/.*"version": *"\([^"]*\)".*/\1/p' | sed -n '1p'
}
newer() { # a b -> success if version a is higher than version b
  [[ "$1" != "$2" && "$(printf '%s\n%s\n' "$1" "$2" | sort -t. -k1,1n -k2,2n -k3,3n | sed -n '$p')" == "$1" ]]
}
newest_xpi() {
  local dir="$1" f v best="" best_v=""
  for f in "$dir"/*.xpi "$dir"/*.xpi.zip; do
    [[ -f "$f" ]] || continue
    xpi_signed "$f" || continue
    v="$(xpi_version "$f")"
    [[ -n "$v" ]] || continue
    if [[ -z "$best" ]] || newer "$v" "$best_v"; then
      best="$f"; best_v="$v"
    fi
  done
  if [[ -n "$best" ]]; then printf '%s\n' "$best"; fi
}
