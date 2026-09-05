# Production observability — logging, a platform to read it on, and profiling

Research for the production launch. Three questions were asked: what the logging strategy should be,
what open-source platform to read the logs on, and how to profile a running JVM for leaks. A fourth is
answered too — what else has to be true before this service takes real customers.

Where Spring or Boot 4 already does something natively, that is the recommendation. Almost everything
below is configuration rather than code, and the code that is needed is small.

---

## 1. What is already here

This is not a greenfield observability story. A surprising amount is in place and correct, and the plan
below is mostly about closing the gap between "the pieces exist" and "someone can actually answer a
question at 2am".

| Piece | Where | State |
|---|---|---|
| Correlation id per request | `core/logging/service/CorrelationIdFilter` | **Done.** Reuses an inbound `X-Correlation-Id`, echoes it on the response, clears it in a `finally` so a pooled thread cannot inherit it. |
| Structured JSON logs when deployed | `logback-spring.xml` | **Done.** Cloud Logging's own field names (`severity`, `time`, `message`), `WARN`→`WARNING` translated, `correlationId` promoted to a top-level field. Human-readable console under `local`/`test`/`e2e`. |
| A disciplined level policy | `core/error/handler/GlobalExceptionHandler` | **Done, and better than most.** 5xx gets ERROR plus a stack trace; 4xx gets one INFO line naming the rule that fired; the eight "this is a client mistake, not our bug" handlers exist specifically so bot traffic and typo'd URLs do not drown the one real 500. |
| Correlation id on every error response | `Problems` / `GlobalExceptionHandler` | **Done.** A user can read the id off the screen and we can find the request. |
| A security audit ledger | `core/audit` | **Done.** `@Async` + `REQUIRES_NEW` so an audit row survives the rollback of the thing it recorded, on a separate bean so the proxies are actually live. |
| Health probes | `management.endpoint.health.probes` | **Done.** Liveness and readiness split; the deploy smoke test asserts `/actuator/health`. |
| Metrics registry | `micrometer-registry-prometheus` | **On the classpath, and unreachable — see gap 4.** |

The foundation is good. What is missing is the reader's half: nowhere to put the logs, no way to
pivot from a user to their requests, no traces, no numbers that anyone actually sees, and no profiler.

---

## 2. The gaps, in the order they hurt

### Gap 1 — The MDC carries a request id and nothing else

The stated goal is "trace down per user, what happened". Today a correlation id finds **one request**.
It cannot answer "show me everything Sara did this morning", or "everything that happened in this
workspace", which is what an executive-search support ticket actually sounds like.

Every log line should carry, when known:

```
correlationId   already there
userId          the acting user
workspaceId     the tenant — also the thing that proves an isolation bug when it is wrong
projectId       the mandate, where the route has one
traceId/spanId  gap 3
```

**Two traps this will hit, both worth knowing before writing the filter:**

`CorrelationIdFilter` is `@Order(HIGHEST_PRECEDENCE)` — deliberately, so a request that dies inside a
later filter is still findable. That means it runs **before Spring Security authenticates**, so
`AuthPrincipal` does not exist yet and the user id cannot be stamped there. This needs a *second*
filter, ordered after the security chain, that adds the principal fields and removes them in a
`finally` for the same pooled-thread reason.

And `@Async` does not carry MDC across the thread hand-off. `AuditEventWriter.write` is `@Async`, so
its own log lines already lose the correlation id today. Boot auto-configures a
`ContextPropagatingTaskDecorator` when `io.micrometer:context-propagation` is on the classpath — which
the OpenTelemetry starter in gap 3 brings in. Worth verifying it actually took effect rather than
assuming, because a silently inert propagator looks exactly like a working one until an incident.

Virtual threads are on (`spring.threads.virtual.enabled: true`). MDC is a `ThreadLocal` and works
fine on a virtual thread; the thing that breaks is the hand-off, not the carrier.

### Gap 2 — There is no access log

Nothing writes "this request finished, with this status, in this many milliseconds". That single line
is the highest-value log entry a service produces, and it is the one that is missing. Without it you
cannot answer "was it slow, or did it fail?", cannot find the slow endpoints, and cannot see a user's
session as a sequence.

