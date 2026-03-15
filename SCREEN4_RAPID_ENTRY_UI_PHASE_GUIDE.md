# Screen 4 Rapid Entry UI Phase Guide

## Purpose
This guide defines a phased implementation plan for the Screen 4 Rapid Entry UI refresh based on the stacked-commit-history mockup.

Primary intent:
- Preserve high-speed serial entry workflows.
- Show recent committed context without navigating away.
- Keep deterministic validation/commit behavior from current Screen 4 architecture.

Out of scope for this guide iteration:
- Full "measurements included" feature content.

---

## Scope constraints (current request)
- Include a **placeholder hook** for future "measurements included" support.
- Do **not** implement the measurements-included feature body yet.
- Keep existing Room + coordinator + validation architecture intact.
- Keep UI strings in `app/src/main/res/values/strings.xml`.

---

## Target UX summary
Rapid Entry should become a custom modal experience with three parallel channels:
1. **Committed values context** (left) with a visible stacked card affordance.
2. **New entry fields** (right) for immediate next-row input.
3. **Commit actions** (footer):
   - `Append Committed` (stages row context/history movement without final workflow exit).
   - `Commit & Next` (persists row and keeps user in rapid loop).

The modal should support re-selecting active columns and quick exit back to long-form/manual mode.

---

## Phase 0 — Contract & terminology alignment
Goal: lock naming and interaction contracts before refactor.

Status: completed (current baseline).

Deliverables:
- Rename user-facing "Manual Entry" terminology to **"Long Form Entry"**.
- Define rapid-modal state vocabulary:
  - `Idle`
  - `Editing`
  - `Appended`
  - `Committed`
  - `Error`
- Add explicit placeholder contract for future measurements module:
  - UI placeholder label only.
  - No persistence schema changes.
  - No validation surface changes.

Validation:
- String audit confirms no visible "Manual Entry" labels remain in Screen 4 UX copy.
- Rapid state names documented in code comments/KDoc where state is owned.

---

## Phase 1 — Modal shell + layout scaffolding
Goal: replace basic rapid input popup with structured modal shell.

Status: implemented as a baseline shell; follow-up review tracked in `SCREEN4_RAPID_ENTRY_PHASE1_REVIEW.md`.

Deliverables:
- Create a dedicated Rapid Entry modal layout XML with:
  - Header title + close affordance.
  - Re-select columns action.
  - Two-column body region:
    - Left: committed card container.
    - Right: entry input container.
  - Footer action row:
    - `Append Committed`
    - `Commit & Next`
- Preserve current validation and commit pathways via `Screen4Coordinator`.
- Keep accessibility baseline:
  - Content descriptions for header/actions.
  - Focus order from left context to right inputs to footer actions.

Validation:
- Modal opens from Begin Rapid Entry flow.
- Modal closes to Screen 4 host without app state loss.
- Existing commit action still inserts rows through repository transaction path.

---

## Phase 2 — Committed-value card stack visualization
Goal: add short-term history context (up to 5 cards) without expanding layout.

Status: implemented as a baseline stack visualization; review tracked in `SCREEN4_RAPID_ENTRY_PHASE2_REVIEW.md`.

Deliverables:
- Introduce in-memory/session model for rapid-entry commit history window (`max = 5`).
- Render layered card stack style behind top card (visual depth cues).
- Top card displays:
  - Column icon (category/type mapped).
  - Column name.
  - Latest committed value.
- Lower layers can be reduced-detail placeholders (text-only or reduced opacity).

Validation:
- After each successful rapid commit, history shifts correctly (newest first).
- History trims deterministically at 5 entries.
- Empty-history state renders a clear placeholder card.

---

## Phase 3 — Append Committed behavior
Goal: support staged capture movement separate from final rapid commit loop.

Status: implemented as baseline append staging; review tracked in `SCREEN4_RAPID_ENTRY_PHASE3_REVIEW.md`.

Deliverables:
- Define explicit append semantics:
  - Validate current entry inputs.
  - Move accepted values into committed-context preview stack.
  - Clear/prepare entry fields for next capture according to rapid-retain rules.
- Keep append action local to modal session unless committed.
- Show status line feedback for append success/failure.

Validation:
- Append does not break subsequent `Commit & Next`.
- Field-level validation errors prevent append and surface inline.
- Re-select columns after append preserves consistent field mapping.

---

## Phase 4 — Commit loop hardening + parity checks
Goal: ensure new UI keeps deterministic behavior of existing rapid flow.

Status: implemented as baseline hardening; review tracked in `SCREEN4_RAPID_ENTRY_PHASE4_REVIEW.md`.

Deliverables:
- `Commit & Next` remains one-tap loop:
  - Validate.
  - Persist atomically.
  - Update table preview.
  - Refresh modal state for next input.
- Preserve auto-column behavior (timestamps/etc.) from existing `RapidEntryConfig`.
- Ensure cancel/close path does not corrupt draft persistence.

Validation:
- Regression checks for:
  - Required fields.
  - Max length.
  - Semantic validators.
  - Auto-column injection.
- Confirm row count/table preview updates match current engine expectations.

---

## Phase 5 — Future measurements-included placeholder (no feature body)
Goal: reserve UI location and contract for future module without implementing it.

Status: implemented as non-interactive placeholder region; review tracked in `SCREEN4_RAPID_ENTRY_PHASE5_REVIEW.md`.

Deliverables:
- Add clearly labeled placeholder region in modal (non-interactive for now), e.g.:
  - `Measurements Included (Coming Soon)`
- Add TODO/KDoc references pointing to future phase owner file.
- Ensure placeholder does not alter commit payload or validator surface.

Validation:
- Placeholder visible and stable in modal layout.
- No new persistence fields/columns introduced.
- No changes to current commit schema paths.

---

## Recommended technical mapping
- Keep Screen 4 host activity thin; route command actions through coordinator.
- Add modal-specific rendering helpers in `Screen4Activity` only as orchestration.
- Place reusable rapid modal view state data classes under `feature/screen4`.
- Avoid introducing cross-screen dependencies.

---

## Testing strategy per phase
- Unit tests:
  - Rapid history stack reducer logic.
  - Append transition behavior.
  - Rapid field-map stability when reselecting columns.
- Existing Screen 4 validator/registry tests remain required gates.
- Instrumentation/manual checks:
  - Rapid modal open/close loop.
  - Append + Commit & Next interactions.
  - Orientation/activity recreation resilience.

If Android SDK/emulator is unavailable, run static/unit checks and document the limitation.

---

## Rollout notes
- Ship as incremental PRs by phase.
- Each phase should maintain functional parity with existing commit correctness guarantees.
- Defer visual polish (gradients, glow intensity, animation tuning) until functional parity is complete.
