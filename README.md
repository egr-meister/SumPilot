# SumPilot

Offline calculator and calm mental-arithmetic trainer for children. Native Android, Kotlin + Jetpack Compose.
No accounts, network, ads, analytics, payments or permissions. Everything works in airplane mode from first launch.

## Features

- **Pilot Panel** (main screen): Calculator Screen (latest calculation or “Ready to calculate.”), Practice Mission
  (type, difficulty, operations, Start/Resume), Accuracy Meter (“Last session accuracy”, “No session yet” when
  empty), Session Result flight-log strip, and controls for Mistake Practice, Session History, Badges, Settings.
  Phones stack panels vertically; windows ≥ 720 dp wide put the mission panel beside calculator + meter.
- **Calculator** with history (latest 50), “Use result”, BigDecimal arithmetic.
- **Missions**: default untimed 10-question mission; optional “5 minutes of answering time” mission.
- Four answer choices, optional Hint (recorded, never penalised), feedback + explanation, no auto-advance.
- **Results & review**, **Mistake Practice** (untimed retries), **Session History** (latest 100), six
  participation **badges**, **Settings** with grown-up-checked data reset, bundled **privacy** screen.

## Architecture

Single `:app` module, manual DI (`AppContainer`), MVVM with `StateFlow` + `collectAsStateWithLifecycle`.

```
domain/model        Operation, Difficulty, SessionType, SessionStatus, MissionConfig, Clock
domain/calculator   CalcArithmetic (BigDecimal rules) + CalculatorEngine (state machine)
domain/generation   QuestionPools, QuestionGenerator, DistractorGenerator, Explanations (hints/explanations)
domain/timer        ActiveTimer (checkpoint-based, monotonic clock)
domain/sessions     PracticeService (session lifecycle, scoring, mistakes, retention), PracticeStore interface
domain/progress     Badge, BadgeRules
data/local          Room entities, DAOs, AppDatabase, Migrations
data/repository     RoomPracticeStore, PracticeRepository, CalculatorRepository, SettingsRepository (DataStore)
ui/*                panel, calculator, mission, results, history (+badges), mistakes, settings (+privacy), theme
```

All rules (arithmetic, generation, timer, scoring, mistakes, badges) live in `domain/` with no Android
dependencies. Clock and `Random` are injected, so tests are deterministic. Room DAOs are suspend/Flow, so
database work runs off the main thread. Writes that must be atomic (answers, completion, mistakes, badges)
run in `PracticeStore.transaction` (`Room.withTransaction`).

## Toolchain (pinned)

| Tool | Version |
|---|---|
| JDK | 17 (Temurin in CI) |
| Gradle (wrapper) | 8.14.3 |
| Android Gradle Plugin | 8.13.0 |
| Kotlin + Compose compiler plugin | 2.2.21 |
| KSP | 2.2.21-2.0.4 |
| Compose BOM | 2025.09.00 (+ material-icons-core 1.7.8) |
| Room | 2.7.2 · DataStore 1.1.7 · Navigation 2.9.3 · Lifecycle 2.9.2 · Activity 1.10.1 · Core 1.16.0 · SplashScreen 1.0.1 · Coroutines 1.10.2 |
| SDK | **compileSdk 36, targetSdk 36, minSdk 26**, build-tools 36.0.0 |

All versions live in `gradle/libs.versions.toml`. Every directly used library is a direct dependency.

## Build

```bash
./gradlew testDebugUnitTest     # unit tests
./gradlew lintRelease           # release lint
./gradlew assembleDebug         # debug APK, no credentials needed
./gradlew assembleRelease bundleRelease   # needs release credentials (below)
```

Outputs: `app/build/outputs/apk/release/app-release.apk`, `app/build/outputs/bundle/release/app-release.aab`.

## Calculator rules

- Digits, decimal point, + − × ÷, =, C (clear), ⌫ (backspace, edits the active operand), ± (sign), History.
- One binary operation at a time. No %, parentheses, powers, roots or scientific functions.
- Operands and results limited to |x| ≤ 1,000,000; up to 6 fractional digits per operand (extra digits are ignored while typing).
- Division and multiplication results are rounded to 6 fractional digits with `HALF_UP`; trailing zeros removed;
  rounded results are shown with “≈” and “Rounded to 6 decimal places”.
- Leading zeros are normalised, a second decimal point is ignored, an operator pressed before the second operand
  replaces the previous one, repeated = does nothing, a digit after a result starts a new calculation, an operator continues from it.
