# The assistant's tools, and the guard on every call

## Context

`docs/llm-guardrails.md` covers the calls this application makes *to* a model. This one covers the
calls a model makes back: the assistant's tool surface, and the authorisation on it.

Until now the assistant could hold a conversation and nothing more — V65's tables, the turn runner,
the durable event log and the resumable stream were all in place, but the model had no way to look
anything up. Giving it one is not an incremental feature. A tool call is the model asking the
application to read a mandate's data, with arguments the **model** chose, on a thread with no
`SecurityContext`, for a user who is not on the other end of a request any more. Every assumption
`@PreAuthorize` rests on is gone.

So the guard came before the tools, which is why this document exists before there is much of a tool
surface to describe.

## The rule

**Every tool declares the action it needs, and is authorised against its own arguments before its
body runs.**

Not against the thread's mandate, and not against the screen the panel was opened from. The model
emits a `projectId`; it may have read one earlier in the conversation, or inferred one, or been told
one by a page the user has since navigated away from. None of that is evidence about the call in
hand. `AuthorisingToolCallback` reads the id out of the arguments and hands it to `ProjectAccess`,
which re-reads the membership rows exactly as it does on a request.

## Where the guard sits, and why there

`ToolCallback.call(String, ToolContext)` is the single choke point every execution passes through,
so the guard is a decorator on it rather than a line at the top of forty tool methods. The reason is
the one `LlmCallPolicy`'s own javadoc gives about itself: a convention every author must remember is
not enforcement. A decorator is.

Three things about that decorator are load-bearing.

**It overrides both `call` overloads.** `ToolCallback` declares `call(String)` abstract and
`call(String, ToolContext)` default. Spring AI's own manager uses the two-argument form, but the
one-argument form is reachable, carries no caller, and therefore admits no authorisation decision.
It throws.

**Nothing undecorated is a bean.** `ToolCallingAutoConfiguration` folds every `ToolCallback` and
`ToolCallbackProvider` bean into one resolver keyed on the tool's *name*, and an MCP provider would
land in the same one. A raw callback left in the context would be reachable by any call naming that
string, with no guard at all. `AssistantToolset` therefore builds them per turn and hands out
nothing else — `AssistantToolRegistrationTest` is what keeps that true.

**A callback belongs to one turn.** It carries that turn's caller and refuses a `ToolContext`
carrying a different one, so an instance that ever did escape could not authorise someone else's
conversation against the turn it was born for.

## Failing closed at startup

`ToolPermissions` reads `@RequiresWorkspaceAction` / `@RequiresProjectAction` off every `@Tool`
method when the toolset is constructed, and **refuses to start the application** on a method that
declares neither, declares both, or names a project-id argument it does not have. A tool whose guard
was forgotten cannot reach production; it cannot reach a running instance at all. Lookup fails
closed for the same reason — a tool name the permissions never saw is refused rather than passed.

## What a refusal tells the model

One sentence, the same for every cause.

`ProjectAccess` answers `NOT_FOUND` for a mandate that does not exist and `FORBIDDEN` for a real one
the caller has no seat on. Over HTTP that is defensible — projects are browsable to staff, and the
class says so. Through a tool it is an enumeration oracle in prose: the questioner asks again with a
different id and reads the difference back in the assistant's own words. Worse, the caller may be a
client representative, for whom the existence of another mandate is not public at all.

So the decorator catches `ApiException` and returns a constant. The reason goes to the log and to
`SecurityEventType.ASSISTANT_TOOL_DENIED`, and nowhere near the conversation.

**A failure inside the tool is contained too, and for the same reason.** Letting it propagate reads
as the safer choice and is not. `MethodToolCallback` wraps whatever a body throws in a
`ToolExecutionException`, `spring.ai.tools.throw-exception-on-error` defaults to false, and
`DefaultToolExecutionExceptionProcessor` hands the **cause's message** back as the tool result — so
the model reads it and may quote it to whoever asked. `ApiException` licenses its internal detail to
name a column or a rejected value *precisely because it never leaves the server*, and inside a tool
body that stopped being true. So the decorator catches it and answers a second constant.

Two constants rather than one, because a refusal and a failure are different answers and the model
should act on them differently — retry a failure, do not retry a refusal. Neither says why, and
reaching the failure one tells a caller only that the guard let them through, which they already
knew. What went wrong stays in the log, where it is a bug report rather than a sentence a
conversation can repeat.

## Identity off the request thread

`@PreAuthorize` cannot be used here: it is proxy-based and reads a `SecurityContext` the worker
thread does not carry. Neither can the `@projectAuthorizer` SpEL beans, which are documented as
belonging on controllers for the same reason. The guard calls `ProjectAccess` and `WorkspaceAccess`
imperatively, with typed enums rather than the string wrappers — `ProjectAuthorizer.can` does
`ProjectAction.valueOf(action)`, and a typo off-request gets no error mapping.

