# Foundation handoff — 2026-11-20

Repository was empty. Main initialized with README only; app foundation is on `feature/android-sms-foundation`.

## Implemented foundation
- Native Android Java, minSdk 26 / compileSdk+targetSdk 35; AGP 8.9.2, Gradle 8.11.1, Java 17.
- Arabic RTL form, exact Nexvary Inc sender/profile, Egyptian mobile normalization, Unicode/GSM multipart estimate, explicit paid confirmation.
- OkHttp 4.12.0 with retries and redirects disabled; one POST per explicit send, GET-only status lookup.
- Persisted send lock before POST, in-process AtomicBoolean and serial executor. Unknown outcomes remain locked; manual portal review required before reset.
- Optional AES-GCM Android Keystore key storage, forget action, screenshots/autofill/backup restricted. No key or SMS body in journal/logs.
- Response ID/state/cost parsing. No SMS/contacts permissions, analytics, backend, AWS changes or paid external requests.
- Fifteen JUnit policy tests and GitHub Actions tests/lint/APK-signature pipeline.

## Verification limits
No Android SDK/Gradle/JDK compiler was available locally. No local APK, emulator, physical phone or delivery test is claimed. CI must compile/test/lint and its actual result must be recorded. Initial source is a foundation, not a verified release.

## Next task for Claude
1. Inspect CI logs and fix actual compiler/lint/test defects.
2. Add deterministic HTTP mocks and tests for malformed JSON, timeout/HTTP errors, no retry, status distinction and UTF-8 body.
3. Add Android lifecycle/UI tests for duplicate taps, rotations, background-return/process death, saved-key failure and lock recovery. Review no key leakage in Android state restoration or logs.
4. Confirm the key vault and form behavior on an emulator without a real API key or paid send.
5. Produce an installable signed test APK and provide its exact GitHub artifact link/SHA256. Debug signing is temporary and not a production update key.
6. Provide concise Arabic usage instructions and clearly distinguish build/unit/emulator/physical-device results. No paid SMS without separate approval.

No permanent assignment of this repository to an AI agent is made. The owner chooses who works on it; avoid concurrent agents editing the same branch.
