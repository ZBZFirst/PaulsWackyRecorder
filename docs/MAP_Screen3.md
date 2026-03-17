# MAP: Screen 3 (Action Pad / Soundboard)

## Source files
- [activity_screen3.xml](../app/src/main/res/layout/activity_screen3.xml)
- [Screen3Activity.kt](../app/src/main/java/com/example/templei/Screen3Activity.kt)
- [SoundboardAudioEngine.kt](../app/src/main/java/com/example/templei/feature/soundboard/SoundboardAudioEngine.kt)
- [SoundboardStateMachine.kt](../app/src/main/java/com/example/templei/feature/soundboard/SoundboardStateMachine.kt)
- [strings.xml](../app/src/main/res/values/strings.xml)

## Intersections
- Enter from [[MAP_MainMenu]]
- Navigate laterally with [[MAP_TopNavigation]]
- Parallel page with [[MAP_Screen1]], [[MAP_Screen2]], [[TableManagementScreen]]

## Purpose and contract
Screen 3 is the scaffold's **Action Pad / Soundboard** surface.

Current contract:
- User selects a root folder through SAF (`OpenDocumentTree`).
- Browser scope is the selected level plus sibling folders (back/forward switching).
- Candidate formats are `.wav` and `.mp3`.
- Clips are only considered playable when `duration in 1..6000ms`.
- Playback is user-gesture-only (tap favorite pad slot or folder clip button).
- Top-level state vocabulary remains explicit: `Loading`, `Ready`, `Playing`, `Error`.

## UI structure (XML-driven)
`activity_screen3.xml` is organized into explicit sections:
- Status + loading detail + horizontal progress bar.
- Folder navigation row (previous, current-folder label, next, choose folder, settings).
- Favorites frame (3x3 pad + assignment target + clear slot action + collapse toggle).
- Clip browser frame (dynamic vertical button stack + collapse toggle).

This keeps the scaffold easy to inspect and adjust without Compose migration pressure.

## Data model and state ownership
`Screen3Activity` owns the live session model:
- `folderEntries` + `currentFolderIndex`: active sibling-navigation context.
- `activeFolderClips`: clips discovered for current folder.
- `clipById`: cross-folder clip metadata cache by URI-string id.
- `favoriteSlotClipIds`: explicit slot-to-clip mapping (9 slots).
- `clipCache`: load-state cache used for preload bookkeeping + trimming heuristics.
- `rejectionCounts` + `lastRejectionEvent`: deterministic diagnostics.

State presentation is mediated through `SoundboardStateMachine` snapshots.

## Folder discovery and loading pipeline
When a root folder exists, `bindFolderBrowser()`:
1. Materializes readable sibling directories into `folderEntries`.
2. Selects `currentFolderIndex` and calls `bindCurrentFolder()`.

`bindCurrentFolder()` then:
1. Emits `Loading` state.
2. Resets discovery detail text.
3. Launches background scan via `folderScanExecutor`.
4. Uses `folderScanToken` cancellation guard so stale scans cannot overwrite newer folder changes.

`scanFolderClips()` workflow:
1. Filters candidate files to `.wav`/`.mp3` only.
2. Emits **Discovery** progress (`N candidate files found`).
3. Reads each duration via `MediaMetadataRetriever`.
4. Marks clip playable only if duration is within max bound.
5. Emits **Validation** progress (processed / total / playable).
6. Returns sorted clip metadata list.

## `.wav` playback flow (current implementation)
Playback is now intentionally routed through `MediaPlayer` for reliable SAF URI reads:

1. User taps a playable folder clip button or assigned favorite slot.
2. `attemptPlayback(clip)` enforces guardrails:
   - playable flag check,
   - cooldown window check,
   - max-stream admission check.
3. `ensureClipLoaded(clip)` still performs SoundPool load bookkeeping and cache-state tracking for diagnostics and trim policy.
4. Actual sound output is triggered by `audioEngine.playClipUri(context, clip.uri)`.
5. `SoundboardAudioEngine.playClipUri()` creates a `MediaPlayer`, sets media audio attributes, binds SAF URI datasource, and starts with `prepareAsync()`.
6. Completion/error listeners release player instances and keep `activePlayers` clean.
7. Screen state transitions to `Playing`, then returns to `Ready` after a timed active-stream release window.

### Why this matters for `.wav`
- SAF URI sources can be inconsistent with direct low-latency assumptions.
- Async `MediaPlayer` prepare has proven more reliable for triggering `.wav` clips from picker-selected folders.
- This path aligns with current user-visible behavior: clips play directly from folder buttons without needing favorite assignment.

## Favorites and assignment model
- Favorites are explicit user assignments, not auto-filled from folder scans.
- Long-press browser clip -> assignment dialog for any of 9 slots.
- Long-press favorite pad slot -> quick clear/remove behavior.
- "Assignment target" row tracks default slot for assignment actions.
- Slot mappings persist in `SharedPreferences` and restore on reopen.

## Cache and constraint controls
Runtime settings dialog controls:
- `maxStreams` (1..8)
- `cooldownMs` (0..500)
- `maxCacheSize` (8..36)
- `unloadOnFolderChange`
- cache policy (`AGGRESSIVE`, `BALANCED`, `STICKY`)

Trim operations respect pinning/favorites and active-folder context.

## Diagnostics exposed in UI/state
Screen 3 surfaces deterministic operational visibility:
- discovery counts,
- validation counts and progress,
- ready summary (playable/cached/favorites),
- rejection reason counters,
- last rejection event details,
- clip load snapshot (unloaded/loading/loaded/failed).

## Known implementation nuance (important before next refactors)
Current runtime uses **two audio concerns** in parallel:
- SoundPool cache/load state machine for clip load bookkeeping.
- MediaPlayer engine for actual playback.

This is intentional in the current phase and should be treated as an explicit transitional architecture when planning subsequent cleanup.
