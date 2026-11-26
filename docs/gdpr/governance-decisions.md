# Governance decisions register

_For counsel and the business (#930, epics #917 and #1467). Answers from the decision list of 19 Nov 2026, answered
25 Nov 2026 ("Data protection: the decision list, with answers"); not legal advice._

Every ⚖️ decision in one list. Each bracketed placeholder on the website's legal pages (`uncava-website`
`src/content/legal/*.md`) and in the documents in this folder maps to a row's **Fills**. When a row changes, update
its answer, status and basis, then fill the placeholders it names.

**Status.** *Decided*: answered; only the build is left. *Agreed direction*: the business or counsel agreed the
direction; a detail or a legal check remains. *Recommended*: engineering's proposal; the owner confirms.
*Needs counsel*: a legal answer is required; the recommendation is stated. *Needs input*: a fact or choice from the
business. 58 rows: 9 Decided, 8 Agreed direction, 23 Recommended, 14 Needs counsel, 4 Needs input. F4 and F12 are
engineering calls the 25 Nov answers adopted as settled, so they count as *Decided*.

**Basis.** *Counsel 23 Nov*: counsel's inline reply. *Email 25 Nov*: the business's email. *Engineering*: an
engineering recommendation. *Leadership* and *product default* as named.

**Build** names the issue that carries the answer into the product (epic #1467), or says none is needed. A document
that receives the answer goes under **Fills**.

## A. Who we are (nextwebspark/uncava-website#26)

| # | Decision | Answer | Status | Basis | Build | Fills |
|---|---|---|---|---|---|---|
| A1 | Which company Uncava legally is, and where it is registered | Hakan names the registered company, country and number; counsel then confirms which law the terms follow. Until then `[Company legal name]` stays in the DPA, terms and privacy policy | Needs input: Hakan | leadership | — | `[Company legal name]`, `[free zone or emirate, licence number]`, `[Registered address]` everywhere; terms' governing law |
| A2 | Real, monitored privacy@, legal@ and security@ mailboxes | three shared mailboxes, each read by at least two people; privacy@ in the candidate email footer, security@ on alerts and breach notices | Recommended | engineering | business creates them, then #1483 | `[Privacy email address]`, `[Legal email address]`, `[Security email address]`; root `SECURITY.md` |
| A3 | Data Protection Officer (Art. 37) | counsel decides; if not required, record the reason in writing; either way name a privacy owner | Needs counsel | counsel 23 Nov | website #26 | privacy policy "Data protection officer"; [ropa.md](ropa.md) |
| A4 | EU representative (Art. 27) | yes while Uncava holds data on people in the EU; counsel confirms and names the representative | Needs counsel | engineering | website #26 | privacy policy "Our EU and UK representatives" |
| A5 | UK representative (UK GDPR Art. 27) | as A4, decided together | Needs counsel | engineering | website #26 | as A4 |
| A6 | Lead supervisory authority | follows A4: the authority where the representative sits | Needs counsel | engineering | website #26 | [breach-runbook.md](breach-runbook.md) §5 |
| A7 | Named owners: incident lead, decision owner, who may look across customers' data and who approves it | Hakan names a security owner and an incident lead; the policies in PR #1406 carry `[Owner to confirm]` until then. Cross-customer access follows G4 | Recommended | engineering | #1485; owners in PR #1406 | breach runbook §1; [subject-requests-procedure.md](subject-requests-procedure.md) §4 |

## B. Lawful basis and telling people (website #27, #923, #1267)

| # | Decision | Answer | Status | Basis | Build | Fills |
|---|---|---|---|---|---|---|
| B1 | May firms keep and contact executives on legitimate interests, without asking first? | yes under the GDPR, UK GDPR, DIFC and ADGM: the person is kept with the grey (no consent) status ([lia.md](lia.md)). Only the UAE federal PDPL, which has no legitimate-interest basis, goes to counsel | Agreed direction | email 25 Nov | none; the PDPL question to counsel | privacy policy lawful-basis table |
| B2 | Must a firm tell someone it holds them even if it never contacts them (Art. 14(5)(b))? | a public Uncava privacy notice on the website; people not yet contacted stay grey and are kept 6 months. Counsel adapts the notice. The firm's own notice (#923) and the Not notified view stay | Agreed direction | email 25 Nov | #1490 | privacy policy "Executives" |
| B3 | Is outreach to executives direct marketing (Art. 21(2))? | a stop request always wins, whatever the answer. #924's footer and stop link are appended by the server to every outreach email and cannot be removed | Decided | email 25 Nov | none; live with #1487 | privacy policy "Your right to object" |
| B4 | Is Stripe Uncava's processor or a controller? | an independent controller for card data (Uncava never holds card numbers), Uncava's processor only for what it instructs; counsel confirms the wording | Needs counsel | engineering | sub-processor list wording | sub-processor list |
| B5 | Are Google, Microsoft and Zoom the customer's own providers or Uncava's sub-processors? | the consultant's own account and agreement; Uncava processes what the connection brings in as the firm's processor; counsel confirms | Needs counsel | engineering | sub-processor list wording | sub-processor list; DPA |
| B6 | Is Uncava a controller of the profile data it buys and reuses across customers? | yes. ContactOut confirmed Uncava is an independent controller of the data it returns, so those people are owed the public notice (B2); the same reasoning applies to the other vendors, counsel checks | Decided | email 25 Nov | #1490 | [ropa.md](ropa.md) C5 |
| B7 | Does a consent basis with no consent recorded block work on the person, or only warn? | warn-only until counsel answers. One exception: the notetaker bot never joins a call without recorded consent | Needs counsel | counsel 23 Nov | #1471 blocks; anything else waits on counsel (then a consent-mode rule on #1479) | — |
| B8 | Is a request nobody answers ever consent? | never. Silence is not consent: the person stays grey and the request lapses after 30 days (#1267) | Decided | counsel 23 Nov | built (#1267); #1475 | — |
| B9 | Should a minimal proof of consent outlive the person's erasure? | the do-not-contact list stays keyed hashes (final). A minimal proof (hash, purpose, date, notice version) after erasure is for counsel; recommended yes, minimal only | Needs counsel | engineering | none until answered | retention schedule |
| B10 | Who sends privacy and consent emails? | keep: Uncava's sender in the firm's name, the firm's privacy contact as reply-to | Recommended | engineering | none | privacy policy |
| B11 | Does a share-with-client request name the client? | keep: "a confidential client" unless staff choose to name it, never on a confidential position; standard practice in executive search | Decided | email 25 Nov | none | — |
| B12 | Should a declined or withdrawn share consent hide the person from the client? | yes, automatically. A no is a declined share consent; a withdrawal acts the same | Agreed direction | email 25 Nov | #1488 | — |
| B13 | UAE outside the free zones: does a LinkedIn profile count as information people made public themselves? | for counsel. Until answered, UAE workspaces run in consent mode, which is safe either way. Vendor-sourced data is the harder case: ContactOut cannot prove each record's source | Needs counsel | counsel 23 Nov | #1479 (consent mode meanwhile); #1480 | — |
| B14 | Should showing someone to a client be impossible without their consent? | same question as B7. Recommended: grey (no answer) warns, declined or withdrawn hides (B12); counsel decides whether grey blocks under the PDPL | Needs counsel | engineering | #1488 for declined and withdrawn; grey waits on counsel | — |
| B15 | Should privacy and consent emails go out from a separate address? | yes: a separate sending subdomain, so a large batch cannot hurt everyday delivery | Recommended | engineering | #1493 | — |
| B16 | Should a reply like "please remove me" stop contact? | replies are not read automatically. The recruiter reads the reply and marks the person **Do not contact**; such a reply is an objection, with the same effect as the stop link | Agreed direction | email 25 Nov | #1489 | — |
| B17 | Is looking up a LinkedIn profile live, at capture, acceptable? | keep: a recruiter captures one profile at a time, their action, not bulk collection. Counsel confirms with B13, and that the capture never stores contact details the person did not make public (CNIL v KASPR, 2024) | Needs counsel | engineering | #1480 | — |
| B18 | Customers who joined before the DPA existed: ask in Uncava or sign separately? | ask in Uncava with a re-acceptance prompt (acceptance recording is built); sign separately for large customers on request | Recommended | engineering | #1332, after the website's DPA has a URL and version (website #29) | — |

## C. AI that infers things about people (#922)

| # | Decision | Answer | Status | Basis | Build | Fills |
|---|---|---|---|---|---|---|
| C1 | Are the AI switches off for existing workspaces too? | settled by C2: nationality and gender inference is removed for everyone, so there is nothing left to switch off for those two; the other AI switches stay as they are | Decided | counsel 23 Nov | #1468 | — |
| C2 | Should the AI keep inferring nationality for people in the EU and UK? | no. Remove inference of nationality, origin and gender for everyone; human-filled Local national, Nationality and Gender fields, shown in aggregate reports only; the Diversity report counts confirmed values only | Decided | counsel 23 Nov | #1468 | [dpia.md](dpia.md) §4 |
| C3 | Is the rule-based ranking (`SuggestionJudge`, `SeatFit`) an automated decision about a person? | no: it only adds people and rejects no one; counsel names only the shortlist or decline verdict as high risk, and confirms in the DPIA | Agreed direction | counsel 23 Nov | none | [dpia.md](dpia.md) |
| C4 | DPIA sign-off; prior consultation of a regulator; consulting candidates | counsel reviews the DPIA including DIFC Regulation 10 (high-risk status, recognised certification scheme, Autonomous Systems Officer). Recommended: no prior consultation and no candidate consultation once the C5 controls are built | Needs counsel | counsel 23 Nov | DPIA review; #1481 | [dpia.md](dpia.md) §6 |
| C5 | May Uncava say a person always makes the final decision? | yes, once built: every score confirmed by a person and every verdict signed off by a named person, never automatic. Until #1469 and #1470 ship, client material does not make the claim | Decided | counsel 23 Nov | #1469, #1470 | — |

## D. People asking about their data (#919, #920, website #28)

| # | Decision | Answer | Status | Basis | Build | Fills |
|---|---|---|---|---|---|---|
| D1 | Does an objection to Uncava block the person for every customer? | keep the per-firm lists, and add an Uncava-level suppression for data Uncava controls itself (the vendor cache, B6) | Needs counsel | engineering | an issue to open once answered | — |
| D2 | Is a restricted person hidden from client seats? | yes, automatically; the same build as B12 | Agreed direction | email 25 Nov | #1488 | — |
| D3 | Are other people's names hidden in a subject access export? | keep: the firm's admin checks and removes them by hand. The sender of the 25 Nov email confirms they did not mean automatic hiding; if they did, an issue is opened to build it | Agreed direction | email 25 Nov | none unless the confirmation says otherwise | [subject-requests-procedure.md](subject-requests-procedure.md) §4 |
| D4 | May Uncava tell a requester which customers hold their data? | say that Uncava processes data for customers and send them to the firm; name firms only where the customer agrees; for the vendor cache, say what Uncava holds | Needs counsel | engineering | none | [subject-requests-procedure.md](subject-requests-procedure.md) §4 |
| D5 | A staff tool to find a person across every customer | not now: the requester names the firm. If built later: lookup by keyed hash, platform role only, second-person approval, every use logged (G4) | Recommended | engineering | none | — |
| D6 | How long the request log and the breach register are kept | 3 years after each request closes; 5 years for the breach register | Recommended | engineering | none | [retention-schedule.md](retention-schedule.md) |
| D7 | How carefully a requester's identity is checked | keep: light checks; ask for ID only where there is real doubt | Recommended | product default | none | [subject-requests-procedure.md](subject-requests-procedure.md) §1 |

## E. How long things are kept (#921)

| # | Decision | Answer | Status | Basis | Build | Fills |
|---|---|---|---|---|---|---|
| E1 | Every ⚖️ period in [retention-schedule.md](retention-schedule.md) | the consent periods are agreed: grey 6 months, light green mandate close + 12 months, dark green 24 months, orange 3 months; people never contacted 6 months (B2). The rest of the schedule still needs counsel's approval before `RETENTION_ENABLED` is turned on | Agreed direction | counsel 23 Nov | #1475, #1490, #1486 | retention schedule |
| E2 | How long the audit trail is kept | 12 months, then deleted; needs the audit-trail retention change | Recommended | engineering | #921, after #256 | — |
| E3 | Deleted data stays in backups for 21 days | yes, with the rule that a restore re-applies every erasure; backups must be switched on first | Recommended | engineering | #255, #1495 | [security-of-processing.md](security-of-processing.md) |
| E4 | Workspaces deleted before #1188 are purged with no confirmation email | yes: erase them, write a system-initiated audit line, send nothing | Recommended | engineering | checked in #1486 | — |

## F. Suppliers and transfers (website #29, #925)

| # | Decision | Answer | Status | Basis | Build | Fills |
|---|---|---|---|---|---|---|
| F1 | Transfer mechanism per US supplier (DPF, SCCs, UK IDTA or addendum) | collected per supplier when its DPA is signed; fills the sub-processor list | Needs input: Alok | engineering | — | `[mechanism to confirm]` on the DPA, privacy policy, sub-processor list |
| F2 | Region of each email, payment and data supplier | known: Google Cloud `us-central1`, Vertex AI `global`, Recall `us-east-1`, ContactOut AWS `us-west-1`. Alok asks Resend, Stripe, Bright Data, HarvestAPI, Mapbox and Nylas | Needs input: Alok | engineering | — | `[Region]` on the sub-processor list |
| F3 | Nylas and Recall: US or EU | stay on the US; switching to the EU is a configuration change, made when a customer or counsel requires it | Recommended | engineering | #1482 parked | sub-processor list |
| F4 | Pin Vertex AI to one region | not now: the models used are not offered in a single region. Revisit when a customer or the law requires it ([residency-adr.md](residency-adr.md) §1). Supersedes #917 step 1's "pin to us-central1" and #925's first box | Decided | email 25 Nov | none | privacy policy, sub-processor list |
| F5 | How fast a customer hears of a breach, and which Gulf regulators are told | 48 hours from the moment Uncava knows; regulators per regime (UAE, DIFC, ADGM) and their deadlines are on counsel's list | Recommended | engineering | DPA text (website #29) | DPA, breach runbook |
| F6 | Customer audits: frequency and notice | once a year, 30 days' notice, remote first; an extra audit after a breach | Recommended | engineering | DPA text (website #29) | DPA |
| F7 | How customers hear of a new sub-processor, and the notice period | 30 days, by email to workspace admins and on the sub-processor page, with a right to object | Recommended | engineering | #1491 | DPA, sub-processor list |
| F8 | Customer-managed encryption keys | not now; revisit when an enterprise customer asks | Recommended | engineering | DPA text (website #29) | — |
| F9 | Where backups live | the database's region, the US; revisit with F3 and F11 | Recommended | engineering | #255 | security-of-processing |
| F10 | Search terms in Cloud Run's request log | filter them out; deployed application logs already mask emails, LinkedIn links and phone numbers (#927); keep 30 days | Recommended | engineering | #1494 | — |
| F11 | A separate European copy of Uncava | not now; revisit when an EU customer requires it ([residency-adr.md](residency-adr.md) §2) | Recommended | engineering | none | — |
| F12 | A data processing agreement with every supplier | yes, with all of them, one by one; ContactOut first; Alok owns it | Decided | engineering | business (Alok) | sub-processor list |

## G. Website and how the company runs (website #26, #27, #29, #30)

| # | Decision | Answer | Status | Basis | Build | Fills |
|---|---|---|---|---|---|---|
| G1 | Which service handles the demo form and newsletter, and is the newsletter double opt-in? | Hakan chooses; it must offer a DPA and join the sub-processor list. Recommended: double opt-in | Needs input: Hakan | leadership | — | `[form processor]`, `[Provider]`, `[confirmed by double opt-in]` |
| G2 | How long demo requests are kept | 12 months after the last contact if no deal; counsel confirms | Recommended | engineering | none | privacy policy `[period]`; [retention-schedule.md](retention-schedule.md) |
| G3 | How long billing records are kept | follows A1; the accountant sets it (UAE rules ask 5 to 7 years, to verify) | Recommended | engineering | accountant, after A1 | privacy policy `[statutory period]` |
| G4 | Staff access to live customer data | emergency only, recorded, approved by a second person | Recommended | engineering | #1485 | DPA Annex 1 |
| G5 | Security training, supplier checks and incident response as written policies | drafts of the security, access-control, backup and incident policies are in PR #1406; staff training and supplier checks still to write | Recommended | engineering | PR #1406 | DPA Annex 1 |
