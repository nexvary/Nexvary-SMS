# Development handoff — 2026-11-20 (updated)

Repository was empty. Main initialized with README only; app foundation is on `feature/android-sms-foundation`.

## Implemented foundation (commit d4357b4)
- Native Android Java, minSdk 26 / compileSdk+targetSdk 35; AGP 8.9.2, Gradle 8.11.1, Java 17.
- Arabic RTL form, exact Nexvary Inc sender/profile, Egyptian mobile normalization, Unicode/GSM multipart estimate, explicit paid confirmation.
- OkHttp 4.12.0 with retries and redirects disabled; one POST per explicit send, GET-only status lookup.
- Persisted send lock before POST, in-process AtomicBoolean and serial executor. Unknown outcomes remain locked; manual portal review required before reset.
- Optional AES-GCM Android Keystore key storage, forget action, screenshots/autofill/backup restricted. No key or SMS body in journal/logs.
- Response ID/state/cost parsing. No SMS/contacts permissions, analytics, backend, AWS changes or paid external requests.
- Fifteen JUnit policy tests and GitHub Actions tests/lint/APK-signature pipeline.

## CI fixes applied (commits 48e83f9, db2d108)

### Fix 1: Lint NewApi error — `android:windowLightNavigationBar` (commit 48e83f9)
- **Root cause:** `app/src/main/res/values/styles.xml` used `android:windowLightNavigationBar` which requires API 27, but `minSdk` was set to 26.
- **Fix:** Split styles into `values/styles.xml` (without `windowLightNavigationBar`) and `values-v27/styles.xml` (with it). The v27 qualifier ensures lint sees the attribute is guarded by API level.
- Also in this commit: added `BASE_URL` package-private field to `TelnyxClient` for testability; added `mockwebserver:4.12.0` test dependency; added `TelnyxClientTest.java` (12 HTTP mock tests).

### Fix 2: `org.json.JSONObject` stubbed in JVM unit tests (commit db2d108)
- **Root cause:** Android unit tests (`testDebugUnitTest`) run on JVM with `android.jar` stubs that throw `RuntimeException("Stub!")` for all Android API calls, including `org.json.JSONObject`. `TelnyxClient` uses `JSONObject` to build POST body and parse responses. 9 of 12 `TelnyxClientTest` tests failed; only the 3 that expected `Exception` to be thrown passed (because `RuntimeException` extends `Exception`).
- **Fix:** Added `testImplementation 'org.json:json:20231013'` to `app/build.gradle`. This puts the real Maven Central `org.json` implementation before the Android stub on the test classpath.

## CI evidence — run 44817889364 (SUCCESS)

**Branch:** `feature/android-sms-foundation` (HEAD db2d108)  
**Workflow run:** https://github.com/nexvary/Nexvary-SMS/actions/runs/44817889364  
**Result:** BUILD SUCCESSFUL in 1m 4s  
**Tests:** 27 unit tests (15 SmsPolicyTest + 12 TelnyxClientTest) — 0 failures  
**Lint:** 0 errors, 13 warnings (warnings do not abort build)  
**APK signature:** Verified using v2 scheme (APK Signature Scheme v2): **true**

## Installable APK artifact

**Artifact name:** Nexvary-SMS-1.0.0-APK  
**Download:** https://github.com/nexvary/Nexvary-SMS/actions/runs/44817889364 → Artifacts → Nexvary-SMS-1.0.0-APK  
**Expires:** 2026-12-20  
**APK SHA256:** `e7dff95ec25b1e81bf7cc3fb15389f6d322abafbaf5f5ad87088474c3840563c`  
**Signing:** Android debug key (temporary, CI-generated). Not a production update key.

## Tests added (TelnyxClientTest.java — 12 tests)

All tests use OkHttp MockWebServer. No real Telnyx key used. No paid SMS sent.

| Test | Covers |
|------|--------|
| `sendBodyIsUtf8` | Arabic text in POST body, Content-Type charset=utf-8 |
| `http422ReturnsErrorResult` | HTTP 422 → error Result with status code, no throw |
| `http500ReturnsErrorResult` | HTTP 500 → error Result |
| `invalidJsonThrows` | 200 + malformed JSON → Exception thrown |
| `missingDataKeyThrows` | 200 + `{}` (no data key) → Exception thrown |
| `noAutoRetryOnHttpError` | 503 → exactly 1 request (no retry) |
| `connectionResetThrows` | Socket disconnect → Exception (no silent failure) |
| `statusAccepted` | GET status returns "accepted", empty cost |
| `statusSentDistinctFromAcceptedAndDelivered` | "sent" ≠ "accepted" ≠ "delivered" |
| `statusDeliveredIncludesCost` | "delivered" + cost object → cost string populated |
| `statusUsesGetNotPost` | Status lookup uses GET, no body |
| `sendUsesPost` | Send uses POST |

## Test policy boundaries (SmsPolicyTest.java — 15 tests)
Normalization, prefix validation, GSM/Unicode part counting, extension chars, emoji UTF-16 surrogates, UUID validation, injection rejection. All 15 pass unchanged.

## Verification limits

- Unit tests only: JVM, no Android emulator, no physical device.
- No delivery test, no Keystore test (requires Android device/emulator), no UI rotation/process-death test (requires instrumented run).
- APK is debug-signed by CI. Valid for personal testing; not for production distribution or long-term update delivery.
- Sender, profile ID, and all security constraints from AGENTS.md preserved unchanged.
- No AWS, FusionPBX, or other repository changes made.

## Branch status

- Working branch: `feature/android-sms-foundation`
- Open PR: https://github.com/nexvary/Nexvary-SMS/pull/1
- No merge to main. No release published.
- No concurrent agent work detected on this branch at time of commit.
