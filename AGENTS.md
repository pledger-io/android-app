# AGENTS.md

## Cursor Cloud specific instructions

This is a single native **Android** app (Kotlin, Jetpack Compose, Gradle). See `README.md` for the full stack, project structure, and standard commands (`./gradlew assembleDebug`, `scripts/build-release.sh`, etc.). Notes below cover only non-obvious, durable caveats for this environment.

### Toolchain (already provisioned by the startup update script + VM snapshot)
- **JDK 21** is the system default (`java -version`). AGP does not support JDK 25; do not switch.
- The **Android SDK** lives at `~/android-sdk` (`platform-tools`, `platforms;android-36`, `build-tools;36.0.0`) and is exported via `~/.bashrc` (`ANDROID_HOME`/`ANDROID_SDK_ROOT`).
- `local.properties` (git-ignored) points Gradle at the SDK. The startup update script regenerates it, so you normally don't need to touch it. Gradle also honors `ANDROID_HOME` if `local.properties` is missing.

### Build / test / lint (all run headless, no emulator needed)
- Unit tests: `./gradlew testDebugUnitTest --no-daemon` (153 tests, JVM/MockK/MockWebServer — this is the primary way to exercise app logic here).
- Lint: `./gradlew lintDebug --no-daemon` (a `app/lint-baseline.xml` baseline suppresses known issues; the build passes as long as no *new* errors appear).
- Debug APK: `./gradlew assembleDebug --no-daemon` → `app/build/outputs/apk/debug/app-debug.apk`.
- First Gradle invocation downloads all dependencies and can take a few minutes.

### Emulator / running the GUI app — NOT possible in this VM
- There is **no `/dev/kvm`** (no nested virtualization). x86/x86_64 system images refuse to start ("x86_64 emulation currently requires hardware acceleration"), and the modern emulator (v36+) rejects arm64 images on an x86_64 host ("Avd's CPU Architecture 'arm64' is not supported ... on x86_64 host").
- Therefore `connectedDebugAndroidTest` (instrumented tests) and interactive app launch cannot run here. Validate changes with unit tests, lint, and `assembleDebug` instead. Instrumented/GUI testing must be done on a machine with KVM (e.g. GitHub Actions `reactivecircus/android-emulator-runner`, as in `.github/workflows/ci.yml`).

### Runtime dependency
- The app is a client for a self-hosted **Pledger.io REST backend** (`/v2/api/…`); onboarding asks for a server URL and JWT login. No backend runs in this VM, so end-to-end flows that hit the network can only be validated via the mocked unit tests.
