# Screen 4 Music Sequencer

## Purpose

`Screen4Activity` is the multi-bar WAV loop sequencer. It turns favorite-pad assignments from Screen 3 into a playable loop, exposes transport controls, and keeps the current song structure visible and editable.

## Primary Source Files

- `app/src/main/java/com/example/templei/Screen4Activity.kt`
- `app/src/main/java/com/example/templei/feature/screen4/Screen4Coordinator.kt`
- `app/src/main/java/com/example/templei/feature/screen4/Screen4MusicRuntime.kt`
- `app/src/main/java/com/example/templei/feature/screen4/Screen4SequenceStore.kt`
- `app/src/main/java/com/example/templei/feature/screen4/Screen4SampleLibraryRepository.kt`

## How This Screen Is Used

### 1) Favorite sync from Screen 3

Screen 4 does not define its own clip library in isolation.

- Screen 3 owns the folder-backed clip library.
- Screen 3 owns favorite-pad and favorite-page assignment.
- Screen 4 reads that shared favorite state and lets the user preview or sequence those assignments.

If the user changes the source folder or favorite assignments in Screen 3, Screen 4 refreshes from that shared state.

### 2) Song construction

Operators use Screen 4 to assemble a loop from favorite references.

- favorite pages remain browsable,
- favorite slots remain previewable,
- play bars are addable and removable within coordinator limits,
- steps are assigned explicitly from favorite-pad references,
- selection and batch assignment stay explicit,
- BPM remains adjustable through the sequencer controls.

The screen is a structured loop builder, not a free-form soundboard.

### 3) Transport and runtime feedback

Transport behavior must stay explicit and inspectable.

- play and stop remain direct user actions,
- compile status stays visible,
- runtime status stays visible,
- errors stay visible,
- no autoplay should occur on entry, sync, or save-load operations.

### 4) Save, load, and continuity

Song state is persistent and transport continuity is intentional.

- working state is stored through Screen 4 persistence paths,
- saved song behavior belongs to sequencer stores and coordinator flow,
- playback may continue while the user navigates away from Screen 4,
- Screen 1 camera capture is expected to work while the loop is running.

### 5) Legacy redirect routes

`Screen4ShortFormActivity` and `Screen4LongFormActivity` are legacy compatibility routes.

They redirect into `Screen4Activity` and are not separate feature surfaces anymore.

## Current Contract Status

- Screen 4 is sequencer-driven, not table-management-driven.
- Screen 3 remains the source of truth for favorite-page assignments.
- `Screen4MusicRuntime` is responsible for transport survival across navigation.
- `Screen4Coordinator` is the main command boundary for Screen 4 UI actions.
- Historical measurement and workbook modules may still exist in the repo, but they are not the current Screen 4 user contract.
