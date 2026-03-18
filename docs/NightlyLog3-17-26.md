# Nightly Log (3-17-26)

#03-17-2026

## End of Night Notes

Add this to tomorrow's work list for how we will edit and use the XLSX file in the app project.

## Reworded Clarification

Required should not mean the user must populate every column. The correct distinction is whether a column may be left empty/null while still allowing users to enter only the fields they want.

## Correct Interpretation

`required` is the wrong term if it implies universal user input. The model should use a presence/nullability contract.

The engine should distinguish:

- omitted: user did not provide anything for this field
- blank: user provided empty/whitespace input
- explicit null-equivalent: value normalizes to null
- non-null value: field contains a usable canonical value

The schema should center on:

- nullable/non-nullable
- blank allowed/not allowed
- omission allowed/not allowed
- blank normalization behavior
- export behavior when values are absent

## Replace `required` with Explicit Policies

1. `allow_omission`
   - Can the field be fully absent from user entry?

2. `allow_null`
   - After normalization/parsing, may the field resolve to null?

3. `allow_blank_input`
   - May raw input be empty string or whitespace?

4. `blank_normalization_policy`
   - If raw input is blank, what should happen?
   - Suggested enum values:
     - `KEEP_BLANK`
     - `NORMALIZE_TO_NULL`
     - `REJECT_BLANK`

5. `export_null_policy`
   - When canonical value is null, what should export do?
   - Suggested enum values:
     - `EXPORT_EMPTY_FIELD`
     - `EXPORT_LITERAL_NULL`
     - `OMIT_FIELD_IF_FORMAT_SUPPORTS_IT`
     - `REJECT_RECORD`

## Why This Is Better Than `required`

`required` asks a single question: “Must the user provide this field?”

The actual workflow needs multiple explicit checks:

- If omitted, is omission allowed?
- If blank/garbage, can it normalize to null?
- If null after normalization, is the record still valid?

## Recommended Semantic Model

### Presence State Machine

Raw states:

- `OMITTED`
- `BLANK`
- `RAW_VALUE`

Normalized states:

- `NULL`
- `CANONICAL_VALUE`
- `INVALID`

Policy checks:

- `OMITTED` + `allow_omission = true` -> pass
- `OMITTED` + `allow_omission = false` -> fail
- `BLANK` + `allow_blank_input = true` + `blank_normalization_policy = NORMALIZE_TO_NULL` -> continue as `NULL`
- `NULL` + `allow_null = true` -> pass
- `NULL` + `allow_null = false` -> fail
- `CANONICAL_VALUE` -> continue with type/regex/enum checks

This model is more precise than using a single `required` flag.
