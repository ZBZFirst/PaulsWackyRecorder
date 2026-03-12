# MAP: Screen 3

## Source files
- [activity_screen3.xml](app/src/main/res/layout/activity_screen3.xml)
- [Screen3Activity.kt](app/src/main/java/com/example/templei/Screen3Activity.kt)
- [SoundboardStateMachine.kt](app/src/main/java/com/example/templei/feature/soundboard/SoundboardStateMachine.kt)

## Intersections
- Enter from [[MAP_MainMenu]]
- Navigate laterally with [[MAP_TopNavigation]]
- Parallel page with [[MAP_Screen1]], [[MAP_Screen2]], [[MAP_Screen4]]

## State note
Screen 3 is now a bounded soundboard surface:
- center 3x3 favorite pad for pinned/assigned clips
- bottom horizontal clip browser for active folder clips
- long-press assignment flow from browser to pad
- bounded playback with cooldown + max-stream admission
- lazy loading and cache trimming to limit resource churn

- settings submenu for runtime playback/cache constraints
- cache retention policy modes: Aggressive / Balanced / Sticky
- favorite-slot assignment target + clear-slot controls, with persisted slot mappings
- deterministic diagnostics: clip-load snapshot + last-rejection telemetry in state/status
- phase-1 loading diagnostics: processed/total/playable counters with visible progress bar during catalog scan
- phase-2 favorites decoupled from folder scans: explicit assign/remove flow with persisted slot mappings
- phase-3 layout refactor: framed favorites/browser groups with vertical scrolling and collapse/expand controls
- phase-4 folder clip action list: dynamic buttons support tap-to-play and long-press-to-assign, with per-folder wav/mp3 counts
- phase-5 playback hardening: MediaPlayer SAF URI async playback path, with long-press clear on favorite slots
- phase-5 discovery visibility refinement: separate discovery/validation progress and one-button-per-playable-wav browser rendering
