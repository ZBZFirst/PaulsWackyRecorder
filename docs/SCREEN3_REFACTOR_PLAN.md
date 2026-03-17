# Screen3Activity Refactor Plan (toward 200–300 lines)

## Why this is feasible

`Screen3Activity` already delegates parts of the UI to helper classes (`Screen3UiRenderer`, `Screen3ClipBrowserRenderer`, `Screen3FavoritePadHelper`) and uses dedicated feature classes (`ClipIndexRepository`, `SoundboardAudioEngine`, `SoundboardStateMachine`, settings helper/store). That means the remaining line count is mostly due to orchestration and in-activity state ownership, not missing architecture.

So a 200–300 line activity is realistic if we move **domain state + use cases** out of the activity and keep only:
- view binding,
- lifecycle wiring,
- intent/result routing,
- simple render callbacks.

## Current concern buckets still living inside Screen3Activity

1. **Folder/index loading orchestration**
   - `initializeFromPersistedIndexOrNoRoot`, `rebuildIndexAndBind`, `bindFolderBrowserFromIndex`, `bindCurrentFolderFromIndex`.
2. **Favorites assignment domain logic**
   - `assignFavoriteClip`, `clearSelectedAssignmentSlot`, slot selection persistence and label composition.
3. **Playback policy + rejection accounting**
   - `attemptPlayback`, `reject`, cooldown/max-stream checks, state-machine event assembly.
4. **Clip loading/cache lifecycle**
   - `ensureClipLoaded`, `trimClipCache`, `clearCacheAndPending`, pin/unpin semantics.
5. **Derived state snapshots**
   - `refreshReadyState`, `clipLoadSnapshot`, `cacheState`, `constraintSnapshot`, `rejectionCounters`.

These buckets are all feature logic and are good extraction candidates.

## Target module split (practical + incremental)

### 1) `Screen3Contracts.kt`
Create explicit contracts for UI intents and render models.

- `sealed interface Screen3Intent` (e.g., `PickFolder`, `Rescan`, `TapFavoriteSlot`, `LongPressFavoriteSlot`, `TapClip`, `LongPressClip`, `ToggleSection`, `ApplySettings`).
- `data class Screen3ViewState` (everything needed by XML renderer: status, loading row, folder list/index, favorites, assignment target, section collapsed flags, clip list).
- `sealed interface Screen3Effect` (toast, dialog requests, navigation fallback).

**Benefit:** activity becomes a thin translator from Android events to intents and from view state to renderer.

### 2) `Screen3Coordinator.kt` (or `Screen3Controller.kt`)
Move orchestration from activity into one coordinator class.

Responsibilities:
- Own runtime state currently in activity (folders, selected folder, active clips, favorites map, selected slot, collapse booleans, rejection counters).
- Handle intent dispatch (`dispatch(intent)`), emit `Screen3ViewState` updates and one-off effects.
- Call repository/audio/cache services.

**Benefit:** easiest way to reduce activity line count quickly by moving most methods without changing behavior.

### 3) `Screen3PlaybackPolicy.kt`
Pure domain policy class:
- validates `isPlayable`, cooldown, stream limits,
- returns `PlaybackDecision.Accept` or typed reject reason + message token.

**Benefit:** deterministic unit tests and reusable policy for other screens.

### 4) `Screen3ClipCacheManager.kt`
Extract caching and `SoundPool` load bookkeeping:
- `ensureClipLoaded`,
- callback fan-out for concurrent loads,
- trim strategy by `CachePolicy` and max size,
- clear/release hooks.

**Benefit:** isolates the trickiest mutable state; keeps activity/controller readable.

### 5) `Screen3FavoritesManager.kt`
Extract favorite slot persistence + labels + assignment behavior.

**Benefit:** enables sharing the same pad behavior on another page with minimal wiring.

### 6) `Screen3FolderBrowserCoordinator.kt`
Extract index/root/folder binding workflow and spinner-safe selection logic.

**Benefit:** removes threading + repository concerns from activity.

## Suggested end-state line budget

- `Screen3Activity.kt`: **220–280 lines** (UI wiring/lifecycle only).
- `Screen3Coordinator.kt`: 180–260 lines.
- `Screen3ClipCacheManager.kt`: 140–220 lines.
- `Screen3PlaybackPolicy.kt`: 80–140 lines.
- `Screen3FavoritesManager.kt`: 80–140 lines.
- `Screen3FolderBrowserCoordinator.kt`: 100–180 lines.

This keeps each file understandable and allows selective reuse on other screens.

## Migration sequence (low risk)

1. **Introduce contracts + coordinator skeleton** (no behavior change, activity forwards events).
2. **Move folder/index workflow** into folder coordinator.
3. **Move favorites workflow** into favorites manager.
4. **Move playback validation + reject accounting** into playback policy.
5. **Move clip cache/load logic** into cache manager.
6. **Finalize activity cleanup** to lifecycle + render binding only.

Run checks after each step to keep diffs small and reversible.

## Reuse strategy for “other screen pages with a few lines”

To make Screen 3 soundboard reusable elsewhere:

- expose a single feature entrypoint (`Screen3Coordinator` + `Screen3Contracts`),
- keep dependencies injectable (repository, audio engine, settings store),
- keep UI rendering behind current XML helpers,
- on another activity, only wire view IDs and forward intents.

That yields “new page with a few lines” behavior without duplicating soundboard logic.

## Feasibility summary

Yes—**200–300 lines for `Screen3Activity.kt` is feasible** without a rewrite to Compose.
The most practical path is to extract domain orchestration into a coordinator plus cache/favorites/folder/playback helpers while keeping XML and existing renderer helpers intact.
