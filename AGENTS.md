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
- Keep docs (`docs/README.md`, map notes, and this file) synchronized whenever scaffold behavior changes.

## Working conventions
- Prefer small, incremental commits with clear messages.
- Keep UI strings in `app/src/main/res/values/strings.xml` (avoid hardcoded text when practical).
- Add brief XML/Kotlin comments for placeholders and TODO sections.
- If introducing new activities, declare them in `AndroidManifest.xml`.
- Preserve package namespace: `com.example.templei`.

## Screen 3 soundboard scaffold contract
- Screen 3 represents the **Action Pad / Soundboard** boolean axis.
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
- `docs/README.md` is human-facing onboarding.
- `AGENTS.md` is machine-facing operating guidance.
- Keep both aligned whenever scaffold conventions change.

## Obsidian file index (wikilinks)
Use this index for Obsidian graph/mind-map navigation.

## Obsidian map notes
Use these markdown notes to create richer graph intersections across pages and source files:

- [[docs/MINDMAP.md]]
- [[docs/MAP_MainMenu.md]]
- [[docs/MAP_TopNavigation.md]]
- [[docs/MAP_Screen1.md]]
- [[docs/MAP_Screen2.md]]
- [[docs/MAP_Screen3.md]]
- [[docs/TableManagementScreen.md]]

- [[AGENTS.md]]
- [[docs/README.md]]
- [[docs/MINDMAP.md]]
- [[docs/MAP_MainMenu.md]]
- [[docs/MAP_TopNavigation.md]]
- [[docs/MAP_Screen1.md]]
- [[docs/MAP_Screen2.md]]
- [[docs/MAP_Screen3.md]]
- [[docs/TableManagementScreen.md]]
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
- Folder catalog results are persisted per selected root URI and hydrated on reopen to avoid multi-minute rescans for large libraries.


## Screen 4 build-out summary (current conversation)
Use this summary as the implementation contract for Screen 4's typed table workflow (independent from Screen 3).

### Phase review snapshot (current)
- **Phase 1 completed**: command routing boundary is implemented via `Screen4Coordinator`, and `Screen4Activity` delegates command flows.
- **Phase 2 completed (core)**: semantic registry + validator core is in place and enforced before insert/update paths.
- **Phase 3 completed (core)**: `RapidEntryConfig` persistence (`ActiveColumns` + `AutoColumns`) and base→input→auto row composition are implemented.
- **Phase 4 completed (core)**: XML form widgets map from registry metadata and show field-level validation feedback.
- **Phase 5 completed (core)**: CSV export through SAF create-document flow is available from Screen 4 actions.
- **Phase 6 completed (baseline gate)**: registry declares all 107 semantic type names; unit tests enforce count/uniqueness/alias resolution.

### Current capability (as-built)
- Screen 4 now functions as a typed, Room-backed table editor surface with deterministic validation and rapid-entry behavior.
- Screen 3 persistence remains separate and unchanged (`SharedPreferences` + serialized payloads).

### Post-phase hardening backlog
- Expand validator depth for each semantic type family (beyond baseline mapping).
- Add migration/versioning tests for Room schema evolution.
- Add integration/UI coverage for rapid-entry loop behavior and CSV export edge cases.

### Files currently involved in Screen 4 build-out context
- `app/src/main/java/com/example/templei/Screen4Activity.kt` (Screen 4 shell host + CSV export action wiring).
- `app/src/main/java/com/example/templei/feature/screen4/Screen4Coordinator.kt` (command boundary + field-validation helpers).
- `app/src/main/java/com/example/templei/feature/screen4/Screen4MeasurementEngine.kt` (rapid-entry composition + commit behavior).
- `app/src/main/java/com/example/templei/feature/screen4/Screen4Repository.kt` (Room command paths + validation gate).
- `app/src/main/java/com/example/templei/feature/screen4/Screen4ColumnTypeRegistry.kt` (107-type registry baseline).
- `app/src/main/java/com/example/templei/feature/screen4/Screen4ValidationEngine.kt` (semantic validator dispatch).
- `app/src/main/java/com/example/templei/feature/screen4/Screen4RapidEntryConfig.kt` and `Screen4RapidEntryStore.kt` (rapid-entry config persistence).
- `app/src/main/res/layout/activity_screen4.xml` and `app/src/main/res/values/strings.xml` (Screen 4 control surface + export/status text).
- `app/src/test/java/com/example/templei/feature/screen4/Screen4ColumnTypeRegistryTest.kt` (phase-6 registry gate tests).

