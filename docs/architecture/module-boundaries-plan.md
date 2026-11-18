# Module boundaries plan: Spring Modulith for `apps/api`, no cycles

> Status: **proposed**. Docs only; no code has changed yet. Each numbered commit below lands separately and is reviewed on its own.

## Context
- **The problem.** Nothing in code backs CLAUDE.md's dependency rules today, and the code already breaks them.
- **What was measured.** The `src/main` import graph was mapped by top-level package. One cycle (a strongly connected component) spans 8 packages: core, common, workspace, billing, project, strategy, position and positiontemplate. The other 20 feature packages are acyclic above it.
- **The goal.** Clean, behaviour-identical module boundaries. Then a build that fails whenever a person or an agent:
  - adds a cycle,
  - reaches another module's internals,
  - or adds an undeclared module dependency.
- **Decisions taken.**
  - *Break the cycles first, add enforcement last.* So strict `verify()` goes in with no allow-list and no baseline.
  - *Per-type API.* A module exposes its `dto` and `constant` packages, plus individually marked service and model types. Repositories, controllers and config are never exposed.
- **Research.** Checked against the Spring Modulith **2.1.2** sources from Maven Central. 2.1.x is the line for Boot 4.1. Confirmed:
  - `Type.OPEN` exposes all of a module's types and is excluded from cycle detection.
  - `allowedDependencies = {}` means the module may depend on nothing; leaving it at the default means unrestricted. The `"m :: *"` syntax allows all of m's named interfaces.
  - `@NamedInterface` can go on a single type, with `propagate=true`.
  - Explicit `allowedDependencies` are enforced even for OPEN origin modules.
- **Best practice applied.**
  - Layered DAG: the kernel at the bottom, and no edge pointing back down.
  - Inversion via ports or domain events, never via a new back-edge.
  - Synchronous `@EventListener` where transaction semantics must not change. `@ApplicationModuleListener` is async after commit, so it would change behaviour.
  - The shared kernel depends on nothing.

## What mature SaaS modular monoliths do, and how this plan follows it
- **Shopify (Packwerk, the largest public example):**
  - The *dependency graph* enforcement, with a CI check that blocks regressions, paid off. The acyclic **declared** graph is the one hard-coded meta-rule.
  - Fine-grained *privacy* checks were dropped in Packwerk 3.0, because they turned into API-design busywork.
  - → Here, `allowedDependencies` plus acyclicity is the hard rule. Privacy stays strict, but is cheap: `dto`/`constant` are exposed as whole packages, the type-level API set is generated from Modulith's report, and `propagate=true` pulls in signature types automatically.
- **Spring Modulith guidance:**
  - Fix a cycle with an event, by moving shared types down, or by reassigning ownership.
  - Never call another module's repository; go through its service API.
  - Run verification in CI.
  - → Commits 3 (event), 1/4/5 (move types down, reassign ownership) and 6 (facades).
- **Modular monolith with DDD (Grzybek):**
  - Each module owns its data. No cross-module joins or foreign keys; go through the owner's interface or its events.
  - → This plan enforces this at the *code* level only. Table ownership is a follow-up.
- **Shared kernel:** small, stable, depends on nothing.
  - → `core` gets `allowedDependencies = {}`, and `common` → core only.
  - `core` is a platform layer, not a tiny kernel, so it stays OPEN for now. Closing it is a follow-up.

## Target module DAG (bottom-up; each module may depend only on lower layers)
| L | Modules |
|---|---|
| 0 | `core` (OPEN, `allowedDependencies = {}`) |
| 1 | `common` (OPEN, → core), `billing` |
| 2 | **`universe`** (new: the Apollo company universe), `positiontemplate`, `geocoding` |
| 3 | `workspace` |
| 4 | `account`, `project` |
| 5 | `customcolumn`, `strategy`, `position` |
| 6 | `triagecompany` |
| 7 | `candidate` |
| 8 | `pairing`, `report`, `enrichment`, `outreach` |
| 9 | `dataimport`, `retention`, `task`, `dataexport`, `publicapi`, `talentmap` |
| 10 | `notification`, `notetaker`, `mcp` |
| 11 | `assistant` |

