# Before I ship an LLM feature, I ask two questions

We run a SaaS for executive search firms. Customers upload spreadsheets of executives they are
approaching about a job. Sensitive data.

One feature uses an LLM. Someone uploads a CSV, and we ask the model what each column header means so
we can map it to our fields. Small feature.

Before I approved it, I asked two things. Not "is the prompt good".

1. What is the worst bill one user can create in an hour?
2. What data leaves our servers, and what lands in our logs?

We use Spring AI, and its **Advisor** pattern is how we answered both. This post is how it works,
with the code, and how it pays for itself on cost and on security.

---

## What an advisor actually is

An advisor is a filter around the LLM call. Same idea as a servlet filter. It sits between your code
and the model, sees the request going out and the response coming back, and it can change either one
or answer the call itself without ever talking to the model.

The chain looks like this:

```
your service
    |
    v
SafeGuardAdvisor            <- can stop here and return a canned answer
    |
    v
StructuredOutputValidationAdvisor   <- checks the JSON, re-asks once if wrong
    |
    v
SimpleLoggerAdvisor         <- writes a log line both directions
    |
    v
Gemini on Vertex AI
```

Request goes down. Response comes back up. Each advisor sees both.

That "can stop here" line is where most of our cost saving lives, and it also gave us our worst bug.
Both later.

---

## The wiring

Three pieces. This is the whole setup.

**One shared client.** Model, temperature and logging go here, because they are true for every call:

```java
@Bean
public ChatClient chatClient(ChatClient.Builder builder,
                             @Value("${spring.ai.google.genai.chat.model}") String model,
                             @Value("${spring.ai.google.genai.chat.temperature}") double temperature) {
    return builder
            .defaultOptions(ChatOptions.builder()
                    .model(model)
                    .temperature(temperature))
            .defaultAdvisors(SimpleLoggerAdvisor.builder()
                    .requestToString(ChatCallLog::describeRequest)
                    .responseToString(ChatCallLog::describeResponse)
                    .build())
            .build();
}
```

No system prompt here, and no guard here either. Those change per feature.

**One place that builds the guards.** Every feature asks this for its advisors instead of assembling
its own:

```java
public Consumer<ChatClient.AdvisorSpec> forPrompt(PromptGuardSpec spec) {
    List<Advisor> advisors = new ArrayList<>(2);

    advisors.add(SafeGuardAdvisor.builder()
            .sensitiveWords(injectionPhrasesFor(spec))
            .failureResponse(spec.blockedAnswer())
            .build());

    if (spec.answerSchema() != null) {
        advisors.add(StructuredOutputValidationAdvisor.builder()
                .outputJsonSchema(AnswerSchemas.readFrom(spec.answerSchema()))
                .maxRepeatAttempts(settings.answerRepairAttempts())
                .build());
    }

    return advisorSpec -> advisorSpec
            .param(ChatCallLog.PROMPT_ID_ATTRIBUTE, spec.promptId())
            .advisors(advisors);
}
```

Notice the feature does not build advisors. It describes its prompt, and gets the advisors back. So a
new feature cannot accidentally ship with a weaker guard than the last one.

**The call site.** Resolve once in the constructor, use it on every request:

```java
// constructor
this.guarded = llmCalls.forPrompt(
        PromptGuardSpec.structured(PROMPT_ID, answerSchema, BLOCKED));
```

The schema file is read there, so a broken schema kills the app at startup instead of breaking one
request in production at 2am.

---

## The full flow, user click to screen

Someone drops a CSV on the import screen. Here is everything that happens.

### 1. Permission check

```java
@PostMapping("/preview")
@PreAuthorize("@projectAuthorizer.can(principal, #projectId, 'WORK_EXECUTE')")
public ResponseEntity<ImportPreviewResponse> preview(...)
```

Normal authorization, re-read from the database. Nothing LLM specific yet.

### 2. Spend a token from the user's budget

```java
llmBudget.requireColumnMappingBudget(principal.userId());
```

This is before any work. Our endpoint is protected by login and nothing else on the cost side, so one
logged in user with a loop would be an unlimited charge on our cloud account.

### 3. Try to skip the model completely

A plain matcher maps the sheet from header names first. If it is sure about every column, we return
and never call the model at all:

```java
HeuristicProposal heuristic = heuristics.propose(sheet, existingColumns);

if (heuristic.everyColumnCertain()) {
    return new ProposedColumnMappings(heuristic.mappings(), MappingSource.EXACT_HEADERS);
}
```

