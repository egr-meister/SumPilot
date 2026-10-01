# Verification notes

Status as of 2026-10-01. Nothing below is marked passed unless it was actually run.

## Completed

| Check | Environment | Result |
|---|---|---|
| Domain unit tests (calculator, generation, explanations, timer, sessions, mistakes, retention, badges): 53 tests | Kotlin 2.2.21 compiler (kotlinc) on JDK 21, Linux, outside Gradle | **Passed** (53/53) |
| Static review of Android/Compose/Room code against pinned versions | Manual review | Done; no known compile issues. Not a substitute for a real build |
| Release keystore generated (PKCS12, RSA 4096, alias `sumpilot-upload`) | keytool | Done; kept outside the repo |

The Gradle build itself could **not** run in the authoring environment (Google Maven / Maven Central were unreachable),
so the following are pending until the first GitHub Actions run.

## Pending — CI (GitHub Actions)

- [ ] `./gradlew testDebugUnitTest` (same 53 tests, via Gradle/JUnit)
- [ ] `./gradlew lintRelease`
- [ ] `assembleDebug`
- [ ] Signed `assembleRelease` + `bundleRelease`
- [ ] `apksigner verify --print-certs`, no `CN=Android Debug`
- [ ] AAB `jarsigner` integrity + signer SHA-256 matches keystore
- [ ] Release permission check (aapt2 + merged manifest)
- [ ] 16 KB native-library report + `zipalign -c -P 16`
- [ ] Commit generated `app/schemas/.../1.json`

## Pending — device / emulator (record device, Android version, artifact)

Device: ______  Android: ______  Artifact: app-release.apk (commit ______)

- [ ] `adb install` of the signed release APK, `adb logcat` clean (no crashes / StrictMode noise from app)
- [ ] First launch in airplane mode
- [ ] Calculator: arithmetic, limits, ≈ rounding, ÷0 message, history + “Use result”, draft survives rotation
- [ ] Default untimed 10-question mission end-to-end
- [ ] Optional 5-minute mission: timer pauses on feedback / hint / dialog / Back / background
- [ ] Mixed operations and each difficulty
- [ ] Backgrounding and process recreation (`adb shell am kill com.sumpilot` while backgrounded) → restored paused, “Resume mission”
- [ ] Timer expiry: current question answerable or skippable, no penalty
- [ ] Session result, review, mistake retry (wrong → stays, right → resolved), original score unchanged
- [ ] Session history and badges persist across restarts
- [ ] Rotation, landscape, tablet / resizable window layouts
- [ ] Large fonts (200 %), TalkBack labels for gauge, expression, operators, answer states
- [ ] Android Back from every screen (predictive Back on Android 14+)
- [ ] Each data-reset action, grown-up check (typed sum and press-and-hold)
- [ ] No permission prompts, no crashes, no network dependency
- [ ] 16 KB page-size emulator run (only if native libraries are present)
- [ ] R8 enabled build re-verification (only after the above pass)
