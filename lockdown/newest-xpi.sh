# shellcheck shell=bash
# Sourced by lockdown.sh and update.sh. newest_xpi DIR prints the path of the
# Mozilla-signed add-on in DIR with the highest version, whatever the file is
# called (nsfw-blocker.xpi, "NSFW Blocker-1.4.1.xpi", ...). Files without
# Mozilla's signature are skipped. Prints nothing if there is none.
xpi_version() { # file -> version from its manifest.json, or nothing
  unzip -p "$1" manifest.json 2>/dev/null | sed -n 's/.*"version": *"\([^"]*\)".*/\1/p' | head -n1
}
newest_xpi() {
  local dir="$1" f v best="" best_v=""
  shopt -s nullglob
  for f in "$dir"/*.xpi "$dir"/*.xpi.zip; do
    unzip -l "$f" 2>/dev/null | grep -q "META-INF/" || continue
    v="$(xpi_version "$f")"
    [[ -n "$v" ]] || continue
    if [[ -z "$best" || "$(printf '%s\n%s\n' "$best_v" "$v" | sort -t. -k1,1n -k2,2n -k3,3n | tail -n1)" == "$v" && "$v" != "$best_v" ]]; then
      best="$f"; best_v="$v"
    fi
  done
  shopt -u nullglob
  [[ -n "$best" ]] && printf '%s\n' "$best"
  return 0
}
