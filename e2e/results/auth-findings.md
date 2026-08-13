# Authentication end-to-end findings

Two runs so far.

| | Date | Result |
|---|---|---|
| **Run 1** — baseline | 2026-08-11 | 254 checks, 16 findings raised |
| **Run 2** — after fixes | 2026-08-11 | **271 checks, 0 failures**, all fixes verified |

LightMove `main` · API Spring Boot 4.1 on :8080 · SPA Vite on :5173 · throwaway `postgres:16-alpine`
(Flyway V1–V19 clean on a virgin schema) · email provider forced to `log`, so nothing was mailed.

---

# Run 2 — verification

Every finding below was re-driven against the rebuilt API. The e2e assertions were updated to the new
intended behaviour, and in three places a **regression guard** was added so the old behaviour cannot
come back unnoticed.

| Phase | Script | Run 1 | Run 2 |
|---|---|---|---|
| Happy path | `01-happy-path.sh` | 60 | **61** |
| Signup validation | `02-signup-validation.sh` | 24 / 2 fail | **33** |
| Login & lockout | `03-login-lockout.sh` | 22 | **29** |
| Tokens & verification | `04-tokens-verification.sh` | 34 | **35** |
| Session & CSRF | `05-session-csrf.sh` | 32 | **32** |
| Onboarding edges | `06-onboarding-edges.sh` | 32 | **36** |
| Rate limiting | `07-rate-limits.sh` | 15 | **15** |
| SPA (Chromium) | `spa/run.mjs` | 33 | **33** |
| | | | **271 passed, 0 failed** |

## Fixes confirmed

**A1 · password over 72 bytes** — `N1.8`. 41 accented characters (82 bytes) now returns
`400 VALIDATION_FAILED`. No `IllegalArgumentException`, no 500. `PasswordPolicy` measures
`getBytes(UTF_8).length`, and the Zod mirror uses `TextEncoder`, so the browser catches it first with
a byte-accurate count.

**A2 · unsupported media type** — `N6.5`. `Content-Type: text/plain` returns **415**, not 500. The
`406` sibling and both `ErrorCode` constants landed with it, and `ClientErrorTest` covers them.

**A3 · logout misread as theft** — `P7.3`, `P7.4`, `N19.7`, `N19.8`. A logged-out token now answers
`REFRESH_TOKEN_INVALID`, and **no** `TOKEN_REUSE_DETECTED` row is written for it. Same for a session
revoked by a password change. `RevokeReason.indicatesTheftOnReplay()` is the right shape: the reason
lives on the enum rather than being re-derived at the call site, and a null reason fails closed.

> **Regression guard — `N22.2`.** A replayed `ROTATED` token must *still* be theft, because that is
> the actual attack signature. Verified: `401 REFRESH_TOKEN_REUSED`, family revoked, other families
> untouched (`N22.6`, `N22.6b`). This is the assertion that stops the fix from widening into "no
> revoked token is theft".

**B1 · login timing oracle** — `N9.4`, `N9.5`.

| | Run 1 | Run 2 |
|---|---|---|
| Unknown address | 26 ms | **279 ms** |
| Real address, wrong password | 276 ms | **281 ms** |

A ~10× gap closed to under 1%. Putting the decoy in `PasswordPolicy.matches` rather than in `login()`
was the better call — it covers the Google-only account (null hash) on the same line, which the
original finding had misattributed to `login()`. Deriving the decoy hash at startup so its cost tracks
`bcrypt-strength` is the detail that keeps this from rotting the next time the strength moves.

**B2 · MX check** — `N4.2`, `N4.2b`. NXDOMAIN now rejects (`EMAIL_UNDELIVERABLE`); `example.com`'s
RFC 7505 null MX rejects; timeouts still fail open. Splitting `acceptsMail` out so the answer can be
parsed without a resolver is what makes it testable at unit level.