- Division by zero and out-of-range results show a friendly message and are not saved to history.
- The latest 50 successful calculations are kept. Calculator use never affects practice statistics. The current entry is kept as a DataStore draft.

## Question generation and difficulty

| Difficulty | + / − operands | × factors | ÷ dividend | ÷ divisor |
|---|---|---|---|---|
| Easy (default) | 1–10 | 1–5 | 1–20 | ≤ 10 |
| Medium | 1–50 | 1–10 | 1–100 | ≤ 10 |
| Hard | 1–100 | 1–12 | 1–144 | ≤ 12 |

Questions are drawn from **enumerated valid pools** (so generation always succeeds): subtraction is never negative,
division always has a positive divisor and whole-number answer (no zero divisor exists in the pool).
Mixed (all four) is the default; any non-empty custom selection works.

- 10-question missions: all 10 generated and persisted at start; operations distributed as evenly as possible
  (remainder to random distinct operations), shuffled, no duplicates (reversed + / × operands count as duplicates).
- 5-minute missions: the next question is generated and persisted before it is shown; operations come in shuffled
  cycles (each block contains every enabled operation once); no repeat within the last 20 questions, falling back to
  “not the immediately previous one” when the pool is smaller than that window.
- Four distinct integer options, exactly one correct, shuffled; distractors are operation-specific (off-by-one/ten,
  wrong operation, neighbouring multiple…) with a bounded nearby-value fallback; non-negative for + / −, positive for × / ÷.
- Hints never reveal the answer; explanations adapt to the operands (make-ten, tens-then-ones, count on/back,
  equal groups, split a factor above 10, related multiplication fact). Unit tests check every step of every pool question.

## Sessions, timer and accuracy

- **10-question mission (default)**: no timer, one scored answer per question, feedback + explanation, “Next question”.
- **5-minute mission**: 5 minutes of *active answering time*, no fixed count. Time runs only while a question is
  ready for interaction; it pauses during feedback, while a hint is open, while a confirmation dialog is shown, when
  the question screen is left (Back, navigation) and when the app is backgrounded.
- Timer = `ActiveTimer`: accumulated active time + last resume checkpoint on `SystemClock.elapsedRealtime()`; remaining
  time is derived, never a decrementing counter. Wall-clock changes have no effect. Remaining/active time is persisted at every pause.
- After process death or reboot the session is restored **paused** and requires “Resume mission” (tracked via `SavedStateHandle`).
- At expiry the current question stays available; the child may answer or skip it, then sees results. No new
  question is generated, unanswered questions are dropped and never count as errors.
- Answers are recorded once: buttons disable on first tap, and storage uses `UPDATE … WHERE selectedAnswer IS NULL`.
  Expiry and submission both run on the main thread, so whichever happens first wins and the answer is never counted twice.
- “End mission” (with confirmation) keeps answered questions, marks the session “Ended early” and excludes unanswered ones.
  A session with no answers is discarded without history. Only one unfinished session can exist.
- **Accuracy** = correct ÷ answered × 100 (rounded). Zero answers → “No answers recorded”. No speed scores or comparisons.

## Mistake Practice and history

- Wrong original answers create unresolved mistakes, de-duplicated by normalised expression; a resolved mistake is
  reopened if answered wrongly again. The list shows expression + operation, never the answer.
- Retries are untimed. Correct → explanation, mistake resolved, leaves the list once the child continues.
  Incorrect → stays unresolved; “Try again” (re-shuffled options) or back to the list.
- Retries never change session results, lifetime answer totals or the Accuracy Meter.
- Practise all unresolved mistakes or only those from one session (`mistake_sources` links).
- Session history keeps the latest **100** answered sessions. Mistakes and lifetime totals are independent of retention.
  “Clear session history” keeps mistakes (the dialog says so); “Clear unresolved mistakes” is separate.

## Badges

First Flight (1 answer) · Mission Complete (one finished 10-question mission) · Four Directions (an answer in each
operation) · Fifty Practiced (50 original answers) · Careful Return (one mistake resolved) · Five Missions (five
finished missions). Participation only — no accuracy, speed, streaks, coins or rewards. Each unlocks once with a date.

## Offline, privacy, storage and backup

- No `INTERNET` / `ACCESS_NETWORK_STATE`, no runtime permissions, no networking libraries, remote services, ads,
  analytics, Firebase, payments, accounts, cloud sync or external links.
