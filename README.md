# Android App Scaffold (Agent + Human Iteration Template)

This repository is a **default Android scaffold** for fast, reproducible iteration by both:
- **Humans** (reading docs and making design decisions)
- **Agents** (following operational instructions and automating routine work)

## Purpose

The goal is to remove unnecessary app-design overhead so builders can focus on domain problems.

> Knowledge in one area can block progress in another. Reusable scaffolds and automation reduce that friction.

This project provides a stable baseline that can evolve over time without losing clarity.

## Default app structure

The baseline UI/flow is intentionally simple and reproducible:

1. **Launcher menu** (`activity_main.xml`) routes to activity pages.
2. **Top navigation bar** (`view_top_navigation.xml`) supports movement across pages.
3. **Page-oriented activities** (`Screen1Activity` ... `Screen4Activity`) represent parallel contexts.
4. Pages can be duplicated and adapted to new contexts while preserving structure.

## Human + agent documentation model

This repo uses two complementary docs:

- `README.md` → human-facing explanation and onboarding.
- `AGENTS.md` → machine/agent-facing operational guidance.

Additionally, the repo includes an **Obsidian-style wikilink index** in `AGENTS.md` so humans can open the project as a graph/mind-map and quickly find relevant files.

For graph-first navigation of how pages intersect, start with `MINDMAP.md`.

## How to use this scaffold

1. Clone the repository.
2. Open in Android Studio.
3. Start by editing:
   - `app/src/main/res/layout/activity_main.xml` for menu routing
   - `app/src/main/res/layout/activity_screen*.xml` for page shells
   - `app/src/main/res/layout/view_top_navigation.xml` for shared navigation
4. Keep user-facing copy in `app/src/main/res/values/strings.xml`.
5. Duplicate page/activity patterns when creating new parallel flows.

## Notes

- This scaffold is intended to be the **default view** of how these apps should look.
- It will evolve, but the baseline pattern should remain stable.
- Video instructions for download/run can be layered on top of this structure without changing the core scaffold.

## Obsidian mind-map usage

1. Open the repo in Obsidian as a vault.
2. Open [[MINDMAP]] as the graph hub.
3. Follow page nodes (`[[MAP_MainMenu]]`, `[[MAP_Screen1]]`, etc.) to see intersections.
4. Use the clickable file links inside each map note to open the underlying `.kt` and `.xml` files directly.

## Screen 3 scaffold progression

- Phase 1 added loading diagnostics for folder scanning (processed/total/playable counters).
- Phase 2 separates folder clip browsing from favorite-slot assignments; favorite slots are explicit and persisted.
- Phase 3 groups Screen 3 into framed, collapsible favorites/browser panels with vertical scrolling.
- Phase 4 adds dynamic folder clip action buttons (tap to play, long-press to assign) plus per-folder wav/mp3 counts.
- Phase 5 hardens playback (descriptor-based URI loading with queued play) and adds quick long-press favorite removal.