Then we made that path more likely on purpose. We give customers a CSV template to download with our
headers already in it. Anything built from that needs zero model calls. Their second import also
needs zero, because by then we know their headers.

So the model gets asked once, when a new customer arrives with weird column names. After that it
mostly stops being asked. That is a product decision, not a prompt decision, and it only happens if
someone thinks about cost early.

### 4. Build the prompt

```java
return chatClient.prompt()
        .advisors(guarded)
        .options(ChatOptions.builder().temperature(0.0))
        .system(systemPrompt)
        .user(user -> user.text("""
                Columns in the uploaded file:
                {columns}

                Fields available to map onto:
                {fields}
                """)
                .param("columns", describeColumns(sheet))
                .param("fields", describeFields()))
        .call()
        .entity(ProposedMapping.class);
```

Three things in there are deliberate.

`.advisors(guarded)` attaches the chain. Nothing else in this method knows what the guards are.

Temperature is 0 for this call, overriding the shared client's default. Column mapping has one right
answer, so randomness only buys answers that will not bind.

And the user text goes in as a **parameter**, never string concatenation. With concatenation there is
no line between our structure and their content. With a parameter, the template is a constant we
wrote and their text is just a value dropped into it.

What `describeColumns` produces is the whole privacy decision:

```
- "Contact" (values look like: email addresses)
- "Co." (values look like: short text)
- "ARR" (values look like: numbers)
```

Header plus a shape we calculate ourselves. **No cell values.** Not one.

### 5. Down the advisor chain

**SafeGuardAdvisor** checks the outgoing text against a word list. If it matches, it returns the
canned answer and the call stops here. No model, no cost.

```java
@DefaultValue({
        "ignore previous instructions",
        "disregard the above",
        "system prompt",
        "you are now"
}) List<String> injectionPhrases
```

Short on purpose. Every phrase is also something a real person could write one day, and blocking real
work is its own failure. A control that breaks the product gets switched off by someone under
deadline pressure, and then you have no control at all.

**StructuredOutputValidationAdvisor** validates the reply against our JSON schema and, if it doesn't
fit, sends it back to the model with the error attached.

Its default is 3 repeats. That is four billed calls for one user click. We set it to 1:

```java
@DefaultValue("1") int answerRepairAttempts,
```

A model that gets the shape wrong twice is not going to get it right on the fourth try.

**Order matters here and we left it at the default deliberately.** The validator sits inside the
guard. If you put it in front, a blocked call's canned answer would be treated as "the model answered
badly" and re-asked. You would pay repair attempts on a call that never happened.

**SimpleLoggerAdvisor** writes one line out and one line back.

### 6. The call itself

Bounded by a 20 second timeout. Spring AI's Google starter gives you none, and no property exposes
one, so we had to replace the provider's client bean to set it. Without it a hung provider holds a
request thread and the user's browser for as long as it likes.

### 7. Response comes back up

Jackson binds it to our record. Then we check whether it was actually the model that answered, and
reconcile:

```java
if (wasBlocked(answered)) {
    log.warn("Column mapping blocked before reaching the model: a header matched the "
            + "injection word list. Falling back to the header matcher.");
    return new ProposedColumnMappings(fallback, MappingSource.HEADER_MATCHER);
}
return new ProposedColumnMappings(
        reconcile(sheet, existingColumns, answered, fallback), MappingSource.MODEL);
```

And if anything at all went wrong, we degrade instead of failing:

```java
} catch (RuntimeException e) {
    log.warn("Column mapping fell back to the heuristic matcher: {}", e.toString());
    return new ProposedColumnMappings(fallback, MappingSource.HEADER_MATCHER);
}
```

The user still gets a mapping screen to correct. And the response tells the UI which of the three
produced it, rather than claiming the model did it when the model was never called.

### 8. A human confirms, then we write

The commit call takes the mapping the person approved and does the actual writing. **It calls no
model at all.** And the writes go through the same code the normal UI uses, so all the permission
checks and audit logging stay where they already were. We did not build a second write path for the
LLM.

---

## What that bought on cost

**Most imports never call the model.** Step 3.

**About 100x fewer tokens when we do call it.** We allow 5000 row files. Sending the actual sheet is
around 100,000 cells, roughly 300,000 tokens. Headers and shapes is about 4,000. We did that for
privacy. The cost saving came free.

Worth remembering when product pushes back on a privacy decision. "It is also 100x cheaper" ends the
argument fast.

**Half the worst case, from one config line.** Repair attempts 1 instead of 3.

