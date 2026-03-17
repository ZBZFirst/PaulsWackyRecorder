# Codebase Comparison to Docs (3-16-26 Review)

## Scope Reviewed

- `docs/TableManagementScreen.md`
- `docs/PaulsUSDatasetExplained.md`
- App surfaces tied to those docs:
  - Main shell/navigation (`MainActivity`, `activity_main.xml`, `view_top_navigation.xml`)
  - Screen 4 hosts + Screen 4 feature modules
  - Screen 3 state contract (cross-screen consistency check)

---

## Executive Summary

The codebase is aligned with the current high-level architecture docs for Screen 4 and app navigation contracts. The largest remaining gap is workbook-driven validation integration, where documentation describes a contract/target model and code currently uses an internal registry-driven pipeline.

---

## Detailed Comparison

### 1) Screen 4 host model and activity boundaries

**Documented**

`TableManagementScreen.md` defines:

- `Screen4Activity` as long-form/workspace host,
- `Screen4ShortFormActivity` as rapid-entry host,
- shared workspace/session context between both.

**Codebase**

This is reflected in app structure and role boundaries.

**Status**

✅ Aligned at architecture level.

---

### 2) Screen 4 command/domain/persistence layering

**Documented**

Expected layering:

- UI routes through coordinator,
- measurement behavior in engine,
- persistence in repository/Room.

**Codebase**

Current implementation follows this separation.

**Status**

✅ Aligned with layered boundaries.

---

### 3) Workspace lifecycle and table operations

**Documented**

Expected capabilities include workspace lifecycle controls, schema operations, row operations, and export behavior.

**Codebase**

These are implemented on Screen 4 surfaces and coordinated through Screen 4 feature layers.

**Status**

✅ Aligned for listed lifecycle/operation flows.

---

### 4) Main menu and shared navigation assumptions

**Documented/Contract expectation**

Main menu remains the navigation hub, with shared top navigation partial across screens.

**Codebase**

`MainActivity` and the shared XML surfaces preserve this behavior.

**Status**

✅ Aligned with repository invariants.

---

### 5) Workbook-driven validation narrative vs implemented validation

**Documented**

`PaulsUSDatasetExplained.md` describes workbook-centered regex + allowed-values orchestration.

**Codebase**

Current validation is app-native and registry-driven, without direct runtime import of workbook sheets.

**Status**

⚠️ Partially aligned conceptually; direct workbook ingestion remains planned.

---

## Recommended Next Actions

1. Keep workbook docs explicit that sheet-driven import is planned work.
2. Add a dedicated import contract doc if workbook parsing is prioritized next:
   - parsing boundary,
   - mapping from sheet concepts to internal typed rules,
   - validation stage ordering,
   - error/report semantics.

---

## Final Assessment

- Screen 4 architecture docs and current implementation are structurally aligned.
- Main navigation contract remains consistent.
- Workbook validation docs are accurate as contract intent, with direct integration still pending.