**B3 · space-padded address** — `N3.4`, `N5.1`–`N5.3`. `  user@x.com  ` is now trimmed on
deserialization and lands as a duplicate (`409`), not as "That doesn't look like a valid email".
`EmailAddressNormaliser` beats `@Email` because it runs during binding. Correctly not applied to
password fields.

**B6 · expired onboarding hold** — `N30.1`–`N30.6`. The workspace is now created even when the hold
lapsed, and the user lands in it as `ADMIN`. The competing case still wins: someone who joined a
workspace in the meantime keeps that one and the stale draft is discarded, exactly one active
membership (`N30.6`). The ordering fix — decide, then delete — is the important half.

**C2 · 423/403 existence oracle** — `N10.7`–`N10.12`, `N12.1`–`N12.4`. A locked account and a
suspended one now return `401 INVALID_CREDENTIALS` with a body byte-identical to an unknown address.
The real reason is still in the audit trail (`status_DELETED`, `account_locked`). The lockout reaches
the owner by email instead — sent **once when the lock arms**, verified as still one after two further
attempts (`N10.11`), so an attacker cannot use the lockout to mail-bomb someone.

**C1, C3, B4 · documentation** — `CLAUDE.md` now matches the code on consumer domains, the vagueness
rule, password-reset-as-verification, and the non-decaying lockout. `N4.5`/`N4.6` were flipped from
"finding" to an assertion that the documented default holds.

## Findings correctly dropped

Four did not survive contact with the code, and the reasoning is sound in each case:

- **B5 (suspension window)** — nothing in `src/main` ever sets `SUSPENDED`; the e2e run manufactured
  it with raw SQL. Recording it as a constraint for whoever builds suspension is the right disposal.
  The e2e case is kept as `N12.5`, downgraded from an assertion to a note, so the fact stays visible
  where the feature will be written.
- **C4 (duplicate invites)** — real behaviour is `200 {"sent":0}` with no mail. My run-1 report read
  the status code and inferred the rest. Now asserted properly: `N33.3`, `N33.3b`, `N33.3c`.
- **C6 (`<script>` in a name)** — subjects reach Resend as JSON, not SMTP headers, and HTML bodies are
  escaped. The sink audit I asked for had already been done.
- **B1 attribution** — the leak was real; my location was wrong.

## Still open, unchanged

- **Google OAuth remains entirely untested.** No local credentials, so `OAuth2LoginSuccessHandler`,
  account linking, and the `/auth/callback` fragment hand-off have neither automated nor manual
  coverage. Largest blind spot in authentication; needs a test OAuth client.
- **`N12.5`** — a token minted before suspension still answers 200 on `/auth/me` for up to 15 minutes.
  Not actionable until suspension exists; the note is the handover.
- **`N26.4`** — the same statelessness after logout. By design.

---

# Run 3 — what happens when the mail provider is down

Every earlier run used `LogEmailSender`, which cannot fail, so the whole delivery-failure branch was
unexercised. Run 3 boots the API with `provider=resend` pointed at `http://127.0.0.1:9` — a dead local
port, so the connection is refused before anything leaves the machine. A bogus key alone would still
have sent the request to Resend's servers; this cannot.

`08-email-outage.sh` — **19 checks, 0 failures.**

## The design is right

The load-bearing decision holds up. `EmailSender`'s contract says implementations must not throw on a
delivery failure, `ResendEmailSender` catches and logs, and the result is what it should be:

- Signup returns **201**. The account exists. A provider's bad minute does not cost the user their
  registration (`N41.1`, `N41.2`).
- Signup took **326 ms** with the provider refusing connections. `READ_TIMEOUT` is wired at
  `EmailSenderConfig:54`, so a *hanging* provider caps at 10 s rather than holding the request thread
  and its database connection indefinitely (`N41.3`).
