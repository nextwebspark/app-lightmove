# Custom dashboards

A member builds their own dashboards beside the fixed report: on a position (the report's rail, **Your dashboards**)
and across the workspace (**Dashboards** in the sidebar). The design is `uncava-design` PR #12 (`Position.dc.html`
`?page=reports&dashboard=custom`, `Workspace.dc.html?view=dashboards`, the shared `Dashboard*.dc.html` parts).

## What a member can do

- **Name** dashboards (1–80 characters), private by default; **share** one with the position's or the workspace's
  staff, who open it read-only and may **duplicate** it to make it theirs. Rename, duplicate, delete.
- **Add chart** from the library: ready-made queries by category, with a search. Picking one opens **Show as** —
  only the chart types that fit the answer can be picked (Number, Bar, Column, Pie, Donut, Line, Area, Stacked bar,
  Table) — its options (group by, period, top N), an optional title and a live preview.
- **Edit layout**: the canvas turns to graph paper (the twelve columns and rows a chart snaps to). Press anywhere on a
  chart and drag to move it; drag its right edge, bottom edge or corner to resize it, within its type's minimum
  (`gridLayout.ts` `LIMITS`) and the full width. A chart it lands on is pushed down; charts then slide straight up into
  empty rows from where they sit, never sideways and never through another chart. Arrow keys move a focused chart
  (Down trades places with the chart below it), Shift + arrows resize it. The pencil
  changes it, the × removes it; nothing changes for anyone until **Save layout**. Below ~720px the charts stack in
  reading order and Edit layout is hidden.

There is no free-form query builder, deliberately: recruiting platforms (Ashby, Greenhouse, Bullhorn, Lever) give
every user a catalogue of reports with a chart choice and keep free-form builders for admins or premium plans, and a
builder is where tenant isolation and the rules on gender and nationality are hardest to hold. A query exists because
someone wrote and reviewed it.

## How it is built

