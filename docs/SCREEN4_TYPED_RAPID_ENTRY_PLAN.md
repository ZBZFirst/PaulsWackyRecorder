# Screen 4 Implementation Plan: Typed Rapid Entry Data Engine

This plan defines how Screen 4 should evolve into a deterministic, schema-driven data table and rapid-entry engine, independent from Screen 3 behavior.

## Goals

- Keep Screen 4 deterministic: no type inference, no schema inference, no runtime schema mutation.
- Keep semantic typing explicit via a centralized `ColumnTypeRegistry`.
- Use Room as local source-of-truth for schema + row/cell persistence.
- Preserve XML-first Activity scaffold style while isolating logic in `feature/screen4/*`.
- Make the 107 supported semantic types executable through a checklist-driven rollout.

---


## Dependency impact (current `app` module)

Given current dependencies, the plan shifts from *"add core infrastructure libraries"* to *"formalize and leverage what is already available"*:

- **Room is already integrated** (`room-runtime`, `room-ktx`, `kapt room-compiler`).
  - This confirms Screen 4 can immediately use Room as local source-of-truth without adding new DB dependencies first.
  - Priority becomes schema/migration discipline, DAO contracts, and transactional command flows.

- **Coroutines are available** (`kotlinx-coroutines-android`) and **Lifecycle KTX** is available.
  - Coordinator/engine/repository command paths should run with structured coroutine scopes (`lifecycleScope` / suspend repository APIs) to keep UI responsive during validation, row commits, and exports.

- **Compose dependencies are present**, but scaffold guidance remains XML-first.
  - Plan should continue to keep Screen 4 in XML Activity/UI for core workflows.
  - Compose should be optional for incremental subcomponents only; no full-screen migration is required by dependency presence.

- **ConstraintLayout and DocumentFile are available**.
  - ConstraintLayout supports existing deterministic screen shell constraints.
  - DocumentFile enables SAF-based CSV import/export and folder/file handling without additional storage libraries.

### Practical plan adjustments

1. **Phase naming update**: replace any wording like "add Room" with "harden existing Room implementation".
2. **Early migration policy**: define Room schema versioning + migration tests at the beginning of implementation, not late phase.
3. **Repository contract first**: standardize suspend DAO/repository APIs and transaction boundaries before expanding widgets.
4. **Threading contract**: all validation + insert/update/delete/export operations execute off main thread through coroutines.
5. **Compose boundary statement**: keep XML-first for Screen 4; only extract optional Compose widgets when there is a clear reuse gain.


## Design constraints (non-negotiable)

1. Every column must declare exactly one semantic `ColumnType`.
2. Primitive SQL types remain internal implementation details only.
3. Validation is explicit and type-driven before insert/update.
4. UI widget selection is type-driven (registry metadata), not hardcoded per screen form.
5. Schema changes are performed only through explicit developer-defined commands.
6. Screen 4 module boundaries remain independent from Screen 3 soundboard module boundaries.

---

## Proposed Screen 4 architecture

```text
Screen4Activity (UI shell, XML bindings)
    ↓
Screen4Coordinator (command routing, UI-state orchestration)
    ↓
Screen4MeasurementEngine (draft + rapid-entry semantics)
    ↓
TypedTableService
    ├── ColumnTypeRegistry
    ├── ValidationEngine
    ├── RapidEntryEngine
    └── TableSchemaService
    ↓
Screen4Repository (Room-backed)
    ↓
Screen4Dao / Screen4Database / Entities
```

### Responsibilities

- **`Screen4Activity`**
  - Owns view bindings, button listeners, slider updates, dialogs, and toasts/status labels.
  - Delegates all command logic to coordinator/service APIs.

- **`Screen4Coordinator`** (new)
  - Handles action commands (`add row`, `edit row`, `delete`, `select`, `begin rapid`, `commit`, `export`).
  - Translates domain results into UI-friendly statuses.