## Commits on `claude/affectionate-dijkstra-m6co9b`, in order, each one compiling and green
Every commit is either a pure package move or the introduction of a port or event. None changes logic, bean names, URLs, JSON shapes or the SQL. For each moved class, also grep for its fully qualified name in JPQL `select new …` strings, yml, logback config and tests. Javadoc `{@link}`s that would cross back down become `{@code}`.

### 1. Kernel layering: remove `core → common` (21 imports)
- Move `ApiValueEnum` and `ApiValueTokenIndex` (package-private) to `core.constant`. About 105 importers; rewrite the import lines.
- Move `ClasspathJsonLoader` to `core.json`.
- Move `DateFormatStyle`, `TimeFormatStyle`, `DateTimePreferences`, `OfferedTimeZone`, `StandardTimeZones` and `DateTimeFormats` to `core.time.{constant,model,service}`. `CountryTimeZones` stays in common, since it needs `Countries`.
- Move `CurrencyCode`, `CurrencyCodeValidator`, `SuppliedCurrencyCode` and `SuppliedCurrencyCodeValidator` out of `core.validation` to `common.location.validation`. `Currencies` needs `Countries`, so it stays in common.
- Move the tests with them: `ApiValueEnumTest`, `DateTimeFormatsTest`, `StandardTimeZonesTest`.

### 2. `billing → workspace` (only `BillingNotices` → `DateTimePreferenceService`)
- Add a port `core.time.DateTimePreferenceLookup` with just the methods BillingNotices calls. `DateTimePreferenceService` implements it.
- `workspace → billing` (`SeatAllowance`, `WorkspaceTrials`, `WorkspaceBillingClosure`) stays.

### 3. `project → position` (`ProjectService.create` calls `positionService.seedFor`)
- Publish `project.model.ProjectCreated(workspaceId, projectId, positionTitle, hqCountry, actorUserId)` at the exact line where `seedFor` is called today.
- Add `position.service.PositionSeeding`, a synchronous `@EventListener` with `@Transactional(propagation = MANDATORY)`. This follows the existing `PositionArchiveOutreach`/`ProjectArchived` precedent: same thread, same transaction, same ordering, and an exception still rolls back.
- Add or confirm an integration test asserting a brief exists after a project is created. This guards against a listener that silently stops firing.

### 4. Extract `universe`: removes `workspace|project|common → strategy`
Move 27 main files from `strategy` to `app.lightmove.api.universe.{service,model,dto,constant,controller}`:
- **service:** `ApolloCompanyQueryService`, `WhereClause`, `SectorTaxonomy`, `MarketSegments`, `ClasspathVocabulary`, `IndustryAdjacency`, `CompanySuggestionSearch`, `UniverseFacets`
- **model:** `CompanyRow`, `ScopeBreakdown`, `CompanyScope`, `NumericRange`, `ScoredCompanyRow`, `SimilarityScope`, `CompanyExclusion`
- **dto:** `CompanySuggestionsResponse`, `CompanySuggestion`, `FacetCount`, `SectorGroup`, `FacetsResponse`, `KeywordSuggestionsResponse`, `CompanyResultDto`
- **constant:** `CompanySizeBand`, `EmployeeBand`, `RevenueBand`, `CompanySortField`
- **controller:** `CompanySearchController`, and `common.location.controller.CountryController`

Notes:
- `NumericRange` is stored inside Strategy JSON by plain Jackson with no type info, so the move is safe for stored data.
- `/api/v1/companies/people-facets` uses strategy's ContactOut vocabulary. It stays in strategy, in a small controller on the same path (the literal route beats `/{apolloAccountId}`), so the URL and JSON are unchanged.
- `SortDirection` goes to `core.constant`. Pairing then drops its strategy dependency.
- Move the tests with them (about 10 classes, e.g. `CompanyFacetIntegrationTest`, `SectorTaxonomyTest`, `UniverseFacetsTest`). 23 test files need import updates.

