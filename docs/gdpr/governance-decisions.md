# Data protection: the decision list

_Updated 19 November 2026. A working list, not legal advice._

Every data protection decision we are still waiting on is in this list. Each one has a short ID (like **B7**), so
other documents can point to it.

**How to use it.** When a decision is made, write the answer and the date in the **Status** column. Then whoever
looks after the website and the legal documents fills in the gaps it unlocks (see the last section).

To see where each question shows up in the product, and what is already finished, read
[open-questions.md](open-questions.md).

**Who decides**
- **Legal**: our data protection lawyer.
- **Leadership**: founders or management.
- **Design**: a screen needs designing first.
- **Product team**: we can settle it ourselves.

**Status**
- **Open**: nobody has decided yet.
- **Default chosen**: the product already works one way. Legal only needs to say yes or change it.
- **Decided**: answered. The answer is written in the row.

---

## A. Who we are

| ID | What needs deciding | Where things stand | Who decides | Status |
|---|---|---|---|---|
| A1 | Which **company** Uncava legally is, and where it is registered. This also sets which country's law our terms follow. | Our terms mention UAE, DIFC or ADGM, but no company is named yet. | Leadership | Open |
| A2 | Real email inboxes that someone checks: **privacy@, legal@ and security@**. | They don't exist yet. Security alerts go to personal inboxes. | Leadership | Open |
| A3 | Do we need a **Data Protection Officer**? If not, we must write down why. | Studying executives across many firms is our main business. That probably means yes. | Legal | Open |
| A4 | Do we need a **representative in the EU**? | We're based outside the EU but hold data on people inside it. That probably means yes. | Legal | Open |
| A5 | Do we need a **representative in the UK**? | Same reasoning as A4. | Legal | Open |
| A6 | Which **European regulator** we report to, if any. | Depends on A4. | Legal | Open |
| A7 | **Who is in charge** when something goes wrong, who makes the call, and who may look across all customers' data (and who approves it). | Not named. Several staff can read live customer data today. | Leadership | Open |

## B. Our right to hold data, and telling people about it

| ID | What needs deciding | Where things stand | Who decides | Status |
|---|---|---|---|---|
| B1 | Can firms keep and contact executives because they have a **genuine business need**, without asking each one first? | Yes, that's how the product works. Our written reasoning is ready for review ([lia.md](lia.md)). | Legal | Open |
| B2 | Must a firm **tell someone it holds their details** even if it never contacts them? Or is that too much effort to be required? | Nothing is sent automatically. Firms can see who hasn't been told and send a notice by hand. | Legal | Open |
| B3 | Does emailing an executive about a job count as **marketing**? If yes, a "stop" request must always win. | Every email already has a link to stop emails. | Legal | Open |
| B4 | Does **Stripe** (payments) work for us, or is it responsible for card data itself? | We list it as working for us. | Legal | Open |
| B5 | When a consultant connects their own **Google, Microsoft or Zoom** account, is that their supplier or ours? | We treat it as theirs. | Legal | Open |
| B6 | Are we **responsible ourselves** for the profile data we buy and reuse across customers? If so, we owe those people a notice too. | Our draft says yes. | Legal | Open |
| B7 | If a firm relies on someone's **consent but doesn't have it**, should the product **stop** working on that person, or just **warn**? | It warns and stops nothing. | Legal | Default chosen |
| B8 | If someone **never answers** a request for consent, can that ever count as yes? | Never. The request expires after 30 days. | Legal | Default chosen |
| B9 | When someone is deleted, should we **keep proof that they once agreed**? | No. Everything about them is deleted. | Legal | Default chosen |
| B10 | Who should **privacy and consent emails come from**: Uncava's address on the firm's behalf, or the firm's own inbox? | Uncava's address, in the firm's name. Replies go to the firm. | Legal | Default chosen |
| B11 | When we ask if we may **share a profile with a client**, should we name the client? | We say "a confidential client" unless staff choose to name them. We never name the client on a confidential search. | Legal | Default chosen |
| B12 | If someone **says no to being shared** with a client, should the client stop seeing them automatically? | Staff get a warning; the client still sees them. | Legal, then Design | Open |
| B13 | **UAE (outside the free zones):** the law allows using information people made public themselves. Does a **LinkedIn profile** count? | Undecided. If not, UAE and probably Qatar customers must ask for consent first. | Legal | Open |
| B14 | Should showing someone to a client be **impossible without their consent**? | Staff are asked first but can choose "Show anyway". | Legal | Open |
| B15 | Should privacy emails go out from a **separate email address**, so a big batch can't hurt delivery of everyday emails such as invitations? | They share the everyday address. Quick to change. | Product team | Open |
| B16 | Should a reply like "please remove me" **prompt staff** to stop contacting the person? That means reading replies, which we deliberately don't do. | Not built. | Legal | Open |
| B17 | Is **looking up a LinkedIn profile live**, when someone is captured, acceptable? It is riskier than buying data from established vendors. | We do it. | Legal | Open |
| B18 | Customers who joined earlier **never signed our data agreement** (the DPA). Ask them in the product, or sign with each one separately? | Nothing asks them. | Legal | Open |

