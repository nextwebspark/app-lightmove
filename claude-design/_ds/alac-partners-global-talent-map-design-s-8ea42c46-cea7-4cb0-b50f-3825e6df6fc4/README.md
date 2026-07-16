# ALAC Partners — Global Talent Map · Design System

> A design system extracted from the **Global Talent Map** product, the AI-driven
> market-intelligence and executive-search visualization tool built by **ALAC Partners**.
> Use it to design new screens, marketing, decks, and prototypes that look and feel
> native to the product.

---

## 1. Product & company context

**ALAC Partners** is an executive-search / talent-advisory firm. Their flagship internal
tool, **Global Talent Map**, helps search consultants build a "company universe" for a
mandate and map the executives inside it.

The product tagline (from the live OpenGraph card) is:
> **Global Talent Map** — "AI-driven market intelligence for executive search."

### What it does
1. **Define a universe** three ways (landing page):
   - **Search** — describe a target in natural language; AI builds the company list.
   - **Import a list** — upload an existing company list (CSV/XLSX) and extend it.
   - **From brief** — upload a Position Description / JD; AI infers sectors & companies.
2. **Stream discovery** — companies appear live with a relevance class
   (`Direct` / `Adjacent` / `AI Inferred`) and a confidence score; the user accepts/rejects
   each to "Confirm the universe."
3. **Work the map** — a Mapbox **globe** plots each company as a scaled bubble; hovering a
   company orbits its key **executives** as draggable "satellite" pills that can be snapped
   into an org hierarchy.
4. **Table & Dashboard views** — a dense data grid of companies×executives (export to Excel),
   and an analytics dashboard (mapping completion, executive universe, compensation analytics,
   availability/status, gender & ethnicity diversity).
5. **Enrich** — per-company / bulk AI enrichment (summary, business profile, executive comp),
   plus a "Clockwork" ATS project match/enrichment flow.
6. **CRM** — a firm-wide relationship-management layer accessed via the icon rail. Five tabs:
   - **My Day** — personal task roll-up across contacts and accounts, grouped by due-date
     bucket (overdue / today / this week / later), with mine-vs-team scope toggle.
   - **Contacts** — global person directory with saved segments (All / My contacts /
     Due follow-up / Candidates / Clients / Open to move), sortable table, bulk actions
     (add to search, assign owner, tag, export), and per-contact detail (career history,
     activity timeline, follow-up tasks, off-limits enforcement).
   - **Companies** — account directory with type pills (Client / Prospect / Source /
     Off-limits), relationship-strength meter, firmographics, linked people, mandate
     history, and account-level tasks.
   - **Searches** (Mandates) — mandate registry listing every active, placed, and
     pitching search with pipeline stage, fee tracking, and candidate count. Drill into
     a mandate detail for a mini-pipeline funnel, account link, placed-candidate record,
     and linked talent-map project.
   - **Business Dev** — BD pipeline kanban (Lead → Qualified → Proposal → Negotiation →
     Won / Lost) with deal cards showing probability bars, fee values, next-step notes,
     and owner. Won deals link forward to mandate and project records.
7. **Project workspace views** — inside a talent-map project, a left sidebar exposes
   seven sections that extend beyond the original map/table/dashboard:
   - **Position** — structured JD editor (role title, level, location, compensation,
     free-text description, AI-assisted fields).
   - **Strategy** — mandate strategy definition (target profile, ideal experience,
     sector priorities, geography, compensation benchmarking, and approval checklist).
   - **Long list** — editable candidate table with include/exclude/shortlist toggles,
     inline notes, and a "Sync to ATS" export menu (Clockwork, Bullhorn, etc.).
   - **Pipeline** — kanban board + table for the project's candidate pipeline; stages
     run Sourced → Contacted → Screening → Interview → Offer → Hired → Closed;
     drag-and-drop between columns, entry side-panel, and off-limits enforcement.
   - **Status report** — client-facing progress report (key candidates, activity summary,
     timeline, next steps) with live preview and export.
   - **Internal** — private team scratchpad and activity feed visible only to project
     members.

### Market context
Heavy **GCC / MENA** orientation. Example queries shipped in the UI:
*"Top FMCG distributors in UAE", "Leading PE firms in Saudi Arabia",
"Industrial equipment manufacturers in Egypt", "Retail chains across GCC".*
The dashboard defaults to a `GCC` region and an "origin country" comparison.

