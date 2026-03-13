# MAP: Screen 4

## Source files
- [activity_screen4.xml](app/src/main/res/layout/activity_screen4.xml)
- [Screen4Activity.kt](app/src/main/java/com/example/templei/Screen4Activity.kt)

## Intersections
- Enter from [[MAP_MainMenu]]
- Navigate laterally with [[MAP_TopNavigation]]
- Parallel page with [[MAP_Screen1]], [[MAP_Screen2]], [[MAP_Screen3]]

## State note
Screen 4 now hosts a deterministic serial-measurement workflow with Room persistence:
- `Screen4MeasurementEngine` initializes schema + active columns and manages rapid-entry draft state.
- Commits are atomic (`Row` + `Cell` inserts in one transaction) and draft values persist through interruptions.
- The table preview is runtime-rendered from persisted rows/cells with visible-row slider control.

## Screen 4 persistence surface
- `feature/screen4/Screen4Database.kt`
- `feature/screen4/Screen4Dao.kt`
- `feature/screen4/Screen4Entities.kt`
- `feature/screen4/Screen4Repository.kt`
- `feature/screen4/Screen4MeasurementEngine.kt`
- `feature/screen4/Screen4DraftStore.kt`
