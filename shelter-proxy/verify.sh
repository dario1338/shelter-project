#!/bin/sh
# Starts the proxy image with made-up upstreams and checks what it answers by itself, so that a mistake
# in the configuration fails the pull request and not the delivery to staging.
# Usage: sh verify.sh IMAGE COMMIT
set -eu

image="$1"
commit="$2"
name="proxy-check-$$"

cleanup() {
  docker rm -f "$name" >/dev/null 2>&1 || true
}
trap cleanup EXIT

docker run -d --name "$name" -e API_UPSTREAM=api.example.com -e UI_UPSTREAM=ui.example.com "$image" >/dev/null

# The answer to a path, asked from inside the container.
get() {
  docker exec "$name" wget -q -T 20 -O - "http://127.0.0.1:8080$1"
}

# The whole answer to a path, headers included, also when it is an error (wget shows no headers then).
# The busy-box nc closes the connection as soon as its input ends, so the input is held open for a few
# seconds, which is longer than the proxy needs to answer; nc ends when the proxy closes the connection.
headers() {
  { printf 'GET %s HTTP/1.0\r\nHost: check\r\n\r\n' "$1"; sleep 7; } | docker exec -i "$name" timeout 15 nc 127.0.0.1 8080 || true
}

fail() {
  echo "Proxy check failed: $1"
  docker logs "$name" || true
  exit 1
}

attempt=0
until get /proxy-health >/dev/null 2>&1; do
  attempt=$((attempt + 1))
  [ "$attempt" -le 20 ] || fail 'the proxy did not start'
  sleep 1
done

[ "$(get /proxy-health)" = "ok" ] || fail '/proxy-health does not answer ok'
[ "$(get /api-host)" = "api.example.com" ] || fail '/api-host does not announce the API host'
[ "$(get /proxy-version)" = "{\"revision\":\"$commit\"}" ] || fail '/proxy-version does not report the commit'

# Every kind of answer carries the security headers: a static one, one from a location that adds its own
# header, and a proxied one that fails (the upstream does not exist, so the proxy answers 502).
for path in /proxy-health /api-host /api/health; do
  found="$(headers "$path")"
  for header in X-Content-Type-Options X-Frame-Options Referrer-Policy Permissions-Policy; do
    echo "$found" | grep -qi "^$header:" || fail "$header is missing from the answer to $path"
  done
done

echo 'Proxy check passed'
