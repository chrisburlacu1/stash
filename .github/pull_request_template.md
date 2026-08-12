<!--
Workstream PRs: title as `[X] Short description`, where X is the workstream letter
from MVP-PLAN.md (e.g. `[I] Enable R8 and add release signing config`).
-->

## What this changes

<!-- One or two sentences. What is different after this merges. -->

## Workstream

<!-- Letter + name from MVP-PLAN.md, or "none" for ad-hoc work. -->

## Verify on device

<!--
CI proves it compiles and unit tests pass. It cannot prove it *looks* right, and most of
this app's work is visual. List what a reviewer should actually open and look at — the
debug APK is attached to the CI run, so this can be checked without pulling the branch.

Be specific: "open a card and expand it, check the glow brightens and spreads" beats
"check the card still works".

Say "nothing visual" if that is genuinely true (build config, tests, docs).
-->

- [ ]

## Design notes

<!--
Required if this changed anything visual.

If a visual problem was diagnosed here, it belongs in DESIGN-NOTES.md as a symptom/cause
pair — that log is a deliberate learning tool, not incidental notes. Link the section.

If a constraint from CLAUDE.md or DESIGN-NOTES.md was deliberately worked against, say so
and why. Several plausible-sounding changes are recorded there as already-tried failures.
-->

## Checklist

- [ ] Comments explaining non-obvious values travelled with the code they explain
- [ ] No hardcoded colours, shapes or type sizes — theme tokens only
- [ ] AGSL shaders: **opened the surface on a device** (shaders compile at draw time, so a
      green build proves nothing)
- [ ] No new network calls sending page content or user data off-device
