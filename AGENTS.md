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
`Screen1Activity` is a lightweight shell host.

Do not overload it with unrelated business logic unless a task explicitly targets Screen 1.

### 3.3 Screen2Activity
`Screen2Activity` is a lightweight shell host.

Do not overload it with unrelated business logic unless a task explicitly targets Screen 2.

### 3.4 Screen3Activity
`Screen3Activity` is the Action Pad / Soundboard axis.

Screen 3 contract:
- sound source is a user-selected folder obtained through the system picker,
- navigation is lateral across sibling folders at the selected level,
- supported clip types are `.wav` and `.mp3`,
- clips longer than 6 seconds are non-playable,
- playback is button-press driven only,
- no autoplay behavior may be introduced,
- folder back/forward navigation must remain available,
- the centered label must show the active sample folder name,
- state vocabulary should remain explicit and finite.

Preferred Screen 3 state set:
- `Loading`
- `Ready`
- `Playing`
- `Error`

Do not introduce implicit state transitions.

### 3.5 Screen4Activity
`Screen4Activity` is the structured measurement/table workspace.

Screen 4 contract:
- supports workspace-oriented table management,
- supports row/cell/column operations through defined boundaries,
- validation behavior belongs to engine/repository/validator layers, not ad hoc UI code,
- schema behavior must remain deterministic,
- regex and validation rules must be explicit and inspectable.

Do not collapse Screen 4 into a monolithic activity script.

---

## 4. Screen 4 architecture contract

Agents modifying Screen 4 must preserve the layered boundary below.

### 4.1 UI host boundary
- `Screen4Activity` is the long-form / workspace management host.
- `Screen4ShortFormActivity` is the rapid-entry host.

### 4.2 Command boundary
- activity/UI code routes actions through `Screen4Coordinator`.
- UI code must not bypass coordinator/repository boundaries for persistence logic.

### 4.3 Domain engine boundary
- `Screen4MeasurementEngine` owns active columns, draft state, rapid-entry configuration, and commit composition behavior.

### 4.4 Persistence boundary
- `Screen4Repository` owns Room-backed persistence, workspace lifecycle, commit/update/delete behavior, and persistence-side validation gates.

### 4.5 Type and formatting boundary
- semantic type handling belongs in the Screen 4 type registry / formatter / validator path.
- do not hardcode one-off validation rules in activity classes when a reusable registry or validator path exists.

---

## 5. Change control rules

Agents must prefer bounded edits over broad rewrites.

### Required behavior
- make the smallest coherent change that satisfies the task,
- preserve buildability where possible,
- preserve public behavior unless the task requests behavioral change,
- preserve existing contracts unless updating them is part of the task,
- update associated comments/docs only when observable behavior changes.

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
- input/output contracts,
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

If a new helper/type is required, it must have:
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
- Shared navigation/UI partials should remain shared unless the task requires divergence.

---

## 10. Validation and testing rules

Agents must validate changes to the degree the environment permits.

### Minimum required
- perform static review for contract consistency,
- check imports, references, and likely compile surfaces,
- identify boundary breakage risks.

### When environment permits
- run Gradle checks,
- run compile/build validation,
- report failures precisely.

### When environment does not permit
- state the limitation explicitly,
- still perform code-level validation,
- do not claim runtime verification was completed.

---

## 11. Spreadsheet / schema integration rules

When integrating workbook- or schema-driven behavior into Screen 4:

- treat external schema as a contract source, not ad hoc UI data,
- map schema fields into typed internal definitions first,
- keep parsing, validation, and persistence separated,
- do not couple XLSX parsing logic directly into activity classes,
- prefer import/report flows over silent coercion,
- report mismatches explicitly,
- preserve deterministic validation behavior.

Schema import should enrich existing Screen 4 template/validation systems, not bypass them.

TableManagementScreen.md has more information on Screen 4 and the Table Management System/Engine/State Machine we are trying to build.

---

## 12. Database and persistence rules

For Room-backed changes:

- preserve migration correctness,
- do not change entity meaning without corresponding migration/update work,
- keep repository ownership of persistence,
- avoid direct database access from UI hosts,
- preserve workspace lifecycle semantics.

Any schema change must account for:
- entity compatibility,
- migration path,
- seed/update behavior,
- validator interaction.

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
