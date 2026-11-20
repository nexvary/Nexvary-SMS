# Nexvary SMS — agent instructions

Personal Android utility for authorized Nexvary Inc SMS through Telnyx. Continue the existing Java/native Android implementation; do not rebuild it in another framework.

- Preserve exact sender `Nexvary Inc` and configured messaging profile unless the owner explicitly changes them.
- Never hardcode, request in chat, log, commit or upload an API key. No real key is present in this repo.
- Never send paid SMS during automated tests. Use mocks; real delivery requires per-message owner approval and phone confirmation.
- No bulk messaging, automatic retries, background replay, contact harvesting or unrelated AWS/FusionPBX changes.
- Keep HTTPS validation, redirect/retry blocking, persistent send lock, and Android Keystore protection.
- Accepted/Sent is not Delivered. Do not claim sender presentation or physical-device verification without evidence.
- Work on a feature branch, preserve changes, open a reviewable PR; no auto-merge or release publication.
- Test policy boundaries, networking errors, duplicate taps, process recreation and uncertain-outcome recovery. Do not weaken tests or security to pass CI.
- Review docs/DEVELOPMENT_HANDOFF.md first and update it with actual build evidence.