- Steps 2 and 3 carry on and are held exactly as normal (`N42.1`, `N42.2`).
- **The draft survives intact** — workspace name, size, region, and the queued invitation are all
  still on the held row (`N43.9`–`N43.11`). Combined with the B6 fix, which stopped `materialise()`
  refusing a lapsed hold, recovery is *one resend* whenever the provider comes back, however long the
  outage lasted. B6 turned out to matter more than the original finding suggested.
- Nothing is half-created: no workspace, no invitation rows, no consumed token (`N43.7`, `N43.8`).

## Two gaps, both about signalling rather than state

**The audit trail records a send that did not happen.** `N41.7`, `N41.8`:

```
EMAIL_VERIFICATION_SENT   outcome = SUCCESS
```

`VerificationService.sendVerificationEmail` calls `emailSender.send(...)` and then records the event
unconditionally — and because the sender swallows the failure by contract, the service cannot tell.
This is the record an operator consults when a customer says "I never got the email", and it currently
says it was sent successfully. The only contrary evidence is an ERROR line in the application log.

Worth fixing at the seam rather than in the caller: `EmailSender.send` returning a boolean, or a
`DeliveryOutcome`, lets `VerificationService` record `.failed()` without any caller learning which
provider is in use. The same applies to invitations and password resets, which record their own events
the same way.

**Nobody is told.** `N42.5`, `N43.1`–`N43.4`, `N43.12`:

- The signup response is byte-identical to a successful one — the SPA cannot distinguish a failed send
  from a mail in flight, so it parks the user on "check your inbox" and waits.
- `POST /auth/verify/resend` answers **202** while failing again. `CheckInboxPage` flips its button to
  **"Link sent"**, actively confirming a delivery that did not occur.
- `POST /auth/password/forgot` — the other route past verification (C3) — also answers 202 and also
  delivers nothing, so the escape hatch is shut at the same moment.
- The user can still log in (`N43.5`) but reaches no workspace data (`403 EMAIL_NOT_VERIFIED`,
  `N43.6`). They are stuck in a loop with no error anywhere in the product.

Recovery exists and is cheap, but only if the user happens to press resend after the provider
recovers. Nothing prompts them, and nothing pages anyone: six consecutive `Failed to send` lines
produced no audit event, no metric, no alert.

**Suggested minimum:** record the delivery outcome truthfully (above), and emit something an operator
can alarm on — a counter or an audit event on repeated send failures. Telling the *user* "we could not
send the email, try again shortly" is a further step and needs care, since the send failure is known
only after the account exists.

## One small gap left by A1 — closed 2026-08-13

The improved message never reaches an API caller. `PasswordPolicy.validate()` returns
*"Use at most 72 characters — fewer if they are accented or emoji"*, but the over-length rule lives in
the service rather than on the DTO, so `GlobalExceptionHandler` answers with the generic
`"One or more fields are invalid"` and no `fieldErrors`:

```json
{"detail":"One or more fields are invalid","code":"VALIDATION_FAILED"}
```

The wording is only in the DEBUG log. The `min 8` and `digit` rules do reach the user, because those
are `@Size`/`@Pattern` on `SignupRequest` and arrive as `fieldErrors`.

**Impact is small** — the Zod schema catches it in the browser with the same wording, and the SPA is
the only client. But the message was rewritten precisely so a user would not retype a password the
encoder refuses again, and through the API that is still what happens. If it is worth closing, carry
the validate() message through as a `fieldErrors.password` entry rather than dropping it.

**Closed** — that is exactly what it does now. `ApiException` gained a second, opt-in message channel
(`userFacing` / `withField`); the ordinary constructor still keeps a thrower's message internal, because
several rules quote the request. All four `PasswordPolicy.validate()` sites and `termsAccepted` now
throw `withField`, so the wording arrives as `fieldErrors.password` beside the `@Size`/`@Pattern` ones.
See the Resolution section of `workspace-role-findings.md`, which carried the same gap.

---

# Run 1 — baseline findings (historical)

