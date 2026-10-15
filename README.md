# Form fill test (proof of concept)

A tiny Android app that opens official portals (PM-KISAN, AgriStack, MahaDBT, PMFBY, Aaple Sarkar) inside a WebView and fills the visible form fields with test values, to prove a native app can autofill these portals without backend access.

- Captcha, OTP and password fields are skipped on purpose.
- Nothing is ever submitted.
- Built by GitHub Actions; the APK is attached to each "Form fill test" release.
