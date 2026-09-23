# The Uncava Assistant

A chat inside a project. You ask for companies ("top 10 retail companies in the UAE"). The assistant
searches the company universe (`app_lm_apollo_companies`) and answers with a **card** of companies
that you tick and file into the mandate as In universe, Shortlisted or Declined.

v1 does only this. New abilities are added one tool at a time, each one tested before the next.

## One question, one request

```
Panel ──POST /api/v1/projects/{projectId}/assistant/ask {question, threadId?}──▶ AssistantController
   @PreAuthorize projectAuthorizer WORK_EXECUTE; a chat that is not yours → 404 (plain HTTP, before streaming)
   └─ AssistantAskStream: returns an SseEmitter, runs AssistantService.ask on a background thread
        ├─ find my chat in this project (or start one titled from the question)
        ├─ last N question/answer pairs → history
        ├─ ChatClient.call() with the tools + ToolContext {workspaceId, projectId, TurnRecorder}
        │     describeMarket         → exact country / industry spellings
        │     searchCompanyUniverse  → top 25 by headcount, with the total matched
        │     proposeCompanies(ids)  → resolves ids from the universe, drops off-limits,
        │                              records the card
        │   each tool reports its steps ──▶ event: step {index, label, detail, done}
        └─ save the turn {question, answer, steps, proposal} ──▶ event: done {turn}
                                              model failed ──▶ event: failed {code}
Panel shows the steps live; on `done` it reads the chat back (GET /api/v1/assistant/threads/{id})

Card button ──POST /api/v1/assistant/turns/{turnId}/accept {apolloAccountIds, status}──▶
   owner check + WORK_EXECUTE on the chat's project
   → TriageCompanyService.addSelected(..., source ASSISTANT)
   → the outcome {status, added, skipped} is saved on the turn
```

The request itself streams its progress: no queue, no event table, no reconnect. It waits for
Gemini (Flash, usually 5–15s) and the stream closes at 55s, inside Cloud Run's 60s request timeout.
If the model call fails, nothing is saved and the panel shows `ASSISTANT_UNAVAILABLE`. If the tab
closes mid-answer, the answer is still saved and shows up in History.

## Storage (V65, simplified by V68)

| Table | Row |
|---|---|
| `app_lm_assistant_thread` | One chat: `workspace_id`, `user_id`, `project_id`, `title`. Private to its user. |
| `app_lm_assistant_turn` | One answered question: `question`, `answer`, `steps` (jsonb, V69), `proposal` (jsonb card), `proposal_accepted` (jsonb outcome). |

## Security

- **Checked once, at the door.** `ask` and the history list need `WORK_EXECUTE` on the project,
  which is the same action filing the card needs. A client representative (`WORK_VIEW` only) is
  refused.
- **Tools never take a project or workspace from the model.** Both come from `AssistantToolContext`,
  which the service builds from the authorised request. So a tool needs no permission check of its
  own. Keep it that way: a tool argument naming a project would reopen the hole.
- **The card is built from the universe, never from the model's text.** `proposeCompanies` takes
  account ids and reads every name and figure from the universe row. Accept files only ids that the
  stored card holds.
- A chat that is not yours answers 404.
- Tool output is data. The system prompt (`prompts/assistant-system.st`) says so. The real guard is
  the rule above, not the sentence.

## Adding a tool

1. Add a `@Tool` method to a `@Component` in `assistant/tool/`. Read the workspace and project with
   `AssistantToolContext.from(toolContext)`, never from an argument. Report what it does with
   `recorder().startStep("Searching …")` and `finishStep(index, "342 matched")`, so the person
   waiting sees it.
2. Pass the bean to `.tools(...)` in `AssistantService.callModel`.
3. Tell the model when to use it in `prompts/assistant-system.st`.
4. If it writes anything, it must propose rather than write. A person confirms every change.

## Code map

- Backend `apps/api/.../assistant`:
  - `controller/AssistantController` has the four endpoints.
  - `service/AssistantAskStream` streams an ask's steps and result.
  - `service/AssistantService` handles ask, the history list and reading a chat.
  - `service/AssistantProposalService` handles accept.
  - `tool/` holds `CompanySearchTools`, `ProposalTools`, `MarketSearch`, `MarketQuery`, `AssistantToolContext` and `TurnRecorder`.
- Frontend `apps/web/src/features/assistant`:
  - `AssistantProvider` holds whether the panel is open and the chat shown per project.
  - `components/AssistantPanel` has the history list, New chat, the transcript and the composer.
  - `components/AssistantTurnView` shows one question and answer and files the card.
  - `components/AssistantSteps` draws the step list, live and saved.
  - `components/AssistantProposalCard` draws the card.
  - `AssistantDock` / `AssistantLauncher` handle layout.
