# Module boundaries (`apps/api`)

Every top-level package under `app.lightmove.api` is a Spring Modulith application module. The boundaries are
checked against the compiled classes on every `./mvnw test`, so CI fails when code breaks them, whoever wrote it.

- `ModuleBoundariesTest` checks that there is no cycle between modules, no dependency a module has not declared,
  and no use of another module's internal types. It also writes the module canvases and PlantUML to
  `target/spring-modulith-docs/`.
- `ModuleConventionTest` checks that:
  - a module publishes no repository, controller or configuration type;
  - every module declares `allowedDependencies`;
  - the declared graph has no cycle;
  - `core` depends on nothing and `common` only on `core`. Modulith skips open modules in its own cycle check, so
    this test covers it.

The plan and the reasoning behind it are in [`module-boundaries-plan.md`](module-boundaries-plan.md).

## The layers

Each module may depend only on the layers below it. Its `package-info.java` lists exactly which modules.

| Layer | Modules |
|---|---|
| 0 | `core`: platform kernel. Open; may depend on nothing. |
| 1 | `common` (shared vocabulary; open, → core) · `billing` |
| 2 | `universe` (the Apollo company universe) · `positiontemplate` · `geocoding` |
| 3 | `workspace` |
| 4 | `account` · `project` |
| 5 | `customcolumn` · `strategy` · `position` |
| 6 | `triagecompany` |
| 7 | `candidate` |
| 8 | `pairing` · `report` · `enrichment` · `outreach` |
| 9 | `dataimport` · `retention` · `task` · `dataexport` · `publicapi` · `talentmap` |
| 10 | `notification` · `notetaker` · `mcp` |
| 11 | `assistant` |

`core` and `common` are the shared kernel: `@Modulithic(sharedModules = {"core", "common"})` on
`LightMoveApplication`. Every module may use them without listing them.

## What a module publishes

A module's API is its one named interface, `api`:

- every type in its `dto` and `constant` packages, through `@NamedInterface(name = "api", propagate = false)` on
  their `package-info.java`;
- each service or model type that other modules may use, marked with the same annotation on the type.

Nothing else is visible to other modules, and repositories, controllers and `@Configuration` classes never are.
`propagate = false` matters: with the default, the repositories in a service's constructor would be published along
with the service.

## When the build fails

| Message | What happened | Fix |
|---|---|---|
| `Module 'a' depends on non-exposed type x.y.Z within module 'b'` | `a` uses something `b` has not published. | If it is a service or model `b` means to offer, mark it `@NamedInterface(name = "api", propagate = false)`. If it is a repository, add or extend a facade in `b` (`ProjectLookup`, `WorkspaceLookup` and `ClientLookup` show the shape). |
| `Module 'a' depends on named interface(s) 'b :: api' … Allowed targets: …` | `a` uses `b` without declaring it. | If `b` is in a lower layer, add `"b :: api"` to `a`'s `allowedDependencies`. If `b` is in the same layer or above, don't add it; invert instead (below). |
| `Cycle detected: Slice a -> Slice b -> Slice a` | Two modules now reach each other. | Invert the newer edge (below). |
| `ModuleConventionTest.apiHoldsNoInternals` | A repository, controller or config type was published. | Publish a facade or a DTO instead. |
| `ModuleConventionTest.kernelUsesNothingAbove` | `core` uses a feature, or `common` uses anything but `core`. | Move the needed type down into `core`, or put a port in `core` and implement it above. |
| `ModuleConventionTest.declaredGraphIsAcyclic` | `allowedDependencies` were widened into a loop. | Remove the upward entry and invert. |

## Inverting a dependency

When a lower module needs something from a higher one, pick the first of these that fits:

1. **Publish an event** from the lower module and listen in the higher one.
   - Example: `project` publishes `ProjectCreated`, and `position`'s `PositionSeeding` drafts the brief.
   - Use a synchronous `@EventListener` with `@Transactional(propagation = MANDATORY)` when the work must stay in
     the same transaction. `@ApplicationModuleListener` runs asynchronously after commit, which changes behaviour.
2. **Declare a port** (an interface) in the lower module and implement it in the higher one.
   - Examples: `core`'s `AuthResponseAssembler`, `WorkspaceNames` and `WorkspaceDateTimeDefaults` are answered
     by `workspace`.
   - `project`'s `ProjectCompanyCounter` is answered by `triagecompany`.
3. **Move the shared type down** to the lowest module that owns its meaning.
   - Example: membership moved into `core.security.membership` because every sign-in reads it.

Never fix a failure by widening `allowedDependencies` upward, by making a module OPEN, or by publishing a
repository.

## Adding a module

1. Create the top-level package.
2. Give it a `package-info.java` with `@ApplicationModule(allowedDependencies = { … })` naming only lower layers.
3. Mark its `dto` and `constant` packages `@NamedInterface(name = "api", propagate = false)`.

`ModuleConventionTest` fails until the module declares its dependencies.

## Follow-ups

- **Close `core`.** It is open today, so features can reach platform internals. Giving it named interfaces would
  narrow that.
- **Table ownership.** Map the `app_lm_*` tables to modules, and check that a module's SQL names only its own
  tables. `ArchivedProjects.IDS_SQL`, which tasks and notifications embed, is the known exception.
- **After-commit side effects.** Where after-commit semantics are wanted, move side effects such as notifications
  and audit to `@ApplicationModuleListener` with the event publication registry.
- **Frontend.** Give `apps/web` the same treatment: feature `index.ts` public APIs plus an import-cycle lint.
