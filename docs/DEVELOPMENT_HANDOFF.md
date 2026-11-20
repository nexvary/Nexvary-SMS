# Development handoff — 2026-11-21 (final)

Repository initialized on main with README only.
All work is on `feature/android-sms-foundation` (open PR #1).

## Commit history on feature branch

| Commit | Description |
|--------|-------------|
| d4357b4 | Initial foundation: Java/Android, security, 15 unit tests, CI pipeline |
| 48e83f9 | Fix lint NewApi (windowLightNavigationBar), add TelnyxClientTest (12 HTTP mock tests) |
| db2d108 | Fix org.json stub in JVM unit tests |
| d86ce69 | Update HANDOFF doc with CI evidence |
| ab7c96c | Premium dark UI redesign + API key security fix |

## Implemented features

- Native Android Java, minSdk 26 / compileSdk+targetSdk 35; AGP 8.9.2, Gradle 8.11.1, Java 17
- Full Arabic RTL interface with premium dark theme (#0A0A12 bg, #C9A227 gold branding, #12C3AA teal accents)
- Exact Nexvary Inc sender / profile `4001a1ea-906a-4085-b6a2-9188edac65b8`
- Egyptian mobile number normalisation (+2010/11/12/15 prefixes only)
- Unicode/GSM multipart estimate with live counter
- Explicit paid-send confirmation dialog (shows recipient, segment count, message text)
- OkHttp 4.12.0 with retries and redirects disabled; one POST per explicit send
- GET-only status refresh (never retriggers a send)
- Persisted send lock before POST; unknown outcomes remain locked until user reviews Telnyx portal
- Optional AES-GCM Android Keystore key storage with Save/Forget buttons
- Security fix: saved key is no longer auto-displayed in the text field — vault is used internally
- Status labels distinguish: accepted / sent / delivered / failed / unknown
- Process-death recovery: in-flight → unknown outcome, locked until user action
- FLAG_SECURE (no screenshots), no key/body in logs, no SMS/contacts permissions
- HTTPS-only, redirects disabled, no retry on ambiguous POST

## CI evidence — run 44832827264 (SUCCESS)

**Branch:** `feature/android-sms-foundation` (HEAD ab7c96c)
**Workflow:** https://github.com/nexvary/Nexvary-SMS/actions/runs/44832827264
**Result:** BUILD SUCCESSFUL in 1m 13s
**Tests:** 27 unit tests (15 SmsPolicyTest + 12 TelnyxClientTest) — 0 failures
**Lint:** 0 errors (warnings do not abort build)
**APK signature:** Verified using v2 scheme (APK Signature Scheme v2): true

## Installable APK

**Artifact:** Nexvary-SMS-1.0.0-APK
**Run:** https://github.com/nexvary/Nexvary-SMS/actions/runs/44832827264
**APK size:** 1,362,436 bytes (~1.3 MB)
**SHA256:** `461707620d4c6b652f6eec4c1ca780cee204abce8147310eecea31df224bcb42`
**Signing:** Android debug key (temporary CI key — not a production release key)
**Expires:** 2026-12-21

## Unit tests (27 total, all passing)

**SmsPolicyTest (15):** normalization, prefix validation, GSM/Unicode part counting,
extension chars × 2, emoji UTF-16 surrogates, UUID validation, injection rejection.

**TelnyxClientTest (12):** UTF-8 body, HTTP 422/500 error results, invalid JSON throws,
missing data key throws, no auto-retry (request count = 1), connection reset throws,
accepted/sent/delivered status distinction, cost parsing, GET/POST method assertions.

All tests use MockWebServer — no real Telnyx key, no paid SMS.

## Verification limits

- Unit tests only (JVM). No Android emulator or physical device tests performed.
- Android Keystore not tested (requires a real device).
- Rotation/process-kill lifecycle not tested with instrumented tests.
- APK is debug-signed — not suitable for production distribution or long-term update delivery.
- No delivery confirmation (Accepted/Sent ≠ Delivered — physical device required).
- No AWS, FusionPBX, or other repository changes made.
- No merge to main. No release published. No paid SMS sent.

## Branch / PR

- Branch: `feature/android-sms-foundation`
- PR: https://github.com/nexvary/Nexvary-SMS/pull/1 (open, awaiting review)