Tomcat's own `server.tomcat.accesslog` is native and free, but it writes its own format to its own
file and knows nothing about `userId` or `workspaceId`. A ~40-line `OncePerRequestFilter` emitting one
structured line is the better answer here, and it composes with gap 1 rather than duplicating it.

### Gap 3 — No tracing, and Boot 4 now ships the answer natively

Spring Boot 4 added `spring-boot-starter-opentelemetry` — a single dependency that pulls in the
OpenTelemetry API, the Micrometer tracing bridge, and OTLP exporters for both traces and metrics. It
auto-instruments Tomcat, JDBC, `RestClient`/`RestTemplate`, and honours Micrometer's `@Observed`.

This is exactly the "if Spring has it natively, prefer that" case. Adding it gets, for one dependency
and a handful of properties:

- `traceId` and `spanId` in the MDC automatically, so log lines join up to a trace with no code change
- a span per HTTP request, per JDBC statement, per outbound vendor call (Bright Data, HarvestAPI,
  Resend, Vertex AI) — which is where the latency in this application actually lives
- a metrics exporter that **pushes**, which fixes gap 4

Cost is real but small: a few percent overhead, and one genuine caveat — on Cloud Run the SDK must
flush pending spans before the instance is killed, so the batch exporter's schedule and the graceful
shutdown in §5 are the same problem.

### Gap 4 — Prometheus metrics are dead on arrival in production

This is the finding worth acting on first, because right now the application computes metrics that
**nothing can ever read**.

`micrometer-registry-prometheus` is a *pull* model: something must scrape `/actuator/prometheus`. In
production:

- Cloud Run routes one port, so Actuator shares 8080 and `SecurityConfig` chain 0 stands itself down
- chain 3 then permits only `health` and `info` and applies `denyAll` to the rest
- the deploy workflow's own smoke test **asserts** `/actuator/prometheus` returns 401
- `--min-instances 0` means there is usually no instance to scrape at all, and `--max-instances 2`
  means the two that exist are anonymous and short-lived

Every one of those decisions is individually right. Together they mean the pull model cannot work.
Serverless wants **push**, and the OTLP metric exporter from gap 3 is the fix — the same dependency,
pointed at a collector. Keep the Prometheus registry for local development if it is useful; stop
pretending it does anything deployed.

### Gap 5 — Nowhere to read any of it

Today the JSON goes to stdout and Cloud Logging picks it up. That is genuinely a decent floor and the
log shape already targets it. What it does not give: dashboards, alerting anyone configured, trace
correlation, profile correlation, or a UI built for "filter to this user, show me the errors".

### Gap 6 — No profiling, at all

No JFR, no heap dump path, no continuous profiler, and no JVM memory alert. A leak would present as
Cloud Run silently recycling instances, which looks like nothing.

### Gap 7 — The frontend is a black hole

`apps/web` has no error boundary, no `window.onerror`, no `unhandledrejection` handler, and no RUM.
`apiClient.ts` parses the server's `ProblemDetail` carefully — and then does not record the
`X-Correlation-Id` the server just sent back on it. When a user says "the screen went blank", there is
no record that it happened, and no id to join it to the backend request that caused it.

Capturing that header in `apiClient` and attaching it to a client-side error report is a few lines,
and it is what turns "a user complained" into "here is the exact server request".

---

## 3. Recommendation — the logging strategy

Strategy first, tooling second. The tool is a URL; the discipline is the thing that survives.

**Levels — the rules the existing handler already follows, written down and applied everywhere:**

| Level | Means | Example |
|---|---|---|
| `ERROR` | We are broken. Someone should be paged. | Unhandled exception, audit write failed, vendor call exhausted its retries |
| `WARN` | Degraded, self-healing, but trending badly if it repeats | Vendor retry fired, rate limit tripped, optimistic-lock retry, pool near exhaustion |
| `INFO` | A business event worth having in the record | Login, signup, workspace created, project created, brief published, triage decision, invitation sent |
| `DEBUG` | Diagnostic detail, off in production, on per-package when hunting | Bad request bodies, malformed URLs, auth failure reasons |

`INFO` is the level to be deliberate about. It should be *business events*, not narration. One line per
meaningful state change, not one per method entered.

**One line per request, always** (gap 2), and **one line per business event**. That pair is the whole
strategy. Everything else is detail on top.

