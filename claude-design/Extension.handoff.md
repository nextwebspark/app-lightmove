# Handoff: LightMove Capture — Chrome extension

## Overview
A Chrome toolbar extension for the LightMove / Global Talent Map workspace. It reads the page the
consultant is on (a LinkedIn profile, a LinkedIn company page, or a corporate site) and writes the
extracted person or company record straight into a project section. Five popup states are designed:
signed out, capture person, capture company, saved receipt, settings.

## About the design files
`Extension.dc.html` in this bundle is a **design reference created in HTML** — a prototype showing
the intended look and behaviour, not production code to ship. The task is to recreate it inside the
extension codebase using its existing environment (React + Tailwind v4 + shadcn/ui, matching the web
app), not to embed the HTML. Supporting screens (`Login.dc.html`, `Project.dc.html`, `Settings.dc.html`)
sit alongside it so the extension's visual language can be checked against the app it plugs into.

## Fidelity
**High-fidelity.** Colours, type, spacing, radii, and interaction states are final and match the web
app's token set. Recreate pixel-for-pixel with the codebase's own primitives.

## Architecture / auth
- **Surface:** browser-action popup, fixed **400 × 600 px**. Keyboard shortcut `⌥⇧L`.
- **Session:** the popup reuses the `app.lightmove.io` session cookie. If a valid session exists the
  popup opens directly on the capture form — there is no in-popup login. If not, it renders the
  signed-out state, whose primary action opens `app.lightmove.io/login` in a **new tab**; on
  successful login the popup re-checks the session on next open.
- **Sign out** inside extension settings clears extension-local state only; the workspace session is
  untouched.
- **Page reading:** a content script classifies the active tab (person vs company) and extracts
  fields. Every extracted field is rendered as an **editable input** — nothing is written blind.
  A "Re-scan" action re-runs extraction.

## Screens

### 1 — Signed out
- Header: 22–24px amber (`#e2b65c`) rounded-square logo tile with `L`, product name "LightMove Capture", 12.5–13.5px 600.
- Centred column: 44px rounded-square icon tile (lock, Lucide `Lock`, 20px, muted stroke), title
  "Sign in to capture" (15px/600), body copy 12.5px/1.6 muted, max-width 280px.
- Primary button: amber fill `#e2b65c`, ink text `#1a1712`, 13px/600, padding 9×16, radius 8, label
  "Open LightMove" + `ExternalLink` icon. Below it the target URL in 11px mono-feel muted.
- Footer bar: version + detected host on the left, "Use SSO" link (blue) on the right.

### 2 — Capture person
- **Header row:** logo tile, "Capture", settings icon button (26px, 1px border, radius 7), user
  initials avatar (26px circle, emerald tint fill).
- **Tabs:** Person | Company — 12px, active tab 600 with a 2px amber bottom border; inactive muted.
- **Source strip:** blue-tint band (`--sky-dim`), `Check` icon, truncated source URL in 11px, "Re-scan" link.
- **Subject row:** 40px circle initials avatar (amber tint), name 14.5px/600, "title · company" 11.5px muted.
- **Detected fields** (section eyebrow: 10px uppercase, `.12em` tracking, muted). Editable rows,
  8px gap, each = 9.5px uppercase label + input (radius 7, 1px border, `--panel2` fill, 7×10 padding,
  12.5px text, blue border on focus):
  Full name · Current title · Current company · Location · Tenure · LinkedIn · Email · phone.
- **Previous roles:** bordered list (radius 8), rows of title (12px/500) + company (10.5px muted) and
  right-aligned year range.
- **Seniority:** chip row — Board / C-suite / N-1 / N-2. Single-select; selected chip = tinted fill +
  saturated border and text in the accent colour; unselected = `--panel2` fill, muted text.
- **Tags:** removable chips (X button turns red on hover) + a dashed-border "Add a tag, press enter"
  input that becomes solid blue on focus.
- **Notes:** 3-row textarea, same field styling, vertical resize only.
- **Off-limits:** full-width toggle row with a 15px checkbox; when on, the row switches to red tint
  fill, red border, red label. Sub-label: "Blocks outreach across the project".
- **Footer:** project select + section select side by side (Candidates / Long list / Pipeline), then
  "Save to project" (amber, with `Plus` icon) and a secondary "Save & next".

### 3 — Capture company
Same chrome and field styling as person, with:
- Subject row uses a rounded-square (radius 9) neutral initials tile.
- **Detected fields:** Company name · Website · LinkedIn · Sector · HQ · Headcount band ·
  Revenue band · Ownership.
- **Criteria note:** neutral panel row with an info icon — "Matches 4 of 5 Strategy criteria. Fails
  revenue ≥ $500M." Read-only; there is deliberately **no relevance chip set** here, because the
  destination buttons make the intent explicit.
