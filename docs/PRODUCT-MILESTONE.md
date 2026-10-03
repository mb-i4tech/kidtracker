# Product stabilization milestone 1

This is a maintained fork of [mayask/kidtracker](https://github.com/mayask/kidtracker), based on commit `351f9d02bb2cca83b2e0bc5ace5a5a83e75778c2`, originally by [mecotrade](https://github.com/mecotrade/kidtracker). Apache-2.0 license and original attribution are preserved. This is the original Java watch-protocol server, **not a Traccar distribution**.

## Architecture and boundaries

Spring Boot 2.5.2 / Java 8 serves a session-authenticated REST API and static Browserify/jQuery/Bootstrap UI. Raw device TCP is separate from HTTP. SockJS/STOMP supplies live status; unavailable live transport must not block account and ownership dialogs. JPA stores users, devices and reports. Existing persistent schema and data are retained in this milestone.

Fixed unsupported locale formatting (including English and Lithuanian), strict-mode undeclared variables, Chart import and contact enum reference, misleading missing-phone disabled UI, asynchronous modal failures, error/timeout handling, ownership-token confirmation ordering and retry/cancel behavior. Error content and child names are rendered as text. SMS help uses explicitly configured public device coordinates, never the browser's LAN hostname. Missing endpoint configuration is visible instead of generating a wrong command.

## Build and verification

```sh
npm ci --ignore-scripts
npm run lint
npm test
npm run build
npx playwright install chromium
npm run test:browser
# Java 8 + Maven 3.8.8
mvn -B verify
# Full multi-stage image, frontend and backend verification included:
docker build -f Dockerfile.product -t kidtracker:$(git rev-parse --short=12 HEAD) .
```

The npm lockfile is committed. Browserify/minifier updates remove high/critical npm build-tool advisories; five low-severity elliptic-chain development advisories remain. Production npm dependency audit is clean at verification time. Compatible crypto/minifier overrides are explicit in package.json; do not blindly run audit fix (it selected an older vulnerable crypto implementation during evaluation). ESLint's focused runtime-safety rules cover every application JS module, not a cosmetic formatting rewrite. Tests include real HTML/jQuery/Bootstrap DOM execution and Chromium modal/token integration in English, Lithuanian and Russian. Browser tests deny all network requests and use explicit API fixtures; they **do not prove a real watch, production login, carrier SMS or live device ownership transfer**. Backend unit tests use mocks, plus an opt-in disposable PostgreSQL 18 schema/ownership round-trip in CI. This is not a migration rehearsal on populated PostgreSQL 18.6 data; that remains a release gate.

`Dockerfile.product` deliberately avoids the historical generated certificate and HTTP debug listener. It serves HTTP behind an operator-managed TLS reverse proxy; do not expose plain HTTP credentials to the Internet. Historical compose/build files below the original README are reference material, not the recommended upgrade path for an existing installation.

## Configuration / onboarding

Inject secrets through deployment settings, never commit an environment file:

- `KIDTRACKER_ADMIN_USERNAME` and strong `KIDTRACKER_ADMIN_PASSWORD`: bootstrap only when user table is empty. Existing users must never be reset by changing these variables.
- Existing `SPRING_DATASOURCE_*`, dialect and storage mounts: retain unchanged. Do not replace a production database with the historical compose PostgreSQL 13 service.
- `SERVER_PORT=8003`, `SERVER_SSL_ENABLED=false` behind TLS termination.
- `KIDTRACKER_SERVER_MESSAGE_PORT=8001`: internal watch listener.
- `KIDTRACKER_SERVER_PUBLIC_HOST=watch.example.com` and `KIDTRACKER_SERVER_PUBLIC_PORT=9001`: externally reachable raw TCP destination; the router forwards this to the internal listener. These are not HTTP proxy ports.
- `KIDTRACKER_SERVER_DEBUG_START=false`; do not publish 8002.
- `SPRING_H2_CONSOLE_ENABLED=false`.

An administrator creates named accounts; registration is not public. A newly bootstrapped administrator must complete its phone field before watch commands. Existing verified phone numbers should not be silently replaceable. Add a watch using its ID and name, then enter the token delivered through the existing watch protocol; knowing an ID alone is not ownership proof. Do not bypass the token to make a demo work. SMS text shown in the UI is an instruction for the authorized owner, not automatic transmission by this documentation or test suite.

## Coordinated deployment and rollback (operator only)

No product deployment is performed by this change. Before approval:

1. Require green PR checks and review. Record the exact currently running image ID/digest, source archive, environment/settings and container creation time. Record PostgreSQL 18.6 version, existing mounts and port bindings; take a verified normal backup using the existing operational procedure, never put a dump in this repository.
2. Build the reviewed SHA using `Dockerfile.product` above; publish an immutable SHA tag in the authorized registry. In Dokploy, preserve the existing app identity, PostgreSQL connection, persistent mounts, user data, TCP listener 8001 and UI 8003. Do not apply old compose files or create a replacement database.
3. Set only the new public endpoint variables to the deployment's actual forwarded address/port. Keep debug disabled. Deploy the immutable image or a drop source archive selecting `Dockerfile.product`; do not re-enable upstream auto-deploy.
4. Verify the running image/container really changed, authenticated `/api/user/info`, `/api/user/config` public host/port, `/api/user/kids/info`, and SockJS `/device/info`. Click My kids → + in English/Lithuanian; check visible help, cancel, errors and unchanged existing account/device list. Do not assign a watch or send commands during smoke testing.
5. Monitor startup/runtime logs without logging tokens or personal data. Only then call deployment complete.

Rollback: select the **recorded previous immutable image or preserved drop source archive**, restore only settings changed for this release, and redeploy the same app with the same DB/mounts/ports. Verify previous image ID and basic authenticated flows. This milestone introduces no intentional schema migration; never downgrade PostgreSQL or restore/overwrite user data as an application rollback. If schema changes are observed unexpectedly, stop and have the operator assess before rollback.

## Remaining security and upgrade work

This milestone is not a claim that this old stack is Internet-ready. Boot 2.5.2, Spring Security, Hibernate, H2, PostgreSQL JDBC, Guava and other Java dependencies need a staged supported-JDK/Boot upgrade and software-composition scan. Keep the service access-restricted until that review. Do not blindly jump to Boot 3: javax/jakarta migration and security config changes require integration coverage and schema compatibility proof.

Further gates: real PostgreSQL 18.6 disposable integration environment, login/admin authorization browser tests against a real isolated backend, CSRF review, device protocol threat model, ownership-token brute-force rate limiting, transport reconnect and concurrent modal lifecycle hardening. Tokens now default to six cryptographically random digits, remain short-lived and user-scoped, and are consumed atomically; existing four-digit overrides remain supported. None of this substitutes for rate limiting. Browser CDN dependencies and old Bootstrap should be vendored/pinned with integrity or upgraded in a separate measured change. Base Docker tags should be pinned to approved multi-architecture digests for release reproducibility. No firmware, watch assignment or SMS was executed during this milestone.
