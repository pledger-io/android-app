# ADR-009: Dark-First Design Language

**Date:** 2026-05-13
**Status:** Accepted

## Context

A personal finance app needs to feel trustworthy, premium, and easy on the eyes during extended use (reviewing transactions, analyzing reports). The visual identity must differentiate from the Pledger.io web interface while maintaining brand coherence.

Options considered:
- **Light-first with dark mode** — Traditional approach, wider familiarity
- **Dark-first with light mode** — Premium feel, better for OLED screens, reduces eye strain
- **System-adaptive only** — Follows user preference but no opinionated identity

## Decision

Adopt a **dark-first design** with a secondary light theme. The dark theme is the primary visual identity:

| Token | Dark | Light |
|-------|------|-------|
| Background | `#0D1B2A` (deep navy) | `#F8FAFC` |
| Surface | `#152032` | `#FFFFFF` |
| Card | `#1C2D42` | `#F1F5F9` |
| Primary | `#00C896` (emerald) | `#00C896` |
| Income | `#4ADE80` | `#4ADE80` |
| Expense | `#F87171` | `#F87171` |
| Warning | `#FBBF24` | `#FBBF24` |

Typography:
- **Sora** for display/headlines — geometric, modern
- **DM Sans** for body/labels — friendly, highly legible

Theme selection follows system preference by default, with a manual override in Settings.

## Consequences

### Positive
- Dark navy + emerald green creates a distinctive, premium financial app identity
- OLED screens benefit from darker colors (battery savings, true blacks)
- Semantic colors (green = income, red = expense, amber = warning) are universal and colorblind-accessible
- Both themes share the same accent colors, maintaining brand consistency

### Negative
- Dark-first design may feel unfamiliar to users who prefer light themes
- Some content (charts, images) requires careful contrast management in dark mode
- Google Fonts provider requires network for first font load (falls back to system font)

### Typography Decision
Sora and DM Sans were chosen over alternatives:
- **Sora vs Inter**: Sora's geometric forms feel more distinctive for a financial app
- **DM Sans vs Roboto**: DM Sans is friendlier and more readable at small sizes while being less "stock Android"
- Both fonts are loaded via Google Fonts provider, avoiding bundled font file size impact
