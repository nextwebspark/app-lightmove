# Before I ship an LLM feature, I ask two questions

We run a SaaS for executive search firms. Customers upload spreadsheets of executives they are
approaching about a job. Sensitive data.

One feature uses an LLM. Someone uploads a CSV, and we ask the model what each column header means
so we can map it to our fields. That is it. Small feature.

Before I approved it, I asked two things. Not "is the prompt good".

1. What is the worst bill one user can create in an hour?
2. What data leaves our servers, and what lands in our logs?

We use Spring AI, and its Advisor pattern is how we answered both. An advisor is basically a filter
around the LLM call. It sees the request going out, the response coming back, and it can stop the
call completely. That stopping part saved us the most money, and also gave us our worst bug.

---

## Cost

Your bill is four things multiplied:

number of requests × calls per request × tokens per call × price per token

You don't control the price. You control the other three. So we worked on those.

### 1. The cheapest call is the one you don't make

Before asking the model anything, a plain matcher tries to map the sheet from header names alone. If
it is sure about every column, we return and never call the model.

```java
if (heuristic.everyColumnCertain()) {
    return new ProposedColumnMappings(heuristic.mappings(), MappingSource.EXACT_HEADERS);
}
```

Then we made that path more likely on purpose. We give customers a CSV template to download with our
headers already in it. Anything built from that needs zero model calls. Their second import also
needs zero, because by then we know their headers.

So the model gets asked once, when a new customer shows up with weird column names. After that it
mostly stops being asked.

This is a product decision, not a prompt decision. It only happens if someone thinks about cost
early.

Ask your team: how many of your LLM calls are asking the model to confirm something you already
know?

### 2. Send less data

We decided no cell values go to the model. Ever. It is a list of real executives.

So we only send the column header plus a small description we calculate ourselves:

```
- "Contact" (values look like: email addresses)
- "Co." (values look like: short text)
- "ARR" (values look like: numbers)
```

We allow files up to 5000 rows. If we sent the actual sheet, that is around 100,000 cells, roughly
300,000 tokens. What we send instead is about 4,000 tokens.

So about 100x cheaper. And we did it for privacy, not for cost. The cost saving was free.

That is worth remembering. When product pushes back on a privacy decision, "it is also 100x cheaper"
ends the argument fast.

### 3. Check the retry defaults

Spring AI can validate a JSON answer against a schema, and if it doesn't fit, it sends it back to
the model with the error attached. Good feature. Default is 3 retries. That is 4 billed calls for
one user click.

We set it to 1:

```java
@DefaultValue("1") int answerRepairAttempts,
```

A model that gets the shape wrong twice is not going to get it right on the fourth try.

Go and look at your retry defaults. They are usually higher than you would pick yourself.

### 4. Cap the user

Our endpoint is protected by login and nothing else on the cost side. So one logged in user with a
loop is an unlimited charge on our cloud account.

```yaml
column-mapping-requests-per-minute: 10
```

Now I have a number I can defend. Worst case, one user, one minute: 10 requests, up to 2 calls each
because of the repair, so 20 calls. 1200 an hour. Multiply by seats and I can put it in a board deck.

On the default of 3 retries it would have been 40 a minute. So one config line cut our worst case in
half.

Two honest caveats we wrote in the code, not hid:

- the limiter is in memory per instance, so with 3 pods the real ceiling is 3x
- it counts requests, not billed calls, because one request can become two

### 5. Config that does nothing looks like config that works

We configured retry properly, shipped it, and it did nothing at all. The provider wraps its errors in
a plain RuntimeException, which matched nothing in the retry policy. So nothing was ever retried.

It saved us money by accident. But our resilience story was wrong and we would have found out during
an outage instead of during a code read.

If a setting is supposed to save you money, there should be a test or a log line proving it is
actually connected.

---

## Security

### Prompt injection

The headers come from a file somebody uploaded. So a header can say "ignore previous instructions".

Three layers. The first is a word list. If the text matches, we never call the model:

```java
@DefaultValue({
        "ignore previous instructions",
        "disregard the above",
        "system prompt",
        "you are now"
}) List<String> injectionPhrases
```

Kept short on purpose. Every phrase is also something a real person could write one day, and blocking
real work is its own failure. A control that breaks the product gets switched off by someone under
deadline.

Second layer is the system prompt telling the model plainly that headers are data, never
instructions.

Third layer is that user text never goes into the prompt by string concatenation. It goes in as a
parameter:

```java
.user(u -> u.text("Columns:\n{columns}").param("columns", describeColumns(sheet)))
```

With concatenation there is no line between our structure and their content. With a parameter, the
template is a constant we wrote and their text is just a value.

The word list is the weakest of the three, honestly. The real control is the last section below.

### Our worst bug

The guard answers in place of the model. Your code calls the client, gets a String back, and has no
way to know if that came from the model or from the guard. Same type. No exception. No flag.

So a blocked call can be shown to the user as if the model actually answered. Which means something
nobody ever decided showing up on screen as a real answer, and somebody acting on it.

Fix is dumb but works. Every blocked answer carries a marker:

```java
public static final String MARKER = "__lightmove_blocked__";
```

And the app refuses to start if any prompt is configured with a blocked answer that doesn't carry it.
So we find out at boot, not on the request that would have been misread.

Same problem in a second shape. Our answer is JSON, so if the blocked answer was a sentence, the
JSON parsing would fail, and that failure looks exactly like "the provider is down", which we already
handle by falling back. So an injection attempt would have been logged as an outage. The blocked
answer had to be shaped as JSON too.

This is also why the guard cannot just be a default on the shared client. The blocked answer has to
match whatever that particular call expects back.

### Logs

The default logging in Spring AI dumps the full prompt and the full response. For us that is
spreadsheets of real executives going into a log system, kept on a different retention schedule and
readable by more people than the app allows.

We replaced it. We log metadata only:

```
chat request  prompt=import-column-mapping model=gemini-2.5-flash temperature=0.0
chat response inputTokens=1200 outputTokens=340 totalTokens=1540 finish=STOP
```

Two useful bits in there. The prompt name means we get per feature token counts from day one with no
vendor. And finish=STOP tells you whether the answer was complete, because a truncated answer still
comes back as a successful call and you paid full price for half an answer.

One warning. When the provider reports no token count, Spring AI puts 0, not null. So if you build a
cost dashboard by adding up that field, unreported calls quietly become free calls and the dashboard
lies to you.

If you do one thing after reading this, go and read one of your own LLM log lines today.

### The model cannot write anything

This matters more than anything above.

The import is two calls. The first one reads the file and proposes a mapping. The second one takes
the mapping a human confirmed and does the actual writing, and it calls no model at all.

And the writes go through the same code the normal UI uses, so all the permission checks and audit
logging stay where they already were. We did not build a second write path for the LLM.

Even in the preview, we don't trust the answer. We match the model's reply back to real columns by
header name, not by position, because a model that drops or reorders one entry would shift every
mapping after it onto the wrong column. That is the failure where a nice looking answer writes a
whole file into the wrong fields.

Schema validation proves the shape is right. It proves nothing about whether the content is sane.

---

## What we did not solve

Writing these down was more useful than pretending they don't exist.

- the guard is opt in. Any developer can inject the client and call the model with no guard. Code
  review is the only check.
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

The Advisor pattern is good. It gave us one place for all of this. Just remember an advisor that can
answer for the model is a feature and a footgun at the same time, and design for the footgun first.
