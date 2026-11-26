# Personal data breach runbook

_Draft for counsel and the business (#930, epic #917). Engineering facts read from the code on 2026-11-13; not legal
advice. Bracketed items are decisions still to be made._

A personal data breach is any breach of security that leads to personal data being destroyed, lost, altered,
disclosed or accessed without authority (Art. 4(12)). That includes one firm's data reaching another, a lost laptop
holding a database dump, a mis-sent export, and a provider telling us about its own breach.

## 1. Roles

| Role | Who | Does |
|---|---|---|
| Incident lead | [Incident lead] | runs the response, keeps the timeline |
| Decision owner | [Decision owner] | decides severity and notification |
| Counsel | [Counsel] | advises on notification and wording |
| Customer contact | [Customer success owner] | sends the customer notices |

Anyone who suspects a breach reports it at once to the incident lead through [Security email address] or
[internal channel], without investigating alone.

## 2. Detection sources

| Source | What it shows | Where |
|---|---|---|
| Cloud Monitoring alerts | ERROR log lines and 5xx responses (5 or more in 10 minutes); uptime check | `ops/gcp/monitoring.sh` |
| Daily log digest | the day's errors | `ops/gcp/log-digest.sh` |
| Lockout emails | an account locked after 5 failed sign-ins | `LoginLockout` |
| Audit trail (`app_lm_audit_event`) | sign-in and session: `LOGIN_FAILED`, `ACCOUNT_LOCKED`, `TOKEN_REUSE_DETECTED`, `MFA_CHALLENGE_FAILED`, `MFA_RECOVERY_CODE_USED`, `MFA_DISABLED`, `RATE_LIMIT_EXCEEDED`, `PASSWORD_RESET_*`, `SESSION_REVOKED`, `EXTENSION_PAIRED` | Settings → audit log; SQL |
| Audit trail | data leaving: `CANDIDATES_EXPORTED`, `COMPANIES_EXPORTED`, `WORKSPACE_EXPORT_STARTED`, `WORKSPACE_EXPORT_DOWNLOADED`, `PERSON_DOCUMENT_DOWNLOADED`, `NOTETAKER_RECORDING_DOWNLOADED`, `OUTREACH_ATTACHMENT_DOWNLOADED`, `PUBLIC_API_READ`, `MCP_TOOL_CALL`, `AUDIT_LOG_EXPORTED`, `PERSON_SUBJECT_ACCESS_EXPORTED`, `ACCOUNT_DATA_EXPORTED` | as above |
| Audit trail | new access: `API_KEY_CREATED`, `OAUTH_GRANT_CREATED`, `OAUTH_CLIENT_REGISTERED`, `PLATFORM_ROLE_GRANTED`, `MEMBER_ROLE_CHANGED` | as above |
| Cloud Audit Logs (Admin Activity) | IAM grants, instance changes, secret version access by config change | GCP console |
| Provider notices | a sub-processor reports its own breach | [the mailbox each provider writes to] |
| Customers and the public | reports to [Security email address] or the privacy mailbox | |

**Gaps, stated plainly** (each is in [dpia.md](dpia.md) R8 and R11):
- No alert fires on any audit event; someone must look.
- No Data Access audit logs on Cloud SQL, Cloud Storage or Secret Manager, so a human reading data directly leaves no
  trace (`data-protection-plan.md` 1.2).
- Humans can be granted direct database access to every tenant (`ops/cloudsql/grant-db-user.sh`, gap G1).
- No `PERSON_VIEWED` event, so who read a record in the app is not recorded.
- The alert emails go to personal addresses, not a monitored team mailbox [to change].
- Automated backups and point-in-time recovery are off ([security-of-processing.md](security-of-processing.md)), so a
  destructive incident may not be recoverable.

## 3. Response steps

1. **Contain** (first hour). Revoke the sessions, keys or grants involved (`SESSION_REVOKED`, revoke the API key or AI
   app, disable the member); rotate a leaked secret in Secret Manager and redeploy; for a provider, disconnect it.
2. **Preserve evidence.** Do not delete rows or logs. Export the relevant audit events, Cloud Logging entries (they age
   out in 30 days) and Cloud Audit Logs to [an evidence bucket with restricted access]. Note every action with a
   UTC time.
3. **Assess.** Which workspaces, which people (by id), which data categories, how many, whether special-category or
   AI-inferred data, whether the data was encrypted, whether it is recovered. The clock for "aware" starts when there
   is reasonable certainty a breach happened.
4. **Classify** (§4) and decide notification (§5).
5. **Notify** with the templates in §6.
6. **Recover and learn.** Fix the cause, write the post-incident review, and add the entry to the register (§7).

## 4. Severity

| Level | Example | Notify |
|---|---|---|
| S1 | data of one workspace visible to another; database or bucket copy outside our control; mass export by an attacker | customers at once; regulator likely; individuals likely |
| S2 | one account taken over; one export sent to the wrong person; a provider breach touching our data | the affected customer; regulator unless unlikely to result in a risk |
| S3 | an attempt stopped with no access (lockouts, rate limits) | record only |

## 5. Who must be told, and when

| Uncava's role | Who | Deadline |
|---|---|---|
| Processor (workspace data) | each affected customer (the controller) | without undue delay, and within [24–48] hours of awareness (the DPA, nextwebspark/uncava-website#29) |
| Processor | the regulator and the individuals | the customer decides and notifies; Uncava assists (Art. 33(2), 28(3)(f)) |
| Controller (accounts, billing, the vendor cache) | the lead supervisory authority [via the EU representative ⚖️] | within 72 hours of awareness, unless unlikely to result in a risk (Art. 33) |
| Controller | the individuals | without undue delay where the risk is high (Art. 34) |
| Any | UAE / KSA / Qatar regulators | [per the customer's jurisdiction ⚖️] |

## 6. Templates

**To a customer (Uncava as processor)**

> Subject: Security incident affecting your Uncava workspace
>
> On [date, UTC] we became aware of [what happened]. It affects [categories of data] of about [number] people in
> your workspace [workspace name]. We have [containment taken]. We do not yet know [open questions]; we will update
> you by [time]. Your contact for this incident is [name, email]. Under our DPA we will help you assess whether to
> notify your supervisory authority and the people affected.

**To a supervisory authority (Uncava as controller), Art. 33(3)**

> Nature of the breach: [what happened, when, how discovered]. Categories and approximate number of data subjects:
> [ ]. Categories and approximate number of records: [ ]. Contact: [DPO or privacy contact]. Likely consequences:
> [ ]. Measures taken or proposed: [ ]. [If late: reasons for the delay.]

**To individuals, Art. 34**

> We are writing to tell you that [what happened] on [date]. It involved your [data]. This could [likely
> consequences]. We have [measures]. We recommend you [steps]. Questions: [Privacy email address].

## 7. Breach register

Every incident, notified or not, is recorded (Art. 33(5)). Keep it in [location, restricted access].

| Ref | Aware (UTC) | Found by | Role | Workspaces | Data and people | Severity | Contained | Customer told | Regulator told | Individuals told | Cause | Fix | Review |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| — | — | — | — | — | — | — | — | — | — | — | — | — | — |

## 8. Rehearsal

Run a tabletop once a year, and once before `DATA_PROTECTION_ENABLED` goes on in production: an S2 account takeover
and an S1 cross-tenant exposure. Record the date and the gaps found here.

| Date | Scenario | Gaps found | By |
|---|---|---|---|
| — | — | — | — |

## UAE addendum (2026-11-19)

_Draft for counsel; not legal advice. Nothing above this heading has changed._

§5 above sets the Uncava-as-controller deadline from **Art. 33 GDPR, 72 hours from awareness**, and takes the
customer deadline from the DPA ([24 to 48] hours, decision F5 in [governance-decisions.md](governance-decisions.md),
still **open**: 24 hours or 48 hours to customers). The last row of §5 ("UAE / KSA / Qatar regulators, per the customer's
jurisdiction") is a placeholder. This addendum fills the UAE part of it as a list of questions, not as settled law.

The lawyer's advice is to build to the highest common standard across the three regimes (Alac Partners, 2026-10-29), so
the **shortest** deadline that any regime sets should govern the internal clock once counsel confirms it. She lists an
"incident response and breach notification procedure" among the documents referred to in the DPA (message 6).

### 1. Authorities and deadlines

| Regime | Authority | Who notifies | Deadline | Threshold | Notify individuals |
|---|---|---|---|---|---|
| Federal PDPL (Decree-Law 45 of 2021) | [UAE Data Office, or the authority designated; Counsel to confirm] | [Controller; processor to the controller; Counsel to confirm] | [Counsel to confirm] | [Counsel to confirm] | [Counsel to confirm] |
| DIFC DP Law 2020 | Commissioner of Data Protection (DIFC) | [Controller; processor to the controller without undue delay; Counsel to confirm] | [Counsel to confirm; the law's own wording, not a GDPR assumption] | [Counsel to confirm] | [Counsel to confirm] |
| ADGM DP Regulations 2021 | Office of Data Protection (ADGM) | [Controller; Counsel to confirm] | [Counsel to confirm] | [Counsel to confirm] | [Counsel to confirm] |

### 2. How it fits the existing runbook

| Item | Position |
|---|---|
| Uncava as processor | The customer is the controller and decides whether to notify its authority; Uncava notifies the customer within the F5 period and assists. The DPA must meet the DIFC and ADGM service-provider terms [Counsel to confirm] |
| Uncava as controller (accounts, billing, the vendor cache, and, on the lawyer's reading, **data passed on from ContactOut**) | The 72-hour GDPR clock in §5 stays. For a UAE data subject it may be shorter under one of the regimes above [Counsel to confirm]. ContactOut's own breach notice to us starts our awareness clock (§2 detection sources, "Provider notices") |
| Which regime applies | Decided per incident from where the affected people and the customer are: mainland UAE (PDPL), a DIFC entity, an ADGM entity, or none. A customer's contract may name the regime [Counsel to confirm] |
| Contacts | The privacy contact and the incident lead (§1) hold a per-regime contact sheet for each authority [to fill in] |
| Templates (§6) | Reuse; each authority's required content [Counsel to confirm] |
| Rehearsal (§8) | Include one UAE scenario (a cross-tenant exposure touching a DIFC customer) in the next tabletop |

### 3. Open decisions

- **F5**: 24 or 48 hours to customers; pick the shorter if any UAE regime or a large client's terms require it.
- The deadlines and thresholds in §1, from counsel.
- Whether one internal clock (the shortest across all regimes) replaces the three, as the highest-common-standard approach suggests.
- KSA and Qatar regulators remain in the placeholder row of §5; the lawyer said KSA can be addressed later.
