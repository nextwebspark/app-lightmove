# Handoff: the executive profile panel — read first, edit a section at a time

## Overview
The right-hand panel a consultant opens by clicking an executive's name on the Companies grid. It
supersedes the "Candidate drawer" in `Project.dc.html` for editing: that mockup showed the profile
as read-only furniture with a whole-record Edit form behind it, and two things were wrong with the
form in use. Saving it threw the reader back to the grid, and it put twenty-two fields on screen to
change one salary. This document is the pattern that replaces it; the built panel in
`apps/web/src/features/candidates/components/` is the reference implementation.

## The principle
**A profile is read, and corrected in place.** Nothing about the panel changes shape when someone
edits: the section they are correcting becomes its own small form, the rest of the profile stays
readable around it, and a save puts the section back as it now reads. The panel never closes on a
save — the reader had not finished, and the next thing they do is usually write the note.

This is the pattern of the products a consultant already uses at this level (Linear's issue panel,
Attio's record page, HubSpot's contact sidebar): one pencil per section, an inline form with its own
Cancel and Save, keyboard dismissal that stays inside the section.

## Anatomy
```
┌────────────────────────────────────────────────────────────┐
│ [YE]  Yasmin El-Sayed  in  ✎                            ✕  │  ← header: name, title, employer · city · country,
│       VP Finance                                           │    seniority pill, source pill, STATUS (live select)
│       Gulf Industrial Holdings · Dubai · UAE               │    ✎ opens "Details" (identity + location)
│       [N-1] [PLUGIN] [Engaged ▾]                           │
├────────────────────────────────────────────────────────────┤
│                                  Expand all · Collapse all │
│ ⌄ SUMMARY                                               ✎  │  ← every section: fold + summary line + pencil
│   …prose…                                                  │
│ ⌄ EXPERIENCE  4                                         ✎  │  ← timeline (read) / rows of company·title·period (edit)
│ › EDUCATION  1   American University in Cairo              │  ← enrichment's alone: no pencil
│ ⌄ COMPENSATION                                          ✎  │  ← tiles + Total + composition bar (read)
│ › BACKGROUND   Egyptian · 18 yrs · 3 languages          ✎  │
│ › CONTACT      yasmin@… · +971 …                        ✎  │
│ › YOUR COLUMNS  2                                       ✎  │  ← the mandate's custom columns
│ ⌄ NOTE                                          SAVE NOTE  │  ← always a textarea; Save appears when dirty
│   ┌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌┐   │
│   ╎ Met at the GCC CFO Forum in May…                  ╎   │
│   └╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌┘   │
│ Added 02 Aug 2026 · Researched 03 Aug 2026 · Captured from…│
├────────────────────────────────────────────────────────────┤
│ [Remove from mandate]                                      │
└────────────────────────────────────────────────────────────┘
```

## The section editor
Clicking a pencil turns that section's body into a form, inside the same section frame:

```
│ ⌄ COMPENSATION                                             │
│   CURRENCY            NOTICE PERIOD                        │
│   [AED            ▾]  [3 months          ]                 │
│   BASE                BONUS                                │
│   [AED  1,200,000  ]  [AED  324,000     ]                  │
│   ALLOWANCES          LONG-TERM INCENTIVE                  │
│   [AED  180,000    ]  [AED  216,000     ]                  │
│   ┌──────────────────────────────────────────────────┐     │
│   │ Total · as typed                   AED 1,920,000 │     │  ← recomputed on every keystroke
│   │ ████████████████████▓▓▓▓▓▓▒▒▒░░░                 │     │
│   │ ● Base 63%  ● Bonus 17%  ● Allowances 9%  ● LTIP 11%   │
│   └──────────────────────────────────────────────────┘     │
│   Whole units, in the currency it was quoted in.           │
│   ─────────────────────────────────────────────────────    │
│   Esc cancels · ⌘/Ctrl ↵ saves          [Cancel] [ Save ]  │
```

Rules, all of them implemented:

- **One section at a time.** While one is open the other pencils are disabled (dimmed, not hidden,
  so the reader sees why they do not answer). The open section is held unfolded.
- **Focus lands in the first field** the moment the section opens, and the section scrolls into
  view if its pencil was further down than its fields.
- **Escape cancels the section, not the panel.** The keypress is stopped inside the form. Ctrl/⌘-Enter
  saves from anywhere in the form, including a textarea where Enter is a new line.
