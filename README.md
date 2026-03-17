# Android App Scaffold (Agent + Human Iteration Template)

This repository is a **default Android scaffold** for fast, reproducible iteration by both:
- **Humans** (reading docs and making design decisions)
- **Agents** (following operational instructions and automating routine work)

## Purpose

The goal is to remove unnecessary app-design overhead so builders can focus on domain problems.

> Knowledge in one area can block progress in another. Reusable scaffolds and automation reduce that friction.

This project provides a stable baseline that can evolve over time without losing clarity.

## Default app structure

The baseline UI/flow is intentionally simple and reproducible:

1. **Launcher menu** (`activity_main.xml`) routes to activity pages.
2. **Top navigation bar** (`view_top_navigation.xml`) supports movement across pages.
3. **Page-oriented activities** (`Screen1Activity` ... `Screen4Activity`) represent parallel contexts.
4. Pages can be duplicated and adapted to new contexts while preserving structure.

## Human + agent documentation model

This repo uses two complementary docs:

- `README.md` → human-facing explanation and onboarding.
- `AGENTS.md` → machine/agent-facing operational guidance.

Additionally, the repo includes an **Obsidian-style wikilink index** in `AGENTS.md` so humans can open the project as a graph/mind-map and quickly find relevant files.

For graph-first navigation of how pages intersect, start with `MINDMAP.md`.

## How to use this scaffold

1. Clone the repository.
2. Open in Android Studio.
3. Start by editing:
   - `app/src/main/res/layout/activity_main.xml` for menu routing
   - `app/src/main/res/layout/activity_screen*.xml` for page shells
   - `app/src/main/res/layout/view_top_navigation.xml` for shared navigation
4. Keep user-facing copy in `app/src/main/res/values/strings.xml`.
5. Duplicate page/activity patterns when creating new parallel flows.

## Notes

- This scaffold is intended to be the **default view** of how these apps should look.
- It will evolve, but the baseline pattern should remain stable.
- Video instructions for download/run can be layered on top of this structure without changing the core scaffold.

## Obsidian mind-map usage

1. Open the repo in Obsidian as a vault.
2. Open [[MINDMAP]] as the graph hub.
3. Follow page nodes (`[[MAP_MainMenu]]`, `[[MAP_Screen1]]`, etc.) to see intersections.
4. Use the clickable file links inside each map note to open the underlying `.kt` and `.xml` files directly.


## Screen 3 playback documentation (current)

For detailed implementation notes, use `MAP_Screen3.md` as the canonical page-level reference.

Key points for the current `.wav` path:
- Folder scanning still validates candidate `.wav`/`.mp3` files and enforces the `<= 6s` playable gate.
- Discovery + validation counters are surfaced in the loading row during scans.
- User taps on folder clip buttons or favorite slots trigger guarded playback checks (playable/cooldown/max-stream).
- Actual audio playback is started via `SoundboardAudioEngine` (`MediaPlayer` + async `prepareAsync` on SAF URIs), which is the reliability path for `.wav` clip triggering.
- SoundPool load/cache state remains active for diagnostics + cache trimming policy, even though MediaPlayer is the runtime playback engine in this phase.

## Screen 3 scaffold progression

- Phase 1 added loading diagnostics for folder scanning (processed/total/playable counters).
- Phase 2 separates folder clip browsing from favorite-slot assignments; favorite slots are explicit and persisted.
- Phase 3 groups Screen 3 into framed, collapsible favorites/browser panels with vertical scrolling.
- Phase 4 adds dynamic folder clip action buttons (tap to play, long-press to assign) plus per-folder wav/mp3 counts.
- Phase 5 hardens playback (MediaPlayer async SAF URI path) and adds quick long-press favorite removal.
- Phase 5 refinement adds explicit discovery+validation progress and renders one browser button per playable `.wav` in the active folder.
- Phase 5 also persists folder catalog metadata by root URI so large libraries can reopen without a full initial rescan.
- UI polish pass: Screen 3 now applies a dedicated themed visual treatment (gradient backdrop, card sections, styled action/favorite buttons) without changing playback/state logic.
- Navigation refinement: folder switching now uses a dropdown spinner between prev/next for rapid scrolling through long folder lists, and Clear Slot prompts for explicit target-slot selection before removal.
- Control actions (Choose Folder / Settings / Rescan) are grouped in a collapsible Library Controls section at the bottom of Screen 3 content.

