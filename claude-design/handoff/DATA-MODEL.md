# ALAC talent-map prototypes — data model & handoff notes

For a developer (or Claude Code) implementing these designs in a real codebase.

## Why the exported HTML has no records in it

The prototypes are React-style components. Every client, project, mandate, candidate and
team row you see on screen is **generated at runtime** from arrays defined inside each
file's logic class — there is no static `<tr>` markup in the file. So if you open the
exported HTML in a text editor (or hand it to a tool that reads the source rather than
the rendered page) you see template placeholders like `{{ row.name }}` and empty
`<tbody>`s, not the data.

Nothing is fetched from a server and nothing is stored in a database. The only persisted
values are two UI preferences in `localStorage`: `lm-theme` (`light|dark`) and
`lm-side-collapsed` (`0|1`).

**Use `handoff/seed-data.json` as the data reference.** It contains every record the
prototypes show, extracted verbatim, in one place. Treat it as fixture/seed data — it is
mock data for design purposes, not real client information.

## Where the data lives in the source (if you need to trace it)

| File | Arrays in the logic class |
|---|---|
| `Workspace.dc.html` | `this.MEMBERS`, `this.CLIENTS`, `this.STAGES`, `this.HEALTH`, `seed` (→ `state.projects`) |
| `Clients.dc.html` | `this.DB` (company database), `this.MANDATES`, `this.STAGES`, `this.GATES`, `state.clients` |
| `Project.dc.html` | `this.CLIENT_DIR`, `this.FIRM`, `this.STATUS`, `companies` (universe), `this.criteria`, `this.CRIT_POOLS`, `state.candidates`, `state.team`, `state.pos` |
| `Settings.dc.html` | plan/seat figures are literal copy in the markup (see `billing` in the JSON) |
| `Login.dc.html`, `Signup.dc.html` | no records — form UI only |

## Entities

### Member (firm user)
`id` · `name` · `initials` · `role` (`Admin | Consultant | Researcher`) · avatar tint.
Current user is `yh` (Yara Haddad) — `this.ME` in Workspace and Project.

### Company (company database)
`id` · `name` · `hq` · `sector`. The searchable master list a client record points at.
Clients screen also allows a **custom** company (`source: 'custom'`) with `name` +
`domain` only.

### Client
`id` · `companyId` (→ Company) · `source` (`db | custom`) · `override` (per-client field
overrides of the company record: name, sector, hq, domain, offlimits) ·
`representatives[]`.

**Representative** (client-side contact with portal access): `name` · `position` ·
`email` · `status` (`active | invited`).

### Mandate / Project
Two shapes exist for the same concept — the Clients drawer shows `mandates`, the
Workspace table shows `projects`. Unify these when implementing.

- Mandate: `id` · `clientId` · `position` · `stage` · `lead` (name string) · `created` ·
  `target` · `companies` · `candidates` · `team[[name, role]]` · `health` · `healthLines[]`
- Project: `id` · `client` (name string) · `position` · `stage` · `lead` (member id) ·
  `team[memberId]` · `targetDate` (ISO) · `companies` · `candidates` · `health`

Stage is a linear pipeline: `brief → universe → locked → mapping → outreach → delivered
→ closed`. The five progress gates rendered in the drawer are
`Brief · Universe · Mapping · Outreach live · Shortlist delivered`.

Health is a 4-value enum, but the two files use different keys for the same states —
`ok/risk/off/done` (Workspace) vs `ontrack/atrisk/offtrack` (Clients). Pick one.

### Project team member
`memberId` + `roles[]` from `lead | admin | researcher`. Only `lead` and `admin` may
change the client, invite client contacts, or edit team roles (`canManage()` in
`Project.dc.html`); everyone else gets a "Only leads and admins can …" toast.

### Universe company (inside a project)
`id` · `name` · `location` · `sector` · `revenueBand` · `employeeBand` · `ownership` ·
`desc` · `relevance` (`direct | adjacent | inferred`) · `inUniverse` · `shortlisted` ·
`declined` · `comment`.

### Candidate
`id` · `companyId` (→ universe company) · `name` · `title` · `level` (`N | N-1 | N-2`) ·
`status` (`executive | interested | not_interested | offlimits | out_of_scope`, nullable) ·
`email` · `notes` · `comp {base, bonus, allowances, ltip, currency}` · `summary` ·
`career[{company, title, years}]` · `languages[]` ·
`verification {emailVerified, linkedinMatched, compChecked}` · `confidence` (0–100).

### Search criteria (Strategy page)
Six keyed groups — `sector`, `size`, `geography`, `ownership`, `offlimits`, `seeding` —
each `{ mode: 'required' | 'preferred', values: string[] }`. `criteriaPools` holds the
selectable options for the four picker-driven groups; `offlimits` and `seeding` are
free-entry. Off-limits is inherited from the client record plus project additions;
seeded companies bypass the required filters.

## Relations

```
Member ──lead/team──> Project ──> Client ──> Company (companyDb)
                        │            └──> Representative[]
                        ├──> UniverseCompany[] ──> Candidate[]
                        └──> Criteria (6 groups)
```

## Known inconsistencies to resolve when building

1. **Two client sets.** Workspace/Clients use Meridian Energy Group, Al Rabie Saudi
   Foods, Bindawood Holding, Agthia Group. `Project.dc.html` uses a separate directory
   (Aurora Capital, Meridian Foods, Zenith Industrial) and its open project is
   Aurora Capital · CFO. They were prototyped independently; one client table should
   serve both.
2. **Client → project link is a name string** in Workspace (`client: 'Agthia Group'`)
   but an id in Clients (`clientId: 'agt'`). Use ids.
3. **Health enum keys differ** (see above).
4. **Lead is a name string** in mandates, a member id in projects. Use ids.
5. Counts shown per project (`companies`, `candidates`) are stored numbers in the seed
   data, not derived — in a real implementation derive them from the related rows.

## Suggested API surface

```
GET  /members
GET  /companies?q=                     # company database search
GET  /clients            POST /clients
GET  /clients/:id        PATCH /clients/:id
POST /clients/:id/representatives       # sends invite → status 'invited'
GET  /projects?scope=my|all&stage=      POST /projects
GET  /projects/:id       PATCH /projects/:id        # stage, target, health
GET  /projects/:id/team  POST /projects/:id/team    # roles[]
GET  /projects/:id/criteria   PUT /projects/:id/criteria
GET  /projects/:id/companies  PATCH /projects/:id/companies/:cid   # inUniverse, shortlisted, declined
GET  /projects/:id/candidates POST/PATCH /projects/:id/candidates/:cid
```

## Files in this project

`Workspace.dc.html` (project list, my/all scope, new-project modal) ·
`Clients.dc.html` (client list, client drawer, mandate drawer, add-client flow) ·
`Project.dc.html` (open project: Position, Strategy, Sourcing, Candidates, Team, Client) ·
`Settings.dc.html` · `Login.dc.html` · `Signup.dc.html`.

These are **design references** — recreate them in the target codebase's own framework
and patterns rather than shipping the HTML.
