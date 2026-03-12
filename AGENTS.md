# AGENTS.md

Operational guidance for agents working in this repository.

## Scope
This file applies to the full repository rooted at `/workspace/PaulsWackyRecorder`.

## Repository intent
This repo is an Android scaffold for **highly reproducible iteration** by humans and agents.

Core idea:
- Avoid overfitting to one domain's app knowledge.
- Prefer repeatable structures and automation.
- Keep app scaffolding simple so domain work can move faster.
- Keep capability/permission visibility explicit so feature gating is obvious early.

## Current scaffolding baseline (codebase-aligned)
Maintain this baseline unless a task explicitly requests changes:

- `MainActivity` is the launcher/menu and currently acts as a **device capability + permission status console**.
- `activity_main.xml` remains the primary routing surface to Screens 1-4.
- `view_top_navigation.xml` provides cross-page navigation and should stay shared.
- `Screen1Activity`, `Screen2Activity`, and `Screen4Activity` are lightweight shell hosts by design.
- `Screen3Activity` is the most feature-forward page and represents the **Action Pad / Soundboard** axis.
- Keep activity classes lightweight, easy to scan, and XML-first for this scaffold layer.
- Compose modules under `ui/*` are present for incremental adoption; do not force a full migration unless requested.

## Near-term product direction
- Continue using Main screen as the host-readiness dashboard (hardware + permission + gate status).
- Keep screen-level feature work modular (e.g., `feature/soundboard`, `device/*`) and avoid coupling unrelated page logic.
- Preserve reproducible page shells so domain-specific variants can be duplicated quickly.
- Keep docs (`README.md`, map notes, and this file) synchronized whenever scaffold behavior changes.

## Working conventions
- Prefer small, incremental commits with clear messages.
- Keep UI strings in `app/src/main/res/values/strings.xml` (avoid hardcoded text when practical).
- Add brief XML/Kotlin comments for placeholders and TODO sections.
- If introducing new activities, declare them in `AndroidManifest.xml`.
- Preserve package namespace: `com.example.templei`.


## Screen 3 soundboard scaffold contract
- Screen 3 now represents the **Action Pad / Soundboard** boolean axis.
- Sound files are discovered from a user-selected folder via system file picker; browsing is lateral across sibling folders at that selected level.
- Supported formats: `.wav` and `.mp3`.
- Soundboard clips must be `<= 6 seconds`; longer clips are treated as non-playable.
- Playback is button-press driven only (no autoplay state transitions).
- Screen 3 includes folder back/forward navigation; the centered label must show the active sample folder name.
- Keep Screen 3 state vocabulary explicit (`Loading`, `Ready`, `Playing`, `Error`) when extending behavior.

## Validation guidance
- Run Gradle checks where environment permits.
- If Android SDK is unavailable, still perform static validation and report the limitation.

## Human + machine documentation contract
- `README.md` is human-facing onboarding.
- `AGENTS.md` is machine-facing operating guidance.
- Keep both aligned whenever scaffold conventions change.

## Obsidian file index (wikilinks)
Use this index for Obsidian graph/mind-map navigation.

## Obsidian map notes
Use these markdown notes to create richer graph intersections across pages and source files:

- [[MINDMAP.md]]
- [[MAP_MainMenu.md]]
- [[MAP_TopNavigation.md]]
- [[MAP_Screen1.md]]
- [[MAP_Screen2.md]]
- [[MAP_Screen3.md]]
- [[MAP_Screen4.md]]

