# The Uncava Assistant

A chat inside a project. You ask for companies ("top 10 retail companies in the UAE"). The assistant
searches the company universe (`app_lm_apollo_companies`) and answers with **suggested companies**,
listed under the answer, that you tick and file into the mandate as In universe, Shortlisted or Declined. You can also ask about
the executives the mandate has mapped ("who have we mapped at Aldar?"); that answer is text only.

## One agent, playbooks on demand

One model call answers every question (`AssistantAgent` → `AssistantModelCall.ask`). Its system prompt is
short and always on — `prompts/assistant-core.st`: who the assistant is, the data and injection guards,
what it may never say about a person, and the hiring company and brief last. Everything that applies to one
kind of question lives in a **playbook**, `resources/assistant/skills/<name>/SKILL.md`, which the model loads
by name through the `Skill` tool (`AssistantSkills`, over `spring-ai-agent-utils`' `SkillsTool`) before it
does anything else. The tool's description lists every playbook's name and description, so the model reads
a playbook only when its question needs it.

| Playbook | When | What it tells the model |
|---|---|---|
| `find-companies` | companies by sector, country, size, what they do ("watch distributors"), "top N" | search + name lookup (+ `searchCompaniesByActivity` for an activity) in one turn, `proposeCompanies`, a two-sentence answer, adjacent industries |
| `similar-companies` | a named company's competitors, peers, "companies like X" | `identifyCompany`; ask which one when several share the name; `findSimilarCompanies` + name lookup in one turn; say what was loosened |
| `recommend-sectors` | which sectors to target | reason from the brief, name only what `describeMarket` / `adjacentIndustries` report, propose nothing |
| `earlier-list` | "what are these", "the first three", "more like these" | list or narrow from the `<suggested_companies>` block; for more, load `find-companies`; to rank, `rank-companies` |
| `rank-companies` | rank, tier, prioritise or group by fit — an earlier list or a stage of the position | `readCompanyDetails` for a block's keys or `listMandateCompanies` for a stage, judged against the brief and the hiring company; tiers or a numbered ranking with a reason each; never claims the list or the brief is missing |
| `mapped-executives` | who the position has mapped | `listMappedExecutives`, `readExecutiveProfile`, `companiesWithoutExecutives` — read-only |

- **The model is offered exactly `Skill`, `AskUserQuestionTool` and the assistant's own `@Tool`s** (`AssistantToolset`, built once,
  pinned by `AssistantAgentTest`). The library's shell, file, web and sub-agent tools are never registered:
  a playbook is text, with no scripts and no files beside it, and is registered by its text alone
  (`addSkill`), so the model is never told where it sits on disk.
- A playbook load is a step of the answer ("Following the find companies playbook", `SkillStepListener`, a
  `ToolCallListener` around the `Skill` tool) and `ASSISTANT_ASKED` records the `skills` loaded. A name the
  library does not hold is answered "Skill not found" and recorded nowhere.
- **Asking instead of guessing** (`AskUserQuestionCallback`): the library's `AskUserQuestionTool` lets the
  model put one to four multiple-choice questions (a 12-character header, two to four options with a
  description, single or multi select, and an "Other" box the panel always adds). The library's handler is
  synchronous — it waits for the answers — and an ask cannot: it is one request closed at 50 seconds, holding
  one of four slots, whose answer may reach another instance. So the handler records the questions on the
  `TurnRecorder` and the tool tells the model to stop with one short line; the turn is saved with its
  `questions` and a fixed lead-in as its answer, and no companies are suggested. The tool is deliberately not
  `returnDirect`: Spring AI reads that before the call, so a question the tool turns away would have become the
  answer. It turns one away when no playbook is loaded yet (the eval and a live chat both asked where a named
  company operates, which a lookup finds), when this answer already suggested companies, and when
  `AssistantQuestions` leaves nothing the card can draw (a question, a header of at most 12 characters and two
  to four labelled options, four questions at most) — the model is then told to carry on without asking.
  The panel draws them (`AssistantQuestionCard`); **Send answers** is the chat's next ask ("Region: GCC only ·
  Ownership: Listed, Family-owned", without the model's "(Recommended)" mark), and `QuestionMemory` replays the
  questions to the model as an `<asked_consultant>` block and sends that message inside `<consultant_answers>`
  beside the request it answers. Sent bare, the answers read as a remark: the model answered from the chat's
  earlier answers without searching and named companies no tool returned. The model may ask again on a later
  turn when it judges it necessary; within one turn only the first set is kept. The library's `answers`
  parameter is taken out of the schema the model sees, so it cannot answer its own questions.
  `ASSISTANT_ASKED` records `questionsAsked`.
- Every call is sent the earlier lists (`CardMemory`), so `AssistantModelCall` strips a
  `<suggested_companies>` block from the answer if the model echoes one.