**Every line carries the context block** (gap 1) — `correlationId`, `traceId`, `userId`,
`workspaceId`, `projectId`. As MDC fields promoted into the JSON, never interpolated into the message
string, because a field is filterable and a sentence is not.

**A field is filterable; a sentence is not.** Prefer
`log.info("Triage decision", kv("stage", stage), kv("companyId", id))` — the logstash encoder is
already on the classpath and `StructuredArguments.kv` is what it is for — over
`log.info("Moved company {} to {}", id, stage)`. The second reads better in a terminal; the first is
the one you can group by at 2am.

**What must never be logged.** This is a legal requirement here, not hygiene. LightMove stores personal
data about executives who never consented to anything — names, employers, career history, salary bands.
Never log:

- passwords, tokens (raw *or* hashed), refresh cookies, JWTs, `Authorization` headers
- full request or response bodies on candidate, position or client routes
- a candidate's name, email, phone or career history — log the id and look it up
- an email address in full where it is not the subject of the event; the `GlobalExceptionHandler`
  already gets this right by logging *field names* and never their values, and that is the pattern

**Retention.** Pick a number and configure it rather than inheriting a default. Roughly: 30 days for
application logs, 30 days for traces, 400 days for the audit ledger — the ledger is the compliance
artefact and it lives in Postgres, not in the log platform, which is already the right call.

---

## 4. Recommendation — the platform

### The shape: instrument once with OTLP, decide the backend separately

The single most useful decision is to emit **OTLP** and treat the destination as a config value. That
is what Boot 4's OpenTelemetry starter does, it is vendor-neutral by construction, and it means the
choice below is reversible for the cost of an environment variable — which matters, because you are
choosing this before you know your own volume.

### Backend: Grafana LGTM

Loki (logs) + Grafana (UI) + Tempo (traces) + Mimir (metrics) + Pyroscope (profiles), all Apache-2.0,
all OTLP-native, all correlated in one UI. It is the stack that answers this exact question — click a
slow request in Tempo, jump to its logs in Loki, jump to the flame graph in Pyroscope, all joined by
`traceId`. That pivot is the thing you are actually buying.

Loki's per-user drill-down is a LogQL query, and it is genuinely this small:

```logql
{service_name="lightmove-api"} | json | userId="…" | line_format "{{.message}}"
{service_name="lightmove-api"} | json | severity="ERROR" | workspaceId="…"
{service_name="lightmove-api"} | json | correlationId="a3f9…"
```

**How to run it — start hosted, self-host if the bill or the policy demands it.** Two honest options:

1. **Grafana Cloud free tier.** Same open-source software, someone else's servers, no ops. For a
   service on `--max-instances 2` the free tier is not a trial — it is genuinely enough, for a long
   time. Start here. It costs an afternoon.
2. **Self-hosted.** `grafana/otel-lgtm` is a single all-in-one image (Grafana + Loki + Tempo +
   Prometheus, OTLP in on 4317/4318) and runs on one small GCE VM behind a private IP. Real ops work —
   storage, retention, upgrades, backups, and a thing that can page you about the thing that pages you.

The strong recommendation is **(1) now, (2) when there is a reason**. Nothing in the instrumentation
changes between them.