- [[AGENTS.md]]
- [[README.md]]
- [[MINDMAP.md]]
- [[MAP_MainMenu.md]]
- [[MAP_TopNavigation.md]]
- [[MAP_Screen1.md]]
- [[MAP_Screen2.md]]
- [[MAP_Screen3.md]]
- [[MAP_Screen4.md]]
- [[build.gradle.kts]]
- [[settings.gradle.kts]]
- [[gradle.properties]]
- [[gradlew]]
- [[gradlew.bat]]
- [[gradle/libs.versions.toml]]
- [[gradle/wrapper/gradle-wrapper.properties]]
- [[gradle/wrapper/gradle-wrapper.jar]]
- [[app/build.gradle.kts]]
- [[app/proguard-rules.pro]]
- [[app/src/main/AndroidManifest.xml]]
- [[app/src/main/java/com/example/templei/MainActivity.kt]]
- [[app/src/main/java/com/example/templei/Screen1Activity.kt]]
- [[app/src/main/java/com/example/templei/Screen2Activity.kt]]
- [[app/src/main/java/com/example/templei/Screen3Activity.kt]]
- [[app/src/main/java/com/example/templei/Screen4Activity.kt]]
- [[app/src/main/java/com/example/templei/device/DeviceCapabilityProbe.kt]]
- [[app/src/main/java/com/example/templei/feature/camera/CameraFeature.kt]]
- [[app/src/main/java/com/example/templei/feature/export/ExportFeature.kt]]
- [[app/src/main/java/com/example/templei/feature/soundboard/SoundboardStateMachine.kt]]
- [[app/src/main/java/com/example/templei/ui/components/PulseButton.kt]]
- [[app/src/main/java/com/example/templei/ui/components/UiPaletteBar.kt]]
- [[app/src/main/java/com/example/templei/ui/navigation/NavGraph.kt]]
- [[app/src/main/java/com/example/templei/ui/navigation/Routes.kt]]
- [[app/src/main/java/com/example/templei/ui/navigation/TopNavigation.kt]]
- [[app/src/main/java/com/example/templei/ui/state/HomeEvent.kt]]
- [[app/src/main/java/com/example/templei/ui/state/HomeUiState.kt]]
- [[app/src/main/java/com/example/templei/ui/theme/Color.kt]]
- [[app/src/main/java/com/example/templei/ui/theme/Theme.kt]]
- [[app/src/main/java/com/example/templei/ui/theme/Type.kt]]
- [[app/src/main/res/layout/activity_main.xml]]
- [[app/src/main/res/layout/activity_screen1.xml]]
- [[app/src/main/res/layout/activity_screen2.xml]]
- [[app/src/main/res/layout/activity_screen3.xml]]
- [[app/src/main/res/layout/activity_screen4.xml]]
- [[app/src/main/res/layout/view_top_navigation.xml]]
- [[app/src/main/res/values/strings.xml]]
- [[app/src/main/res/values/colors.xml]]
- [[app/src/main/res/values/themes.xml]]
- [[app/src/main/res/xml/backup_rules.xml]]
- [[app/src/main/res/xml/data_extraction_rules.xml]]
- [[app/src/main/res/drawable/ic_launcher_background.xml]]
- [[app/src/main/res/drawable/ic_launcher_foreground.xml]]
- [[app/src/main/res/mipmap-anydpi/ic_launcher.xml]]
- [[app/src/main/res/mipmap-anydpi/ic_launcher_round.xml]]
- [[app/src/main/res/mipmap-mdpi/ic_launcher.webp]]
- [[app/src/main/res/mipmap-mdpi/ic_launcher_round.webp]]
- [[app/src/main/res/mipmap-hdpi/ic_launcher.webp]]
- [[app/src/main/res/mipmap-hdpi/ic_launcher_round.webp]]
- [[app/src/main/res/mipmap-xhdpi/ic_launcher.webp]]
- [[app/src/main/res/mipmap-xhdpi/ic_launcher_round.webp]]
- [[app/src/main/res/mipmap-xxhdpi/ic_launcher.webp]]
- [[app/src/main/res/mipmap-xxhdpi/ic_launcher_round.webp]]
- [[app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp]]
- [[app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.webp]]
- [[app/src/test/java/com/example/templei/ExampleUnitTest.kt]]
- [[app/src/androidTest/java/com/example/templei/ExampleInstrumentedTest.kt]]

## Screen 3 phased implementation log
Use this section to keep incremental delivery transparent and reproducible.

### Phase 1 (in progress): Load diagnostics + progress visibility
- Add explicit loading progress reporting while scanning candidate files from the selected root folder.
- Report these counters during loading: processed files, total discovered files, playable files accepted.
- Keep state vocabulary explicit and unchanged at the top level (`Loading`, `Ready`, `Playing`, `Error`) while allowing richer `Loading` details.
- Surface load progress in `activity_screen3.xml` using a dedicated progress row (`TextView` + horizontal `ProgressBar`).
- Keep this phase focused on observability only; favorites management and layout collapse/scroll refactors belong to later phases.


### Phase 2 (in progress): Favorites decoupling + explicit assignment flow
- Favorites are no longer auto-filled from folder scans; slots are explicit user assignments.
- Clip browser selection assigns to the active favorite slot, and clear-slot removes the assignment.
- Favorite slot mappings persist in `SharedPreferences` so assignments survive activity recreation.
- Folder browser and favorite pad responsibilities are now separated: folder clips are browsable items; favorite slots are play targets.


### Phase 3 (in progress): Frame grouping + vertical scroll + collapse controls
- Screen 3 layout is grouped into framed sections for Favorites and Clip Browser to make movement/reformatting easier.
- Main content is vertically scrollable for long clip/favorite workflows in constrained device heights.
- Favorites section and Clip Browser section each expose explicit collapse/expand controls.
- Clip browser content is displayed in a vertically scrollable button stack to support many clips.


### Phase 4 (in progress): Folder clip action list (play + assign)
- Folder clip browser now exposes dynamic per-file buttons for all playable files in the active folder.
- Single-tap on a folder clip plays it immediately (including `.wav` clips) without requiring favorite assignment first.
- Long-press on a folder clip assigns it to the active favorite slot.
- Browser header now shows folder clip counts (total / wav / mp3) for explicit load visibility after folder binding.


### Phase 5 (in progress): Playback hardening + quick favorite remove UX
- Sound playback now uses MediaPlayer async prepare from SAF URIs for more reliable button-driven playback.
- Clip play requests start through a single reliable async path that avoids prior URI load/play race failures.
- Favorite slots support direct long-press clear for faster remove workflows.
- Favorites and browser hints are explicit in UI copy to clarify tap vs long-press actions.
- Loading now surfaces two explicit sub-stages in UI: discovery (folders/files found) and validation (processed/playable progress).
- Clip browser renders one dynamic button per discovered playable `.wav` in the active folder.
