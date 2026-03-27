# Screen 4 Input Constraints

This document defines the intended operator-facing UI constraints for Screen 4 column groups.

## Goals

- reduce unnecessary keyboard usage
- make input constraints visible through widget choice
- keep normalization and validation deterministic
- prevent the IME from obscuring focused entry fields

## Constraint Matrix

### Numbers
- Primary widgets:
  - numeric keypad for direct numeric entry
  - integer and decimal numeric-policy dialog during column creation
- Input constraints:
  - max digits
  - decimal places
  - separators on or off
  - negative allowed on or off
- Validation:
  - numeric shape while typing
  - digit and decimal-place enforcement on append or save

### Dates
- Primary widgets:
  - date picker dialog
- Input constraints:
  - typed keyboard suppressed by default
  - formatted output follows the selected date column pattern
- Validation:
  - picker output plus existing date validation

### Time
- Primary widgets:
  - time picker dialog
- Input constraints:
  - typed keyboard suppressed by default
  - output format respects 24-hour vs AM or PM variants
- Validation:
  - picker output plus existing time validation

### Enumerated Values
- Primary widgets:
  - dropdown or autocomplete
- Input constraints:
  - values should come from allowed_values whenever present
- Validation:
  - allowed-values enforcement on append or save

### Contact And Identifiers
- Primary widgets:
  - specialized keyboard for email and phone
  - full keyboard for free-form identifiers
- Input constraints:
  - regex and semantic validation remain active

### Long Text
- Primary widgets:
  - multiline text box
- Input constraints:
  - expanded height
  - sentence-capitalization keyboard

## Keyboard Safety

- Screen 4 long form and short form use resize-safe IME handling.
- Focused inputs are scrolled into view when the keyboard opens.
- Date and time picker fields suppress the keyboard and use dialogs instead.

## Remaining Follow-Up Ideas

- add timezone picker support instead of free text
- add timestamp picker flow for combined date and time fields
- add grouped live formatting for separator-enabled numeric-policy fields
- allow per-group custom widgets such as switches, chips, and segmented selectors
