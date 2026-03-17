# Rapid Entry UI (Screen 4 Short Form)

This document replaces the older phase-tracking guide and now summarizes the **as-built** rapid-entry UI behavior.

## Scope
Rapid Entry is the short-form measurement loop used from `Screen4ShortFormActivity`.
It keeps fast serial entry while preserving typed validation and deterministic persistence.

## Completed implementation summary

### Modal/surface structure
- Dedicated rapid-entry shell is in place with:
  - committed-context preview region,
  - new-value input region,
  - action footer (`Append Committed`, `Commit & Next`),
  - quick column re-select control,
  - close/back-to-host flow.

### Session context + history
- In-session committed preview history is maintained as a capped stack (`max = 5`).
- Newest commit is shown with highest detail; lower layers are reduced-detail context cards.

### Append semantics
- `Append Committed` validates inputs, stages values into local committed-context history, then clears input fields for next capture.
- Append is **session-local only** and does not persist a row by itself.

### Commit loop hardening
- `Commit & Next` remains the persistence boundary (validate -> atomic write -> continue loop).
- Duplicate-tap guards and in-progress status feedback are enforced.
- Failure paths keep the operator in loop and recover controls.

### Placeholder region
- `Measurements Included (Coming Soon)` is intentionally non-interactive.
- No schema, validator, or commit-payload expansion is attached to placeholder-only UI.

## Lifecycle + persistence contract
- Short-form session restoration is supported across recreation (selected columns/modal context/history).
- Shared workspace context is preserved between long-form and short-form surfaces via Screen 4 session state.

## Usage notes
- Use this doc for current rapid-entry behavior reference.
- Use `SCREEN4_TYPED_RAPID_ENTRY_PLAN.md` for extended typed-engine planning and rollout checklist context.
