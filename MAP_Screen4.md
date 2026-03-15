# MAP: Screen 4

## Source files
- [SCREEN4_TYPED_RAPID_ENTRY_PLAN.md](SCREEN4_TYPED_RAPID_ENTRY_PLAN.md)
- [activity_screen4.xml](app/src/main/res/layout/activity_screen4.xml)
- [Screen4Activity.kt](app/src/main/java/com/example/templei/Screen4Activity.kt)

## Intersections
- Enter from [[MAP_MainMenu]]
- Navigate laterally with [[MAP_TopNavigation]]
- Parallel page with [[MAP_Screen1]], [[MAP_Screen2]], [[MAP_Screen3]]

## State note
Screen 4 now hosts a deterministic serial-measurement workflow with Room persistence:
- Phase 6 foundation is now present: registry includes all 107 semantic type definitions with executable count/uniqueness test coverage.
- Phase 5 foundation is now present: table snapshot can be exported to CSV through SAF create-document flow.
- Phase 4 foundation is now present: XML inputs map from registry widget metadata and show per-field validation feedback.
- Phase 3 foundation is now present: rapid-entry config persists active/auto columns and commit composes base→input→auto row values.
- Phase 2 foundation is now present: a core Screen 4 type registry + validator engine runs prior to row insert/update.
- `Screen4MeasurementEngine` initializes schema + active columns and manages rapid-entry draft state.
- Commits are atomic (`Row` + `Cell` inserts in one transaction) and draft values persist through interruptions.
- The table preview is runtime-rendered from persisted rows/cells with visible-row slider control.
- Table rows are now selectable for edit/delete-selected operations, and add-column is wired through a prompt-driven command path.
- Rapid Entry now opens a multi-select column picker (required columns always enforced) and supports enter-key commit loops.
- Column Management now supports optional-column pruning for field-level streamlining during collection.
- Entry UX now uses explicit Long Form vs Rapid states rendered in separate cards, with collapsible action/entry sections to reduce visual clutter.
- Rapid Entry Phase 1 shell is now a custom modal layout with committed-preview panel, new-value panel, append/commit footer actions, re-select button, and a placeholder for future measurements-included content.
- Rapid Entry Phase 2 now visualizes recent committed context as a layered card stack (max 5 session snapshots) with detailed top-card values and compact lower-card summaries.
- Rapid Entry Phase 3 append semantics now validate-and-stage input into local committed history and clear input fields for the next capture without writing to persistence.
- Rapid Entry Phase 4 now hardens commit-loop behavior with duplicate-tap guards, explicit commit-in-progress status, and resilient error recovery that re-enables rapid controls after failure.
- Rapid Entry Phase 5 now exposes a framed measurements-included placeholder section (non-interactive) to reserve future UI space without changing commit payload or validator behavior.

## Screen 4 persistence surface
- `feature/screen4/Screen4Database.kt`
- `feature/screen4/Screen4Dao.kt`
- `feature/screen4/Screen4Entities.kt`
- `feature/screen4/Screen4Repository.kt`
- `feature/screen4/Screen4MeasurementEngine.kt`
- `feature/screen4/Screen4Coordinator.kt`
- `feature/screen4/Screen4Contracts.kt`
- `feature/screen4/Screen4ColumnTypeRegistry.kt`
- `feature/screen4/Screen4ValidationEngine.kt`
- `feature/screen4/Screen4RapidEntryConfig.kt`
- `feature/screen4/Screen4RapidEntryStore.kt`
- `feature/screen4/Screen4DraftStore.kt`
