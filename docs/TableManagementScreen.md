# Table Management Screen (Screen 4)

## Purpose
`Screen4Activity` is the **Table Management Screen** for typed measurement workspaces. It is the long-form control surface where operators manage table lifecycle, schema, and row visibility.

## Primary source files
- activity_screen4.xml
- Screen4Activity.kt
- Screen4ShortFormActivity.kt
- activity_screen4_short_form.xml

## How this screen is used

### 1) Workspace lifecycle
Operators use the top actions to manage active table context:
- **New Table** creates/selects a workspace and optionally seeds default columns.
- **Open Table** switches active workspace.
- **Archive Active Table** archives current workspace and shifts context safely.
- **Restore Archived** reactivates an archived workspace and selects it.

### 2) Schema + row operations
Operators perform table maintenance from the same host screen:
- **Add Column** for semantic-type-bound columns.
- **Delete Columns** for active-column pruning.
- **Delete Selected Row** after row selection from the preview table.
- **Export CSV** via SAF create-document flow.

### 3) Long Form draft entry
The collapsible Long Form card renders one draft input per active column (3-column grid). Inputs are semantic-type formatted/validated through the Screen 4 registry + validator pipeline.

### 4) Short Form launch
This host screen launches dedicated short-form capture (`Screen4ShortFormActivity`) while both surfaces share the same active workspace/session context.

## Current contract status
- Typed/validated Room-backed table editing is active and deterministic.
- Active workspace continuity is shared across long-form and short-form surfaces.
- Rapid-entry/short-form commit loop is hosted in a dedicated activity.

## Planned Buildout
- The contracts and specifications are still being ironed out and developed by the DEV, please offer suggestions but make sure DEV INTENT is FOLLOWED AFTER BEING CLARIFIED IF UNCLEAR STATEMENTS REGARDING FUNCTION ARE PRESENT.

## Related Document
` PaulsUSDataset.xlsx under the docs folder. This file contains an intro sheet that explains its purpose. The following sheets explain how we can use regex and validation logic in the sheet to verify if sample data is valid for entry.
How to implement this for easier coding is still being discussed.
