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
- [ ] **Phase 2** — Home editing: drag-and-drop, pages, folders, shortcuts, Room persistence
- [ ] **Phase 3** — Widgets + gestures
- [ ] **Phase 4** — Icon pack support
- [ ] **Phase 5** — Wallpaper generator (engine + live wallpaper service)
- [ ] **Phase 6** — Settings, theming, backup/restore, performance pass

## minSdk
26 (Android 8.0) — required for adaptive icons and shortcuts API.