Kept for the reasoning and the reproductions. Severity: **A** crash or broken security promise ·
**B** incorrect/misleading · **C** works as written, design decision needed.

| | Finding | Disposition |
|---|---|---|
| A1 | Password over 72 **bytes** returns 500 (`IllegalArgumentException: password cannot be more than 72 bytes`). Policy counted characters, BCrypt counts bytes. `PasswordPolicy.java:39` | **Fixed** |
| A2 | Unsupported `Content-Type` returns 500 instead of 415 — `HttpMediaTypeNotSupportedException` unhandled, fell to the catch-all | **Fixed** |
| A3 | Logged-out refresh token reported as theft: `TokenService:93` branched on *whether* revoked, never *why*. Told the user their session ended "for security reasons" and burned `TOKEN_REUSE_DETECTED` on routine logouts | **Fixed** |
| B1 | Login leaked account existence by timing, 26 ms vs 276 ms — BCrypt only ran for a real account | **Fixed** (in `matches`, not `login`) |
| B2 | MX check blocked nothing: NXDOMAIN filed as "inconclusive" and accepted; RFC 7505 null MX accepted. Cost 3 s of DNS for no protection | **Fixed** |
| B3 | Space-padded address rejected rather than trimmed — `@Email` ran before `normalise()` | **Fixed** |
| B4 | Failed-attempt counter survives lock expiry, so one wrong password re-locks instantly | **Documented** as intended |
| B5 | Suspension leaves live access tokens working ≤15 min | **Dropped** — no suspension feature exists |
| B6 | Expired onboarding hold deleted *before* the expiry check, destroying the draft on the path that declined to use it | **Fixed** |
| C1 | `gmail.com`/`outlook.com` accepted although CLAUDE.md said otherwise | **Documented** — code was right |
| C2 | `423`/`403` on login reachable only for real addresses — cheaper oracle than the timing attack | **Fixed** |
| C3 | Password reset doubles as email verification | **Documented** |
| C4 | Inviting an existing member returns 200 | **Dropped** — already skipped, `sent:0` |
| C5 | SPA hardcodes `termsAccepted: true`; no checkbox | Open — product/legal call |
| C6 | `<script>` stored verbatim in `fullName` | **Dropped** — sinks escape |
| ops | `AUTH_*` env vars silently ignored when a profile file hardcodes the property | **Documented** in README |

## What was verified sound in both runs

Attacked and held, twice. Listed so a future change knows what it might break.

**Session and CSRF** — double-submit enforced on `/auth/refresh` and `/auth/logout` (missing,
mismatched, and cookie-less header all `403`); the exempt list is exactly the anonymous routes;
`GET /auth/csrf` really writes the cookie. JWT integrity holds against an edited payload under the
original signature, `alg:none`, an extended `exp`, and a forged `wsId`. Rotation and family revocation
behave as designed. Refresh cookie is `HttpOnly` and path-scoped; no token in web storage.

**Tokens** — single use, 24 h TTL, expiry distinguished from invalidity, purposes do not cross, resend
supersedes, only SHA-256 digests stored, a weak password does not burn a reset link, a reset revokes
prior sessions and clears a lockout.

**Rate limiting** — login, signup, resend and password-reset budgets enforced; 429 arrives *before*
the credential check; forged `X-Forwarded-For`/`X-Real-IP` buy nothing; a throttled signup creates no
user. In-memory per instance, so every limit multiplies by instance count.

**Onboarding** — one workspace per user holds under a second create and under a cross-workspace
invitation accepted by an existing member (both `409`); invitations single use; an already-registered
address cannot be duplicated through invited signup.

**SPA** — the wizard routes off server state; the single-use token survives StrictMode's
double-invoke; a cold reload rebuilds the session from the httpOnly cookie alone; guards hold both
ways; sign-out clears the cookie and the back button does not restore the view; two tabs booting at
once are serialised by the cross-tab lock with no false theft detection.
