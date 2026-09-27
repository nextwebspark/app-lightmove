# Workspace modes — Agency vs In-house company

## Context

A workspace today is shaped as an **in-house company**. Its `app_lm_client` rows are the firm's own
**business units**, the Clients screen and drawer show a name and notes only, and the assistant's
system prompt says "a mandate's client is one of the firm's departments or business units".

The product must also serve **search agencies**, where each client is a separate hiring company. The
data model already supports this: a `Client` still carries an Apollo snapshot (`company_source[_id]`,
`sector`, `hq_country`, `hq_city`, `domain`, `logo_url`, `off_limits_note`). Commits `f2ee559` and
`7ce30a5` hid those fields and renamed the screens to "Business unit". No endpoint, column or DTO was
removed.

This change adds a per-workspace **mode**, chosen when the workspace is created and switchable later
by an admin:

| | **COMPANY** (in-house; all existing workspaces) | **AGENCY** |
|---|---|---|
| Sub-unit | "Business unit" / "Hiring manager" (unchanged) | "Client" / "Client contact" |
| Creating one | Name only, with the org-structure list from `Clients.dc.html`. No global search | Search the global company universe (Apollo), or type a company that isn't listed |
| Client drawer and grid | Name and notes (unchanged) | The full company card: logo, industry, HQ, website, LinkedIn, headcount, off-limits, plus a **client persona** |
| AI persona | The workspace persona (unchanged) | The project's **client persona** is the hiring context; the agency's own persona is one short line |

**Decisions confirmed with you:**
- Company mode keeps "Business unit".
- An admin can switch the mode in Settings → General. The switch is audited and asks for confirmation.
- Agency AI gets the client persona plus a brief line about the agency.
- The create flow has no default: you must pick a mode. Existing workspaces are backfilled to `COMPANY`.

**Guiding rule:** one data model and one set of routes and tables. The mode changes **labels, which
fields a screen shows, the create-client path, and the AI context**. It never changes storage or
RBAC. That is why switching is safe: nothing is migrated on a switch.

---

## Engineering standards (every phase; this will be reviewed)

Load the `java-spring-development`, `react`, `lightmove-domain` and `db-ops` skills before touching their areas, and copy the neighbouring code's idiom rather than inventing a new one.

**Backend layout.** Each module is laid out by type:
- `constant/` holds **every enum**. `WorkspaceMode` goes in `workspace/constant/`.
- `model/` holds entities and internal records, and never enums or HTTP payloads.
- `dto/` holds one HTTP record per file.
- `service/` holds the logic, and `controller/` the `@RestController` classes only.
- Shared vocabulary that two features speak goes under `common/`. The persona moves to `common/persona/model/HiringPersona`, the same shape as `common/industry` and `common/location`.

**Feature seams.** Features depend on each other only through a public service method plus the records it returns, and only in the sanctioned direction.
- The assistant reads a client through a new `ClientService.hiringProfileOf(workspaceId, clientId)`. It returns a `ClientHiringProfile` record in `project/model/`, and the assistant never touches `ClientRepository`.
- `project` must not learn about the assistant.
- If a reverse read is ever needed, use the `ProjectCompanyCounter`-style interface inversion.

**Spring conventions.**
- Constructor injection via `@RequiredArgsConstructor` only, with `@Slf4j` for logging.
- Entities use `@Getter` + `@NoArgsConstructor(access = PROTECTED)`, and a state change is an intention-named method (`Workspace.changeMode`, `Client.describePersona`). Never a public setter, `@Data` or `@Builder`.
- `@Transactional` boundaries live in services, with `readOnly = true` on reads. No LLM or Apollo call runs inside a write transaction.
- Every tenant query filters by `AuthPrincipal.requireWorkspaceId()`.
- Gates are `@PreAuthorize` over **actions** on controllers only (`WORKSPACE_MANAGE`, `CLIENT_RECORD_MANAGE`). This adds no new role checks, and nothing branches on a role.

**Validation and errors.**
- Bean Validation on request records (`@NotNull WorkspaceMode mode`, `@Size` caps mirroring `HiringPersona.MAX_LIST_ITEMS`).
- Refusals are `ApiException` with an `ErrorCode`, rendered as RFC 9457 by `GlobalExceptionHandler`. `userFacing` sentences are fixed and never interpolated.
- The SPA switches on `code`.

**Audit.** Mode changes and client persona writes go through `AuditService`, as the existing persona and settings writes do (section `"mode"`, section `"persona"`).

