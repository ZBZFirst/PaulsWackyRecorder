<img width="237" height="21" alt="image" src="https://github.com/user-attachments/assets/c2597466-5f42-4a95-aa8c-1024c0f08089" /># CODEBASE COMPARISON TO DOCS - NIGHTLY REVIEW 3-16

## Scope Reviewed

- `docs/TableManagementScreen.md`
- `docs/PaulsUSDatasetExplained.md`
- Key app surfaces tied to those docs:
  - Main menu/navigation shell (`MainActivity`, `activity_main.xml`, `view_top_navigation.xml`)
  - Screen 4 host + short form + Screen 4 feature modules
  - Screen 3 state contract (for cross-screen contract consistency)

---

## Executive Summary

The codebase is generally aligned with the **high-level Screen 4 architecture** described in `TableManagementScreen.md` (long-form host, short-form host, coordinator/engine/repository layering, workspace lifecycle actions). However, there are still important documentation mismatches and incomplete integrations:

1. **Workbook (`PaulsUSDataset.xlsx`) integration is documented conceptually but not implemented as a direct import/contract pipeline yet.**
2. **`TableManagementScreen.md` still contains placeholder wiki-style links (`[[MAP_*]]`) and planning language that does not map to real in-repo docs/pages.**
3. **The docs describe spreadsheet-driven regex/allowed-values orchestration, while the app currently uses an internal type registry + validation engine path.**

---

## Detailed Comparison

## 1) Screen 4 host model and activity boundaries

### Documented
`TableManagementScreen.md` defines:
- `Screen4Activity` as long-form/workspace host
- `Screen4ShortFormActivity` as rapid-entry host
- Shared workspace/session context between both

### Codebase
This is implemented and visible in code structure:
- `Screen4Activity` hosts workspace lifecycle actions, table controls, and long-form input grid.
- `Screen4ShortFormActivity` hosts dedicated rapid-entry UX and column selection.
- Both initialize through `Screen4Coordinator -> Screen4MeasurementEngine -> Screen4Repository`.

### Status
✅ **Aligned** at architecture level.

---

## 2) Screen 4 command/domain/persistence layering

### Documented
The docs (and agent contract) expect:
- UI routes through coordinator
- measurement behavior in engine
- persistence in repository/Room

### Codebase
Current implementation follows this separation:
- Activity code uses `Screen4Coordinator` APIs for workflow operations.
- `Screen4MeasurementEngine` owns draft/config/commit composition behaviors.
- `Screen4Repository` and Room entities/DAO/database own persistence.

### Status
✅ **Aligned** with layered boundaries.

---

## 3) Workspace lifecycle and table operations

### Documented and Planned
`TableManagementScreen.md` expects support for:
- New/Open/Close table
- Add/Delete column(s)
- Add/Delete row(s)
- Change/Delete/Blank selected cell value
- Export a single table as a CSV or xlsx file or a group of tables as a xlsx
- Assign a Column type from the file "PaulsUSDataset.xlsx", under the 'ColumnMetaData' Sheet.
- Use .xlsx file to assign column meta data and constraints for usage in the app.

### Codebase
These actions are present in `Screen4Activity` and delegated via coordinator.

### Status
✅ **Aligned** for listed lifecycle/operation flows.

---

## 4) Main menu and shared navigation assumptions

### Documented/Contract expectation
Main menu remains navigation hub, with shared top navigation partial across screens.

### Codebase
- `MainActivity` is still launcher + readiness/status console + routing entry.
- `activity_main.xml` remains the menu surface with buttons for Screens 1-4.
- `view_top_navigation.xml` exists as shared navigation partial.

### Status
✅ **Aligned** with repository invariants.

---

## 5) Spreadsheet-driven validation narrative vs implemented validation system

### Documented
`PaulsUSDatasetExplained.md` describes a workbook-centered validation flow using:
- `ColumnMetaData`
- `RegexPatterns`
- `ValidationRules`
- `TestMatrix`/`TestMatrixStep2`

### Codebase
Current app validation is app-native and registry-driven:
- Semantic type catalog + format groups in Screen 4 modules.
- Validation routed through `Screen4ValidationEngine` and type registry definitions.
- No explicit parser/import pipeline from `PaulsUSDataset.xlsx` sheets into runtime rules is currently evident.

### Status
⚠️ **Partially aligned conceptually, not yet aligned implementation-wise.**
The docs describe a target model; code currently implements a local equivalent path rather than direct workbook contract ingestion.

---


## Recommended Next Documentation Actions

1. Add a “Current Implementation vs Planned Integration” section to `docs/PaulsUSDatasetExplained.md` clarifying that workbook-sheet import is not yet fully wired.
2. If workbook integration is intended next, add a short contract doc specifying:
   - parsing boundary,
   - mapping from sheet concepts to internal types,
   - validation stage ordering,
   - error/report format expectations.

---

## Final Assessment

- **Screen 4 architecture docs are directionally accurate** and mostly match current implementation boundaries.
- **Workbook validation docs are best read as target architecture guidance**, not a fully realized implementation snapshot.
- **Biggest delta is documentation precision**, not foundational code structure.