- Data in app-private storage: Room database `sumpilot.db`, DataStore `sumpilot_settings`.
- `android:allowBackup="false"`, `fullBackupContent` excludes everything (≤ Android 11) and `dataExtractionRules`
  excludes all domains from cloud backup **and** device transfer (Android 12+).
- Destructive actions need a grown-up check (type a two-digit sum, or press-and-hold 3 s; screen readers get a
  long-press action). This prevents accidental taps; it is not authentication.

## Android 16 / API 36 notes

Edge-to-edge is enforced: the app draws edge-to-edge with `safeDrawing` insets (bars and cutouts), keeps system bars visible
(no immersive mode) and never keeps the screen on. Predictive Back: `enableOnBackInvokedCallback="true"`; Navigation Compose and
`BackHandler` handle Back (mission Back preserves progress and pauses the timer; ending is a separate action). No orientation or
resizability restrictions are declared, so large-screen behaviour changes don't affect the app; landscape, tablets and resizable windows are supported.

## Permission verification

CI runs `scripts/check_permissions.sh` (`aapt2 dump permissions`) on the release APK and greps the merged release
manifest. The only allowed entry is androidx.core's signature-level `com.sumpilot.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`.

## Release signing (PKCS12)

`app/build.gradle.kts` defines `signingConfigs.release` with `storeType = "PKCS12"`, reads credentials from environment
variables (`ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`) or an
uncommitted `keystore.properties`, and assigns it to the release build type. Release packaging tasks fail when
credentials are missing; there is **no** debug-signing fallback. Debug builds need nothing.

GitHub Secrets: `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`.
Never commit the keystore or print passwords (`*.p12`, `*.jks`, `keystore.properties` are gitignored).

**Keys:** this keystore is your **upload key**. Enrol in Play App Signing: Google holds the app-signing key that signs what users
install; you sign uploads with the upload key. If the upload key is lost or leaked, it can be reset through Play Console support;
the app-signing key is unaffected. Keep an offline backup of the `.p12` and its password.

## CI

`.github/workflows/android.yml`: JDK 17 + SDK Platform 36 → committed wrapper → unit tests → `lintRelease` → debug build →
decode PKCS12 to `$RUNNER_TEMP` → signed `assembleRelease bundleRelease` → `apksigner verify --print-certs` (fails on
`CN=Android Debug`) → `jarsigner` AAB integrity + signer SHA-256 must equal the keystore certificate (a self-signed upload
certificate is accepted) → permission check → 16 KB native-library inspection + `zipalign -c -P 16` → upload verified artifacts →
delete signing material. Without secrets, tests/lint/debug still run and release steps are skipped with a warning. No emulator test in CI.

**Google Play:** upload only `app-release.aab`. Use the APK for local install and verification.

## R8 / resource shrinking

Disabled by default (`sumpilot.minify=false` in `gradle.properties`) until a signed non-minified release has been verified on a
device. To enable afterwards: set `sumpilot.minify=true`, rebuild, repeat the calculator, generation, timer, persistence, sound and
mistake-practice checks, and keep `app/build/outputs/mapping/release/mapping.txt` (CI uploads it).

## 16 KB page-size compatibility

`scripts/check_native_libs.py` lists every `.so` in the APK and AAB (including transitive dependencies), checks that each
`PT_LOAD` segment has `p_align ≥ 16 KB` and that stored libraries in the APK sit at 16 KB-aligned offsets; CI also runs
`zipalign -c -P 16`. Expected finding: the only native code likely to appear comes from Compose's transitive
`androidx.graphics:graphics-path` (`libandroidx.graphics.path.so`); current releases ship it 16 KB-aligned. The report in the
`sumpilot-release` artifact (`native-libs-report.md`) is the source of truth. Runtime compatibility still needs a test on a 16 KB
emulator/device before it is claimed. Targeting API 36 alone is not proof.

## Room schema and migrations

`exportSchema = true`; the Room Gradle plugin writes `app/schemas/com.sumpilot.data.local.AppDatabase/1.json` on the first build
(CI uploads it in `sumpilot-reports`; commit it). Future schema changes bump the version and add a `Migration` in
`data/local/AppDatabase.kt`. Destructive migration is not used.

## Local verification (adb)

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
adb logcat --pid=$(adb shell pidof -s com.sumpilot)    # or: adb logcat | grep -i sumpilot
adb shell dumpsys package com.sumpilot | grep -i permission
```

Checklist and results are recorded in [VERIFICATION.md](VERIFICATION.md).
