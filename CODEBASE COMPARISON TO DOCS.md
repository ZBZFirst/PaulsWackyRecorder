# Documentation Alignment Summary

This file records the current documentation posture after the Screen 4 contract was realigned to the live app structure.

## Current Runtime Story

- `MainActivity` is the launcher, permission gate, readiness console, and screen router.
- `Screen2Activity` is the bounded `.wav` recorder and save flow.
- `Screen3Activity` is the folder-backed soundboard and source of truth for favorite-pad assignments.
- `Screen4Activity` is the multi-bar loop sequencer that consumes shared favorites from Screen 3.
- `Screen1Activity` is the camera capture surface used while sequencer playback continues.

## Screen 4 Architecture Notes

- the active Screen 4 host is `Screen4Activity`
- `Screen4MusicRuntime` owns process-level transport continuity
- `Screen4Coordinator` owns sequencer state, favorite sync, compile state, and transport-facing UI state
- `Screen4ShortFormActivity` and `Screen4LongFormActivity` are legacy redirect shims

## Legacy Material Still In Repo

- workbook and table-management documents remain as reference material only
- historical table and validation modules may still exist under `feature/screen4`
- those legacy materials must not be treated as the active Screen 4 product contract unless a task explicitly revives them

## Documentation Files To Trust First

- `AGENTS.md`
- `docs/README.md`
- `docs/Screen4MusicSequencer.md`
- `docs/Screen4InputConstraints.md`
- `docs/Screen4MockupRegistry.md`
- `docs/PaulsDatasetExplained.md`