- **`TypedTableService`** (new, pure domain service)
  - Ensures deterministic schema and typed row operations.
  - Contains no Android UI dependencies.

- **`ColumnTypeRegistry`** (new)
  - Single source-of-truth for all semantic types.
  - Declares: `name`, `category`, `primitiveType`, `validatorKey`, `uiWidget`, `exampleProvider`.

- **`ValidationEngine`** (new)
  - Executes validator by `ColumnType`.
  - Returns structured `ValidationResult` with field-level errors.

- **`RapidEntryEngine`** (new)
  - Applies 3-stage row construction: base → user input → auto values.
  - Supports `ActiveColumns` + `AutoColumns` behavior.

- **`Screen4Repository` / Room layer**
  - Persists templates, active schemas, rows, cells, selection state metadata as needed.
  - Enforces transactionality for row+cell writes.

---

## Data model proposal (Room-aligned)

Keep existing entities where possible and extend deterministically:

- `ColumnTemplateEntity`
  - `templateName`, `columnTypeName`, `nullable`, `required`, `defaultAutoSource?`
- `ColumnEntity`
  - `tableId`, `columnName`, `columnTypeName`, `ordinal`, `nullable`, `isActive`
- `RowEntity`
  - `tableId`, `rowId`, `createdAt`, `createdByMode(manual|rapid)`
- `CellEntity`
  - `rowId`, `columnId`, `valueRaw`, `valueNormalized?`, `validationState`
- `RapidEntryProfileEntity` (new)
  - `tableId`, `activeColumnsJson`, `autoColumnsJson`, `updatedAt`

> If existing entities already cover these semantics, prefer additive fields/migrations over parallel duplicate tables.

---

## Command surface (Screen 4)

Map UI controls to explicit, deterministic command handlers:

- `Add Row` → `createDraftFromSchema(tableId)`
- `Select Row` → `selectRow(rowId)`
- `Edit Row` → `loadRowIntoDraft(rowId)`
- `Delete Row` → `deleteRow(rowId)`
- `Delete Selected Row` → `deleteSelectedRow()`
- `Begin Rapid Entry` → `openRapidEntry(profile)`
- `Commit Measurement` → `validateAndInsert(draft)`
- `Add Column` / `Prune Optional Columns` → explicit schema commands only
- `Visible Row Slider` → display concern only (no storage mutation)

---

## Validation strategy

Implement validator interfaces by semantic intent, then map registry types to those validators.

- `Uuid4Validator`
- `EmailValidator`
- `PhoneValidator`
- `DecimalScaleValidator(scale)`
- `IntegerRangeValidator`
- `TimestampValidator`
- `DatePatternValidator`
- `TimePatternValidator`
- `UriUrlValidator`
- `IpValidator`
- `TextLengthValidator`
- `CurrencyValidator`
- `PercentValidator`

Validation contract:

- On `commit`, validate all active input fields.
- Reject row if any required field fails.
- Return deterministic error payload with column-wise messages.

---

## Phased delivery plan (from-scratch recommendation)

**Recommended total: 6 phases.**

This keeps each step concise enough for agent prompting while still preserving deterministic architecture boundaries.

### Phase status review (current)

| Phase | Status | Review note |
|---|---|---|
| 1 | Completed | Coordinator boundary and typed contracts are in place. |
| 2 | Completed (core) | Registry + validator core enforced before persistence. |
| 3 | Completed (core) | Rapid-entry config persistence and base→input→auto composition implemented. |
| 4 | Completed (core) | XML widget mapping and field-level validation feedback implemented. |
| 5 | Completed (core) | SAF CSV export workflow implemented. |
| 6 | Completed (baseline gate) | 107-type registry baseline + count/uniqueness/alias tests in place. |


### Phase 1 — Bootstrapped foundation (Room + contracts)

- Confirm/initialize Screen 4 Room schema objects and repository boundaries.
- Define typed domain contracts (`ColumnType`, `ColumnDefinition`, `TableSchema`, command/result models).
- Keep activity shell thin and command-routed.