The caller is rebuilt from the turn row. V65 stores `actor_user_id` and `workspace_id` for exactly
this and is explicit about what it is not: naming the actor is not a shortcut past authorisation,
because the guard beans still re-read the database on every call. It only says whose membership to
read. `AssistantToolCaller` carries those two ids and the turn, and deliberately not an
`AuthPrincipal` — that type also carries roles minted up to fifteen minutes ago, which nothing may
branch on.

One thing this does **not** cover: a turn can outlive token validation. `requireActiveMember` sees a
membership revoked mid-turn; it does not see a session revoked.

## Which tier a tool is guarded at

| Data | Tier | Action |
|---|---|---|
| The Apollo universe | workspace | `PROJECT_BROWSE` |
| One mandate's own rows, read | project | `WORK_VIEW` |
| One mandate's own rows, written | project | `WORK_EXECUTE` |

The universe belongs to no mandate, so there is no project id to authorise against and the workspace
tier is the whole gate. `PROJECT_BROWSE` is ADMIN and MEMBER only (V6), which is what keeps a pure
client representative off the market side while the mandate they are attached to still answers —
`WORK_VIEW` is their seat's own grant.

`WORK_EXECUTE` has no tools yet. When it gets them: a write tool declares `WORK_EXECUTE` and never
`WORK_VIEW`, because CLIENT holds `WORK_VIEW` (V15).

`WorkspaceAuthorizer.member()` is the trap rather than `can()`. It admits pure clients deliberately,
which is right for the assistant's own endpoints — a client may hold a conversation — and wrong for
anything reading market data. Tools gate on named actions only.

## The trace

A tool call and its result are `app_lm_assistant_event` rows, `tool.called` and `tool.result`. No
migration: V65 gives `kind` no CHECK precisely so the kinds a turn emits can grow with every tool
the assistant learns, and `AssistantEventKind` named this pair as the ones it expected.

They are written by the decorator, because it is the only thing that sees both halves. The tool loop
runs inside Spring AI's `ToolCallingAdvisor`, so a tool call never appears in the response stream the
runner consumes and a result never appears anywhere at all.

That also means a turn no longer has one writing **thread**: answer text is drained on the worker
while a tool's events come from inside the advisor's chain. `AssistantEventAppender` allocates
`max(seq) + 1` and a collision would fail loudly on V65's unique index — correct, and still a turn
lost to a race nothing forced — so the worker's sink serialises its own appends. One sink per turn,
so it contends with nothing else.

The arguments are recorded verbatim — that is the point of a trace — so nothing reading the log may
treat them as having been authorised. A refused call records its refusal; *why* is in the audit
trail.

## What Spring AI does and does not do here

Checked against the 2.0.1 jars rather than the documentation.

- **`ToolExecutionEligibilityChecker` is not an authorisation hook.** It is
  `Function<ChatResponse, Boolean>` and answers "should the loop run at all for this response". It
  cannot see a tool name or an argument, so it cannot approve one call and refuse another. Building
  authorisation on it would be a hole.
- **A tool's exception text is a channel to the model**, per the constants above:
  `throw-exception-on-error` is false by default and the processor returns the cause's message.
- **`GoogleGenAiChatModel` does not execute tools.** It calls `ToolCallingManager.resolveToolDefinitions`
  and never `executeToolCalls`, so it declares the tools and hands back the model's calls unrun.
- **The loop is `ToolCallingAdvisor`**, which implements `CallAdvisor` *and* `StreamAdvisor` and is
  added by `DefaultChatClientBuilder` on its own. That is why the assistant's streaming runner needed
  nothing but `.toolCallbacks(...)` and `.toolContext(...)`, and why tool calling works on a streamed
  turn at all.
- **Usage stays a single number across rounds.** The provider accumulates through
  `UsageCalculator.getCumulativeUsage` and the advisor again through its own `UsageAccumulator`, so
  the last reported total is the turn's, not the final round's.
- **`spring.ai.tools.limits.*` bounds the loop**, because those limits live in
  `DefaultToolCallingManager.executeToolCalls`, which the advisor does call.

## Verification

- `ToolPermissionsTest` — an undeclared tool, a doubly-declared one, and one naming an argument it
  does not have each fail at wiring; an unknown tool name is refused rather than passed.
- `AuthorisingToolCallbackTest` — `call(String)` always refuses; another turn's caller is refused; an
  authorised call delegates once with the input unchanged and both halves reach the trace; **an
  absent mandate and one the caller is not on produce the byte-identical refusal**; a call naming no
  mandate is refused rather than authorised against null; a failure inside the tool answers the
  fixed sentence with none of the exception's own text in it, is traced as a result rather than
  leaving a call with no answer, and stays distinguishable from a refusal.
- `AssistantToolRegistrationTest` — every `@Tool` in the application is on a collected subject, every
  collected tool declares a permission, and the toolset hands out only guarded callbacks.
- `AssistantToolAuthorisationIntegrationTest` — the same rules against real membership rows: a pure
  client is refused the market and keeps their mandate, an unseated member cannot tell a real mandate
  from a fictional one, and another workspace's is refused.