## C. AI that guesses things about people

| ID | What needs deciding | Where things stand | Who decides | Status |
|---|---|---|---|---|
| C1 | Should the AI features be **off for older customers** too? | Off for new customers, on for those who joined before the switches existed. One step to change. | Legal | Open |
| C2 | Should the AI keep **guessing nationality for people in Europe and the UK**? Nationality can reveal ethnic origin, which the law treats as extra sensitive. | It guesses for everyone while the switch is on. | Legal | Open |
| C3 | The product uses **fixed rules (no AI)** to pick which search results to add to a list. Is that a "decision made by a machine" about a person? | The rules only add people; they never turn anyone down. | Legal | Open |
| C4 | Is our **risk assessment** of the AI features approved? Must we show it to a regulator first? Should we ask candidates what they think? | Written, waiting for review ([dpia.md](dpia.md)). | Legal | Open |
| C5 | Can we say **a person always makes the final decision**, because the AI only drafts assessments? | Yes, that's how it works today. | Legal | Default chosen |

## D. People asking about their data

| ID | What needs deciding | Where things stand | Who decides | Status |
|---|---|---|---|---|
| D1 | If someone asks **Uncava** to stop using their data, should that **block them for every customer**? | Each firm keeps its own "never add again" list. | Legal | Open |
| D2 | If a firm **pauses** using someone's data, should the **client stop seeing them** too? | The client still sees them. | Legal | Open |
| D3 | When we send someone a copy of their data, should we **hide other people's names** in notes and emails? | The firm's admin is told to check and remove them by hand. | Legal | Open |
| D4 | May we **tell someone which of our customers** hold their data? | Undecided. | Legal | Open |
| D5 | Should a few Uncava staff have a **tool to search every customer** for a person, to pass their request on? | Not built. Nobody at Uncava can look inside a customer's account today. | Legal, then Product team | Open |
| D6 | How long we keep our **list of privacy requests** and our **list of data breaches**. | Not set. | Legal | Open |
| D7 | How carefully we **check someone's identity** before acting on their request. | Light checks; no ID documents unless we have real doubt. | Legal | Default chosen |

## E. How long we keep things