- **Storage** — `app_lm_dashboard` (V231): workspace, project (null on the workspace's), owner, name, `shared`, and
  `widgets` jsonb `[{queryId, chart, options, title, layout: {x, y, w, h}}]` (a row saved before the grid has `size` S/M/L and no layout; the API answers that size, the SPA flows it into place and every save sends it back with a layout). The choice only, never a figure. Forced RLS.
- **Library** — `dashboard/service/ProjectChartQueries` (15) and `WorkspaceChartQueries` (11), registered by
  `ChartQueryLibrary`. Each `ChartQuery` declares its shape (`KPI`, `CATEGORICAL`, `TIME_SERIES`, `MATRIX`, `RANKED`),
  whether its parts add up (`notAdditive`: a median never draws as a pie), and its options — built through the
  shape factories (`ChartQuery.kpi`, `.breakdown`, `.overTime`, `.breakdownByTwo`, `.ranked`). Position queries read
  through `ReportService` and `CandidateService.listAllOfProject` as staff; workspace ones through
  `ProjectService.list` and `WorkspaceChartData`'s grouped SQL, which names the workspace and the positions in every
  statement and runs under a 5-second query timeout.
- **Seat-gated** — workspace charts count only the positions the reader may work on (`viewerCanWork`), never every
  mandate they may browse: project content is `WORK_VIEW`/`WORK_EXECUTE`, not `PROJECT_BROWSE`.
- **One read per dashboard** — the SPA asks for every chart at once (`POST …/answers`); the charts share the report,
  the executives and the positions through `ChartQueryContext.sharedRead`, and the SPA keeps an answer for a minute.
  Each chart runs in a transaction of its own, so one that fails (a statement past its time limit) answers
  `CHART_UNAVAILABLE` and the rest still draw.
- **Chart rules** — `ChartRules` (server) and `chartRules.ts` (SPA) agree: a pie or donut only for parts of one whole
  of at most eight slices; a series as line, area, column or table; a two-way breakdown as stacked bar or table. A
  save naming a chart type its query cannot take is `CHART_TYPE_NOT_ALLOWED`; a run answers `allowedCharts` for that
  very answer, and a saved chart that no longer fits says so.
- **Shaping** — `ChartShaping` cuts a series to the asked period, a breakdown to its top N (the rest folded into
  Other where the parts add up), and every breakdown to 20 labels.
- **Retired queries** — a widget whose query left the library stays on the dashboard exactly as stored (a save keeps
  the stored widget, never the request's spelling of it; `DashboardWidgetValidator`) and draws "This chart is no longer
  available". Duplicate (`POST …/{id}/duplicate`) leaves such charts behind, so the copy can be saved.

## Endpoints

| | Position | Workspace |
|---|---|---|
| List, create | `GET/POST /api/v1/projects/{id}/dashboards` | `GET/POST /api/v1/dashboards` |
| Save, delete | `PUT/DELETE …/dashboards/{dashboardId}` | `PUT/DELETE /api/v1/dashboards/{dashboardId}` |
| Library | `GET …/dashboards/library` | `GET /api/v1/dashboards/library` |
| Run a query (the drawer's preview) | `POST …/dashboards/query/{queryId}` `{options}` | `POST /api/v1/dashboards/query/{queryId}` |
| Answer a dashboard's charts | `POST …/dashboards/answers` `{charts}` | `POST /api/v1/dashboards/answers` |
| Duplicate | `POST …/dashboards/{dashboardId}/duplicate` | `POST /api/v1/dashboards/{dashboardId}/duplicate` |
| Gate | `WORK_EXECUTE` | `@workspaceAuthorizer.staffOnly` (a 404 for a pure client) |

Errors: `DASHBOARD_NOT_FOUND` (not visible), `DASHBOARD_READ_ONLY` (a teammate's shared one), `DASHBOARD_STALE`
(optimistic `version`), `DASHBOARD_TOO_MANY_WIDGETS` (30), `CHART_QUERY_NOT_FOUND`, `CHART_TYPE_NOT_ALLOWED`,
`CHART_OPTION_INVALID`, and in an answers batch `CHART_UNAVAILABLE` (that one chart failed).

## Personal data

- **Staff only.** A client seat reaches no dashboard and keeps the report's Overview.
- **Gender** (`genderByLevel`) counts only a value a researcher recorded or confirmed — one still in
  `aiInferredFields` is left out — and a level is held back and zeroed before it leaves the server when fewer than 3
  are recorded there or when any gender in it counts 1 or 2, so nobody can be singled out (two men and one woman
  would otherwise name the woman). Female, Male and Other are always all drawn, so an Other column that is empty on
  every shown level cannot point at a hidden one. Aggregate only: no query lists people by gender.
- **Nationality** has no library query until the Art. 9 decision (`gdpr/governance-decisions.md` C2).
- A chart reads what its reader may read today; nothing is cached on the dashboard.

## Home

Home (`features/home`) is drawn on the same grid: Needs you, Today and Your positions are tiles beside charts from the
workspace library, and **Customize** is Edit layout on Home (`DashboardGrid`, `ChartLibraryDrawer`, `ChartCard`, one
`POST /dashboards/answers` for every chart on it). Each section has its own minimum size (`homeLayout.ts`
`SECTION_SIZES`; Needs you at least 5 × 6 and never removable), a chart its type's.

- **Storage** — `app_lm_home_layout` (V233), one row per member per workspace, absent until their first save:
  `tiles` (`{kind, layout}`, a `CHART` tile with the widget's `queryId`, `chart`, `options`, `title`), `needs` (the
  Needs you groups shown) and `landing`. Saving the default deletes the row, so the person keeps following the default.
- **Endpoints** — `GET /api/v1/me/home` (`{layout}` with its `version`, null without a row), `PUT` (the whole layout
  and the `version` it was opened at, null when it opened the default; any other → `HOME_LAYOUT_STALE`, so a stale
  window never overwrites a newer save or a reset), `DELETE ?version=` (reset, over the version it was opened at —
  none when it opened the default — so a stale window saving the default never deletes a newer layout; with no row
  stored it answers 204 whatever version was sent, since the person is already on the default). `@workspaceAuthorizer.staffOnly`: a pure client's Home
  has nothing to arrange.
- **Checks** (`HomeLayoutService`) — exactly one `NEEDS`, at most one `TODAY` and `POSITIONS`, every tile on the
  grid and a section at least its minimum (`HomeTileKind`), at most 12 charts, each one a workspace library chart checked by `DashboardWidgetValidator` (a retired query
  already saved is kept); `needs` keys are `ConversationGroup` names; `landing` `HOME` or `POSITIONS`. Anything else is
  `VALIDATION_FAILED`.

## Not built yet

- Sharing a dashboard with the mandate's client seat (preset, non-staff charts only, re-run under client narrowing).
- Dashboard-level filters (one date range for every chart).
- Outreach and task charts beyond what the report and project list already aggregate.