### 5. Access kernel: removes `core → workspace` (24) and `core → project` (4). All of it is in `core/security`.
- **Membership is access data.** Move `WorkspaceMember`, `MemberStatus` and `WorkspaceMemberRepository` to `core.security.membership` (about 60 import rewrites).
  - This clears `WorkspaceAccess`, `WorkspaceSelection`, `AuthenticationService`, `PasswordChangeService`, `TokenService` and `AuthenticatedSession`.
  - `WorkspaceAccess` stays in core, so billing stays → core only.
- **Project seats belong to project.**
  - Move `ProjectAccess` and `ProjectAuthorizer` to `project.service`, keeping `@Component("projectAuthorizer")`. Core's `@RequireProjectPermission` names that bean only as a SpEL string.
  - Move `core/stream/ProjectStreamController` to `project.controller`.
  - Move `PublicApiAuthorizer`, together with the `RequirePublicScope` and `RequirePublicProjectRead` annotations, to `publicapi`, keeping the bean name `"publicApiAuthorizer"`.
  - `ProjectAudience` stays in core.
- **Session responses through a port.**
  - `core.security.controller.AuthResponseAssembler` becomes an interface with today's three methods: `assemble`, `user` and `userIn`.
  - Today's class moves unchanged to `workspace.service.SessionResponseAssembler` as the implementation. Callers don't change.
  - Move `WorkspaceSummary` and `WorkspaceCompanyResponse` to `core.security.dto`, and `WorkspaceMode` to `core.tenant`. Records and enum constants are unchanged, so the JSON is identical.
  - `WorkspaceCompanyResponse.of(WorkspaceCompany)` becomes `WorkspaceCompany#toResponse()` (4 callers).
- **OAuth consent.** Add a port `core.security.oauth.WorkspaceNames.namesOf(Collection<UUID>) → Map<UUID,String>`, implemented in workspace.
- **Side effect:** report stops depending on workspace.

### 6. Repository facades (repositories are never exposed)
Other modules must stop importing a feature's repositories. Today that means:
- `ProjectRepository`: 34 files, including 96 `requireInWorkspace` calls
- `WorkspaceRepository`: 7
- `ClientRepository`: 2
- `ProjectMemberRepository`: 1
- `InvitationRepository`: 1

Add thin facades that delegate exactly to the repository methods used today, so behaviour is identical:
- `project.service.ProjectLookup`: `requireInWorkspace`, `findById`, `findAllById`, `requireNotArchived`, `isArchived`, `findIdsInWorkspace`, `defaultCurrencyOf`, …
- `project.service.ClientLookup`
- `workspace.service.WorkspaceLookup`

### 7. Spring Modulith plus enforcement
- **`pom.xml`:** import the `spring-modulith-bom` 2.1.2. Add `spring-modulith-api` at compile scope (annotations only, no runtime behaviour) and `spring-modulith-starter-test` at test scope.
- **`LightMoveApplication`:** add `@Modulithic(sharedModules = {"core", "common"})`.
- **`package-info.java` on every top-level module** (29; about 10 already exist and only gain the annotation). Each declares `@ApplicationModule(displayName = …, allowedDependencies = {"workspace :: *", …})` with exactly its DAG edges.
  - `core`: OPEN, `{}`.
  - `common`: OPEN, `{"core"}`.
- **API (named interfaces):**
  - Package-level `@NamedInterface` on each module's `dto` and `constant` packages.
  - Type-level `@NamedInterface("api")` on each service or model type another module uses. Seed this from `ApplicationModules.detectViolations()` output, not by hand.
