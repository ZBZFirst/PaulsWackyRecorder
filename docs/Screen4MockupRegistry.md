# Screen 4 Mockup Registry

Screen 4 sequencer mockups can be generated from a reusable SVG symbol registry instead of hand-redrawing repeated strips, slots, and docks.

## Role

This document defines the visual contract for registry-generated Screen 4 mockups under `docs/ui-mockups/`.

The registry is design-side only.
It does not change Screen 4 runtime behavior.

## Goals

- keep Screen 4 mockups highly iterable
- keep exported SVGs self-contained
- preserve the current Screen 4 visual language
- make layout invariants inspectable instead of implicit

## Invariants

These rules remain fixed across registry-driven mockups.

1. All repeated units come from registry symbols, not manual redraws.
2. Expanded pads inherit a visual relationship to their source strip through color family match, notch alignment, and anchor stem alignment.
3. Step slots remain the dominant interaction zone.
4. Selection state uses a complementary accent color, not the same color as assignment.
5. The FX dock remains visible but secondary to the step grid.
6. The BPM slider belongs inside transport, directly beneath the BPM display block.

## Registry Model

The SVG registry should be organized around explicit component roles.

- `transport shell`: header surface for transport, status, and BPM controls
- `bpm display block`: compact BPM readout that visually owns the slider beneath it
- `channel strip`: collapsed bar selector at roster level
- `anchor stem`: visual connector from strip to expanded editor
- `expanded lane shell`: the active editing workspace for one bar
- `dock shell`: shared dock container for assignment, step grid, and FX areas
- `step slot`: the primary interaction unit for sequence editing
- `fx rail`: compact secondary controls for shaping the active lane
- `action chip`: compact command or mode affordance inside docks

## State Vocabulary

Mockup states should use a finite visual vocabulary.

- `neutral`: inactive and unselected
- `assigned`: a slot or strip carries assignment color
- `selected`: a slot or strip uses the complementary accent
- `expanded`: the active strip is connected to its lane workspace
- `fx-secondary`: FX is present but visually subordinate

## Color Roles

Color meaning should stay consistent across variants.

- green family: active assignment lane
- pink family: alternate assignment lane
- blue family: neutral support surfaces
- amber family: selection accent only

## Export Guidance

Exported mockups should remain easy to move between tools.

- keep each export as a standalone SVG
- inline the symbol registry in exported files when portability matters
- assemble repeated units with `<use>` references
- keep labels editable text rather than converting them to paths

## Prototype Asset

The current proof-of-concept lives at:

- `docs/ui-mockups/screen4-music-component-registry-prototype.svg`

That file includes:

- a symbol catalog
- an assignment-focused generated view
- an FX-visible generated view

Both generated views reuse the same internal symbol registry so the design can iterate by swapping positions, states, and labels instead of redrawing surfaces.
