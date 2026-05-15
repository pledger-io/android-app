# ADR-012: Google Fonts Provider for Typography

**Date:** 2026-05-13
**Status:** Accepted

## Context

The design calls for Sora (headlines) and DM Sans (body text) — two Google Fonts not included in the Android system font set. These fonts need to be available at runtime.

Options considered:
- **Bundled font files** — Include `.ttf` files in `res/font/`. Reliable, no network needed, but adds 200-400KB to APK per font family
- **Google Fonts Provider** — Downloads fonts on demand via Google Play Services. Zero APK impact, automatic caching
- **Downloadable Fonts API (XML)** — Similar to provider but XML-declared, less Compose-friendly

## Decision

Use the **Google Fonts Provider** via `androidx.compose.ui:ui-text-google-fonts`.

Fonts are declared programmatically using `GoogleFont` + `FontFamily`:
```kotlin
val SoraFont = FontFamily(
    Font(googleFont = GoogleFont("Sora"), fontProvider = provider, weight = FontWeight.Normal),
    // ... additional weights
)
```

The font provider is authenticated using Google's GMS font certificates stored in `res/values/font_certs.xml`.

## Consequences

### Positive
- Zero APK size impact — fonts are downloaded and cached by Google Play Services
- Automatic font caching — subsequent launches use cached fonts instantly
- Wide font availability — any Google Font can be used without bundling
- Multiple weights (Light through Bold) are available without separate font files

### Negative
- First-launch latency — fonts may not be available immediately (falls back to system default)
- Requires Google Play Services — not available on devices without GMS (e.g., Huawei)
- Network dependency for first font load
- Certificate verification adds a resource file (`font_certs.xml`)

### Fallback Strategy
When fonts are unavailable (no GMS, first launch, airplane mode), Compose falls back to the platform's default sans-serif font. The UI remains functional; only the typeface differs.

### Alternative for GMS-free Devices
If GMS-free device support becomes important, bundle font files as a fallback:
1. Add `.ttf` files to `res/font/`
2. Create a `FontFamily` that tries Google Fonts first, falls back to bundled