- The whole ask is one `LlmBudget.ASSISTANT` unit; the prompt id is still `assistant-turn`, so existing
  dashboards match. Loading a playbook is one extra round of the same call.
- Answers never call the suggested companies a "card": the panel draws them under the answer with a line
  saying what they are ("6 suggested · 2 already in this position · 1 from LinkedIn").
- `AssistantEval` (`@Tag("eval")`) asks the real model the questions in `eval/assistant-cases.json` over a
  seeded universe and appends what it loaded, ran and suggested to `docs/eval/assistant-eval.md`.

## One question, one request

```
Panel ──POST /api/v1/projects/{projectId}/assistant/ask {question, threadId?}──▶ AssistantController
   @PreAuthorize projectAuthorizer WORK_EXECUTE; a chat that is not yours → 404 (plain HTTP, before streaming)
   └─ AssistantAskStream: LlmBudget.ASSISTANT per user, then one of max-concurrent-asks slots
      (ASSISTANT_BUSY when none), returns an SseEmitter, runs AssistantService.ask on a background
      thread; at 50s an unfinished answer closes the stream as ASSISTANT_STILL_ANSWERING and is
      still saved; every answered ask records ASSISTANT_ASKED with its Bright Data searches
        ├─ find my chat in this project (or start one titled from the question)
        ├─ last N question/answer pairs → history; each answer carries its list as a
        │  <suggested_companies> block
        │  (CardMemory: "[new|already <stage>] key · name · country · staff", and what was filed;
        │  the newest three cards row by row, older ones as title + count only), because the
        │  answer text never lists the companies. Researched pages on those cards are remembered,
        │  so a follow-up can propose them again without a second Bright Data search
        ├─ system prompt carries the hiring company: HiringSideResolver → HiringContext. In-house,
        │  the workspace's company (V68) and the persona its admins wrote in Settings → General (V69);
        │  at an agency (V84), the mandate's client and the persona recorded in its drawer (V85), with
        │  the agency named in one line. Framed as data, never instructions
        ├─ AssistantAgent → one ChatClient.call() with assistant-core.st, the Skill tool and the
        │  tools below + ToolContext {workspaceId, projectId, TurnRecorder}
        │     Skill(name)            → the playbook for this kind of question
        │     readMandateBrief       → the position only (never compensation or internal notes)
        │     describeMarket         → exact country / industry spellings
        │     searchCompanyUniverse  → top 25 by headcount, with the total matched; up to five
        │                              countries and five industries per search (any of), and
        │                              each row's mandateStage where the mandate already filed it
        │     lookUpCompaniesByName  → names the model knows, local and global: a brand's local
        │                              operator first, then the universe, then LinkedIn in the
        │                              country via Bright Data, then the brand's own page anywhere
        │                              (cached per page, 30 days)
        │     identifyCompany        → every company a name could mean (database by every name word,
        │                              else one Bright Data name search), each with its niche
        │     findSimilarCompanies   → the same niche (rare keywords shared, weighted by rarity),
        │                              then sector, then headcount ¼–4×; headcount widened to
        │                              ⅒–10×, then dropped, then the sector, until enough are found
        │                              — never the country; a shortfall goes to Bright Data's
        │                              company dataset in the same countries (country_codes_array)
        │                              and headcount ⅒–10×, by the niche's words in specialties /
        │                              about: first in the sector's V2 industries, then without
        │                              (≤10 hits in all; a search finding nobody costs nothing)
        │     searchCompaniesByActivity → companies by what they do: the database's keywords for
        │                              each word, then Bright Data's specialties / about text
        │     adjacentIndustries     → the sectors beside one, from industry-adjacency.json
        │     readCompanyDetails     → what an earlier list's companies do (industry, sector, size,
        │                              about, niche), by key, from the database or the vendor cache
        │                              — never a vendor call
        │     listMandateCompanies   → one stage of this position, the first 100, with the same details
        │     proposeCompanies(ids)  → account ids from the universe, LinkedIn slugs this answer or
        │                              an earlier card researched; drops off-limits, stamps each
        │                              company the mandate already holds with its stage (shown
        │                              last, unticked, never filed again), records the card
        │   each tool reports its steps ──▶ event: step {index, label, detail, done}
        └─ save the turn {question, answer, steps, proposal} ──▶ event: done {turn}
                                              model failed ──▶ event: failed {code}
Panel shows the steps live; on `done` it reads the chat back (GET /api/v1/assistant/threads/{id})

Card button ──POST /api/v1/assistant/turns/{turnId}/accept {companyIds, status}──▶
   owner check + WORK_EXECUTE on the chat's project
   → companies the card showed with a stage are counted skipped, never refiled
   → TriageCompanyService.addSelected(..., source ASSISTANT) / captureResearched for LinkedIn pages
   → the outcome {status, added, skipped} is saved on the turn
```

