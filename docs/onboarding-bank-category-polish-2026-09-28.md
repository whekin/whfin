# Bank and category setup polish — 2026-09-28

## Observed problem

After bank sign-in, setup could remain on a connected page while the full history read ran. Credo
and TBC then described their work differently. A single imported statement was labelled as if all
history had loaded. On the category step, suggestions based on partial history looked final, and
optional interest packs required individual additions.

## Changes

- A fresh Credo or TBC sign-in returns to Bank SMS once the process-owned data read begins. The
  bank read continues. Repeated completion callbacks cannot pop another setup page.
- Bank rows use shared progress states. Later stages show active work and any action needed for
  balance review, an interrupted read, or bank attention. Bank SMS still offers the other bank.
- A prior import is described as available transactions with full history unverified. Completion
  is shown only for a finished run that reached the data read.
- The category step says when bank history is unfinished. Its proposals count unfiled spending and
  do not offer a generic category for a merchant that the owner already categorized. Interest packs
  support multi-selection and one batch write. Existing categories are omitted from pack details.
  Failure keeps the selection and shows a retry message. Names follow the app locale.

## Verification

- Host: 1244 tests, 0 failures/errors, 5 skipped.
- Instrumented on Pixel 9 Pro emulator: four bank/category visual scenarios (EN light, RU dark,
  RU dark with font 1.5 and compact size) passed. Three personal setup journeys (EN light, RU dark
  font 1.5, compact RU Bank SMS) passed. Screenshots were inspected.
- Release R8 and `lintVitalRelease` passed; `git diff --check` passed.

The connected Samsung S25 was unavailable during this pass. Its backup and actual spending were not
read, so personalized category accuracy still needs the owner's device or backup file. No phone
data, bank login, SMS, or Drive state was changed. The differing bank-specific confirmation screens
remain because Credo and TBC require different evidence; the setup stage now presents their progress
consistently.