**A number I can defend.** 10 requests a minute per user, up to 2 calls each, so 20 calls a minute,
1200 an hour. Multiply by seats and it goes in a board deck. On the default it would have been 2400.

**Per feature token counts from day one.** The client is shared, so without the prompt id in the
advisor context every log line would just say something called the model:

```
chat request  prompt=import-column-mapping model=gemini-2.5-flash temperature=0.0
chat response inputTokens=1200 outputTokens=340 totalTokens=1540 finish=STOP
```

`finish=STOP` tells you whether the answer was complete. A truncated answer still comes back as a
successful call, and you paid full price for half an answer.

One warning. When the provider reports no token count, Spring AI puts 0, not null. So if you build a
cost dashboard by adding up that field, unreported calls quietly become free calls and the dashboard
lies to you.

Two honest caveats we wrote in the code rather than hid: the rate limiter is in memory per instance,
so with 3 pods the real ceiling is 3x. And it counts requests, not billed calls, because one request
can become two.

**One more.** We configured retry properly, shipped it, and it did nothing at all. The provider wraps
its errors in a plain RuntimeException, which matched nothing in the retry policy. It saved money by
accident, but our resilience story was wrong and we would have found out during an outage. Config
that does nothing looks exactly like config that works.

---

## What that bought on security

**Injection has three layers.** The word list in step 5. The system prompt, which tells the model
plainly that headers are data and never instructions. And the parameter binding in step 4. Honestly
the word list is the weakest of the three. The real control is that the model cannot write anything.

**Nothing sensitive reaches the provider.** Step 4.

**Nothing sensitive reaches our logs.** Spring AI's default logging dumps the full prompt and the
full response. For us that is spreadsheets of real executives going into a log system, kept on a
different retention schedule and readable by more people than the app allows. So both formatters are
replaced and we log metadata only. There is a test whose only job is to fail if content ever appears
in a log line.

If you do one thing after reading this, go read one of your own LLM log lines today.

**The model proposes, a person commits.** Step 8. And even inside the preview we don't trust the
answer. We match the model's reply back to real columns by header name, not by position, because a
model that drops or reorders one entry would shift every mapping after it onto the wrong column. That
is the failure where a nice looking answer writes a whole file into the wrong fields.

Schema validation proves the shape is right. It proves nothing about whether the content is sane.

---

## Our worst bug

Back to that "can stop here" line.

SafeGuardAdvisor answers **in place of** the model. Your code calls the client and gets a value back.
It has no way to know whether that came from the model or from the guard. Same type. No exception. No
flag.

So a blocked call can be shown to the user as if the model actually answered. Something nobody ever
decided, appearing on screen as a real answer, and somebody acting on it.

The fix is dumb but it works. Every blocked answer carries a marker:

```java
public static final String MARKER = "__lightmove_blocked__";
```

And the app refuses to start if a prompt is configured with a blocked answer that doesn't carry it.
So we find out at boot, not on the request that would have been misread.

Then the same problem in a second shape. Our answer is JSON. If the blocked answer were a sentence,
Jackson would fail to parse it, and that failure looks exactly like "the provider is down", which we
already handle by falling back. So an injection attempt would have been logged as an outage. The
blocked answer had to be shaped as JSON too:

```java
private static final String BLOCKED =
        "{\"columns\":[{\"header\":\"" + BlockedAnswer.MARKER + "\"}]}";
```

**And this is why the guard cannot just be a default advisor on the shared client.** Very tempting,
because it would make the guard impossible to forget. But the blocked answer has to match whatever
that particular call expects back, and one default cannot be both a sentence and a JSON document.

So the safety net is in the spec instead, checked at startup.

---

## What we did not solve

Writing these down was more useful than pretending they don't exist.

- the guard is opt in. Any developer can inject the client and call the model with no guard at all.
  Code review is the only check.
- the marker is a substring match, so a user can type it and make their own request look blocked. It
  costs them their own call. But no trust decision can rest on it.
- the rate limiter is per instance
- we have a brake, not chargeback

---

## Five questions for your next LLM review

1. How many of these calls could a plain deterministic path answer instead?
2. What is the maximum billed calls one user can trigger in an hour?
3. How many calls does one request actually become? Check the retry defaults.
4. What does your logging write? Go read one line.
5. Can your code tell a refusal apart from a real answer?

Takes twenty minutes and finds most of it.

The Advisor pattern is good. It gave us one place for all of this, and the guards now apply from a
spec rather than from whatever the last developer remembered to copy. Just remember an advisor that
can answer for the model is a feature and a footgun at the same time, and design for the footgun
first.
