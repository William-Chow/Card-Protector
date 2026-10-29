# Card Protector

An Android app that masks card numbers and other sensitive details in text, on
the device, so the text can be shared safely. Published on Google Play as
**Card Number Protector** (`com.kotlin.card`); the launcher name is **Card Pro**.

Nothing the user types, pastes or selects ever leaves the phone. See the
[privacy policy](https://william-chow.github.io/Card-Protector/privacy-policy.html).

## What it does

Three ways in:

| Entry point | Where | What happens |
|---|---|---|
| **Single card** | Main screen | Type or paste one card number; the masked form updates live, with brand and Luhn hints, and can be copied, shared or exported as an image. |
| **Batch text** | Main screen | Paste any text; every sensitive value in it is masked at once. |
| **Redact** | Text-selection toolbar in any app | Select text, tap *Redact*, and replace it in place with the masked version (or copy it). |

What it finds, in the order a conflict is resolved:

| Type | Example | What survives |
|---|---|---|
| Secret | `AKIA…`, `sk-…`, JWTs, `password: …`, PEM keys | the brand prefix only |
| Link | `https://drive.google.com/file/d/1a2B3c/view?usp=sharing` | scheme, host, query keys |
| Email | `siti.aminah@sekolah.edu.my` | first character and the domain |
| IBAN | `GB29 NWBK 6016 1331 9268 19` | country code and last four |
| Malaysian IC | `901231-14-5678` | nothing |
| Phone | `+60 12-345 6789` | `+60` and the last three digits |
| Card expiry | `exp 12/26` | nothing |
| CVV | `CVV: 123` | nothing |
| Card number | `4111 1111 1111 1111` | what the **Reveal** control allows (at most first 6 + last 4) |
| Public IPv4 | `203.0.113.45` | first octet |

The Reveal control only ever governs card numbers; every other type has a
fixed, safe default.

## How the engine works

The redaction engine is plain Kotlin in `app/src/main/java/com/kotlin/card/filter`
with no Android dependencies, so all of it is tested on the JVM.

1. **Fold** (`Normalize.kt`). The text is folded into a scan string that
   detectors can read with ASCII patterns: every Unicode digit becomes `0`-`9`;
   every space, dash and invisible format character becomes `' '` or `'-'`;
   styled and full-width ASCII (`𝐀`, `ｈｔｔｐｓ`, `²`, `④`) becomes plain
   ASCII. `ScanText` maps each scan position back to the original text.
2. **Detect** (`detect/`). Each detector scans the whole string, in priority
   order (`SensitiveType.priority`); the first claim on a span wins, and the
   unclaimed remainder of an overlapping match is offered again.
3. **Sweep**. Any digit still unclaimed inside a card-length run (12+ digits)
   is masked outright.
4. **Replace and cap**. Replacements are spliced into the original text, then
   a final pass guarantees that no card-length run reveals more than 10 of its
   digits, however the detectors split it up.

`maskAllInText` in `Masking.kt` is the original card-only masker, kept as a
regression oracle: `RedactorParityTest` checks the engine against it on a
corpus of ordinary text (`app/src/test/resources/fp_corpus.txt`).

## Project layout

```
app/src/main/java/com/kotlin/card/
  filter/          redaction engine (pure Kotlin)
    detect/        one detector per sensitive type
  ui/              Compose screens, the Redact sheet, ads consent, in-app updates
  data/            the four stored display preferences
app/src/test/      JVM unit tests for the engine and the UI decisions
docs/              the privacy policy, served by GitHub Pages
```

## Building

Requirements: JDK 17 and the Android SDK (compileSdk 37, minSdk 28).

```bash
./gradlew :app:testDebugUnitTest   # run the unit tests (what CI runs)
./gradlew :app:assembleDebug       # debug APK, with AdMob test ads
```

Debug builds use Google's AdMob test unit IDs and do not send crash reports.

### Release

Release builds are minified with R8, use the production AdMob units, and send
crash reports to Firebase Crashlytics.

1. Put the upload keystore at `app/keystore/keystore.jks` and create
   `key.properties` in the project root:

   ```properties
   storeFile=app/keystore/keystore.jks
   storePassword=…
   keyAlias=upload
   keyPassword=…
   ```

   Both are gitignored, and CI fails if a keystore or `key.properties` is ever
   committed.
2. Bump `versionCode` and `versionName` in `app/build.gradle`.
3. Build the bundle for Google Play (this also uploads the R8 mapping file to
   Crashlytics, so it needs network access):

   ```bash
   ./gradlew :app:bundleRelease
   ```

   The bundle is written to `app/build/outputs/bundle/release/app-release.aab`.

The app uses Play App Signing; the key above is only the upload key.

## Continuous integration

`.github/workflows/ci.yml` runs the unit tests and a debug build on every push
and pull request, and checks that no signing material or build output is
tracked.

## Privacy

- Text being masked is processed only on the device. It is never stored,
  backed up, logged or sent anywhere, and never appears in crash reports.
- Only the four display preferences in `RedactPrefs` are included in Android
  backups; the backup rules in `res/xml/` are allowlists.
- Third-party services: Google AdMob (ads, behind the UMP consent form in the
  EEA and UK), Firebase Crashlytics (crash reports) and Google Play In-App
  Updates.

If the data the app collects changes, update `docs/privacy-policy.html` and the
Data safety form in Play Console together.
