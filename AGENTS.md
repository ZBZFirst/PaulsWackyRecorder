# AGENTS.md

Repository operating contract for automated and semi-automated agents.

## 1. Scope

This contract applies to the entire repository rooted at `/workspace/PaulsWackyRecorder`.

Agents must treat this file as the default execution policy unless a user task explicitly overrides a rule.

---

## 2. Repository invariants

Agents must preserve the following repository-level invariants:

- Package namespace remains `com.example.templei`.
- `MainActivity` remains the launcher and routing entry point.
- `activity_main.xml` remains the primary menu surface for navigation to Screens 1-4.
- `view_top_navigation.xml` remains the shared cross-screen navigation partial.
- Screen shells remain reproducible and separable.
- Compose under `ui/*` is incremental only. Do not force full migration from XML unless explicitly requested.
- UI work must not silently change existing screen roles.
- The primary operator loop remains: `MainActivity` setup, `Screen2Activity` WAV capture, `Screen3Activity` favorites-pad assignment, `Screen4Activity` sequencing, then `Screen1Activity` camera capture while playback continues.

Do not rename packages, activities, files, ids, database entities, or routing surfaces unless the task explicitly requires it.

---

## 3. Screen role contract

### 3.1 MainActivity
`MainActivity` is the launcher, menu host, and device-readiness console.

It may expose:
- permission status,
- hardware/device capability status,
- feature readiness gates,
- navigation to Screens 1-4.

Do not convert `MainActivity` into a feature-specific workflow screen.

### 3.2 Screen1Activity
`Screen1Activity` is the camera preview and capture surface used after sequencing starts.

Screen 1 contract:
- camera feed start or stop remains explicit,
- photo and video storage configuration stays local to Screen 1 media capture,
- it may capture reactions while Screen 4 playback continues in the background,
- it must not become the primary audio recording or sequencing editor.

Do not overload it with unrelated business logic unless a task explicitly targets Screen 1.

### 3.3 Screen2Activity
`Screen2Activity` is the bounded WAV recording surface.

Screen 2 contract:
- recorded output is `.wav`,
- clip capture depends on an explicitly selected target folder,
- file naming and save behavior stay explicit before recording begins,
- save completion is the handoff point into the Screen 3 sound library flow,
- microphone permission and recording status remain visible and bounded.

Do not overload it with unrelated business logic unless a task explicitly targets Screen 2.

### 3.4 Screen3Activity
`Screen3Activity` is the Action Pad / Soundboard axis and the source of truth for favorites-pad assignment.

Screen 3 contract:
- sound source is a user-selected folder obtained through the system picker,
- navigation is lateral across sibling folders at the selected level,
- supported clip types are `.wav` and `.mp3`,
- clips longer than 6 seconds are non-playable,
- playback is button-press driven only,
- no autoplay behavior may be introduced,
- folder back/forward navigation must remain available,
- the centered label must show the active sample folder name,
- favorite pages and favorite-pad assignments remain explicit,
- Screen 4 consumes the shared favorite-pad state rather than redefining it locally,
- state vocabulary should remain explicit and finite.

Preferred Screen 3 state set:
- `Loading`
- `Ready`
- `Playing`
- `Error`

Do not introduce implicit state transitions.

### 3.5 Screen4Activity
`Screen4Activity` is the multi-bar WAV loop sequencer.

Screen 4 contract:
- the sequencer is built from favorite-pad assignments shared from Screen 3,
- playback transport remains explicit through user play and stop actions,
- song construction uses play bars, steps, and favorite references rather than ad hoc clip launching,
- BPM, compile status, runtime status, and error state remain inspectable,
- save and load behavior belongs to sequencer stores and coordinator paths,
- transport continuity across navigation is intentional so the user can move to Screen 1 while the loop keeps playing,
- no hidden autoplay or implicit reassignment behavior may be introduced on entry or sync.

Do not collapse Screen 4 into a monolithic activity script.

---

## 4. Screen 4 architecture contract

Agents modifying Screen 4 must preserve the layered boundary below.

### 4.1 UI host boundary
- `Screen4Activity` is the active sequencer host.
- `Screen4ShortFormActivity` and `Screen4LongFormActivity` are legacy redirect shims that forward into `Screen4Activity`.

### 4.2 Runtime boundary
- `Screen4MusicRuntime` owns the process-level Screen 4 coordinator lifecycle.
- transport continuity across screen navigation must remain outside any one activity instance.

### 4.3 Sequencing boundary
- activity and UI code route sequencer actions through `Screen4Coordinator`.
- `Screen4Coordinator` owns favorite-page synchronization, play-bar state, selection state, compile composition, and transport-facing UI state.

### 4.4 Sample and shared-state boundary
- `Screen4SampleLibraryRepository` resolves sample metadata derived from the Screen 3 library/index path.
- `Screen3SettingsStore` remains the source of truth for shared favorite-page assignments.
- Screen 4 must not silently fork favorite-pad state away from Screen 3.

