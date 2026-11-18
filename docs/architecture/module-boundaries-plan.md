# Module boundaries plan: Spring Modulith for `apps/api`, no cycles

> Status: **implemented**. The result is described in [`modules.md`](modules.md); this page keeps the reasoning and the research behind it.

## Context
- **The problem.** Nothing in code backs CLAUDE.md's dependency rules today, and the code already breaks them.
- **What was measured.** The `src/main` import graph was mapped by top-level package. One cycle (a strongly connected component) spans 8 packages: core, common, workspace, billing, project, strategy, position and positiontemplate. The other 20 feature packages are acyclic above it.
- **The goal.** Clean, behaviour-identical module boundaries. Then a build that fails whenever a person or an agent:
  - adds a cycle,
  - reaches another module's internals,
  - or adds an undeclared module dependency.
- **Decisions taken.**
  - *Break the cycles first, add enforcement last.* So strict `verify()` goes in with no allow-list and no baseline.
  - *Per-type API.* A module exposes its `dto` and `constant` packages, plus individually marked service and model types. Repositories, controllers and config are never exposed, except inside the open `core`/`common` kernel.
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

## What was done

The layers, each step's commit and the rules that now fail the build are in [`modules.md`](modules.md) and in
`git log` for PR #1396. In short:

- **Cycles broken first, enforcement last,** so strict `verify()` ran with no allow-list and no baseline.
  The 8-package cycle came apart through six behaviour-identical changes: shared types moved down into `core`; the
  Apollo company universe extracted into its own `universe` module; membership moved into `core.security`; project
  access checks moved into `project`; ports (`AuthResponseAssembler`, `WorkspaceNames`, `WorkspaceDateTimeDefaults`)
  and one synchronous event (`ProjectCreated`) replaced the upward calls.
- **Facades** (`ProjectLookup`, `ClientLookup`, `WorkspaceLookup`) replaced the cross-module repository imports.
- **Enforcement:** Spring Modulith 2.1.2 `verify()`, plus `ModuleConventionTest` for what Modulith skips (open
  and shared modules).

## Follow-ups (listed in `modules.md`)
- **Close `core`.** Give it named interfaces for `security.model`, `rbac`, `error`, `tenant`, …, so features stop reaching platform internals.
- **Table ownership.** Map `app_lm_*` tables to modules, then add a check that a module's native SQL and JPQL name only its own tables (Grzybek's data-ownership rule).
- **Async side effects.** Move side effects such as notifications and audit to `@ApplicationModuleListener` with the JPA event publication registry, where after-commit semantics are wanted.
- **Frontend.** The same treatment for `apps/web`: feature `index.ts` public APIs plus import-cycle lint.

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
