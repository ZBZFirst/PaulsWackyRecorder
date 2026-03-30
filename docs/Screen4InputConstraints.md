# Screen 4 Sequencer Interaction Constraints

This document defines the intended operator-facing interaction constraints for the current Screen 4 sequencer.

## Goals

- keep sequencing touch-first and explicit
- keep transport state readable during live use
- keep favorite assignment deterministic
- avoid hidden automation that changes a loop without a direct operator action
- preserve transport continuity when moving from Screen 4 to Screen 1

## Constraint Matrix

### Favorite Pads

- Source:
  - favorite pages and favorite-pad assignments come from Screen 3 shared state
- Primary interactions:
  - tap to preview
  - select for explicit assignment into the sequencer
- Constraints:
  - empty favorite slots remain visibly empty
  - missing clips must surface as unavailable instead of silently remapping

### Favorite Pages

- Primary interactions:
  - explicit previous and next page navigation
  - explicit add and remove actions when supported
- Constraints:
  - page switching must not autoplay the transport
  - Screen 4 must stay synchronized with the shared Screen 3 page model

### Play Bars

- Primary interactions:
  - add bar
  - remove bar
  - focus or expand a bar for editing
- Constraints:
  - bar count stays within coordinator-defined limits
  - at least one play bar must remain
  - selection scope must remain explicit

### Steps

- Primary interactions:
  - explicit step assignment from a favorite reference
  - explicit clear or reassign behavior
  - batch assignment through defined selection actions
- Constraints:
  - no implicit fill or randomization behavior
  - assignments must remain inspectable at the step level
  - empty steps must remain visibly empty

### Transport

- Primary interactions:
  - play
  - stop
  - BPM adjustment
- Constraints:
  - transport does not auto-start on screen entry
  - compile and runtime status remain visible
  - playback continuity survives navigation by runtime design, not by duplicating transport logic in UI hosts

## Navigation Safety

- Screen 4 is expected to hand off naturally into Screen 1 after a loop is running.
- Transport survival belongs to `Screen4MusicRuntime`.
- `Screen4ShortFormActivity` and `Screen4LongFormActivity` are redirect shims, not separate editing surfaces.

## Remaining Follow-Up Ideas

- refine saved-song naming and recall affordances
- expand visual feedback for unavailable favorite references
- tune large-song editing ergonomics without hiding assignment rules
