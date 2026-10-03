# Security modernization milestone 2

Base: product milestone commit `1fc5855e02c8437ce3dc315f77fdc208748b63fa`. This is a separately reviewed change, not a production deployment.

## Measured runtime migration

Java 8 / Spring Boot 2.5.2 / Hibernate 5 -> Java 21 LTS / Spring Boot 3.5.16 / Hibernate 6. Boot 3.5.16 was the latest stable 3.5 patch in Maven Central on 2026-10-03. This is a supported migration stepping stone, not a claim to use the newest Boot major; review the support window before release and schedule the next supported line. Jakarta imports, repository queries, legacy Base64 conversion and native JAVE audio were tested. Guava 33.7.2, Thumbnailator 0.4.21 and JAVE 4.2.0 replace old libraries. Scanner-led compatible overrides further patch Jackson 2.21.7, Tomcat 10.1.60, PostgreSQL JDBC42.7.13, commons-lang3 3.18.0 and Log4j API2.25.5; the Boot BOM alone still had fixable advisories.

Hibernate mapping explicitly preserves the existing shared `hibernate_sequence` with allocation size 1, integer enum columns, quoted user table, OID media data and legacy ownership keys. Default `ddl-auto=validate` prevents implicit production DDL. Existing PostgreSQL deployments must REMOVE `org.hibernate.dialect.PostgreSQL10Dialect` overrides (class removed) or replace with `org.hibernate.dialect.PostgreSQLDialect`; driver auto-detection is preferred. Do not deploy the old dialect environment unchanged.

**Fresh databases:** initialize deliberately in a disposable/staging environment using an explicit schema-init procedure; `validate` does not create a schema. Existing H2 1.x data files are not tested for automatic H2 2.x conversion and must not be opened in place without an offline migration and backup.

## Security contracts

- Session CSRF enabled. `GET /api/csrf` returns the current masked token and header name with no-store. Client mutations send that header; tokens are refreshed rather than kept in local storage. Login/logout use Spring CSRF handling.
- Confirmation, alarm/notification acknowledgement and raw administrative command endpoints are POST-only. GET cannot mutate these resources. API authentication errors are HTTP 401, authorization/CSRF failures 403 rather than HTML login redirects.
- Both confirmation APIs share a per-authenticated-user budget: five attempts per five minutes, HTTP 429/Retry-After. No trust in X-Forwarded-For. Map capacity is bounded and fails closed. This limiter is instance-local, not distributed; use a shared store before multi-replica deployment.
- SockJS transport alone is exempt from HTTP CSRF; authenticated STOMP CONNECT verifies the session's CSRF proof. Same-origin handshake remains required. Direct broker queue subscription/publication and foreign user application destinations are rejected.
- Existing phone immutability, BCrypt password hashes, administrator roles, token user-binding/expiry/atomic consumption and protocol listener separation remain intact.

## Reproduce verification

```sh
npm ci --ignore-scripts
npm run lint && npm test && npm run build
npx playwright install chromium
npm run test:browser
# JDK21 Maven + fresh PostgreSQL fixture tests run in GitHub CI.
docker build -f Dockerfile.product -t kidtracker:security-review .
scripts/test-backend-browser.sh kidtracker:security-review
scripts/test-postgres-upgrade.sh
scripts/scan-image.sh kidtracker:security-review /tmp/kidtracker-scan
python3 scripts/summarize-scan.py /tmp/kidtracker-scan/trivy.json
```

The real browser harness starts an isolated image with a fresh in-memory H2 DB and synthetic administrator, logs in through Spring's generated form, changes the initial admin phone with valid CSRF, creates a synthetic ordinary account, logs in as that account and verifies negative authorization. It also opens a real authenticated STOMP-over-WebSocket session using the session CSRF token. It publishes only a loopback HTTP port, no device port, uses no persistent volumes and removes its container. Fixture-only modal tests are separate and not misrepresented as backend proof.

The populated upgrade script materializes the exact M1 baseline, runs Java8/Hibernate5 to create and populate a disposable PostgreSQL18.6 database, then validates/reads/writes it with Java21/Hibernate6. Synthetic fixtures include BCrypt/admin/member/creator relationships, ownership, device, config/contact, message and OID media. A schema-only dump comparison confirms no DDL changes. No private production data is copied. The baseline seed source is preserved in `tests/fixtures/PostgresUpgradeSeed.java.txt`.

## Scanner evidence

`docs/security/trivy-before.json` and `trivy-after.json` contain package/version/advisory summaries from Trivy 0.69.3, not a manually curated allowlist. The full scanner JSON remains a local build artifact. `npm-before.json` and `npm-after.json` are npm audit output. A zero scanner count is not a security certificate; OS advisory coverage, database date and native bundled ffmpeg coverage are limitations. No secret/source upload scanner was used.

### Recorded 2026-10-03 result

| Scope | Before | After |
|---|---|---|
| Java advisory findings | 18 critical, 58 high, 59 medium, 15 low | 0 findings |
| OS advisory findings | 38 medium, 4 low (Ubuntu26.04 base) | 13 medium, 16 low (patched Ubuntu22.04 base) |
| npm production | 0 | 0 |
| npm build-tool chain | 5 low | 5 low |

OS distributions changed, so OS totals are not a like-for-like fixed-CVE count. Java comparison uses the same scanner vulnerability DB/cache. No Java/OS high or critical findings remain in the scanned candidate. The five low npm findings are the Browserify/elliptic transitive development chain; not silently accepted as production vulnerabilities or falsely reported as removed.

Local gate: full image Maven suite 42 tests, zero failures/errors (two PostgreSQL opt-ins skipped inside image); separate populated PostgreSQL18.6 rehearsal passes with schema unchanged. Seven JS unit checks plus three DOM locale cases, five fixture Chromium cases and one real-backend Chromium login/CSRF/authorization/WebSocket test pass. CI runs the fresh-PG, populated upgrade and real-image browser gates separately.

## Deployment is intentionally NOT performed

Require review plus green CI, backup and approved staging first. Preserve existing DB/mounts/users/passwords/roles and internal ports 8001/8003; keep 8002 disabled. Public device endpoint configuration remains independent of UI. Remove old dialect override as above; use `validate` to fail closed on unexpected schema. Never run the upgrade test URL against a persistent database (test fixtures are destructive).

Rollback uses the recorded M1 image and existing PostgreSQL storage only after confirming the schema remained unchanged. The synthetic rehearsal proves this for its covered mappings, not arbitrary production drift. No firmware, watch assignment or SMS is part of this work.

## Remaining release gates

Production-population/schema-drift rehearsal under operator control; distributed rate limiting if scaled; login brute-force protection; stable-session/reconnect tests across long-running clients; native ffmpeg and protocol parsing threat analysis; Java/browser dependency lifecycle updates. Existing legacy CDN resources still need a separately tested vendoring/update pass. Keep the UI TLS-terminated and access-restricted; never expose plain HTTP credentials publicly.