**Migrations.**
- New Flyway files only (V83-V85), with an `app_lm_` prefix and a CHECK constraint for the enum (V34 idiom).
- Backfill before any constraint relies on it, and never edit an applied migration.
- The Java enum and the CHECK list must name the same values; the integration tests round-trip every mode through the database.

**Comments.** They are the exception: a one- or two-line class doc, and a *why* only where it is invisible from the code. Never narrate. Every type name must read standalone.

**Tests.**
- Integration tests extend the existing Testcontainers bases (`WorkspaceSettingsIntegrationTest`, `WorkspaceCompanyIntegrationTest`, the client ones) with `<behaviour>` method names.
- Pure logic (`HiringPersona`, `HiringContextRenderer`, `vocabularyFor`) gets plain unit tests.
- `WorkspacePersonaTest` is renamed and migrated to `HiringPersonaTest`, not duplicated.

**Frontend.**
- Follow the `react` skill: the feature-folder layout, TanStack Query keys, Zod schemas in `schemas.ts`, and types in each feature's `api/types.ts`.
- No new UI library.
- The mode lives in one hook (`useWorkspaceVocabulary`). Components never read `mode` themselves just to choose a string.
- Branching on the mode is confined to small, named components (`ClientMark`, `ClientCombobox` vs `BusinessUnitCombobox`, `ClientCompanyCard`), so the screens keep their structure. There is no `mode === 'AGENCY'` scattered through JSX.
- `npm run build` is the typecheck gate.

**Pre-push checks (each PR):**
- `./mvnw test`, `npx vitest` and `npm run build` pass.
- Re-read the diff adversarially.
- Update CLAUDE.md and the skill paragraphs in the same PR as the behaviour they describe.

---

## Phase 1: Mode on the workspace (backend foundation)

**Migration `V83__workspace_mode.sql`:**
- `app_lm_workspace.mode varchar(16) NOT NULL DEFAULT 'COMPANY'`.
- A CHECK constraint `IN ('AGENCY','COMPANY')`, in V34's idiom.
- Existing rows get `COMPANY` from the default.

**Enum:** `WorkspaceMode { AGENCY, COMPANY }` in `workspace/constant/`.
- Mapped on `workspace/model/Workspace.java`.
- `Workspace.create(...)` takes the mode.
- A new `changeMode(mode)` method.

