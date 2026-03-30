# Documentation Overview

This folder documents the current app structure for `PaulsWackyRecorder`.

## Current Product Flow

The intended operator flow is:

1. Launch `MainActivity` to check permissions and device readiness.
2. Open `Screen2Activity` to record and save new `.wav` clips into a chosen folder.
3. Open `Screen3Activity` to select the clip folder, browse sibling folders, and assign clips to favorite pads and favorite pages.
4. Open `Screen4Activity` to build a loop from those shared favorite pads in the sequencer.
5. Return to `Screen1Activity` to run the camera while the loop keeps playing and capture live reactions.

`MainActivity` remains the launcher and navigation hub.

## Screen Roles

- `MainActivity`: permissions, readiness, and navigation.
- `Screen1Activity`: camera preview and capture while sequencer playback continues.
- `Screen2Activity`: bounded `.wav` recording and save flow.
- `Screen3Activity`: folder-backed soundboard and source of truth for favorite-pad assignments.
- `Screen4Activity`: multi-bar WAV loop sequencer driven by Screen 3 favorites.

## Screen 4 Notes

The active Screen 4 contract is sequencer-oriented.

- `Screen4Activity` is the real host.
- `Screen4ShortFormActivity` and `Screen4LongFormActivity` are legacy redirect shims into `Screen4Activity`.
- `Screen4MusicRuntime` keeps transport alive across navigation.
- `Screen4Coordinator` owns sequencer state, shared favorite sync, and transport-facing status.

## Documents In This Folder

- `TutorialSequence.md`: deterministic onboarding flow, starting with the Screen 2 tutorial contract and shared hub controls.
- `Screen4MusicSequencer.md`: current Screen 4 behavior and boundaries for the live sequencer surface.
- `Screen4InputConstraints.md`: operator interaction constraints for the current Screen 4 sequencer.
- `Screen4MockupRegistry.md`: design-side mockup contract for Screen 4 visuals.
- `PaulsDatasetExplained.md`: archival workbook reference material. It is not the active Screen 4 product contract.

## Legacy Reference Material

The workbook-based table and validation experiments still exist as reference material in the repo, but they are not the current runtime story for Screen 4.

If those ideas are revisited later, they should be reintroduced explicitly rather than inferred from the active music workflow.
