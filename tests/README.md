# Focused dialog regressions

The original focused fix used a source-level stub that did not test localization or rendered controls.

`device-dom-regression.cjs` exercises actual static HTML, the lockfile-pinned jQuery and Bootstrap, the real `i18n.js`, and `device.js`. It opens My kids, triggers the actual plus click handler, and checks that the Bootstrap add-device dialog is visible and its ID input enabled for `lt-LT`, `en-US`, and `ru-RU`. Network/API responses and STOMP are fixture data; it does not connect to a site or simulate ownership assignment.

Run from the repository root with Node 22:

```sh
npm ci --ignore-scripts
npm test
npx playwright install chromium
npm run test:browser
```

The earlier stub regression is superseded by the DOM regression and Chromium tests.
Chromium tests deny external network, exercise public-endpoint help and token failure/retry/cancel, and do not mutate a real backend.

Root cause of the remaining plus failure: `i18n.translate()` returned `undefined` for browser languages not present in its token dictionaries. The add dialog uses `i18n.format()`, which then throws `Cannot read properties of undefined (reading 'split')`. The earlier stub replaced `i18n.format()` and therefore missed this failure. The fix returns the original token for unsupported locales; `setLocale()` now uses its argument rather than rereading the navigator.