**The alternative worth knowing about: [SigNoz](https://signoz.io).** Apache-2.0, ClickHouse-backed,
one product rather than five, logs + metrics + traces in a single query layer, built OTel-first. If
this is going to be self-hosted from day one, SigNoz is meaningfully less to operate than assembling
LGTM. If it is going to be hosted, Grafana's free tier wins on effort.

**Keep Cloud Logging either way.** It already works, the JSON already targets its field names, it costs
almost nothing at this volume, and it is the sink that still has the logs when the shiny one is down.
Adding OTLP is additive; do not rip the working thing out.

### Getting the data out of Cloud Run

Two routes, and the first is much simpler:

- **Direct OTLP export from the app** to the collector endpoint. One property, no extra container.
  Needs the batch exporter to flush on shutdown (§5).
- **A collector sidecar.** Cloud Run supports multi-container revisions; the app writes to
  `localhost:4318` and the sidecar forwards. More robust to backend outages, and it is the documented
  Google pattern — but it is a second container to size, and on `--min-instances 0` it has the same
  cold-start and flush-on-death problems anyway.

Start direct. Move to a sidecar if export failures start showing up in the app's own logs.

### The frontend half

**[Grafana Faro](https://github.com/grafana/faro-web-sdk)** (Apache-2.0) is the matching piece for
gap 7 — a RUM SDK with a React distribution that hooks error boundaries and React Router v7, captures
JS errors, unhandled rejections, console output and web vitals, and ships them to the same backend.
Because it is OTel-based, a browser error can carry the **same `traceId`** as the API request behind
it. Combined with capturing `X-Correlation-Id` in `apiClient.ts`, that closes the loop from "the screen
went blank" to the stack trace on the server.

Even without Faro, the minimum is worth doing this week: a React error boundary, a `window.onerror`
and `unhandledrejection` handler, and a `POST /api/v1/client-errors` that logs them server-side. That
is an afternoon and it is the difference between knowing and not knowing.

---

## 5. Recommendation — profiling and memory leaks

Three different questions get called "profiling", and they need different tools.

### "Is memory growing?" — metrics, and this is the alert

Micrometer already publishes `jvm.memory.used` by pool, `jvm.gc.pause` and `jvm.gc.memory.promoted`
for free. A leak is **old-gen occupancy after a full GC trending up across restarts**, and that is a
graph plus a threshold, not a profiler. Once gap 4 is fixed and the metrics actually arrive somewhere,
this costs nothing and it is the single most valuable memory signal.

Set `-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=…` too. On Cloud Run the filesystem is ephemeral,
so the dump dies with the container unless it is written to a mounted GCS bucket — worth wiring,
because an OOM you did not capture is an OOM you will have again.

### "Where is the CPU/allocation going?" — continuous profiling

**[Grafana Pyroscope](https://grafana.com/docs/pyroscope/latest/configure-client/language-sdks/java/)**
(AGPL-3.0, part of the same stack) is the open-source answer. Use the **Java agent SDK**, which lives
inside the JVM and *pushes* — not Alloy's `pyroscope.java` component, which attaches externally and
needs root plus the host PID namespace, neither of which exists on Cloud Run. Overhead is
1–3% CPU. It gives always-on flame graphs for CPU, allocation and lock contention, and with the OTel
starter in place, span-to-flame-graph correlation.

Worth being honest about the limit: a profiler shows you **where memory is allocated**, and a leak is
about **what is retained**. Pyroscope narrows the search; a heap dump in Eclipse MAT or JDK Mission
Control is what actually finds the leak. Use both.

### "What happened in that one bad minute?" — JFR

Java Flight Recorder is in the JDK, costs ~1%, and is the deepest tool available. `/actuator/heapdump`
and `/actuator/threaddump` are Actuator endpoints that already exist and are currently `denyAll` in
production — correctly, they are dangerous — but they can be reached in staging or through an
authenticated break-glass path. Keep them shut on the internet.

### The Cloud Run caveat that undercuts all of it

`--min-instances 0 --max-instances 2` means instances are created and destroyed constantly. A slow leak
may never live long enough to manifest, and a profile is a few minutes of a process that no longer
exists. **Setting `--min-instances 1` is a prerequisite for meaningful profiling** — and it is wanted
anyway, because a Spring Boot JVM cold start is seconds and right now the first user after an idle
period pays for it.

### If GCP-native is acceptable

**Google Cloud Profiler** is free, is an agent flag, works on Cloud Run without any of the above, and
is built for exactly this. It is not open source, so it does not meet the stated preference — but it is
the lowest-effort thing on this page by a wide margin and is worth knowing about before ruling out.

---

## 6. What else to fix before production

Asked for, and the honest answer is that some of these are more urgent than the profiler.

**Blocking — do these first**

1. **Graceful shutdown is not configured.** `server.shutdown: graceful` and
   `spring.lifecycle.timeout-per-shutdown-phase` are absent from `application.yml`. Today a Cloud Run
   revision swap kills in-flight requests mid-response, drops queued `@Async` audit writes, and (once
   §4 lands) loses unflushed spans. This is one property and it is free.
2. **`--min-instances 0` in production.** Every idle period ends with a user waiting on a JVM cold
   start. Set it to 1. It also unblocks §5.
3. **The rate limiter is in-memory, per instance.** `application.yml` says so plainly — "a speed bump,
   not a quota" — and with `max-instances 2` the real login ceiling is 20/minute, wiped by every cold
   start. For a login endpoint on the public internet that is thin. It needs a shared store, and
   Postgres is already there.
4. **Confirm Cloud SQL backups and point-in-time recovery are on.** `bright-gcc` is a `db-f1-micro`
   shared with the Bright Data ETL. Do not find out during the incident.
5. **Nothing alerts anyone.** The minimum set: 5xx rate over a threshold, p95 latency, readiness
   failing, Hikari pool exhaustion, JVM old-gen trending up, Cloud Run pinned at max-instances.
   An alert that pages a human is what makes all the above worth building.

**Soon**

6. **An external uptime check.** Health that is only checked from inside proves the inside works.
7. **Capacity sanity.** `concurrency 80` × 2 instances = 160 concurrent requests against a Hikari pool
   of 5 each, on 1 CPU and 1Gi, with a 60s request timeout. Those numbers should be reconciled against
   a load test rather than against intuition — the pool is the ceiling that will bind first.
8. **`--allow-unauthenticated` with no Cloud Armor in front.** No WAF, no DDoS filter, no IP
   reputation. Fine at launch, worth a plan.
9. **Secret rotation.** JWT signing keys and the DB password are in Secret Manager; there is no
   documented rotation procedure. Write the runbook before it is needed.
10. **PII retention and log scrubbing** as a reviewable checklist — see §3.
11. **A runbook.** Where the logs are, how to find a user, how to roll back a revision (the deploy
    workflow already tags images by SHA precisely so you can, which is the hard half — write down the
    command), who to call.

---

## 7. A phased plan

Ordered by value per hour, not by topic.

**Phase 1 — the free wins (about a day)**
- `server.shutdown: graceful` + `--min-instances 1`
- The MDC filter for `userId` / `workspaceId` / `projectId` (gap 1)
- The one-line-per-request access log (gap 2)
- Verify MDC survives `@Async` rather than assuming it

After this, Cloud Logging alone can already answer "what did this user do and what broke".

**Phase 2 — the platform (two to three days)**
- Add `spring-boot-starter-opentelemetry`; traces and OTLP metrics start flowing (gaps 3, 4)
- Point it at Grafana Cloud's free tier
- Build the four dashboards that matter: error rate, latency, JVM memory, DB pool
- Wire the five alerts from §6

**Phase 3 — the blind spots (two to three days)**
- Frontend: error boundary, global handlers, capture `X-Correlation-Id` in `apiClient.ts` (gap 7)
- Then Faro if the minimum proves insufficient
- Pyroscope agent for continuous profiling (gap 6)
- `HeapDumpOnOutOfMemoryError` to a GCS bucket

**Phase 4 — hardening**
- Shared-store rate limiting
- Load test, then resize from evidence
- Cloud Armor
- The runbook

---

## Sources

- [OpenTelemetry with Spring Boot — spring.io](https://spring.io/blog/2025/11/18/opentelemetry-with-spring-boot/)
- [OpenTelemetry with Spring Boot 4: The New Starter — Dan Vega](https://www.danvega.dev/blog/opentelemetry-spring-boot)
- [Grafana Alloy as an OpenTelemetry Collector distribution](https://grafana.com/oss/alloy-opentelemetry-collector/)
- [Grafana Faro Web SDK](https://github.com/grafana/faro-web-sdk) and [`faro.receiver`](https://grafana.com/docs/alloy/latest/reference/components/faro/faro.receiver/)
- [Pyroscope Java SDK](https://grafana.com/docs/pyroscope/latest/configure-client/language-sdks/java/) and [continuous profiling costs and benefits](https://grafana.com/blog/continuous-profiling-in-production-a-real-world-example-to-measure-benefits-and-costs/)
- [Write OTLP metrics using an OpenTelemetry Collector sidecar on Cloud Run](https://docs.cloud.google.com/run/docs/tutorials/custom-metrics-opentelemetry-sidecar)
- [Serverless observability: monitoring Cloud Run with OpenTelemetry — Grafana](https://grafana.com/blog/serverless-observability-how-to-monitor-google-cloud-run-with-opentelemetry-and-grafana-cloud/)
- [SigNoz as a self-hosted alternative](https://signoz.io/blog/grafana-alternatives/)