| ID | What needs deciding | Where things stand | Who decides | Status |
|---|---|---|---|---|
| E1 | Are our **time limits** right? Main ones: candidates untouched for **24 months**, deleted accounts erased after **30 days**, account exports kept **7 days**. The full list is in [retention-schedule.md](retention-schedule.md). | The limits are set, but **automatic deleting stays off** until legal approves them. | Legal | Open |
| E2 | How long we must keep our **security log**. | Kept forever for now. We need a small technical change (#256) before we can delete from it. | Legal | Open |
| E3 | Is it fine that deleted data stays in **backups for 21 days**? | Backups are **switched off** today, so this needs turning on first. | Legal + Leadership | Open |
| E4 | Accounts deleted **before our deletion feature existed** will be erased **without a confirmation email**, because we never recorded who deleted them. Acceptable? | They'll be erased quietly. | Legal | Open |

## F. Suppliers and moving data abroad

| ID | What needs deciding | Where things stand | Who decides | Status |
|---|---|---|---|---|
| F1 | Which **legal safeguard** covers each supplier that handles data in the US. | Left blank on our legal pages. | Legal | Open |
| F2 | **Which country** our email, payment and data suppliers work in. | Unknown; we need to ask each one. | Leadership | Open |
| F3 | Our **scheduling** and **call-recording** suppliers can run in the US or the EU. Which? | US. | Legal + Leadership | Open |
| F4 | Should our **AI run in one fixed region**? | **Decided by the product team:** no, for now. The AI models we use aren't offered in a single region. We'll revisit if a customer needs it or legal requires it ([residency-adr.md](residency-adr.md)). | Product team | Decided |
| F5 | How fast we tell a customer about a **data breach** (24 or 48 hours), and which Gulf regulators we tell. | Left blank in our customer contract. | Legal | Open |
| F6 | How often customers may **check (audit) us**, and with how much warning. | Left blank. | Legal | Open |
| F7 | How we tell customers about a **new supplier**, and how much warning they get. | The contract says 30 days, but we have no way to tell them yet. | Leadership | Open |
| F8 | Should big customers be able to use **their own encryption keys**? | Not offered. | Legal | Open |
| F9 | **Where our backups are stored.** | Not fixed to one country. | Legal | Open |
| F10 | Our server logs keep what people **type into search**, which can include email addresses. Filter it out, or keep the logs for less time? | Kept for 30 days. | Legal + Product team | Open |
| F11 | Should we run a **separate European copy** of the product for EU customers? | Suggested, not decided. | Leadership | Open |
| F12 | Sign a **data agreement with every supplier** (scheduling, call recording, email, payments, data vendors, maps). | None confirmed yet. | Leadership | Open |

## G. Our website and how we run the company

| ID | What needs deciding | Where things stand | Who decides | Status |
|---|---|---|---|---|
| G1 | Which **service** handles the website's demo form and newsletter, and do subscribers **confirm by email**? | Not chosen. | Leadership | Open |
| G2 | How long we keep **demo requests**. | Not set. | Legal | Open |
| G3 | How long we keep **billing records**. | Depends on tax law where our company is registered (A1). | Legal | Open |
| G4 | Should staff get access to live customer data **only in an emergency**, with a record kept? | Several staff have standing access today. | Leadership | Open |
| G5 | Written policies for **security training, checking suppliers and handling incidents**. | The incident plan is drafted ([breach-runbook.md](breach-runbook.md)); the rest isn't. | Leadership | Open |

---

## For whoever updates the documents

Our website's legal pages (`uncava-website`, `src/content/legal/*.md`) and the documents in this folder have gaps in
square brackets. This is what each decision fills in.

| ID | Fills |
|---|---|
| A1 | `[Company legal name]`, `[free zone or emirate, licence number]`, `[Registered address]` everywhere; the governing law in the terms |
| A2 | `[Privacy email address]`, `[Legal email address]`, `[Security email address]`; root `SECURITY.md` |
| A3 | Privacy policy "Data protection officer"; [ropa.md](ropa.md) |
| A4, A5 | Privacy policy "Our EU and UK representatives" |
| A6 | [breach-runbook.md](breach-runbook.md) §5 |
| A7 | [breach-runbook.md](breach-runbook.md) §1; [subject-requests-procedure.md](subject-requests-procedure.md) §4 |
| B1 | Privacy policy, lawful-basis table |
| B2 | Privacy policy "Executives" |
| B3 | Privacy policy "Your right to object" |
| B4, B5, F3 | Sub-processor list; DPA |
| B6 | [ropa.md](ropa.md) C5 |
| B9, E1 | [retention-schedule.md](retention-schedule.md) |
| B10 | Privacy policy |
| D6 | [ropa.md](ropa.md) C7; [subject-requests-procedure.md](subject-requests-procedure.md) §5 |
| E3, F9 | [security-of-processing.md](security-of-processing.md); the website's `/security` page drops "[to confirm]" |
| F1 | `[mechanism to confirm]` on the DPA, privacy policy and sub-processor list |
| F2 | `[Region]` on the sub-processor list |
| F4 | Privacy policy; sub-processor list |
| F5 | DPA `[24–48]` hours; breach runbook |
| F6 | DPA audit terms |
| F7 | DPA `[30]` days; sub-processor list |
| G1 | `[form processor]`, `[Provider]`, `[confirmed by double opt-in]` |
| G2 | Privacy policy `[period]` for demo requests |
| G3 | Privacy policy `[statutory period]` |
| G4, G5 | DPA Annex 1 |
