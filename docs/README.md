# Docs Index and Consistency Notes

This folder contains the primary human-facing documentation for the current app architecture and in-progress validation model. The 'Paulsdataset.xlsx' is the current revised document.

## Documents

- `TableManagementScreen.md`
  - Defines the purpose and current role of Screen 4 (`Screen4Activity` + `Screen4ShortFormActivity`).
  - Describes workspace lifecycle actions, table operations, and long-form/short-form responsibilities.
- `PaulsDatasetExplained.md`
  - Explains workbook-driven validation concepts in `PaulsDataset.xlsx`.
  - Clarifies that workbook sheet logic is still a planning/contract source and not yet imported as a runtime app pipeline.
- `CodebaseComparison3-16-26.md`
  - Snapshot review comparing documentation claims to implemented behavior.
  - Focuses on Screen 4 boundaries, shared navigation surfaces, and workbook-validation integration status.
- `PaulsUSDataset.xlsx`
  - Spreadsheet contract source for schema, regex, and validation experiments.

## Consistency Summary

- Screen 4 boundary docs and current code are aligned at the coordinator/engine/repository layering level.
- Main navigation role assumptions remain aligned with the app shell contracts.
- Workbook validation docs represent target integration intent; direct runtime import from workbook sheets remains planned, not complete.

## Maintenance Rule

When architecture or workflow contracts change, update this docs index and the impacted detailed document in the same change so doc state stays coherent.