- **Tags** and **Notes** as per person.
- **Queue row:** "Also queue 12 executives found on this page for review" with a "Queue" link action.
- **Footer:** a single labelled **Project dropdown** (full width) above **two destination buttons** —
  "Add to universe" (amber primary, `Plus` icon) and "Add to shortlist" (outline, `Check` icon).
  Each button performs the save and sets the section; there is no separate section select.

### 4 — Saved
- 46px emerald-tint circle with a `Check`, title "<Name> added" (15px/600), sentence describing where
  it landed with the section and project emphasised.
- **Receipt card:** `--panel2` fill, radius 9, label/value rows (uppercase 9.5px label, 11.5px value):
  Project · Section · Company link · Source.
- Actions: "View in project" (outline) + "Capture another" (amber). Below: "Undo" text link.
- Footer: running counts — "14 captures this week · 3 pending review". Auto-closes after 4s when the
  matching setting is on.

### 5 — Settings
- Header with back chevron button + "Settings".
- **Default destination:** Project (incl. "Ask every time"), Section for people, Section for companies
  (Universe / Shortlist / Long list) as selects.
- **Behaviour:** bordered list of toggle rows (label 12.5px/500 + hint 10.5px muted, 32×18 track,
  12px knob; on = accent tint track, accent knob, knob right):
  Auto-detect page type · Warn on duplicates · Queue people from company pages · Close popup after save.
- **Session:** card with 30px initials avatar, name, "email · session shared with app", and a
  "Sign out" outline button that turns red on hover. Explanatory 10.5px note beneath about cookie reuse.
- Footer: shortcut hint + link to workspace settings.

## Interactions & behaviour
- Tab switch (Person/Company) swaps the form; page classification preselects the tab when
  "Auto-detect page type" is on.
- Chips (seniority) are single-select; tag chips are add/remove; off-limits is a boolean.
- Save posts to the workspace API with `{ projectId, section, entityType, fields, tags, notes, offLimits, sourceUrl }`
  and shows state 4. "Save & next" keeps the popup open and clears the form.
- Duplicate check runs before write when "Warn on duplicates" is on.
- Undo issues a delete of the just-created record for the toast/receipt lifetime.
- Motion: mount fades with a short upward slide (y 8–20px, 0.5s), staggered 0.1s. No decorative loops.

## State
`session` (none | valid) · `tab` (person | company) · `scrape` (loading | ready | failed, plus fields) ·
`editedFields` · `seniority` · `tags[]` · `notes` · `offLimits` · `projectId` · `section` ·
`saveState` (idle | saving | saved | duplicate | error) · `settings` (defaults + 4 booleans).

## Design tokens (from the app; dark mode is the default)
Light: `--bg #f8f9fb` · `--panel #ffffff` · `--panel2 #f3f4f6` · `--line #e2e4e9` ·
`--line-soft #edeef2` · `--text #111113` · `--text2 #475569` · `--text3 #64748b`.
Dark: `--bg #0c0d0e` · `--panel #16171a` · `--panel2 #131316` · `--line #2a2a2e` ·
`--line-soft #212326` · `--text #f8fafc` · `--text2 #cbd5e1` · `--text3 #94a3b8`.
Accents: amber `#e2b65c` (primary action; ink text `#1a1712`) · blue `#2563eb` / dark `#60a5fa` ·
green `#059669` / `#34d399` · red `#dc2626` / `#f87171`. Status colours always appear as soft tint
fill + saturated text, never solid blocks.
Type: Montserrat/Geist stack, 9.5–15px in the popup; uppercase micro-labels 9.5–10px with
`.11–.12em` tracking. Radii: 6 (chips) / 7 (inputs, small buttons) / 8 (buttons, panels) /
9 (lists, cards) / 14 (popup shell). Shadow: `0 8px 28px rgba(0,0,0,.5)` dark, `rgba(15,23,42,.10)` light.
Spacing: 4px grid; popup padding 14px, field gap 8px, section gap 18–20px.

## Assets
No images. All icons are **Lucide**, 2px stroke, rendered 10–22px: `Lock, ExternalLink, Check, Plus,
Settings, ChevronLeft, X, Info, FileText`. Avatars are initials in tinted fills. No emoji.

## Files
All paths are relative to `claude-design/`. The bundle's other four files were already in this
folder byte-for-byte, so only `Extension.dc.html` and this document were added.

- `Extension.dc.html` — the five popup states (the design under handoff). Open it directly; it
  loads `./support.js` from this folder and links across to `Settings.dc.html`.
- `Login.dc.html` — the workspace login page the signed-out state opens
- `Project.dc.html` — project workspace, showing the sections captures land in
- `Settings.dc.html` — workspace settings, for visual consistency reference
- `handoff/DATA-MODEL.md`, `handoff/seed-data.json` — existing entity/field reference for the workspace