**Creation:**
- Both creation doors share `CreateWorkspaceRequest`: `POST /onboarding/workspace` (`OnboardingController.java:62`) and `POST /workspaces` (`WorkspacesController.java:33`).
- `CreateWorkspaceRequest` gets `@NotNull WorkspaceMode mode`. That enforces "must pick".
- The mode is threaded through `CreateWorkspaceCommand` and `OnboardingService.createFirstWorkspace` / `createWorkspace`.
- The `PATCH /onboarding/workspace` re-describe (the wizard's Back button) also accepts it.

**Switching:**
- A dedicated `PUT /workspace/mode` taking `UpdateWorkspaceModeRequest`, gated by `WORKSPACE_MANAGE` (ADMIN), beside `PUT /workspace/persona`. It is kept off `PATCH /workspace` because that request requires the firm's name and company, which a mode switch has no business resending.
- `WorkspaceSettingsService` audits the change as a mode change (section `"mode"`, from → to).

**Exposure:**
- `mode` is added to `WorkspaceSummary` (`workspace/dto/WorkspaceSummary.java`). `/me`, login and every `AuthResponse` then carry it, so every screen can read it from `useAuth().user.workspace.mode`.
- It is also added to `WorkspaceDetail` (`GET /workspace`).
- It is sent to pure clients too. A hiring-company representative needs the right labels, and the mode is not sensitive.

**Tests:**
- Creation without a mode returns 400.
- Creation with each mode persists it.
- `/me` carries the mode.
- The mode switch is ADMIN-only (a MEMBER gets 403 or 404 per the existing pattern) and writes an audit event.
- `RbacCatalogTest` is untouched, because this adds no new action.

## Phase 2: A shared vocabulary in the SPA

> **Built** (PR #565) as `features/workspace/lib/vocabulary.ts` (`vocabularyFor`, `useWorkspaceMode`,
> `useWorkspaceVocabulary`) plus `ClientMark`. `TeamAccessPage` and `AddClientContactModal` keep
> "Client" as their mockup does; the backend's seven fixed sentences now read true in both modes
> ("Representatives are invited to a position…"). The notes below are the original plan.

There is no central vocabulary today. The strings are inlined in about 15 files.

**New module `apps/web/src/lib/workspaceVocabulary.ts`:**
- A pure `vocabularyFor(mode)` function and a `useWorkspaceVocabulary()` hook reading `useAuth().user.workspace?.mode`.
- It returns singular and plural forms plus the "N open …" helpers.

| Key | COMPANY | AGENCY |
|---|---|---|
| `unit` | Business unit | Client |
| `units` | Business units | Clients |
| `unitRecord` | Business unit record | Client record |
| `rep` / `reps` | Hiring manager(s) | Client contact(s) |
| `noUnit` | No business unit | No client |
| `newUnit` | New business unit | New client |

**Replace the inlined strings:**
- The files to change are `WorkspaceLayout.tsx:77` (nav), `ClientsPage`, `clientColumns`, `ClientsList`, `filtering.ts`, `ClientDrawer`, `NewClientModal`, `NewProjectModal`, `BusinessUnitCombobox`, `projects/lib/grouping.ts`, `ProjectsPage.tsx:187`, `ProjectDrawer`, `projects/lib/access.ts`, `TeamAccessPage` and `AddClientContactModal`.
- In `lib/errorCodes.ts`, `CLIENT_ALREADY_EXISTS` becomes a function of the vocabulary. The error code itself is unchanged.
- The backend `detail` strings that say "business unit" become neutral ("…this record…"): `UpdateClientRequest`, `CreateProjectRequest` ("Choose a business unit"), `ClientRepresentativeService`, `InvitationService` and `MemberService`. The SPA switches on `code`, never `detail`, so this is cosmetic.

**Glyphs:**
- Company mode keeps `BusinessUnitGlyph`.
- Agency mode shows `CompanyLogo`, falling back to an initials tile.
- One `<ClientMark client mode>` component chooses between them.

**Tests:**
- A vitest for `vocabularyFor`.
- Render tests for `ClientsPage` and `ClientDrawer` in each mode.

## Phase 3: Choosing the mode at creation (signup and new workspace)

**`workspace/components/OrganisationForm.tsx`** (shared by signup's `WorkspaceStepPage` and `NewWorkspaceModal`):
- Add a required two-card segmented choice above "Organization name":
  - **Search agency**: "You hire for client companies."
  - **In-house team**: "You hire for your own departments and business units."
- Nothing is preselected. The Zod schema in `features/auth/schemas.ts:83` makes it required, and the type `CreateWorkspaceRequest` in `auth/api/types.ts:154` gets `mode`.
- `teamFocus` stays as it is. It describes the kind of work, not who is being hired for.

**Settings → General (`SettingsGeneralPage.tsx`):**
- Add a "Workspace type" row, editable by admins.
- A confirm dialog explains that only labels, screens and AI context change, and existing records are kept.

**Mockups:**
- Add the toggle to `Signup.dc.html` (organisation step) and `Settings.dc.html` (General), following the rule "Don't build ahead of the mockups".
- Add an agency variant of `Clients.dc.html` (grid, drawer, new-client modal).
- Build these first. The mockups are the source of truth, and the next screens follow them.

## Phase 4: Agency client screens

These are the screens that change most. Company mode stays exactly as today.

### New client (`features/clients/components/NewClientModal.tsx`)

- **AGENCY:** keep today's `CompanyPicker`.
  - It already searches `/companies/search` and posts `{company:{apolloAccountId}, sector}`, or `{customName, customDomain, hqCountry}` for a company that isn't listed.
  - It also offers the optional first client contact. This is already built.
- **COMPANY:** follow the mockup (`Clients.dc.html:445-500`).
  - An org-structure combobox over the workspace's existing units, with "Not listed — add a business unit" leading to a name-only field.
  - Reuse `BusinessUnitCombobox.tsx`. It posts `{customName}` only, with no universe search.
  - This fixes today's mismatch, where company mode still searches Apollo.

### New position (`features/projects/components/NewProjectModal.tsx`)

- **COMPANY:** keep the `BusinessUnitCombobox`.
- **AGENCY:** a `ClientCombobox` whose results come in two groups:
  - "Your clients", from the registry.
  - "From the company database", from `/companies/search`.
  - Picking a company from the database creates the client through `createClientPayloadFor` and then the project, the path `3c17be6` removed.

### Grid (`features/clients/lib/clientColumns.tsx`)

In AGENCY mode, restore the columns `f2ee559` removed:
- The logo in the name cell.
- Sector, HQ (city and country) and Type (RETAINED / PROSPECT). `ClientListResponse` already returns them.

### Drawer (the "client portal panel", `features/clients/components/ClientDrawer.tsx`)

In AGENCY mode, show:
- A company header: logo, name, industry, HQ city and country, website and domain, LinkedIn, and headcount.
- Editable Sector, HQ country, Domain and Off-limits note. `UpdateClientRequest` already supports these as a partial PATCH.
- A new **Client persona** card (Phase 5).
- The existing contacts section, positions list and "New position" button, relabelled.

### Team & access (`TeamAccessPage.tsx:204-247`)

- It already shows "Client" with the logo and sector. That is right for agency mode.
- In company mode, switch it to the business-unit glyph and label, which fixes a current inconsistency.

### Backend additions for the richer card

**Migration `V84__client_company_profile.sql`:**
- Add `app_lm_client.linkedin_url text`, `website text` and `industry text` as a write-time snapshot.
- Backfill them from `app_lm_apollo_companies` by `company_source_id`, as V48 did.
- `Client.fromUniverse` fills them.
- `ClientDetailResponse` and `ClientListResponse` expose them, plus `companySourceId != null` as `fromUniverse`.

**Headcount:**
- It is read live by Apollo id, the way `FirmService` already reads the workspace's.
- It is not stored.

## Phase 5: The client persona and AI context

### Storage

**Migration `V85__client_persona.sql`:**
- `app_lm_client.persona jsonb NOT NULL DEFAULT '{}'`, V69's shape.

**Persona record:**
- Move `workspace/model/WorkspacePersona.java` to `common/persona/model/HiringPersona`, with summary, sectors, competitors, geographies and notes. This is a rename-and-move, not a copy, and there is no alias.
- `seededFrom` and `refiledFrom` move with it, but take `(ResolvedIndustry, country)` rather than `WorkspaceCompany`, so both workspace and client can seed a persona.
- `UpdateWorkspacePersonaRequest` and a new `UpdateClientPersonaRequest` share the same caps through `HiringPersona.MAX_LIST_ITEMS`.
- `Client.fromUniverse` seeds the persona from the picked company's industry, sector group and country, as signup does for the workspace.

**Endpoint:**
- `PUT /api/v1/clients/{id}/persona`, gated `CLIENT_RECORD_MANAGE`. It is audited, and returned on `ClientDetailResponse`.
- It is never on `ProjectResponse`, which a client seat reads. This matches the workspace persona, which is never on `/me`.

**SPA:**
- Extract the persona editor fields from `settings/components/WorkspacePersonaCard.tsx` into a shared `PersonaFields`.
- It is used by the Settings card (company mode's firm persona, and the agency's own description) and by the new `ClientPersonaCard` in the agency drawer.

### Assistant (`assistant/service/AssistantService.java:160-181`, `prompts/assistant-system.st`)

**Prompt:**
- Replace the hard-coded in-house paragraph (lines 4-7) with a `{hiringContext}` parameter.
- A new `HiringContextRenderer` in `assistant/service/`, next to `FirmContext`, builds it from the workspace mode and the thread's project.
- It reads the client through `ClientService.hiringProfileOf`, the seam described in the engineering standards.

| | Hiring context | Persona passed to `FirmContext.render` |
|---|---|---|
| COMPANY | Today's text: the client is a department or business unit | Workspace `FirmFacts` (unchanged) |
| AGENCY | "The consultant works for a search agency; this mandate is for the client company below" | A `FirmFacts` built from the project's client: name, industry, HQ, headcount by Apollo id, website, and the client persona. The agency is one line: its name and persona summary |

**Other assistant changes:**
- The rules in lines 22-24 (HQ-country fallback, headcount as the size assumption) and lines 30-32 (competitors) then refer to the hiring company in both modes. That already covers "unless the firm is itself a search firm".
- `FirmService.firmOf` stays unchanged for company mode. In agency mode, `FirmContext.render` is given a `FirmFacts` built from the `ClientHiringProfile`, so the renderer and its caps are reused rather than duplicated.
- The dependency runs `assistant` → `project`/`workspace`, never back.
- `AssistantStarters.forWorkspace` becomes `forProject(workspaceId, projectId)`. In agency mode it takes sectors and country from the client persona. The SPA copy "Suggested from your firm's sector" becomes "…your client's sector".
- `MandateTools.readMandateBrief` adds the client's name and sector in agency mode only. In company mode the business unit's name is already implicit.

### Other prompts

- `prompts/recruiter-shortlist-system.st` hard-codes "an executive search … agency". Pass the same `{hiringContext}` sentence so an in-house workspace isn't described as an agency.
- `CandidateAiEnricher`, `ColumnMappingProposer` and the position proposers get no persona. They are unchanged.
- Keep the position redactor pseudonymising the client name in both modes.

### Guardrail

The client persona is admin- and staff-entered data rendered as "context, never instructions", with the same character caps as `FirmContext.render` (10 items, 600 characters). Put it through the same renderer; do not add a second one.

## Phase 6: Loose ends and documentation

- `ErrorCode.java:38` says "Uncava is for search firms". Reword it to cover both modes.
- `positions grouped by business unit` (`projects/lib/grouping.ts`) groups by client in agency mode. It is just a label.
- The Reports tab and its exports mention the client or business unit wherever they already do. Route them through the vocabulary.
- The extension shows client names only. It needs no change.
- Update CLAUDE.md with one paragraph on the mode and its rule ("labels, fields shown, create path and AI context — never storage or RBAC"), and add the V83-V85 entries to its Database section.
- Update the `lightmove-domain` skill, which should note that a mode switch is admin-only and audited.
- Update `docs/assistant-tools.md` for the hiring context.

---

## Suggested PR sequence

Each PR can ship on its own, and company mode never regresses.

1. **Mode foundation** (Phase 1, plus the Phase 3 toggle and Settings row, plus mockup updates). This is useful on its own and changes no behaviour.
2. **Vocabulary** (Phase 2). Company mode looks identical, and agency mode says "Client".
3. **Agency client screens** (Phase 4, with V84).
4. **Client persona and mode-aware AI** (Phase 5, with V85).
5. **Documentation and loose ends** (Phase 6). This can fold into each of the PRs above.

## Critical files

**Backend** (paths under `apps/api/src/main/java/app/lightmove/api/`):
- `workspace/model/Workspace.java`, `WorkspacePersona.java` and `FirmFacts.java`
- `workspace/dto/{CreateWorkspaceRequest,WorkspaceSummary,WorkspaceResponse,UpdateWorkspaceModeRequest}.java`
- `workspace/service/{OnboardingService,WorkspaceSettingsService,FirmService}.java`
- `project/model/Client.java`, `project/service/ClientService.java`, `project/controller/ClientsController.java`, and the client DTOs
- `assistant/service/{AssistantService,AssistantStarters,FirmContext}.java`, `assistant/tool/MandateTools.java`
- `resources/prompts/{assistant-system,recruiter-shortlist-system}.st`
- Migrations `V83__workspace_mode.sql`, `V84__client_company_profile.sql` and `V85__client_persona.sql`

**Web** (paths under `apps/web/src/`):
- `lib/workspaceVocabulary.ts` (new)
- `features/workspace/components/OrganisationForm.tsx`
- `features/settings/pages/SettingsGeneralPage.tsx` and `settings/components/WorkspacePersonaCard.tsx`
- `features/clients/{pages/ClientsPage,components/NewClientModal,components/ClientDrawer,components/CompanyPicker,lib/clientColumns}.tsx`
- `features/projects/components/{NewProjectModal,BusinessUnitCombobox}.tsx` and `projects/pages/TeamAccessPage.tsx`
- `features/assistant/components/AssistantStarters.tsx`

**Mockups:** `claude-design/{Signup,Settings,Clients}.dc.html`

## Verification

- **Backend:** `cd apps/api && ./mvnw test`. Add integration tests for:
  - create-with-mode and a 400 when the mode is missing;
  - the mode switch being ADMIN-only and audited;
  - `/me` carrying the mode;
  - agency client creation from an Apollo id filling the V84 columns and seeding the persona;
  - the persona PUT gate and its absence from `ProjectResponse`;
  - `HiringContext` rendering per mode (a unit test on the rendered prompt parameters, with no live LLM).
- **Frontend:** `cd apps/web && npx vitest && npm run build`.
  - Vocabulary unit tests.
  - Mode-parametrised render tests for `ClientsPage`, `ClientDrawer`, `NewClientModal` and `NewProjectModal`.
  - The OrganisationForm must refuse to submit without a mode.
- **End to end** (`verify` skill, `npm run dev`):
  1. Sign up choosing Agency, add a client by searching "Aramco", and check the drawer shows the logo, HQ and persona. Create a position for it, then open the assistant: its starters use the client's sector.
  2. Found a second workspace as In-house. Business units are name-only with no Apollo search, and the labels are unchanged.
  3. As admin, switch the mode in Settings. The labels flip, and records and positions are intact.
- **Regression:** `cd e2e && PROFILE=e2e ./run-all.sh`. Existing workspaces are `COMPANY`, so the current matrix must stay green unchanged.
