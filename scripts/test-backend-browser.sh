#!/usr/bin/env bash
set -euo pipefail
image=${1:?usage: test-backend-browser.sh IMAGE}
name="kidtracker-browser-${RANDOM}-$$"
cleanup(){ docker rm -f "$name" >/dev/null 2>&1 || true; }
trap cleanup EXIT
# No host volumes, external DB, or device port publication. Only synthetic credentials.
docker run -d --name "$name" -p 127.0.0.1::8003 \
 -e SERVER_SSL_ENABLED=false -e SPRING_DATASOURCE_URL=jdbc:h2:mem:browser \
 -e SPRING_JPA_HIBERNATE_DDL_AUTO=create-drop \
 -e KIDTRACKER_ADMIN_USERNAME=synthetic-admin \
 -e KIDTRACKER_ADMIN_PASSWORD=synthetic-test-only-password \
 -e KIDTRACKER_SERVER_DEBUG_START=false "$image" >/dev/null
port=$(docker port "$name" 8003/tcp | sed 's/.*://')
base="http://127.0.0.1:$port"
ready=false
for attempt in $(seq 1 90); do
 if curl -fsS "$base/login" >/dev/null 2>&1; then ready=true; break; fi
 sleep 1
done
if [ "$ready" != true ]; then docker logs "$name"; exit 1; fi
KIDTRACKER_TEST_BASE_URL="$base" npx playwright test tests/browser/backend-auth.spec.cjs
