# Legitimate interests assessment: candidates and executives

_Draft for counsel (#930, epic #917). Engineering facts read from the code on 2026-11-13; not legal advice. Counsel
decides whether each balance holds._

Almost everything Uncava does with an executive's data rests on legitimate interests (Art. 6(1)(f)): the executive
never signed up, and consent is neither collected nor practical before a firm first approaches them. This assessment
runs the three-part test (purpose, necessity, balancing) for each activity. Where the customer firm is the controller
(§1–§3) the assessment is the firm's to adopt, and Uncava offers this as the template its product supports; §4 is
Uncava's own as controller of the vendor cache.

## 1. Mapping and holding a candidate record

**Purpose.** A search firm or in-house team maps the executives who could fill a senior role, keeps what it learns
about them, and comes back to them for later roles. This is the core of executive search, a lawful and expected
business activity.

**Necessity.** The record holds professional facts (name, current and past roles, location, education, LinkedIn URL),
the team's notes and documents, and compensation where the executive shared it. Contacts are fetched only on a
consultant's request (`ContactLookupService`). Without a record the firm cannot run a search.

**Balancing.**
- *Reasonable expectations.* Senior executives with a public professional profile generally expect to be approached by
  search firms. They do not expect inferred demographics (§5) or detailed compensation to be held without being told.
- *Impact.* Mostly an approach about a role. The risk is a record kept long after it serves anyone, or one that is
  wrong.
- *Safeguards in the product:*
  - erasure, restriction and the suppression list, so an objection is honoured and the person is never filed again
    (`PersonErasureService`, `ProcessingRestrictionService`, `SuppressionGate`);
  - a candidate retention period the firm chooses, with admin review of anyone untouched and on no live position
    (default 24 months ⚖️; `CandidateRetentionService`);
  - a subject access export with the logic of each AI use (`PersonSubjectAccessService`, `AiUse`);
  - client representatives read only the mandates they are attached to, and never notes, documents, tasks,
    assessments, AI readings or contacts.
- *Gap.* The executive is not yet told (Art. 14). #923 and #1267 build the notice; until then the balance leans on the
  first approach carrying it ⚖️.

## 2. Sourcing and enrichment

**Purpose.** Find executives who match a brief (Find executives, People Search, the Capture extension, spreadsheet
import) and fill their record from licensed providers.

**Necessity.** Search uses company, function and title; profiles come back by LinkedIn slug. Ranking is fixed rules
(`SuggestionJudge`, `SeatFit`), and the only model call is turning the brief into search words
(`SearchWordsDrafter`). The extension sends only a name and a profile URL. Spreadsheet import gives the model column
headers only (`send-sample-values: false`).

**Balancing.**
- *Expectations.* Professional profile data from business-data providers is generally expected to be used for
  recruiting; scraping a live profile on demand (HarvestAPI) is closer to the edge ⚖️.
- *Safeguards:* the suppression list is checked at every door that files a person; a restricted person is
  refused a contact lookup and a further mapping (`people-controls-918-920.md`); enrichment providers are off unless `ENRICHMENT_PROVIDER` is set.

## 3. Outreach

**Purpose.** Approach an executive about a role, by email from the consultant's own mailbox, in a sequence.

**Necessity.** One person's address, the consultant's message, and the replies in that thread (read to show the
conversation).

**Balancing.**
- This is direct marketing in the Art. 21(2) sense only arguably ⚖️; either way the objection must stop it at once.
- *Safeguards:* do not contact and a processing restriction stop a send (`RecipientEligibility`,
  `OutreachMessageSendService`); suppression stops a person being filed again.
- *Opt-out (#924):* every outreach email ends with a footer naming the firm and where the details came from, and a
  link to stop emails; Gmail and Nylas-sent Google mail also carry one-click `List-Unsubscribe`. Taking it sets do not
  contact, suppresses the person and stops every run at once. A Microsoft mailbox's email carries the link alone, and
  an objection written as a reply is still a consultant's to act on.

## 4. Uncava's vendor cache (Uncava as controller)

**Purpose.** Avoid paying for, and re-fetching, the same profile for each workspace; keep the service affordable.

**Necessity.** The cache keeps the vendor's record and the slugs a search returned, for 30 days
(`lightmove.enrichment.people-cache-ttl`), deleted by `RetentionSweeper` once `RETENTION_ENABLED` is on. A person
erased in a workspace is also removed from it.

**Balancing.** Short-lived and invisible to the person. The risk is that it is shared across tenants, so an
objection made to Uncava must reach it; [subject-requests-procedure.md](subject-requests-procedure.md) covers that.
Cross-workspace suppression is not built (#920) ⚖️.

## 5. What legitimate interests does not cover

- **Gender and nationality inference.** Nationality can reveal ethnic origin (Art. 9). Legitimate interests is not an
  Art. 9 condition, so either the inference is not special-category data or it needs an Art. 9(2) condition ⚖️. The
  product's answer today: per-workspace switches, off for every workspace founded from V216 (`ai-controls-922.md`),
  and a reading that never counts appearance, religion or inferred ethnicity (`AiUse.NATIONALITY`). See
  [dpia.md](dpia.md) §4.
- **Compensation** is not special-category data, but it is sensitive in effect; the firm should hold it only where the
  executive gave it or it is needed for the role ⚖️.

## Conclusion (for counsel to confirm)

On the facts above, §1–§4 can rest on legitimate interests provided: the Art. 14 notice ships (#923, #1267), the
opt-out ships (#924), the people controls are switched on in production, and the demographic inferences either stay
off by default or gain their own basis.