- **Tests** in `src/test/java/app/lightmove/api/architecture/`. They are plain JUnit, run in seconds and are already run by CI's `./mvnw -B test`.
  - `ModuleBoundariesTest`: `ApplicationModules.of(LightMoveApplication.class).verify()`. It fails on cycles, non-exposed types and undeclared dependencies.
  - `ModuleConventionTest` (ArchUnit, a transitive dependency of Modulith) fails when:
    - a type in `..repository..`, `..controller..` or `..config..`, or any `Repository` subtype, carries `@NamedInterface`;
    - a top-level package lacks `@ApplicationModule` with explicit `allowedDependencies`;
    - a module other than core or common is OPEN;
    - core's `allowedDependencies` is not exactly `{}`, or common's is not exactly `{"core"}`. OPEN modules skip cycle detection, so this is their cycle guard.
  - `ModuleDocumentationTest` writes the Documenter canvases and PlantUML to `target/` for review.
- **Docs:**
  - New `docs/architecture/modules.md` with the DAG table, how to expose a type, how to add a dependency (never upward; use an event or a port), and what each failure message means.
  - In CLAUDE.md, replace "Feature dependency directions" with a 3-line pointer to the test and the doc, so the file stays within its ~12 KB budget.
  - Add a boundaries section to the `java-spring-development` skill.

## Follow-ups (outside this plan; to be listed in `modules.md`)
- **Close `core`.** Give it named interfaces for `security.model`, `rbac`, `error`, `tenant`, …, so features stop reaching platform internals.
- **Table ownership.** Map `app_lm_*` tables to modules, then add a check that a module's native SQL and JPQL name only its own tables (Grzybek's data-ownership rule).
- **Async side effects.** Move side effects such as notifications and audit to `@ApplicationModuleListener` with the JPA event publication registry, where after-commit semantics are wanted.
- **Frontend.** The same treatment for `apps/web`: feature `index.ts` public APIs plus import-cycle lint.

## Verification
- After each commit:
  - `cd apps/api && ./mvnw -B test` (full Testcontainers suite, including the OpenAPI contract test `docs/public-api/openapi.json`).
  - Rerun the import-graph SCC script; it must show no back-edge for the edge just fixed.
- After commit 7:
  - `verify()` is green.
  - **Negative checks**, done temporarily and reverted:
    - (a) `core` importing a workspace type,
    - (b) `candidate` importing `outreach`, which creates a cycle,
    - (c) `outreach` importing `ProjectRepository`,
    - (d) `@NamedInterface` on a repository.
  - Each must fail the build with a clear message.
- The `/verify` skill (API + SPA end to end) before committing: sign in, switch workspace, create a position (to check the brief seeding), company search and country facets, `/me`, and an OAuth consent screen if it's reachable locally.
- Push, then open a draft PR with one commit per step for review.

## Sources
- Spring Modulith 2.1.2 sources: `ApplicationModule`, `NamedInterface`, `Violations`, `ApplicationModule.QualifiedDependency#isValidDependencyWithin` (Maven Central `spring-modulith-{api,core}-2.1.2-sources.jar`)
- [A Packwerk Retrospective – Shopify](https://shopify.engineering/a-packwerk-retrospective)
- [Packwerk PR #247: privacy checks moved out](https://github.com/Shopify/packwerk/pull/247)
- [Packwerk discussion #337: the declared graph must be acyclic](https://github.com/Shopify/packwerk/discussions/337)
- [Packwerk discussion #353: cycles as TODO](https://github.com/Shopify/packwerk/discussions/353)
- [JetBrains: Migrating to a modular monolith using Spring Modulith](https://blog.jetbrains.com/idea/2026/02/migrating-to-modular-monolith-using-spring-modulith-and-intellij-idea/)
- [Kamil Grzybek: Modular Monolith Integration Styles](https://www.kamilgrzybek.com/blog/posts/modular-monolith-integration-styles)
- [kgrzybek/modular-monolith-with-ddd](https://github.com/kgrzybek/modular-monolith-with-ddd)
- [Milan Jovanović: Shared Kernel pattern in modular monoliths](https://milanjovanovic.tech/blog/shared-kernel-pattern-modular-monolith)
