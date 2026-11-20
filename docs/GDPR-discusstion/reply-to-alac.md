# Reply to Alac Partners (final draft)

Draft for Alok to send. Written for Aleksandra Mizińska (lawyer) and Hakan. Items in [brackets] are Alok's calls. The register it points to is `docs/gdpr/uae-alignment.md`.

---

**Subject:** Re: data protection - Uncava

Hi Aleksandra, hi Hakan,

Thank you for the thorough work. In parallel, engineering has built a large part of an EU-GDPR programme. I compared both against the product and the documents. Below are my answers, what exists today, and what is missing. The full item-by-item register is in `docs/gdpr/uae-alignment.md` [attach or link].

**1. Approach**

I agree with building to the highest common standard: the PDPL consent approach plus DIFC/ADGM requirements. KSA: [later, after UAE launch / priority now].

Our current design assumes legitimate interests, which is an EU basis. We will add a jurisdiction default per workspace, so UAE-regulated workspaces run in consent mode.

**2. ContactOut**

I asked ContactOut in writing. Their answers:
- An API agreement and a DPA exist. For the names and profile links we send, they are our processor. For the profiles, emails and phones they return, we are each an independent controller under SCCs. This confirms your view that we are a controller for that data.
- Redisplay to our clients is licensed. Their data is hosted on AWS us-west-1.
- Sources are public directories, web crawling, user-contributed data and licensed datasets. Phones are partly from a third-party provider. They cannot say per record whether a detail was made public by the person.
- Their basis is legitimate interests, with an assessment they will share. They email individuals on first processing and offer an opt-out page.
- By default API data is not stored. They review storage on request. I have submitted our use case (candidate record, stored profile answer, revealed email and phone, deletion on request) and asked for written approval, a lawful-collection warranty, their position on UAE individuals and an opt-out feed.

My reading:
- Profile data is defensible as published by the person.
- Private email and phone cannot rely on that, because provenance cannot be traced per record.
- Proposed decision: keep both, with your four mitigations. **The private email and phone lookup stays off for UAE-regulated clients until ContactOut's written storage approval and lawful-collection warranty are in place.** I will add a per-workspace switch for that lookup [confirm].

Your four mitigations today:
1. Contract with ContactOut: the DPA exists, the warranty is pending.
2. Client terms: for your team to draft.
3. Public Uncava notice: to draft.
4. Email footer: built. It states the source, links the privacy notice and carries a stop link, and it cannot be removed. It is switched off until go-live.

Questions for you:
- Can the PDPL "made public by the person" exception cover profile data obtained through a vendor?
- Is a public Uncava notice acceptable instead of notifying each person, under the PDPL?
- Is our cache shared across customers (30 days) a separate controller activity?

Single provider: [still open / confirmed as ...].

**3. Consent statuses and retention**

I accept the five statuses and the default periods as the target.

Exists today, behind a feature flag:
- consent register with CSV export
- consents in the access-request report
- future-roles validity of 12, 24 or 36 months (default 24)
- a 30-day renewal reminder
- warning before sharing a person with a client

Missing, now planned:
- channel and evidence fields
- privacy-notice version per person, and version management
- the grey 6-month, light-green (mandate + 12 months) and orange 3-month timers
- per-mandate scope
- editable templates
- a "valid consent" filter
- warnings for CV upload and talent-pool adds

All must-haves are planned. From your options I would take the consent warnings, the 30-day renewal reminder with a template, and the valid-consent filter [confirm].

Two points for counsel:
- Your red "do not contact" list keeps name and profile link. Our current design keeps only keyed hashes, and it deletes consent proof when the person is erased. Which do you prefer?
- Today a missing consent shows a warning and does not block. Under the PDPL, should it block?

**4. AI**

Agreed in full. Current state, honestly:
- The AI still infers nationality and gender, behind per-workspace switches.
- The Diversity report counts values the AI guessed and nobody confirmed.
- Assessment review is one click, and behavioural scores can be drafted from career data alone.
- The notetaker has no consent gate. The live copilot is on by default. Recordings are not limited to 90 days by default.

Delivery order:
1. Remove nationality and gender inference. Add manual "Local national", "Nationality" and gender fields. Fix the Diversity report to count confirmed values only.
2. Notetaker consent confirmed in the register before the bot joins, the bot introducing itself, a 90-day default for recordings and transcripts, and the copilot off by default with its own switch and disclosure.
3. Assessment controls: each score confirmed, evidence-only behavioural scores, sources per score, a draft-versus-final log, a rounded overall score, and an AI-assisted label in the client report.

Target dates: [dates].

Your inventory misses these AI uses, which I will add:
- a verdict on a person (shortlist or decline)
- call summary and live coach
- suggested edits to a record from a call
- outreach opening lines
- assistant chat
- position extraction from documents
- import column mapping
- search-word drafting

Please send any further gaps from `Uncava_AI_inventory.xlsx` in that form. Our DPIA does not yet analyse DIFC Regulation 10. I would like your view on it.

**5. Documents**

Draft, written for the EU, not yet reviewed by counsel:
- record of processing
- subprocessor list (regions incomplete)
- DPIA
- incident and breach runbook
- retention schedule
- candidate request procedure and access export
- legitimate-interest and transfer assessments

New drafts, now in the repo:
- information security policy
- access control policy
- backup and recovery procedure
- data flow map
- client message templates, including a reply to a candidate access request

Automated backups are currently off. I will switch them on and run a restore test.

I would like your help with the DPA, the terms of use, the public candidate notice, the user privacy policy and the client notice template. I will add UAE sections to the record of processing, the DPIA and the transfer assessment. I will send the full subprocessor list with hosting regions by [date].

Hosting today is in the US (Cloud Run, Cloud SQL, ContactOut on AWS us-west-1, Recall.ai us-east-1), plus a global AI endpoint. Nothing is in the UAE.

**6. Questions for you**
- Do we need a DPO?
- Which breach-notification deadlines and authorities apply (PDPL, DIFC, ADGM)?
- Do we need a local representative or controller entity in any regime?
- Are the US plus standard clauses acceptable to clients, or do we need a UAE region?

**7. Next steps**

Yes, please prepare recommendations for each area. I suggest a call on [date] to settle sections 2, 3 and 6.

Best regards,
Alok