The request itself streams its progress: no queue, no event table, no reconnect. It waits for
Gemini (Flash, usually 5–15s) and the stream closes at 55s, inside Cloud Run's 60s request timeout.
If the model call fails, nothing is saved, the panel shows `ASSISTANT_UNAVAILABLE` and puts the
question back in the composer to send again. `ASSISTANT_STILL_ANSWERING` does not — that answer is
still being saved, and asking again would pay twice. If the tab closes mid-answer, the answer is
still saved and shows up in History.

A card's stage is the one the mandate held when the card was made (`TriageCompanyReadService.stagesOf`:
by account id, else by name — the rule a capture uses), stored on the turn and not re-read.

## Storage (V65, simplified by V70)

| Table | Row |
|---|---|
| `app_lm_assistant_thread` | One chat: `workspace_id`, `user_id`, `project_id`, `title`. Private to its user. |
| `app_lm_assistant_turn` | One answered question: `question`, `answer`, `steps` (jsonb, V71), `proposal` (jsonb card), `proposal_accepted` (jsonb outcome), `questions` (jsonb, V120: the clarifying questions asked in place of an answer). |

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
- **What a model may read about a person is an allowlist.** The mapped-executives tools return
  `MappedExecutiveSummary` (name, title, company, seniority, status, location, years) and
  `CandidateDossier`. Contacts, compensation, notes, custom fields, nationality and gender never reach
  its prompt.
- Tool output is data. The core prompt (`prompts/assistant-core.st`) says so. The real guard is
  the rule above, not the sentence.

## Adding a tool

1. Add a `@Tool` method to a `@Component` in `assistant/tool/`. Read the workspace and project with
   `AssistantToolContext.from(toolContext)`, never from an argument. Report what it does with
   `recorder().startStep("Searching …")` and `finishStep(index, "342 matched")`, so the person
   waiting sees it.
2. Add its bean to `AssistantToolset` and its name to `AssistantAgentTest`'s allowlist.
3. Tell the model when to use it in the playbook that needs it, never in the core prompt.
4. If it writes anything, it must propose rather than write. A person confirms every change.

## Adding a playbook

1. Add `resources/assistant/skills/<name>/SKILL.md`: front matter with a lower-case hyphenated `name` and a
   one-line `description` (at most 1024 characters, the words a consultant would use), then the
   instructions. A malformed or missing one stops the start (`AssistantSkills`; the boot log lists the playbooks found).
2. Keep it to one kind of question, and name only tools the agent already offers.
3. Add the questions it answers to `eval/assistant-cases.json` and compare the eval before and after.
4. Integration tests load it with `StubChatModel.callToolWhenSystemContains(agent marker, "Skill",
   "{\"command\":\"<name>\"}")`.

## Code map

- Backend `apps/api/.../assistant`:
  - `controller/AssistantController` has the four endpoints.
  - `service/AssistantAskStream` streams an ask's steps and result.
  - `service/AssistantService` handles ask, the history list and reading a chat.
  - `service/AssistantAgent` and `AssistantModelCall` run the one model call over `AssistantToolset`;
    `AssistantSkills` (built by `config/AssistantSkillsConfig`) holds the playbooks and
    `SkillStepListener` shows each one loaded as a step.
  - `service/CardMemory` writes an earlier list back into the chat the model reads, and `QuestionMemory`
    the questions an earlier answer asked.
  - `service/AskUserQuestionCallback` offers the library's question tool, ending the answer rather than
    waiting for one.
  - `service/AssistantProposalService` handles accept.
  - `tool/` holds `MandateTools`, `CompanySearchTools`, `SectorTools`, `NamedCompanyTools`, `CompanyDiscoveryTools` (over `CompanyDiscovery`), `ProposalTools`, `CandidateTools`, `CompanyDetailTools`, `MarketSearch`, `MarketQuery`, `AssistantToolContext` and `TurnRecorder`.
  - The niche read is `ApolloCompanyQueryService.similarTo` / `distinctiveKeywords` / `nicheKeywordCounts` / `namedLike` (`strategy`), over V33's
    `app_lm_apollo_keywords`: a keyword on more than 3% of the universe distinguishes nothing and is not counted. The
    LinkedIn half is `CompanyResearch.pagesNamed` / `byActivity` over the company dataset's synchronous search, every
    hit cached in `app_lm_vendor_company`.
- Frontend `apps/web/src/features/assistant`:
  - `AssistantProvider` holds whether the panel is open and the chat shown per project.
  - `components/AssistantPanel` has the history list, New chat, the transcript and the composer.
  - `components/AssistantTurnView` shows one question and answer and files the card.
  - `components/AssistantSteps` draws the step list, live and saved.
  - `components/AssistantProposalCard` draws the suggested companies.
  - `components/AssistantQuestionCard` draws the questions an answer asked, and sends the choices as the
    next ask while it is the chat's last turn.
  - `AssistantDock` / `AssistantLauncher` handle layout.
