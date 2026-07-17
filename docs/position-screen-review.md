# Code Review — Position Screen (Frontend + Backend)

**Scope:** Uncommitted implementation of the mandate's Position brief — new backend aggregate
(`Position` + owned lists), template seeding, autosaving snapshot PUTs, the lock gate, and the React
editor plus project-shell routing.

**Branch:** `feature/project-position-page-plus-backend`
**Reviewed:** 2026-07-17
**Verdict:** Ship-worthy after addressing Finding #1 (concurrent-autosave optimistic lock) and
confirming #3 (confidential-brief visibility). #2 and #4 are cheap hardening; the rest is polish.

---

## 1. What the change does

### Backend
- **Migration `V7__position.sql`** — `app_lm_position` (1:1 with project, `ON DELETE CASCADE`) plus
  three owned ordered value-lists: `app_lm_position_benefit`, `_criterion`, `_competency`
  (composite PK `(position_id, sort_order)`, no identity of their own). Seeds the `POSITION_UNLOCK`
  action and grants it to the project `ADMIN` role.
- **Aggregate** — `Position` (entity) with `PositionCriterion` / `CompetencyRow` (`@Embeddable`,
  `@ElementCollection` + `@OrderColumn`), `PositionDetails` (scalar snapshot record).
- **`PositionService`** — seeds from `PositionTemplates` at project creation (wired into
  `ProjectService.create`) and lazily on first GET for pre-V7 projects; autosaves scalar / criteria /
  competency snapshots; gates lock on *both panels = 100% and at least one REQUIRED criterion*.
- **`PositionController`** — `GET` (reads ride workspace `PROJECT_BROWSE`), `PUT` / `PUT /criteria` /
  `PUT /competencies` / `POST /lock` (all `PROJECT_EDIT`), `POST /unlock` (ADMIN-only
  `POSITION_UNLOCK`).
- **Catalog wiring** — `ProjectAction.POSITION_UNLOCK`, three `AuditEventType`s, two `ErrorCode`s,
  and `RbacCatalogTest` updated in lockstep (no drift).

### Frontend
- **`ProjectLayout`** — project workspace shell: breadcrumb topbar, mandate sidebar, routed outlet.
- **`PositionPage`** — the brief editor: debounced `useAutosave` per section, client-side `rebalance`
  and `readiness`, lock footer with live checklist, remount-on-lock-change to resync drafts.
- **Placeholder pages** for the not-yet-built tabs (Strategy / Sourcing / Candidates / Outreach /
  Reports).
- Supporting changes: `Sidebar` header slot, `ProjectBreadcrumb`, `ProjectDrawer` "Open project"
  button, new icons, `errorCodes` entries.

---

## 2. Strengths

- **RBAC done right.** Reads ride workspace `PROJECT_BROWSE`, writes `PROJECT_EDIT` on the seat,
  unlock is a distinct ADMIN-only action. Migration INSERT + enum constant + `RbacCatalogTest` all
  move together, so the catalog cannot drift.
- **Tenant isolation correct.** Workspace id comes from the principal; the project is scoped by
  `(id, workspaceId)` before any position row is touched; 404 masking preserved and tested
  cross-tenant.
- **Clean gate separation.** Readiness (100% panels + a REQUIRED criterion) is validated *only* at
  lock time — autosave stays permissive and half-balanced panels persist freely. Well documented on
  both tiers.
- **Strong test coverage.** Backend: template seeding, lazy seed for pre-V7 rows, snapshot
  round-trip, list replace/order, the full lock → whole-brief-freeze → admin-unlock cycle, the auth
  matrix, cross-tenant masking. Frontend: `rebalance` invariants and page behaviour (autosave debounce,
  lock-disabled state, checklist, admin-only unlock).
- **Conventions honoured.** Class-level docs everywhere, `why`-comments, constructor injection,
  records for DTOs, `ApiException.of(code)`, `@OrderColumn` replace-list model, Tailwind tokens.

---

## 3. Issues and risks