- **Save flips the section back to read mode**, toasts "Compensation saved" (the section's own name),
  and the panel shows the profile as the server now holds it — the grid refetches behind it.
- **Cancel or Escape discards** without a confirmation: the section is small and a discarded edit is
  a few seconds' typing.
- **Errors sit in the section**: a duplicate name marks the name field, anything else is one line
  above the fields. A field the section does not show can never block its save.

## What each pencil edits
| Section | Fields |
|---|---|
| Details (header pencil) | Full name, Title, Seniority, Employer (read-only when mapped to a company), City, Country |
| Summary | Profile summary |
| Experience | Career history rows: Company, Title, Period; add / remove |
| Compensation | Currency (picker), Notice period, Base, Bonus, Allowances, Long-term incentive |
| Background | Nationality, Years of experience, Languages (comma separated) |
| Contact | Every email and phone (value, kind, verified), LinkedIn — edited in place, see below |
| Your columns | The mandate's own person columns |
| Note | Always editable; saves alone |

Not editable here: **Status** (the live select in the header — its own write, so a form open for
five minutes cannot undo a pill flicked since), **Education** and **Skills** (enrichment's alone —
no screen writes them, so an empty section would nag about something nobody here can supply).

## Contact, specifically — a deliberate departure from this doc

The anatomy above gives Contact a pencil and nothing else. It now also carries **Find email** and
**Find phone**, which look the person's contacts up through ContactOut. This is a departure from the
position the rest of this doc takes — that enrichment has no trigger on any screen — and it is
deliberate: a phone number is the one fact a researcher cannot supply from what they already know,
and a lookup that costs money has to be somebody's decision rather than a thing that happens on capture.

The section is **one row per channel** — email, phone, LinkedIn — not tiles:

```
│ ⌄ CONTACT                                                    ✎  │
│  ✉  EMAIL                                                       │
│       hakan@alacpartners.com   WORK  VERIFIED               ⧉  │  ← green pill: ContactOut verified it
│       hakanalac@gmail.com      PERSONAL                     ⧉  │
│       hakan@alacep.com         PERSONAL                     ⧉  │
│  ☎  PHONE                                                       │
│     +61 421 904 554                                          ⧉  │
│  in LINKEDIN                                                    │
│     linkedin.com/in/hakan-alac                               ↗  │
```

- **Every value the mandate knows is listed**, whatever door it came through — typed, imported,
  captured, or found. The ledger behind it is `app_lm_candidate_contact`; the row's own address is
  one line among the others and carries **no primary flag**. Changing it is still the pencil's job.
- **Pills say only what ContactOut said.** `WORK` and `PERSONAL` come from its own lists; an address
  it listed without saying, and anything a person typed, gets no kind pill. A green `VERIFIED` pill
  after the kind appears only where the provider vouched for a work address. No tick, no icon: the
  pill is the whole mark, in the same vocabulary as the two beside it. Phones carry no kind at all: no provider says mobile or desk, so the screen does not.
- **No provenance labels on the rows.** Who typed, found, retagged or vouched for a value is the
  audit trail's to answer; a "via ContactOut" beside a value a person has since edited would be wrong
  the moment they saved. The ledger still records the door and the verified status, for the audit
  and the API, but the screen does not print them.
- **A spent channel that found nothing reads `No phone on record`, with no button.** Not a disabled
  button and not an enabled one: the provider charges the same to say "nothing" a second time, so the
  answer is shown instead of re-sold.
- **The Find button sits on the row it acts on**, bordered, with a `Spends 1 credit` caption and a
  tooltip. Beside a value a person already supplied it reads `Find more` — the lookup adds what the
  provider holds and never overwrites what was typed. Without a LinkedIn URL it is disabled and says
  why. While it runs the values stay on screen; nothing is swapped for a skeleton.
- **Every address is a `mailto:` link and every number a `tel:` link**, with a copy button at the end
  of the line.
- **The pencil edits the same rows in place.** Every line becomes an input, a kind select (— / Work /
  Personal), a `Verified` toggle and a remove; `+ Add email` / `+ Add phone` append lines; LinkedIn is
  an input, or a locked line with "Captured from this profile page — not editable" for a person the
  plugin captured. Save writes both channels at once (`PUT …/contacts`) and the link only if it
  changed; Cancel and Esc discard. Read mode has no editing affordance, so a stray click changes
  nothing. A verified mark a person sets is stored as "Verified by researcher" (ContactOut's own is
  "Verified") for the audit and the API; the pill reads `VERIFIED` either way. The Add form uses the
  same line editor, so adding a person by hand and correcting one later look identical.
- **No single email or phone anywhere.** V55 dropped the row's columns; the grid's Email and Phone
  columns (hidden by default) list every value with a `+N` count.

The buttons render only where a ContactOut account is configured (`GET /contact-lookup/config`), and
only for someone who may write — a client representative reads the mandate and does not spend its
credits. A missing LinkedIn profile is the one refusal shown inline, under the channel; every other
failure toasts.

## Compensation, specifically
- **Currency is a pick, not a text field**: the GCC six then USD, GBP, EUR (`lib/currencies.ts`).
  A code stored before the picker existed, or by an import, stays offered as an option — a select
  whose value matches no option posts blank, which would clear a fact nobody touched.
- **The currency code sits inside every amount field** as a prefix, so a figure is never read
  without its unit. Amounts accept "1,200,000" and "1200000" alike and tidy to thousands on blur.
- **The total is live** under the fields, marked "as typed", so a figure can be checked against
  what was said on the phone without saving.
- **Read mode shows the split**: a composition bar (Base sky · Bonus green · Allowances amber ·
  LTIP grey) with percentages, because "how much of this package is at risk" is the reading a
  search consultant actually wants and four tiles do not answer it. Shown only when two or more
  elements are paid; a base-only package is just a total.
- Nothing converts: "whole units, in the currency it was quoted in" stays on the form.

## The note
The mandate's remark, not a fact about the person, so it is never behind a pencil. A dashed
textarea (the mockup's), always live for anyone who may write; "Save note" appears in the section
heading the moment the text differs from what is stored and disappears when it lands.
Ctrl/⌘-Enter saves. Folded, the heading shows the note's first line.

## Adding an executive
"Add executive" still opens as one full form — there is nothing to read yet — built from the same
field groups as the section editors, so a field is asked for identically on both. Saving lands on
the profile it created rather than back on the grid.

## On the wire
The server replaces the whole record on every save (`PUT /candidates/{id}`). A section save is the
stored profile replayed with that section written over it, so nothing outside the section can
change — which is exactly what makes seven small forms safer than one big one. Custom columns are
omitted from every save but their own (omitted, the server leaves them alone). Status uses its own
`PATCH`.

## Fidelity
High. Everything above is built with the app's own primitives (`Field`, `Input`, `Select`,
`DetailTile`, `CollapsibleSection`, `Button`) over `tokens.css`; there are no new colours. The
panel is 560px on desktop and full-width on a phone, where the two-column field grids collapse to
one and the composition legend wraps.