### Sources used to build this system
- **Codebase** (read-only, mounted): `client/` — a Vite + React + TypeScript SPA.
  - Design tokens: `client/src/index.css` (Tailwind v4 `@theme` + shadcn HSL vars).
  - Routes: `client/src/App.tsx` → `/` Landing, `/dashboard` Dashboard.
  - Key screens: `client/src/pages/Landing/*`, `client/src/pages/Dashboard.tsx`.
  - Map system: `client/src/components/map/{Map,ExecutiveSatellites}.tsx`.
  - Panels: `client/src/components/panels/RightPanel.tsx` (company/exec detail).
  - Primitives: `client/src/components/ui/*` (shadcn/ui, Replit-customized).
- **Brand mark**: `client/src/assets/images/logo.png` → copied to `assets/logo.png`.
- **Product screenshot**: `client/public/opengraph.jpg` → `assets/opengraph.jpg`
  (an earlier landing-page state; useful reference).
- ⚠️ `client/public/favicon.png` is the **default Replit logo (orange)**, *not* an ALAC
  asset. It is copied to `assets/favicon.png` for completeness but should **not** be used as
  a brand mark. See *Caveats*.

### Tech stack (for fidelity when recreating)
React 18 · TypeScript · Tailwind CSS **v4** · shadcn/ui (Radix) with Replit's
`hover-elevate` / `active-elevate` interaction layer · **Mapbox GL** (globe projection) ·
**Recharts** · **Framer Motion** · **Lucide** icons · **Montserrat** + **Libre Baskerville**
(Google Fonts) · Zustand store · TanStack Query · wouter router · Sonner toasts.

---

## 2. Content fundamentals (voice & copy)

**Persona:** a competent, fast research co-pilot for a professional search consultant.
Confident and precise, never chatty or cute.

- **Casing — sentence case everywhere.** Headings, buttons, menu items: *"Build your
  company universe", "Confirm universe", "Add company", "Import a list", "From brief".*
  The only ALL-CAPS is the tiny **eyebrow / section label** (`AI THINKING`, `SCALE SNAPSHOT`,
  `BY COUNTRY`, `TALENT MAPPING REPORT`) — letter-spaced, 10–11px, muted.
- **Person:** second person, possessive — *"what **you're** looking for", "**your** project",
  "Select how you want to define the scope of **this** search."* The AI is referred to in the
  third person as a tool: *"AI builds the company list", "AI suggests N adjacent sectors",
  "AI rationale", "AI thinking".*
- **Verbs first, terse.** Buttons are bare imperatives: *Search · Discover Companies ·
  Confirm universe · Enrich · Stop · New search · Dashboard · Export to Excel.*
- **Domain nouns are exact, not invented.** Universe · company · executive · sector ·
  relevance · confidence · enrichment · availability · off-limits · out of scope · remuneration
  · revenue band · mandate. Relevance has three fixed labels: **Direct / Adjacent / AI Inferred.**
- **Microcopy is helpful & quantified.** *"42 companies added — 30 core matches, 12 AI
  suggested", "Median: $1.2M", "Verified 2024", "High (8/10)", "+18%".* Numbers carry units
  and context; bands are shown rather than raw figures when uncertain (`$1B–5B`, `10K–50K`).
- **Toasts** are short past-tense confirmations or plain errors: *"Exported to Excel",
  "Notes saved", "Failed to rename project", "Enriched 30 companies, 4 sectors inferred."*
- **Empty states** teach the next action: *"No executives found. Click Add to create one.",
  "No status data captured yet. Assign levels (Board, C-Suite, N-1, N-2)…"*
- **No emoji. No exclamation marks** (beyond the occasional success). No marketing fluff.
  Trust signals are explicit: *"Notes are private and not sourced from external data.",
  "Source: …", "Data Confidence."*

---

## 3. Visual foundations

The aesthetic is **quiet, data-dense, instrument-panel**. Think a precise analytics console,
not a consumer app. Lots of hairline borders, small type, generous use of muted greys, and a
single warm accent that earns attention.

### Color
- **Ink-navy + white** base. Foreground is a near-black navy `#030712`; the brand/logo navy
  is `#15213a` (also the default map-bubble fill). Backgrounds are pure white (light) / deep
  navy (dark). Both modes ship and are first-class.
