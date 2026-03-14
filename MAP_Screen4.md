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
- Entry UX now uses explicit Manual vs Rapid states rendered in separate cards, with collapsible action/entry sections to reduce visual clutter.

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
