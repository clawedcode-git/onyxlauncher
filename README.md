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
- [ ] **Phase 6** — Settings, theming, backup/restore, performance pass

## minSdk
26 (Android 8.0) — required for adaptive icons and shortcuts API.