- **Action blue** `#2563eb` (hsl 220 70% 50%) — primary buttons, focus rings, links, active
  map scaling, progress. Used sparingly as *the* interactive color.
- **Signature amber-orange** `#f59c0b` (hsl 35 92% 50%) — the system's one warm accent.
  Reserved for **selection on the map**, **org-hierarchy connectors** (amber dashed lines
  between satellite pills), the **"unlock" pulse** animation, and snap targets. When you see
  orange, something is *selected, linked, or being re-parented*.
- **Status semantics** are consistent: **emerald** = Direct / Interested / Verified / success;
  **blue** = Adjacent; **amber** = AI Inferred / "Concentrated" talent pool / warning;
  **violet** = AI activity (sparkles, "thinking"); **red** = Off-Limits / destructive.
  Diversity charts use a fixed categorical ramp (blue, emerald, amber, pink, purple, cyan…).
- Status colors always appear as **soft tint chip + saturated text** (e.g. `bg-emerald-100
  text-emerald-700`, dark: `bg-emerald-900/40 text-emerald-400`), never solid blocks.

### Type
- **Montserrat** is the entire UI typeface (300–700). Headings use weight **700**; the app's
  `font-serif` token is actually remapped to Montserrat, so headings render in heavy Montserrat,
  not a serif. **Libre Baskerville** is loaded but currently unused in product UI — keep it in
  reserve for editorial/report surfaces (it's in the token file as `--font-serif`).
- **Dense working scale:** 10–14px does almost all the work. 10–11px uppercase eyebrows,
  12px table/meta, 14px body, 18–20px panel/section titles, 24–36px only on the landing hero
  and completion screens. Numbers are frequently `tabular-nums` / mono-feel for alignment.
- Tracking: tight (−0.02em) on big display; wide (+0.08em) on uppercase micro-labels.

### Spacing, density & layout
- **Tailwind 4px spacing grid.** The UI is tight: `gap-1 / gap-1.5 / gap-2 / gap-3` dominate;
  panels use `p-4`, cards `p-5`, dashboards `p-6`.
- **App chrome is fixed:** a **48px** (`w-12`) left **icon rail**, an **44px** (`h-11`) top bar,
  and a resizable right detail panel (320–700px, default 384px). Content fills the remaining
  space; the map/table/dashboard swap in the center.
- **Cards:** white surface, `1px` border (`--border`), `rounded-xl` (12px), `shadow` (soft).
  No heavy drop shadows, no colored left-border accents, no gradient fills (one subtle
  `from-primary/10` wash only on the dashboard summary banner).
- **Pills & badges** are `rounded-full` or `rounded-md`, 10–12px, used heavily for sectors,
  relevance, status, and filters.

### Backgrounds & texture
- Mostly **flat white / flat navy.** No illustrations, no photography, no repeating patterns.
- The **Mapbox globe** is the one immersive surface: light-v11 / dark-v11 styles, globe
  projection, custom fog (`horizon-blend: 0.02`) and a faint starfield in dark mode.
- The landing page has a barely-there radial wash (`opacity-5`, primary→background).

### Motion
- **Framer Motion**, restrained. Fades and short upward slides (`y: 8–20`, `0.5–0.6s`) on
  mount; staggered by `0.1s`. List/AnimatePresence cross-fades between phases.
- Satellite pills animate in with a gentle **overshoot** spring
  `cubic-bezier(0.34, 1.56, 0.64, 1)`, staggered ~40ms.
- Bars / rings / progress animate width or dash-offset over `0.5–0.7s`.
- A single keyframe loop exists: **`unlockPulse`** (amber glow) while holding a connected
  satellite to detach it. Decorative infinite loops are otherwise avoided.
- Live/streaming states use small **animate-ping** dots, three-dot **animate-bounce**, and
  **animate-pulse** skeleton rows. Spinners are Lucide `Loader2` with `animate-spin`.

### Interaction states
- **Hover:** Replit's `hover-elevate` (a subtle translucent overlay that lightens/darkens the
  surface) rather than color swaps; nav/menu rows go to `bg-sidebar-accent` / `bg-muted`.
  Links underline. Map bubbles raise opacity.
- **Active/press:** `active-elevate-2` (stronger overlay); shadows drop to none on outline
  buttons. No scale-down on press (except the satellite spring scale-in).
