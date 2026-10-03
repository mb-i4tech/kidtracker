#!/usr/bin/env bash
set -euo pipefail
# Entire rehearsal uses a new disposable container, synthetic data and no host DB ports.
root=$(cd "$(dirname "$0")/.." && pwd)
work=$(mktemp -d)
name="kidtracker-upgrade-${RANDOM}-$$"
cleanup(){ docker rm -f "$name" >/dev/null 2>&1 || true; docker network rm "$name" >/dev/null 2>&1 || true; docker run --rm -v "$work:/cleanup" alpine:3.22 chown -R "$(id -u):$(id -g)" /cleanup >/dev/null 2>&1 || true; rm -rf "$work"; }
trap cleanup EXIT
docker network create "$name" >/dev/null
docker run -d --name "$name" --network "$name" -e POSTGRES_DB=kidtracker_test \
 -e POSTGRES_USER=kidtracker_test -e POSTGRES_PASSWORD=disposable-test-only postgres:18.6-alpine >/dev/null
for i in $(seq 1 60); do docker exec "$name" pg_isready -U kidtracker_test >/dev/null 2>&1 && break; sleep 1; done
mkdir -p "$work/baseline"
git -C "$root" archive 1fc5855e02c8437ce3dc315f77fdc208748b63fa | tar -x -C "$work/baseline"
cp "$root/tests/fixtures/PostgresUpgradeSeed.java.txt" "$work/baseline/src/test/java/ru/mecotrade/kidtracker/processor/PostgresUpgradeRehearsalTest.java"
url="jdbc:postgresql://$name:5432/kidtracker_test"
docker run --rm --network "$name" -v "$work/baseline:/app" -v kidtracker-maven-cache:/root/.m2 -w /app \
 -e KIDTRACKER_UPGRADE_POSTGRES_URL="$url" maven:3.8.8-eclipse-temurin-8 mvn -B -Dtest=PostgresUpgradeRehearsalTest test
docker exec "$name" pg_dump -U kidtracker_test -d kidtracker_test --schema-only | sed '/^\\restrict /d;/^\\unrestrict /d' > "$work/before.sql"
docker run --rm --network "$name" -v "$root:/app" -v kidtracker-maven-cache:/root/.m2 -w /app \
 -e KIDTRACKER_UPGRADE_POSTGRES_URL="$url" maven:3.9.11-eclipse-temurin-21 mvn -B -Dtest=PostgresUpgradeRehearsalTest test
docker exec "$name" pg_dump -U kidtracker_test -d kidtracker_test --schema-only | sed '/^\\restrict /d;/^\\unrestrict /d' > "$work/after.sql"
diff -u "$work/before.sql" "$work/after.sql"
echo 'PASS populated Java8 -> Java21 / Hibernate5 -> Hibernate6 upgrade; schema unchanged'
