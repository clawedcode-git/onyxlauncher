# Onyx Launcher

A lightweight, fast, and highly customizable Android home-screen launcher with icon-pack support and a built-in abstract wallpaper generator.

## Goals
- **Light** — target APK < 6 MB; no analytics, no ads
- **Fast** — 120 Hz-smooth scrolling, < 400 ms cold launch
- **Customizable** — grid, icons, gestures, colors, layout all configurable
- **Private** — all data on-device; no network calls

## Tech Stack
Kotlin · Jetpack Compose · Room · Proto DataStore · Coil · AGSL shaders (API 33+)

## Build
```
./gradlew assembleDebug
```
Requires Android Studio Ladybug (2024.2+) or the Android Gradle Plugin 8.5+.

## Phases
- [x] **Phase 1** — Scaffold: project, theme, launcher plumbing, home + dock + drawer
- [x] **Phase 2** — Home editing: drag-and-drop, pages, folders, long-press context menu + app shortcuts, Room persistence
- [x] **Phase 3** — Widgets (AppWidgetHost, picker, place, resize) + gesture layer (swipe-up/down/double-tap/two-finger, all configurable)
- [x] **Phase 4** — Icon pack support (Nova/ADW appfilter parser, live pack switching, per-icon override, themed-icon LRU cache)
- [x] **Phase 5** — Wallpaper generator (seeded simplex engine, 3 abstract styles, time-of-day palettes, usage-driven parameters, live wallpaper service)
- [x] **Phase 6** — In-app Settings, theming (Light/Dark/System/AMOLED), JSON backup/restore, performance pass (R8 shrink → 1.6 MB, baseline profile, Macrobenchmark module)

## Releases
- [v0.1.0-alpha](https://github.com/clawedcode-git/onyxlauncher/releases/tag/v0.1.0-alpha) — first alpha

## Performance
- **APK size:** ~1.6 MB (R8 + resource shrink; debug is ~18 MB)
- **Baseline profile:** seed profile ships in the APK and is installed at first run by `ProfileInstaller`. Regenerate the measured profile on real hardware:
  ```
  ./gradlew :app:generateBaselineProfile
  ```
- **Macrobenchmark** (`:macrobenchmark` module) — cold start + drawer-scroll frame timing:
  ```
  ./gradlew :macrobenchmark:connectedBenchmarkAndroidTest
  ```

## minSdk
26 (Android 8.0) — required for adaptive icons and shortcuts API.
