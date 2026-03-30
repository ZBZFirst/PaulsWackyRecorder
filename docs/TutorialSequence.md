# Tutorial Sequence

## Purpose

This document defines the deterministic onboarding flow for the app.

The tutorial is built screen by screen and chained in this order:

1. `Screen2Activity`
2. `Screen3Activity`
3. `Screen4Activity`
4. `Screen1Activity`

The first implementation target is Screen 2 because it creates the tutorial clips used by every later screen.

## Shared Hub Controls

The Studio Hub in `MainActivity` should expose three tutorial controls:

- `Start Tutorial` or `Continue Tutorial`
- `Redo Tutorial`
- `Skip Tutorial`

## Shared Tutorial State

Keep the shared state simple and explicit.

### Tutorial Progress

- `NotStarted`
- `InProgress`
- `Skipped`
- `Completed`

### Tutorial Sequence Position

- current screen id
- current step index
- completed screen ids

`Redo Tutorial` clears progress and restarts at Screen 2 step 1.

`Skip Tutorial` sets the tutorial to `Skipped` and prevents automatic tutorial launch until the user explicitly restarts it from the hub.

## Screen 2 Tutorial Goal

The user leaves Screen 2 with five saved `.wav` clips that can be used immediately in Screen 3.

Default prompt set:

1. `so`
2. `re`
3. `mi`
4. `do`
5. `la`

Suggested default file names:

1. `tutorial_01_so`
2. `tutorial_02_re`
3. `tutorial_03_mi`
4. `tutorial_04_do`
5. `tutorial_05_la`

Users may rename each file before saving.

## Screen 2 Tutorial Layout Contract

Tutorial mode on Screen 2 should simplify the page.

### Remove

- the top status or help card that currently explains the recorder flow

That instruction becomes part of the tutorial overlay and tutorial card instead.

### Keep

- folder selection area
- file name field
- countdown or duration feedback

### Add

- a tutorial prompt card
- a large record pad
- a `Save Clip` action
- a `Next Prompt` action when the current clip is saved
- a visible `Skip Tutorial` action

## Screen 2 Deterministic State Model

Screen 2 tutorial mode uses this finite state set:

- `NeedsFolder`
- `NeedsFileName`
- `ReadyToRecord`
- `Recording`
- `DraftReady`
- `Saving`
- `PromptSaved`
- `ScreenComplete`

## Screen 2 State Rules

### `NeedsFolder`

- tutorial starts here if no folder has been selected
- spotlight target is `Choose Folder`
- folder picker cancel keeps the user in this state
- folder picker success advances to `NeedsFileName`

### `NeedsFileName`

- file name field is active and prefilled with the current suggested tutorial name
- the user may accept or edit the name
- blank or invalid names do not advance
- once the name is valid, advance to `ReadyToRecord`

### `ReadyToRecord`

- large record pad shows `Press and Hold to Record`
- tapping the pad does nothing because no draft exists yet
- long press begins recording and advances to `Recording`

### `Recording`

- recording begins on press and hold
- recording stops on release or on the six-second recorder ceiling
- release stores an unsaved draft clip in cache
- after a successful draft capture, advance to `DraftReady`

### `DraftReady`

- the draft is not saved to the selected folder yet
- tap the record pad to preview the draft
- long press the record pad again to overwrite the draft with a new take
- `Save Clip` exports the current draft using the chosen file name
- `Save Clip` advances to `Saving`

### `Saving`

- UI blocks duplicate save actions
- successful save advances to `PromptSaved`
- failed save returns to `DraftReady` with an error message

### `PromptSaved`

- the prompt is complete and the file now exists in the selected folder
- if prompts remain, `Next Prompt` loads the next prompt and returns to `NeedsFileName`
- if this was prompt 5, advance to `ScreenComplete`

### `ScreenComplete`

- Screen 2 tutorial is marked complete
- selected folder information is handed forward to Screen 3
- the tutorial advances into the Screen 3 sequence

## Screen 2 Interaction Rules

Keep the recording interaction simple and deterministic.

### Record Pad

- long press starts recording
- release stops recording
- tap previews the current draft only when a draft exists
- long press after a draft exists overwrites that draft

### Save Behavior

- no recording is saved automatically on release
- only `Save Clip` writes the clip into the selected tutorial folder
- saving uses the current file name field value

### Rename Behavior

- rename remains editable before save
- changing the file name after a draft exists changes the export name, not the audio content

## Screen 2 Tutorial Step Order

Use this step order for the first tutorial pass:

1. Highlight `Choose Folder`
2. Wait for successful folder selection
3. Highlight the file name field for prompt 1
4. Highlight the record pad and require one press and hold recording
5. Allow preview or re-record
6. Highlight `Save Clip`
7. Advance to the next prompt
8. Repeat until all five prompts are saved
9. Mark Screen 2 complete and continue to Screen 3

## Screen 2 Handoff Contract

To keep the tutorial smooth, Screen 2 must hand its selected folder into Screen 3.

The same chosen folder should become:

- the Screen 2 recording target folder
- the Screen 3 initial root folder for tutorial browsing
- the Screen 3 initial selected folder when the tutorial starts

That prevents the tutorial from asking the user to pick the same folder twice.

## Implementation Notes

Keep the first implementation intentionally narrow.

- build shared tutorial progress storage
- add hub buttons for skip and redo
- implement only the Screen 2 tutorial sequence first
- keep Screen 2 tutorial state separate from the normal Screen 2 flow where practical
- do not add hidden automation beyond the explicit state transitions listed above
