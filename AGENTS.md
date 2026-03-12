# AGENTS.md

Operational guidance for agents working in this repository.

## Scope
This file applies to the full repository rooted at `/workspace/AndroidAppScaffold1`.

## Repository intent
This repo is an Android scaffold for **highly reproducible iteration** by humans and agents.

Core idea:
- Avoid overfitting to one domain's app knowledge.
- Prefer repeatable structures and automation.
- Keep app scaffolding simple so domain work can move faster.

## Default product shape
Maintain this baseline unless a task explicitly requests changes:

- `activity_main.xml` is the launcher/menu page.
- `activity_screen1.xml` ... `activity_screen4.xml` are page-oriented activity shells.
- `view_top_navigation.xml` provides cross-page navigation.
- Activity classes should remain lightweight and easy to scan.
- Pages may be duplicated for parallel contexts while preserving structure.

## Working conventions
- Prefer small, incremental commits with clear messages.
- Keep UI strings in `app/src/main/res/values/strings.xml` (avoid hardcoded text when practical).
- Add brief XML/Kotlin comments for placeholders and TODO sections.
- If introducing new activities, declare them in `AndroidManifest.xml`.
- Preserve package namespace: `com.example.templei`.


## Screen 3 soundboard scaffold contract
- Screen 3 now represents the **Action Pad / Soundboard** boolean axis.
- Sound files are discovered from device shared Music storage (including subfolders where other apps drop clips).
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
- [[app/src/main/java/com/example/templei/feature/camera/CameraFeature.kt]]
- [[app/src/main/java/com/example/templei/feature/export/ExportFeature.kt]]
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
