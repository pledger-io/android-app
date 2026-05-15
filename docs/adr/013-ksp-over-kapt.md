# ADR-013: KSP over KAPT for Annotation Processing

**Date:** 2026-05-13
**Status:** Accepted

## Context

The project uses several libraries that require annotation processing:
- **Hilt** — `@HiltViewModel`, `@Module`, `@Inject` → generates Dagger components
- **Room** — `@Entity`, `@Dao`, `@Database` → generates SQL implementations
- **Moshi** — `@JsonClass(generateAdapter = true)` → generates JSON adapters

Android historically uses KAPT (Kotlin Annotation Processing Tool), which generates Java stubs from Kotlin code and feeds them to Java annotation processors. This is slow because it involves an extra compilation step.

KSP (Kotlin Symbol Processing) is a newer alternative that processes Kotlin code directly, avoiding stub generation.

Options considered:
- **KAPT** — Mature, universal support, but slow due to Java stub generation
- **KSP** — 2x faster, Kotlin-native, but not all libraries support it

## Decision

Use **KSP** (`com.google.devtools.ksp`) for all annotation processing:

```kotlin
ksp("com.google.dagger:hilt-android-compiler:2.53.1")
ksp("com.squareup.moshi:moshi-kotlin-codegen:1.15.1")
ksp("androidx.room:room-compiler:2.6.1")
ksp("androidx.hilt:hilt-compiler:1.2.0")
```

## Consequences

### Positive
- **~2x faster builds** compared to KAPT — no Java stub generation step
- All three processor-heavy libraries (Hilt, Room, Moshi) support KSP
- KSP is the officially recommended path forward (KAPT is in maintenance mode)
- Better error messages — operates on Kotlin symbols directly

### Negative
- KSP version must be aligned with the Kotlin version (`2.1.0-1.0.29` for Kotlin `2.1.0`)
- Some older libraries may still require KAPT (none in our stack)
- KSP is still evolving — occasional breaking changes between versions

### Build Time Impact
Measured on typical annotation-heavy Android projects, KSP provides:
- 20-40% reduction in incremental build time
- 40-60% reduction in clean build annotation processing time
- Eliminating KAPT removes the `kaptGenerateStubs*` task entirely
