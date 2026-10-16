#!/usr/bin/env bash
# Builds the SDK, then compiles and RUNS every example against the spec repo's mock server.
#
#   STAMPMITRA_SPEC_DIR=/path/to/stampmitra-developer-openapi scripts/run-examples.sh
#
# Needs Node (for the mock server) and Maven. The Spring example is compiled with -Pspring when JDK >= 17.
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
spec="${STAMPMITRA_SPEC_DIR:-$root/../stampmitra-developer-openapi}"
[ -f "$spec/scripts/mock-server.mjs" ] || { echo "spec repo not found: set STAMPMITRA_SPEC_DIR" >&2; exit 2; }

port="${MOCK_PORT:-$(python3 -c 'import socket; s=socket.socket(); s.bind(("127.0.0.1",0)); print(s.getsockname()[1])')}"
node "$spec/scripts/mock-server.mjs" --port "$port" --host 127.0.0.1 >/dev/null &
mock_pid=$!
trap 'kill "$mock_pid" 2>/dev/null || true' EXIT
for _ in $(seq 1 40); do curl -sf "http://127.0.0.1:$port/__mock/health" >/dev/null && break; sleep 0.25; done

( cd "$root" && mvn -B -q install -DskipTests )

export STAMPMITRA_API_KEY="sk_test_examples0123456789"
export STAMPMITRA_BASE_URL="http://127.0.0.1:$port"
export STAMPMITRA_WEBHOOK_SECRET="whsec_test_demo"

cd "$root/examples"
mvn -B -q compile
for example in VerifyPan CreateOrder LogsPagination AsyncVerify OkHttpTransportExample; do
  echo "=== $example"
  mvn -B -q exec:java -Dexec.mainClass="in.stampmitra.examples.$example"
done
echo "=== WebhookReceiver --demo"
mvn -B -q exec:java -Dexec.mainClass=in.stampmitra.examples.WebhookReceiver -Dexec.args="--demo"

java_major="$(java -version 2>&1 | sed -n '1s/.*version "\([0-9]*\).*/\1/p')"
if [ "${java_major:-0}" -ge 17 ]; then
  echo "=== Spring Boot webhook controller (compile only here; see README for the run recipe)"
  mvn -B -q -Pspring clean compile
fi
echo "all examples ran"