### #1 — Concurrent autosave vs `@Version` (likely a real bug) · **High**
`update`, `putCriteria`, and `putCompetencies` all mutate the **same** `Position` aggregate, and
`BaseEntity` carries `@Version`. The three frontend autosaves are independent 700 ms debounced timers:
edit the narrative, then toggle a criterion within the same window, and both flushes fire as near-
simultaneous parallel `fetch`es. Two overlapping transactions → `OptimisticLockException`, surfacing
as an **unhandled 500** (no `ErrorCode` maps stale-object). The snapshot / last-write-wins design wants
this to be benign; `@Version` makes it fatal.

*Fix options:* serialize the three saves client-side behind a single flush queue; or catch the
stale-object exception and retry; or map it to a `CONFLICT` code the autosave `persist` path already
handles.

- `apps/api/.../project/service/PositionService.java`
- `apps/web/src/features/position/lib/useAutosave.ts`

### #2 — GET performs a write and races the UNIQUE constraint · **Medium**
`get()` is gated by the read-only `PROJECT_BROWSE` yet inserts via `seedFor` on the lazy path. Two
concurrent first-reads of a pre-V7 project both call `seedFor` → `UNIQUE(project_id)` violation → 500.
Low frequency (new projects seed at creation), but a read permission triggering a write is a smell.
Handle the constraint violation (re-read the just-seeded row) or make the insert conditional.

- `apps/api/.../project/service/PositionService.java` (`get`, `requirePosition`)

### #3 — Workspace-wide read of confidential briefs · **Medium (confirm intent)**
Reads ride workspace `PROJECT_BROWSE`, so **any** workspace member can read **any** project's brief —
including `confidential`, `internalContext` ("keep discreet"), and salary — even when not seated on
that project. This matches the documented model ("whoever sees the project list may read its brief"),
but confirm a `confidential` mandate is meant to be visible to non-seated members. If not, reads need a
seat check or the confidential fields need redacting for non-seat readers.

- `apps/api/.../project/controller/PositionController.java` (`get`)

### #4 — Unbounded text fields · **Low**
`internalContext` and `narrative` have no `@Size` (DB columns are `text`). Every other string is
bounded. A client can PUT arbitrarily large payloads on the autosave path. Add a `@Size` cap.

- `apps/api/.../project/dto/PositionDtos.java` (`UpdatePositionRequest`)

### #5 — Audit volume · **Low (confirm intent)**
`POSITION_UPDATED` fires on every debounced flush per section — one editing session writes many audit
rows. Fine if intended, worth confirming for a PII-audit table.

- `apps/api/.../project/service/PositionService.java` (`auditPositionChange`)

---

## 4. Minor / style

- **Inert `@Transactional` on `seedFor` via self-call.** `get()` / `requirePosition` invoke `seedFor`
  on `this` (proxy bypassed) — harmless *only* because both callers are already `@Transactional`, so
  the annotation does nothing on those paths (it works solely through `ProjectService`'s cross-bean
  call). This is the exact proxy trap documented in `CLAUDE.md`; a note or dropping the annotation
  avoids implying protection that isn't there.
- **Array-index React keys** in `CompetencyPanel.tsx` and `CriteriaCard.tsx`. Removing a middle row
  reconciles by index, so inputs / focus / the `From brief` tag can attach to the wrong row. Use a
  stable key.
- **`unlock` uses raw `new ApiException(VALIDATION_FAILED, …)`** while everything else uses
  `ApiException.of(code)`. Consider a dedicated `POSITION_NOT_LOCKED` code for a stable client switch.
- **`rebalance` single-row panel** — the slider is a no-op (drift correction undoes the target).
  Acceptable: one row can't be rebalanced, and the number input still sets it directly.
- **`completion()` hardcoded `/ 17`** — fragile if the counted field list changes; low risk.

---

## 5. Suggested follow-up order

1. **#1** — serialize autosaves or handle the optimistic-lock exception (blocking).
2. **#3** — confirm confidential-brief visibility; add a seat gate if required (blocking-ish).
3. **#4**, **#2** — `@Size` caps and the lazy-seed race (cheap hardening).
4. Style items as convenient.
