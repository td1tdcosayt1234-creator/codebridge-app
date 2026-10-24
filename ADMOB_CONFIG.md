# AdMob Configuration — CodeBridge Android app

Account: td1tdcosayt1234-creator (created 2026-10-24)

| Item | Value |
|------|-------|
| Application ID | `ca-app-pub-3123261757380037~5789907866` |
| Rewarded Ad Unit ID | `ca-app-pub-3123261757380037/5119738300` |
| Ad unit name | `codebridge-earn-reward` |
| Publisher ID | `pub-3123261757380037` |
| Strategy | Rewarded ad shown natively from the app's Earn tab; server-side `/api/earn` verification unchanged (UA gate + nonce + seconds) |

## Test vs production
Google requires test ad units in builds that are NOT yet approved by the AdMob app review.
Current state: app created **2026-10-24, "Requires review"** (~2 days) → v1.0.1 ships with
`TEST_AD_UNIT = ca-app-pub-3940256099942544/5224354917` and `USE_TEST = true` in
MainActivity. After AdMob approves the app, flip `USE_TEST = false` and bump version.

Monetag SmartLink (web/client path) remains for browser users, but its click is
intercepted inside the app (window.open hook) → runs the AdMob rewarded ad instead.
