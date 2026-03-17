# Workbook Validation Overview

**File:** `PaulsUSDataset.xlsx`

## Reality of the Situation

We spent a lot of time figuring out all the specifics of how we would validate user input data and also constrain it to prevent erroneous input from even occurring. We made a regex check to make sure values are allowed to be put in, then
we made a validation loop to see if the values are allowed_values to be used in the dataset. Almost as a precursor to a drop down selection for certain things like DAY OF THE WEEK.

Our logic in the excel file works but now we need to import this logic for validation and regex checking into the app.

## Purpose of the Workbook

The workbook `PaulsUSDataset.xlsx` operates as a layered validation system in which each sheet has a specific responsibility. The sheets work together to evaluate whether each cell in the dataset matches the rules assigned to its column. The core idea is that the raw data does not validate itself. Instead, the workbook uses metadata, reusable regex patterns, and validation formulas to decide what each value is supposed to look like and whether it passes.

## Raw Dataset Sheet

The process begins with the raw dataset sheet, such as `fakerUSDataset`. This sheet contains the actual records and column headers. Each column represents a field such as email, phone number, state abbreviation, postal code, or date. By itself, this sheet is only the source of values. It does not define the rules. Its role is simply to provide the input values that will be checked.

## Column Metadata Sheet

The next major sheet is `ColumnMetaData`, which acts as the schema definition for the workbook. This sheet explains how each column should be interpreted. For each column name, it can define the expected type, whether the field is required, which regex pattern applies, whether normalization is needed, and whether there is a fixed set of allowed values. This makes validation column-aware. The logic is not hardcoded for specific spreadsheet positions; instead, the formulas can look up the current column header and then retrieve that column’s rules from metadata.

## Regex Pattern Library

The `RegexPatterns` sheet stores reusable named regex definitions. Instead of writing a full regex expression repeatedly in many places, the workbook can assign a pattern name to a column in `ColumnMetaData`, then retrieve the actual regex from `RegexPatterns`. This creates consistency. For example, multiple columns might reuse patterns for email format, IPv4 format, hexadecimal color codes, or U.S. state abbreviations. This sheet functions as the workbook’s pattern library.

## Validation Rules and Normalization

The `ValidationRules` sheet complements regex matching by describing additional cleanup or normalization behavior. A value may need to be trimmed, uppercased, or stripped of punctuation before evaluation. This matters because a cell can be logically correct but formatted inconsistently. Normalization allows the workbook to clean the value first, then validate the normalized result. That keeps validation stricter in logic while remaining tolerant of superficial formatting noise.

## Test Matrix Stage One

The actual checking occurs in sheets such as `TestMatrix` and `TestMatrixStep2`. In the first stage, `TestMatrix` mirrors the raw dataset structure and applies formulas cell by cell. The formula determines the current column, looks up that column in `ColumnMetaData`, finds the assigned regex or structural rule, optionally applies normalization, and then tests whether the cell matches the required pattern. This is the regex or structural validation phase.

## Test Matrix Stage Two

In the second stage, `TestMatrixStep2` performs allowed-values validation after the first stage passes. Here the value is compared against the explicit permitted list for that column, such as a list of valid state abbreviations or other constrained domain values. This separates structural correctness from semantic correctness. A value may match a regex but still not belong to the allowed set.

## Overall Validation Flow

Together, the sheets in `PaulsUSDataset.xlsx` form a declarative validation engine. Raw values come from the dataset, rules come from metadata, patterns come from the regex library, normalization comes from validation rules, and results are reported in the test matrices. This allows the workbook to validate data systematically based on the definition and constraints of each column rather than relying on isolated, hardcoded checks.
