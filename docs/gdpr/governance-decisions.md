# Governance decisions register

_For counsel and the business (#930, epic #917). Engineering facts read from the code on 2026-11-13; not legal advice._

Every open ⚖️ decision in one list, so counsel and the business can answer them in one pass. Each bracketed
placeholder on the website's legal pages (`uncava-website` `src/content/legal/*.md`) and in the documents in this
folder maps to a row here. When a row is decided, write the answer and the date in **Status**, then fill the
placeholders it names.

## A. Identity and roles (nextwebspark/uncava-website#26)

| # | Decision | Options | Facts | Fills | Owner | Status |
|---|---|---|---|---|---|---|
| A1 | Legal entity | [UAE free-zone entity / mainland / DIFC / ADGM / other] | the site's terms name UAE, DIFC or ADGM as governing law | `[Company legal name]`, `[free zone or emirate, licence number]`, `[Registered address]` everywhere; terms' governing law | business | open |
| A2 | Mailboxes | real, monitored privacy@, legal@, security@ | the app sends from Resend; no inbound mailbox exists | `[Privacy email address]`, `[Legal email address]`, `[Security email address]`; root `SECURITY.md` | business | open |
| A3 | DPO (Art. 37) | appoint (internal or external), or record why not | Art. 37(1)(b): the core activity is mapping and profiling executives across many firms, regularly and systematically; that likely meets "large scale" | privacy policy "Data protection officer"; [ropa.md](ropa.md) | counsel | open |
| A4 | EU representative (Art. 27) | appoint a representative in a member state where data subjects are | Uncava is outside the EU and profiles people in the EU; Art. 27(2)'s exemption (occasional, not large scale) likely does not apply | privacy policy "Our EU and UK representatives" | counsel | open |
| A5 | UK representative (UK GDPR Art. 27) | as A4 | as A4 for people in the UK | as A4 | counsel | open |
| A6 | Lead supervisory authority | the authority where the EU representative sits, or none (no EU establishment) | — | [breach-runbook.md](breach-runbook.md) §5 | counsel | open |
| A7 | Named owners | incident lead, decision owner, who runs cross-tenant SQL and who approves it | today any granted Google account can query production (G1) | breach runbook §1; [subject-requests-procedure.md](subject-requests-procedure.md) §4 | business | open |

## B. Lawful basis and transparency (website #27, #923, #1267)

| # | Decision | Facts | Fills | Status |
|---|---|---|---|---|
| B1 | Legitimate interests holds for mapping, sourcing, outreach and the cache | [lia.md](lia.md) | privacy policy lawful-basis table | open |
| B2 | Reliance on Art. 14(5)(b) (disproportionate effort) for executives never contacted | the notice is built (#923): an admin sets the firm's details, staff send a notice one by one or in bulk, and a Not notified view lists anyone held over a month and never told; nothing sends one automatically | privacy policy "Executives" | open |
| B3 | Is outreach to executives direct marketing (Art. 21(2))? | sent one-to-one from the consultant's mailbox, in sequences; every email ends with the firm's footer and a link to stop emails, with one-click `List-Unsubscribe` except from Microsoft mailboxes (#924) | privacy policy "Your right to object" | open |
| B4 | Is Stripe Uncava's processor or a controller? | Checkout collects card, address, tax id | sub-processor list | open |
| B5 | Are Google, Microsoft and Zoom the customer's own providers or Uncava's sub-processors? | the consultant connects their own account; Uncava reads reply text in threads it sent | sub-processor list; DPA | open |
| B6 | Is Uncava a controller of the shared vendor cache? | shared by every workspace, 30 days | [ropa.md](ropa.md) C5 | open |

## C. Profiling and AI (#922)

| # | Decision | Facts | Status |
|---|---|---|---|
| C1 | Are the AI switches off for existing workspaces too? | V216 kept them on for existing workspaces, off for new; one migration switches them off | open |
| C2 | Nationality inference for people in the EU, EEA and UK: keep, keep with an Art. 9(2) condition, or drop | [dpia.md](dpia.md) §4 | open |
| C3 | Is the rule-based ranking (`SuggestionJudge`, `SeatFit`) an automated decision about a person? | it chooses who is added as a suggestion, never who is rejected; no model ranks | open |
| C4 | DPIA sign-off and whether to consult the regulator (Art. 36) | [dpia.md](dpia.md) §6 | open |

## D. Rights and requests (#919, #920, website #28)

| # | Decision | Facts | Status |
|---|---|---|---|
| D1 | Does suppression reach across workspaces? | hashes are keyed per workspace today | open |
| D2 | Is a restricted person hidden from client seats (grid, map, report, export)? | today they are not | open |
| D3 | Does Uncava redact third parties in a subject access export? | today the admin checks before sending | open |
| D4 | May Uncava tell a requester which firms hold them? | [subject-requests-procedure.md](subject-requests-procedure.md) §4 | open |
| D5 | A platform-admin lookup by slug or email hash | app work, its own issue once #28 is designed | open |
| D6 | How long the request log and breach register are kept | — | open |

## E. Retention (#921)

| # | Decision | Facts | Status |
|---|---|---|---|
| E1 | Every ⚖️ period in [retention-schedule.md](retention-schedule.md): candidates 24 months, workspace grace 30 days, exports 7 days, and the rest | `RETENTION_ENABLED` stays off until signed | open |
| E2 | The audit-trail floor | needs #256 to enforce | open |
| E3 | Backup window: 21 days of backups, 7 of PITR | backups are off today ([security-of-processing.md](security-of-processing.md)) | open |
| E4 | Workspaces deleted before #1188, purged with no certificate email | no address was captured for them | open |

## F. Transfers and processors (website #29, #925)

| # | Decision | Facts | Fills | Status |
|---|---|---|---|---|
| F1 | Transfer mechanism per recipient (DPF, SCCs module 2/3, UK IDTA or addendum) | [tia.md](tia.md) | `[mechanism to confirm]` on the DPA, privacy policy, sub-processor list | open |
| F2 | Region of Resend, Stripe, Bright Data, HarvestAPI, ContactOut | not in code | `[Region]` on the sub-processor list | open |
| F3 | Nylas and Recall: US or EU | code defaults to US; EU endpoints exist | sub-processor list | open |
| F4 | Pin Vertex AI to a region | `global` today | privacy policy, sub-processor list, `[region to confirm]` | engineering (#925) |
| F5 | Breach notice to customers | DPA: [24–48] hours | DPA, breach runbook | open |
| F6 | Audits and inspections: notice and frequency | — | DPA | open |
| F7 | How customers hear of sub-processor changes, and the notice period | DPA says [30] days | DPA, sub-processor list | open |
| F8 | CMEK for enterprise customers | not adopted | — | open |
| F9 | Where backups live | multi-region unless pinned | security-of-processing | open, with #925 |
| F10 | Keep `?q=` searches out of Cloud Run's request log | exclusion filter or shorter bucket | — | open (#927) |

## G. Website and organisation (website #26, #27, #29, #30)

| # | Decision | Facts | Fills | Status |
|---|---|---|---|---|
| G1 | Which processor delivers the demo and newsletter forms, and is the newsletter double opt-in? | endpoints are build variables (`PUBLIC_NEWSLETTER_FORM_ENDPOINT`, the contact form's) | `[form processor]`, `[Provider]`, `[confirmed by double opt-in]` | open |
| G2 | How long demo requests are kept | — | privacy policy `[period]` | open |
| G3 | How long billing records are kept | tax law of the entity's jurisdiction | privacy policy `[statutory period]` | open |
| G4 | Staff access to production data | standing grants exist today (G1 in `data-protection-plan.md`); break-glass is plan item 1.1 | DPA Annex 1 | open |
| G5 | Security training, vendor review, incident response as written policies | [breach-runbook.md](breach-runbook.md) is the incident half | DPA Annex 1 | open |