### Phase 2 — Registry + validator core

- Implement `ColumnTypeRegistry` with deterministic metadata shape.
- Implement validator interfaces and core validators (uuid/email/phone/decimal/date/time/timestamp).
- Wire commit path to validator engine before persistence.

### Phase 3 — Rapid-entry engine (base/input/auto)

- Implement `RapidEntryConfig` (`ActiveColumns`, `AutoColumns`) persistence.
- Implement 3-stage row assembly pipeline.
- Support fast repeat commit loop for rapid entry.

### Phase 4 — XML UI behavior wiring

- Bind manual + rapid entry widgets from `uiWidget` mapping metadata.
- Add field-level validation errors and deterministic status messaging.
- Keep XML-first screen structure (Compose optional only for reusable subparts).

### Phase 5 — Table workflow + export

- Complete CRUD/selection/edit/delete flows against typed schema and rows.
- Add visible-row controls and CSV export via SAF/DocumentFile.
- Add migration/versioning coverage for schema evolution.

### Phase 6 — 107-type completion gate

- Execute the full 107-type checklist.
- For each type: registry + validator + widget + examples + tests.
- Mark release readiness only when checklist and tests are complete.

Phase-6 progress update (current):
- Registry baseline now declares all 107 semantic type names with deterministic metadata.
- Automated unit tests now enforce registry count (107), uniqueness, and legacy alias resolution behavior.

---

## Concise agent prompt pack (from-scratch execution)

Use these prompts sequentially to keep context short and deterministic.

1. **Phase 1 prompt**
   - "Implement Screen 4 typed-domain contracts and repository boundaries using existing Room setup. Keep Screen4Activity thin and command-routed. Do not change Screen 3."
2. **Phase 2 prompt**
   - "Add ColumnTypeRegistry + validator engine core for uuid/email/phone/decimal/date/time/timestamp. Enforce validation before row insert/update."
3. **Phase 3 prompt**
   - "Implement RapidEntryConfig with ActiveColumns and AutoColumns, plus base→input→auto row assembly and repeat commit loop."
4. **Phase 4 prompt**
   - "Wire XML inputs from uiWidget mappings with field-level validation feedback for manual and rapid entry. Keep XML-first, no full Compose migration."
5. **Phase 5 prompt**
   - "Complete table CRUD/selection/edit/delete and add CSV export via SAF DocumentFile. Add migration tests for Room schema changes."
6. **Phase 6 prompt**
   - "Work through the 107-type checklist; each completed type must include registry mapping, validator mapping, widget mapping, examples, and tests."

---


## Phase review notes (implementation evidence)

- Phase 1 evidence: `Screen4Coordinator` routes activity command flows and keeps the activity shell thin.
- Phase 2 evidence: repository validation path dispatches through semantic registry + validator engine before writes.
- Phase 3 evidence: rapid-entry config is persisted and hydrated; commit supports base→input→auto assembly.
- Phase 4 evidence: XML input `InputType` selection is driven by registry `uiWidget` metadata and per-field validation feedback is surfaced.
- Phase 5 evidence: Screen 4 action panel includes SAF CSV export using create-document flow.
- Phase 6 evidence: registry contains all 107 semantic type names with automated count/uniqueness/alias tests.

## Testing matrix

- Unit tests
  - Registry coverage test (all declared types load and are unique)
  - Validator tests per type family
  - Rapid-entry row assembly tests
- Integration tests
  - Room transaction tests (`Row` + `Cell` atomic insert)
  - CRUD command flow tests from coordinator to repository
- UI tests (where feasible)
  - Manual entry validation errors
  - Rapid-entry fast commit cycle

Recommended gates:

1. `./gradlew :app:testDebugUnitTest`
2. `./gradlew :app:assembleDebug`
3. `./gradlew :app:lintDebug`

---

## 107 Column Types rollout checklist

Legend:
- `[ ]` not started
- `[x]` completed and validated in tests