## Screen 4 serial measurement engine (current)

- Phase 6 baseline gate is complete: Screen 4 registry declares all 107 semantic type names with deterministic metadata and unit-test coverage for count/uniqueness and alias resolution.
- Phase 5 core delivery is complete: Screen 4 supports SAF-based CSV export from the current table snapshot.
- Phase 4 core delivery is complete: Screen 4 maps input widgets from registry metadata and shows field-level validation feedback in XML entry forms.
- Phase 3 core delivery is complete: Screen 4 persists `RapidEntryConfig` (active + auto columns) and composes rapid rows with base/input/auto stages before commit.
- Phase 2 core delivery is complete: Screen 4 includes `Screen4ColumnTypeRegistry` + `Screen4ValidationEngine` and enforces semantic validation before row insert/update.
- Phase 1 core delivery is complete: Screen 4 routes activity commands through `Screen4Coordinator` and includes typed-contract scaffolding in `feature/screen4/Screen4Contracts.kt`.
- Screen 4 from-scratch implementation is now organized as a concise 6-phase execution plan with copy/paste prompt pack in `SCREEN4_TYPED_RAPID_ENTRY_PLAN.md`.
- Screen 4 typed rapid-entry architecture and 107-type rollout checklist are tracked in `SCREEN4_TYPED_RAPID_ENTRY_PLAN.md` (including a dependency-impact section for existing Room/Coroutine/Compose libs).
- Screen 4 now boots a Room-backed schema (`ColumnTemplates`, `Columns`, `Rows`, `Cells`) for deterministic measurement storage.
- Short form entry uses a draft row that is persisted through interruptions and hydrated on reopen.
- `Commit Measurement` validates the draft and atomically inserts row + cell values in a single transaction.
- Table preview is rendered from persisted data, with slider-controlled visible row count for large datasets.
- Select-row, edit-selected, delete-selected, and add-column button paths are now wired to deterministic handlers.
- Open Short Form Entry now prompts for optional-column inclusion (required columns are always included), and Enter on the final field commits quickly for serial row capture.
- Column management includes optional-column pruning so users can reduce field surface during fast capture sessions.
- Add-column flow now uses a deterministic two-step semantic picker (format group → specific type), so a new "Date" column can be bound to a concrete date/time format family and corresponding input widget at creation time.
- Separator-driven input formatting is now applied for selected semantic types (date/time/timestamp and grouped numbers), so operators can type value characters while the selected format injects visual separators.
- Screen 4 now separates Long Form Entry and Short Form Entry into distinct cards and uses collapsible action/entry sections (similar clutter-reduction pattern used elsewhere in scaffold UI).
- Phase A architecture alignment is underway: Screen 4 is treated as the Long Form host surface while the prior rapid-entry flow is being relabeled as Short Form for permanent-screen promotion.
- Phase B promotion has started: Short Form now has a dedicated `Screen4ShortFormActivity` host surface launched from Screen 4 actions while preserving the existing coordinator/engine/repository pipeline.
- Phase C cleanup is underway: `Screen4Activity` has been reduced to Long Form host responsibilities while legacy short-form modal orchestration remains scoped to `Screen4ShortFormActivity`.
- Phase D resilience is underway: `Screen4ShortFormActivity` now restores transient short-form session state (selected columns, modal state, committed preview history) across recreation.
- Rapid Entry Phase 1 modal scaffolding is now in place with a custom two-column shell (committed preview + new values), append/commit actions, re-select control, and a non-interactive "Measurements Included (Coming Soon)" placeholder.
- Rapid Entry Phase 2 committed-history visualization now renders as a layered in-session card stack (up to five recent commits), with a detailed top card and reduced-detail lower cards.
- Rapid Entry Phase 3 now applies explicit append behavior: validated inputs are staged into committed preview history and rapid fields are cleared for the next capture without persisting a row until `Commit & Next`.
- Rapid Entry Phase 4 hardening now guards commit actions against duplicate taps, shows in-progress commit status, and keeps failure paths recoverable without leaving the modal loop.
- Rapid Entry Phase 5 now renders a dedicated non-interactive "Measurements Included (Coming Soon)" placeholder region (title/body/disabled action) with no persistence or validation contract changes.