- **Selected:** amber on the map; `border-2 border-foreground` on mode cards; primary ring
  + `bg-primary/5` on metric cards; `bg-muted` on list rows.
- **Focus:** `ring-1 ring-ring` (1px blue ring), never a thick glow.
- **Disabled:** `opacity-50`, pointer-events off.

### Borders, shadows, radii (quick reference)
- Border: `1px` `--border` hairlines everywhere; dividers are `w-px` / `h-px` bg-border.
- Radius ladder: 4 / 6 (buttons, inputs) / 8 / 12 (cards) / 16 (modals, mode cards) / full (pills).
- Shadow ladder: `xs` (resting controls) → `sm` (cards) → `md` (floating dialogs, pills) →
  `lg` (dragged pill). Inner shadows are not used; emphasis comes from rings + amber glows.
- Transparency & blur: floating map UI uses `bg-background/95 backdrop-blur`; modal scrims are
  `bg-black/40`. Status tints use `/40`–`/15` alpha in dark mode.

---

## 4. Iconography

- **Library: [Lucide](https://lucide.dev) (lucide-react).** This is the *only* icon system in
  the product — there is no custom icon font, sprite, or bespoke SVG set. Recreate with Lucide
  from CDN; it is an exact match (same stroke model).
- **Style:** outline, **2px stroke**, rounded caps/joins, drawn on a 24px grid. Rendered small —
  almost always `w-4 h-4` (16px) in chrome, `w-3 / w-3.5` (12–14px) inline in dense rows.
  Icons inherit `currentColor` and sit at muted-foreground until hovered/active.
- **Icons actually used** (so designs stay in-vocabulary):
  `Map, Table2, LayoutDashboard, Home, Search, FolderOpen, Settings, Zap, Upload, Download,
  Plus, Sparkles, Loader2, ArrowRight, ArrowLeft, ArrowUpRight, Check / CheckCheck / CheckCircle2,
  Lock, ShieldCheck, Building2, Users, UserCheck, MapPin, DollarSign, Banknote, Briefcase,
  GraduationCap, FileText, FileDown, Mail, Phone, Linkedin, Pencil / Edit2, Trash2, Eye / EyeOff,
  Sun, Moon, ChevronDown/Up/Left/Right, X, Square (stop), Target, TrendingUp, BarChart3, Bot,
  AlertCircle, Camera, Link2.`
- **Emoji: never.** **Unicode glyphs as icons: never** (the `·` middot is used as a text
  separator, and `✕` appears once as a remove affordance — otherwise all marks are Lucide).
- **Avatars** are initials, not images: a small rounded square / circle with 1–2 uppercase
  letters in a tinted fill (`bg-primary/10 text-primary`, or emerald when enriched).
- **CDN to use in artifacts:** `https://unpkg.com/lucide@latest` (or `lucide-react` in React).

### Brand assets in `assets/`
- `logo.png` — **ALAC PARTNERS** wordmark: navy `#15213a` letterforms (spaced caps "ALAC" over
  "PARTNERS") with a right-pointing concave **arrow/chevron** mark. White background, no alpha.
- `opengraph.jpg` — product landing screenshot (reference only).
- `favicon.png` — ⚠️ Replit default, **not** brand (see Caveats).

---

## 5. Index — what's in this system

| Path | What it is |
|---|---|
| `README.md` | This file — context, voice, visual foundations, iconography, index. |
| `colors_and_type.css` | All design tokens as CSS custom properties (light + dark) + a semantic type scale & helper classes. Import this into any artifact. |
| `SKILL.md` | Agent-Skill manifest so this folder works as a downloadable Claude skill. |
| `assets/` | `logo.png` (ALAC wordmark), `opengraph.jpg` (product shot), `favicon.png` (⚠️ Replit default). |
| `preview/` | Small HTML "specimen" cards that populate the Design System tab (colors, type, components, etc.). |
| `ui_kits/talent-map/` | High-fidelity, interactive React recreation of the Global Talent Map UI — `index.html` + JSX components (see screen map below). |

### UI kit screen map (`ui_kits/talent-map/`)

| File | Screens / components |
|---|---|
| `app.jsx` | Root `App` — phase routing (landing → universe → workspace → CRM → projects → settings), CRM state, pipeline chokepoint, off-limits modal. |
| `chrome.jsx` | `Rail` (icon sidebar), `TopBar`, `CommandPalette`, `ProjectSidebar` (7-section nav: Dashboard, Position, Strategy, Map, Candidates, Long list, Pipeline, Status report, Internal). |
| `home.jsx` | `HomeScreen` — returning-user landing with recent projects, quick actions. |
| `landing.jsx` | `Landing` — first-run entry (Search / Import / From brief). |
| `universe.jsx` | `UniverseView` — streaming company discovery, accept/reject, confirm universe. |
| `universe-filters.jsx` | Filter sidebar for universe scope (sectors, geography, confidence). |
| `universe-chat.jsx` | AI chat panel during universe build. |
| `universe-selected.jsx` | Selected-companies tray. |
| `mapview.jsx` | `MapView` — Mapbox globe with company bubbles + executive satellites. |
| `panel.jsx` | `RightPanel` — company/exec detail panel on the map view. |
| `tableview.jsx` | `TableView` — dense data grid of companies × executives. |
| `table-config.jsx` | Column-configuration modal for the table. |
| `table-modals.jsx` | Export, enrichment, and bulk-action modals. |
| `views.jsx` | `Dashboard` — analytics (mapping completion, exec universe, comp, diversity). |
| `crm.jsx` | **CRM screens:** `ContactsScreen` (directory + segments + bulk actions), `ContactDetail` (career, timeline, tasks), `PipelineView` (kanban + table), `PositionView` (JD editor), `StrategyView` (mandate strategy), `LongListView` (candidate long list + Sync to ATS), `StatusReportView` (client report), `InternalView` (team scratchpad), `BriefView` (AI mandate intake). Also: `PipelineCard`, `PlColumn`, `EntryPanel`, `StagePill`, `AvailPill`, `StatusPill`, helpers. |
| `accounts.jsx` | **CRM — Companies:** `AccountsScreen` (account directory, strength meter), `AccountDetail` (firmographics, people, mandates, tasks), `AccountTypePill`, `StrengthMeter`, `AccountAvatar`. |
| `mandates.jsx` | **CRM — Searches:** `MandatesScreen` (mandate registry, fees), `MandateDetail` (pipeline funnel, account link, placed candidates), `MandateStatusPill`. |
| `worklist.jsx` | **CRM — My Day & BD:** `MyDayScreen` (task roll-up, due buckets, mine/team), `BizDevScreen` (BD pipeline kanban, probability, deals). |
| `projects.jsx` | `ProjectsPanel` (quick-switcher dropdown), `ProjectsScreen` (full project list + delete). |
| `settings.jsx` | `SettingsScreen` (theme, profile, sign-out). |
| `settings_org.jsx` | Org-level settings (team, billing, integrations). |
| `auth.jsx` | `LoginScreen`, `SignupScreen`. |
| `cookies.jsx` | Cookie-consent banner. |
| `primitives.jsx` | Shared primitives: `Icon` (Lucide bridge), `Button`, `Avatar`, `Tooltip`, `cx`, `initials`. |
| `data.jsx` | Mock data: `TM_COMPANIES`, `TM_PROJECTS`, `TM_PIPELINE`, `TM_CONTACTS`, `TM_ACCOUNTS`, `TM_MANDATES`, `TM_BD_DEALS`, off-limits rules, contact-profile builders. |
| `kit.css` | All component styles (`.tm-*` classes) for the UI kit. |
| `slides/` | *(not built — no deck/template was provided; see Caveats).* |

---

## 6. Caveats

- **No official favicon / app icon.** The shipped `favicon.png` is Replit's default orange
  mark. I've treated `logo.png` (ALAC wordmark) as the brand. **If you have a real app icon /
  favicon, please share it.**
- **Serif intent is ambiguous.** Libre Baskerville is loaded but the theme overrides
  `--font-serif` to Montserrat, so headings render in heavy Montserrat. I documented both and
  kept Baskerville available. Tell me if headings should actually be serif.
- **No brand color spec beyond the code.** Colors are reverse-engineered from
  `index.css` + inline usage. If ALAC has an official palette (print/marketing), share it.
- **No slide template / deck** was provided, so `slides/` was intentionally not built.
- The product depends on a live API + Mapbox token for real data; the UI kit uses **mocked**
  data and a stylized globe, focusing on visual + interaction fidelity, not production logic.
