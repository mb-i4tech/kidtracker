# Operational release: guided onboarding and bounded services

Builds on security PR #2. Production rollout requires the reviewed SHA, passing CI, protected verified backup, schema validation and recorded rollback source/image. No watch assignment, firmware or SMS is part of release verification.

## Changes

- Login submissions bounded by normalized account (10/minute) and direct peer (60/minute); memory bounded, 429/Retry-After, no forwarded-header trust. Short windows do not modify passwords, persistent accounts or active sessions. Forwarded-header strategy defaults to none; deployment must not override it with unrestricted framework header handling. Shared proxies share the peer budget; a distributed limiter is needed before scaling.
- Device parser bounded by four-hex-digit protocol payload length and bounded headers, framing/deadline checks. Device connections limited to 128, idle sockets timeout, notification queue bounded. Real hardware soak remains necessary; caps are appropriate to a small private installation, not a claim of fleet sizing.
- Native audio conversion uses file-only FFmpeg protocols, no shell, two slots, one codec thread, 30-second hard timeout, output cap, unique temporary files and cleanup. Native parser is not an OS sandbox; ongoing bundled FFmpeg updates remain necessary.
- Browser executable/style dependencies served locally from lockfile-pinned packages. `npm run build` emits `/vendor/manifest.json` containing versions and SHA256 hashes. No runtime CDN JS/CSS required. Map tiles remain an explicit external map dependency, not executable code.
- Mobile-friendly Lithuanian guided onboarding: child name and identifier help, explicit configured public endpoint, copy-only SMS, clear watch-delivered confirmation code instructions, accessible numeric entry, field preservation, explicit resend, Retry-After cooldown and honest completion only after backend ownership listing confirms the ID. No invented online status or expiry countdown when backend does not provide it.

## Verification

`npm test`, lint, hermetic Chromium onboarding cases, full application startup with third-party requests blocked, real isolated Spring login/CSRF/admin/authorization/WebSocket, Java protocol/audio/login bounds and populated PostgreSQL18.6 migration are CI gates. Screenshot evidence in `docs/evidence` contains synthetic fixture data only.

Release scan: `docs/security/trivy-release.json`, `npm-release.json`. Java advisory findings: zero; OS medium/low and development-only npm elliptic-chain advisories remain documented. Browser package versions are pinned, but old Bootstrap4/Gijgo/STOMP APIs still warrant a separate replacement project; no hidden major UI rewrite in this release.

## Safe rollout checklist

1. Inspect current environment/mounts/ports/source/image; preserve settings privately. Back up PostgreSQL using a read-only pg_dump helper on its existing private network, no new database port. Copy the dump into protected operator storage, verify hash and restore into a new isolated PostgreSQL18.6 instance.
2. Run candidate with `ddl-auto=validate` against the restored copy, verify existing account login without resetting credentials, compare account/password/role fingerprint and schema before/after. Never run destructive fixture seed scripts against the production database or its protected backup.
3. Build/release exact reviewed source. Preserve old source archive and image for application-only rollback. Set public endpoint independently, remove PostgreSQL10Dialect override, set PostgreSQLDialect or auto-detection, keep schema validate, debug off and all existing volumes/ports.
4. Verify actual running image/container changed, startup logs, authenticated APIs, local vendor assets and HTTP CSRF. Browser navigation must use the permitted browser harness; if policy blocks it, record the limitation rather than bypassing it with a different transport.
5. Verify public raw TCP forwarding separately from LAN HTTP. TLS domain requires functioning authorized DNS/tunnel; absent DNS is an external setup blocker, not permission for broad firewall/DNS changes.

Backup contents, account hashes, operational addresses, credentials and deployment metadata are never repository artifacts. Deployment evidence is returned privately to the operator.
