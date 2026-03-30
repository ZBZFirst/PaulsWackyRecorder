# Workbook Validation Reference

**File:** `Paulsdataset.xlsx`

## Current Status

This workbook is legacy reference material.

- it is not part of the main operator flow,
- it is not the active Screen 4 product contract,
- normal app use does not require importing or editing this workbook,
- Screen 4 currently operates as a music sequencer, not a table-management workspace.

The workbook remains useful as a record of earlier validation and schema experiments that may still inform future import tooling or data-model work.

## Purpose of the Workbook

`Paulsdataset.xlsx` describes a layered validation model where different sheets own different parts of a data-contract experiment.

The workbook’s central idea is that raw values do not validate themselves. Metadata, reusable regex patterns, normalization rules, and validation formulas work together to describe what a value should look like and whether it passes.

## Sheet Reference

### Intro

Contains onboarding guidance for how the workbook was expected to be ingested and interpreted.

### RowMetaDatat

Describes the meaning of row positions and row usage within the sample dataset.

### ColumnMetaData

Acts as the schema definition sheet. It describes column identity, display naming, data type, regex association, min and max hints, allowed values, normalization hints, and UI input hints.

### SampleDataset

Contains generated sample data intended to exercise the metadata and validation ideas described by the workbook.

### RegexPatterns

Stores reusable named regex definitions so multiple fields can reference shared patterns instead of copying raw expressions everywhere.

### NormalizationRules

Describes cleanup and normalization operations that can be applied before validation, such as trimming, replacement, or case adjustments.

## Integration Guidance

If workbook-driven behavior is revisited later:

- treat the workbook as an explicit import-contract source,
- keep parsing, validation, and persistence separated,
- do not let workbook concepts silently redefine the current Screen 2, Screen 3, or Screen 4 music workflow,
- reintroduce workbook behavior behind a deliberate feature boundary instead of by assumption.