For each type, complete all 5 checks:
1. Registry entry added
2. Validator mapping defined
3. UI widget mapping defined
4. Example(s) linked (fakerUSDataset-based)
5. Tests added

### Identity / Person

- [ ] uuid4
- [ ] name
- [ ] first_name
- [ ] last_name
- [ ] prefix
- [ ] suffix
- [ ] user_name
- [ ] job
- [ ] company
- [ ] company_suffix

### Contact / Address

- [ ] email
- [ ] ascii_email
- [ ] company_email
- [ ] phone_number
- [ ] basic_phone_number
- [ ] address
- [ ] street_address
- [ ] street_name
- [ ] building_number
- [ ] city
- [ ] city_prefix
- [ ] city_suffix
- [ ] state
- [ ] state_abbr
- [ ] country
- [ ] country_code
- [ ] zipcode
- [ ] postalcode

### Internet / Network

- [ ] domain_name
- [ ] domain_word
- [ ] url
- [ ] uri
- [ ] uri_path
- [ ] uri_page
- [ ] uri_extension
- [ ] ipv4
- [ ] ipv6
- [ ] mac_address
- [ ] hostname
- [ ] user_agent

### Financial

- [ ] credit_card_number
- [ ] credit_card_provider
- [ ] credit_card_expire
- [ ] credit_card_security_code
- [ ] currency_code
- [ ] currency_name
- [ ] currency_symbol

### Geographic

- [ ] latitude
- [ ] longitude
- [ ] coordinate

### Text / Language

- [ ] word
- [ ] words
- [ ] sentence
- [ ] paragraph
- [ ] slug
- [ ] text

### File / Media

- [ ] mime_type
- [ ] file_name
- [ ] file_extension

### Color

- [ ] color_name
- [ ] hex_color

### Date Formats

- [ ] date_mdy_slash_yyyy
- [ ] date_mdy_dash_yyyy
- [ ] date_mdy_slash_yy
- [ ] date_mdy_dash_yy

### Timestamp Formats

- [ ] timestamp_mdy_slash_minute
- [ ] timestamp_mdy_dash_minute
- [ ] timestamp_mdy_slash_second
- [ ] timestamp_mdy_dash_second
- [ ] timestamp_mdy_slash_millisecond
- [ ] timestamp_mdy_dash_millisecond

### Time Formats

- [ ] time_hh_mm
- [ ] time_hh_mm_ss
- [ ] time_hh_mm_ss_mmm
- [ ] time_hh_mm_am_pm
- [ ] time_hh_mm_ss_am_pm
- [ ] time_hh_mm_ss_mmm_am_pm

### Unix Time

- [ ] timestamp_unix_s
- [ ] timestamp_unix_ms

### Date Components

- [ ] year_yyyy
- [ ] year_yy
- [ ] month_mm
- [ ] day_dd
- [ ] day_of_week

### Numeric Formats

- [ ] decimal_1
- [ ] decimal_2
- [ ] decimal_3
- [ ] decimal_grouped_2
- [ ] ones
- [ ] tens
- [ ] hundreds
- [ ] thousands
- [ ] ten_thousands
- [ ] hundred_thousands
- [ ] millions
- [ ] number_plain
- [ ] number_grouped
- [ ] number_scientific
- [ ] currency_usd
- [ ] currency_usd_plain
- [ ] percent
- [ ] percent_decimal

### Formatting Tokens

- [ ] date_separator_slash
- [ ] date_separator_dash
- [ ] grouping_separator
- [ ] decimal_separator
- [ ] fraction_separator

---

## Registry acceptance criteria

The typed rapid-entry system is considered implementation-ready when:

- Registry contains all 107 types with complete metadata.
- Every type maps to validator + UI widget deterministically.
- All validator mappings are covered by unit tests.
- Screen 4 rapid-entry commit path rejects invalid rows and inserts valid rows transactionally.
- Checklist in this document is fully checked.