### 4.5 Playback and persistence boundary
- `Screen4SchedulerEngine` and `Screen4SamplePlaybackEngine` own timed playback behavior.
- `Screen4SequenceStore` owns sequencer working-state persistence.
- UI hosts must not embed timing logic or persistence logic that belongs in runtime, store, or engine layers.

### 4.6 Legacy module boundary
- historical table-measurement modules may still exist under `feature/screen4`.
- they are not the active Screen 4 product contract and must not be revived or expanded unless the task explicitly calls for it.

---

## 5. Change control rules

Agents must prefer bounded edits over broad rewrites.

### Required behavior
- make the smallest coherent change that satisfies the task,
- preserve buildability where possible,
- preserve public behavior unless the task requests behavioral change,
- preserve existing contracts unless updating them is part of the task,
- update associated comments or docs only when observable behavior changes.

### Disallowed behavior
- silent architectural rewrites,
- opportunistic renaming,
- package migration without request,
- moving logic across layers without explanation,
- replacing reusable logic with duplicated local logic,
- adding placeholder abstractions with no integration path.

---

## 6. Definitions before coding

For feature work, agents must establish and use explicit definitions before implementation.

Prefer defining:
- state models,
- enums,
- indexed mappings,
- invariants,
- input and output contracts,
- validation stages,
- persistence ownership.

When a workflow has multiple modes, define the mode set explicitly before branching behavior in code.

When validation has multiple outcomes, define a finite result model rather than scattering booleans.

---

## 7. Naming and variable discipline

Do not introduce new variables, helpers, or renamed identifiers casually.

Rules:
- preserve existing names unless change is required,
- prefer extending an existing concept over inventing near-duplicates,
- keep helper creation minimal and purpose-bound,
- avoid synonyms that fragment the architecture.

If a new helper or type is required, it must have:
- a single clear responsibility,
- a stable call site,
- a reason it cannot be expressed cleanly in the current structure.

---

## 8. Documentation and commentary contract

Comments must increase semantic clarity.

### Required
- file-level role declaration when introducing new source files,
- brief contract comments for externally meaningful components,
- comments for non-obvious invariants, constraints, or edge-case behavior.

### Disallowed
- narrating obvious syntax,
- debug-history comments,
- speculative comments not enforced by code,
- stale TODO prose,
- verbose commentary compensating for poor structure.

Documentation must describe observable behavior and responsibility boundaries, not line-by-line mechanics.

---

## 9. UI and resource rules

- Prefer `strings.xml` for user-facing strings. Avoid using grammar characters.
- Avoid hardcoded UI text when practical.
- Preserve existing resource naming conventions.
- Do not introduce quote-heavy UI strings unnecessarily.
- Shared navigation or UI partials should remain shared unless the task requires divergence.

---

## 10. Validation and testing rules

Agents must validate changes to the degree the environment permits.

### Minimum required
- perform static review for contract consistency,
- check imports, references, and likely compile surfaces,
- identify boundary breakage risks.

### When environment permits
- run Gradle checks,
- run compile and build validation,
- report failures precisely.

### When environment does not permit
- state the limitation explicitly,
- still perform code-level validation,
- do not claim runtime verification was completed.

---

## 11. Workbook and schema reference rules

Workbook and schema assets under `docs/` are reference material, not the active Screen 4 runtime contract.

When integrating workbook- or schema-driven behavior:

- treat external schema as an explicit import or analysis source, not ad hoc UI data,
- keep parsing, validation, and persistence separated,
- do not couple XLSX parsing logic directly into `MainActivity`, `Screen2Activity`, `Screen3Activity`, or the live `Screen4Activity` sequencer flow,
- prefer import and report flows over silent coercion,
- report mismatches explicitly,
- preserve deterministic validation behavior,
- do not let legacy workbook concepts silently redefine the current Screen 4 music-sequencer contract.

`docs/Screen4MusicSequencer.md` and `docs/PaulsDatasetExplained.md` contain the current documentation posture for the live Screen 4 flow and legacy workbook references.

---

## 12. Database and persistence rules

For Room-backed or store-backed changes:

- preserve migration correctness,
- do not change entity meaning without corresponding migration or update work,
- keep repository or store ownership of persistence,
- avoid direct database or store access from UI hosts,
- preserve sequencer working-state semantics,
- preserve shared favorite-pad synchronization semantics.

Any schema or persistence change must account for:
- entity compatibility,
- migration path,
- seed or update behavior,
- validator interaction,
- saved-sequence compatibility,
- shared favorites compatibility.

---

## 13. Documentation alignment rules

`docs/README.md` is human-facing.  
`AGENTS.md` is agent-facing.

When scaffold, architecture, or operating constraints change, keep both aligned.

Do not update one while knowingly leaving the other contradictory.

---

## 14. Execution style

Agents should operate with these defaults:

- incremental edits,
- explicit boundaries,
- deterministic behavior,
- minimal surface-area changes,
- low-noise comments,
- no speculative rewrites,
- no hidden contract drift.

When uncertain, preserve structure and expose the uncertainty in the output rather than improvising architecture.
