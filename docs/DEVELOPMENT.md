# Development and delivery

Run commands from the repository root. See [documentation home](README.md) for features and architecture.

## Prerequisites

- **Android Studio** Meerkat (2024.3.1) or newer (Android 16 SDK support)
- **JDK 21** for Gradle builds (JDK 25 is not supported by the Android Gradle Plugin)
- **Android SDK Platform 36** and **Build-Tools 36.0.0** (install via SDK Manager)

## Setup

1. Open the project in Android Studio.
2. Sync Gradle dependencies.
3. Run on an emulator or device (API 26+).
4. On first launch, enter your Pledger.io server base URL (validated via `GET /health`).
5. Sign in with your credentials (`POST /v2/api/security/authenticate`).

HTTP cleartext is allowed in debug via `network_security_config.xml` for local/dev servers; use HTTPS in production.

## Building

### Local scripts

| Script | Output |
|--------|--------|
| `scripts/build-debug.sh` / `build-debug.ps1` | Debug APK |
| `scripts/build-release.sh` / `build-release.ps1` | Release APK (minified) |

Or use Gradle directly:

```bash
./gradlew assembleDebug
```

On Windows (PowerShell):

```powershell
.\gradlew.bat assembleDebug
```

Requires **JDK 21**.

### GitHub Actions

| Workflow | Trigger | Result |
|----------|---------|--------|
| [CI](../.github/workflows/ci.yml) | Push / PR to `main` or `master` | Unit tests + lint + instrumented smoke test + debug APK artifact |
| [Development build](../.github/workflows/development-build.yml) | **Manual** (Actions → Development build → Run workflow) | Sideloadable debug APK from the selected branch (use `main` for tip-of-main). **Expires after 7 days** with a blocking warning. |
| [Release](../.github/workflows/release.yml) | **Publish** a GitHub Release, or manual run | Release APK attached to the release |

**Install a development APK on your phone**

1. Open **Actions → Development build → Run workflow**.
2. Choose the **main** branch (or another branch you want to try) and run.
3. When the job finishes, download the **`pledger-dev-apk`** artifact from the run page.
4. Transfer the `.apk` to your phone and open it (allow install from this source if prompted), or install with USB debugging:
   ```bash
   adb install -r pledger-io-dev-*.apk
   ```
5. The package id is `com.pledgerio.app` (debug-signed). Uninstall a previous debug build first if Android refuses the update.
6. **Expiry:** these APKs stop after **7 days** from build time and show a warning until you install a fresh Development build. Local/`assembleDebug` builds are not time-limited.

**Distributing an APK on GitHub Release**

1. Push a tag (e.g. `v1.0.0`) and [create a release](https://docs.github.com/en/repositories/releasing-projects-on-github/managing-releases-in-your-repository) from that tag, or create a release in the GitHub UI.
2. Click **Publish release** (draft releases do not run the workflow).
3. The **Release** workflow builds `assembleRelease` and uploads `pledger-io-<tag>-<version>.apk` to the release assets.

To test the release build without publishing: **Actions → Release → Run workflow**. The APK is saved as a workflow artifact.

### In-app bug reports

Users can report problems from **Settings → About → Report a problem**. The app collects sanitized recent logs, prefills the org [bug report form](https://github.com/pledger-io/.github/issues/new?template=bug_report.yml) in the browser (summary, description, environment, logs), and the user taps **Submit** on GitHub — no app credentials required. Sign in to GitHub only if the repository requires it to open issues.

**Production signing is required for release builds** — configure these repository secrets:

| Secret | Description |
|--------|-------------|
| `ANDROID_KEYSTORE_BASE64` | Base64-encoded `.jks` / `.keystore` file |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | Key alias |
| `ANDROID_KEY_PASSWORD` | Key password |

Encode a keystore (example):

```bash
base64 -w 0 release.keystore > keystore.b64
```



## Verification

Run unit tests with `./gradlew testDebugUnitTest --no-daemon` and lint with `./gradlew lintDebug --no-daemon`. On Windows, use `.\gradlew.bat` with the same task names. Instrumented smoke tests use `connectedDebugAndroidTest` and require a running emulator/device; CI supplies one. A live self-hosted backend is needed for manual end-to-end testing.

The dependency/version source of truth is [the version catalog](../gradle/libs.versions.toml); Android configuration and signing are in [app/build.gradle.kts](../app/build.gradle.kts). See [Constraints](CONSTRAINTS.md) for architectural requirements.

## Licensing reference

This app is designed to work with Pledger.io. See the backend repository for server licensing.
