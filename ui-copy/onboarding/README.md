# Onboarding copy (pilot)

Edit **`de.json`** / **`en.json`** here to iterate onboarding text **without touching UI code**.

After changes:

- **Android:** rebuilt automatically (`copyOnboardingCopy` Gradle task copies into `assets/onboarding/`).
- **Desktop:** Vite reads these files directly via `@onboarding-copy` alias.

## Structure

| Section | Screen |
|---------|--------|
| `positioning` | 1 — one-sentence hook; `variants` for A/B tests |
| `why` | 2 — up to 3 bullets, plain language |
| `faq` | 3 — fear questions + short answers |
| `completion` | 4 — display name + start |
| `actions` | Next / back labels |

## A/B test (screen 1)

- Add keys under `positioning.variants` (e.g. `"a"`, `"b"`, `"c"`).
- Set `positioning.default_variant` to the control arm.
- On first launch the app picks a variant at random (or default if only one key exists) and stores it in preferences for consistent analytics.

Do **not** hardcode variant text in Kotlin/Svelte — only reference keys from this file.

## Locales

Add `fr.json`, etc. when needed. Missing locales fall back to `en.json`.