## Consolidated markdown digest (single source going forward)
This section consolidates key intent from repository markdown notes so agents can use `AGENTS.md` as the primary operational document.

### Canonical-doc policy (effective now)
- `AGENTS.md` is the canonical machine-facing contract and should be kept up to date first.
- Other markdown notes are retained as historical/reference artifacts, but agent behavior should default to this file when conflicts or drift appear.
- When scaffold behavior changes, update this consolidated digest in the same change.

### Human scaffold recap (from docs/README + map notes)
- App remains an XML-first reproducible scaffold with `MainActivity` launcher/menu and shared top navigation.
- Screen shells should stay lightweight and easy to duplicate for domain-specific variants.
- Keep user copy in `strings.xml`, keep navigation shared via `view_top_navigation.xml`, and keep package namespace `com.example.templei`.
- Obsidian map notes (`docs/MINDMAP.md`, `docs/MAP_*`) are conceptual navigation aids; they do not supersede source-of-truth behavior contracts.

### Screen 3 consolidated status
- Screen 3 is the Action Pad/Soundboard surface with folder-scoped discovery from SAF picker.
- Supported file formats remain `.wav` and `.mp3`; playable limit remains `<= 6s`.
- State vocabulary remains explicit: `Loading`, `Ready`, `Playing`, `Error`.
- Implemented direction includes:
  - load progress diagnostics,
  - favorites/browser responsibility split,
  - framed collapsible sections + vertical scrolling,
  - dynamic folder clip actions (tap play, long-press assign),
  - hardened playback path (`MediaPlayer` async SAF URI),
  - persisted folder catalog metadata for faster reopen,
  - control-surface polish and operator UX refinements.

### Screen 4 consolidated status (typed table + workspace lifecycle)
#### Baseline engine phases
- Typed contract + coordinator boundary established.
- Semantic type registry and validation dispatch implemented (107-type baseline gate).
- Rapid-entry config persistence and deterministic row composition implemented.
- XML field rendering + per-field validation feedback implemented.
- CSV export path implemented.

#### Workspace lifecycle phases (table selection continuity)
- **Phase 1 (data model):** workspace entity introduced; rows/columns/cells scoped by `workspaceId`.
- **Phase 2 (selection context):** persisted active workspace id (`Screen4TableSessionStore`) shared by long/short form.
- **Phase 3 (UI routing):** long-form actions route to workspace create/select flows.
- **Phase 4 (archive lifecycle):** archive/restore table flows added with explicit lifecycle vocabulary.
- **Phase 5 (hardening):** active workspace visibility improved in status copy; lifecycle state-machine unit tests added.

#### Current Screen 4 operator contract
- Long form and short form must resolve to the same active workspace context.
- `New Table` creates/selects a workspace (nameable) and initializes workspace columns if missing.
- `Open Table` switches active workspace (not row-level open semantics).
- `Archive Active Table` archives current workspace and shifts context to another active workspace (or bootstrap default).
- `Restore Archived` restores archived workspace and switches context to it.
- Main Screen 4 actions focus on workspace lifecycle, column management (`Add Column`, `Delete Columns`), short-form launch, CSV export, and selected-row deletion.
- Measurement commit is short-form driven (`Commit & Next`) and no longer exposed on the main Screen 4 action surface.
- Column deletion can target any active column in the workspace when operators choose `Delete Columns`.
- Short-form field hints and validators are semantic-type driven and should present concrete format examples (`e.g.`) so operators can match required per-column formatting.
- On first launch/new-empty workspace flows, operators are prompted to create a default table seed or keep the workspace empty.
- Default seed columns are: `ID`, `Date`, `Time`, `Item`, `Quantity`, `Comment` with explicit semantic formatter/validator intent.
- Main Screen 4 includes a collapsible Long Form card that renders one draft input per active column in a 3-column grid layout.

### Rapid entry review-note consolidation
- Phase review notes indicate the rapid-entry modal now has:
  - capped committed-history visualization,
  - append staging behavior separate from persistence,
  - commit-loop hardening and in-progress guards,
  - explicit placeholder region for deferred measurements-included body,
  - no schema expansion from placeholder-only phase work.
- Continue treating rapid-entry placeholder behavior as non-persistent unless explicitly changed by a later phase.

### Forward-maintenance checklist for agents
When modifying scaffold behavior, ensure this file is updated with:
1. Behavioral contract change (what operators can do now).
2. State vocabulary or lifecycle transition updates.
3. Persistence/schema scope changes.
4. UI control-surface changes and naming semantics.
5. Validation/testing notes and known environment limitations.
