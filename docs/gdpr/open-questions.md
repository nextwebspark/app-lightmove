# Data protection (GDPR): where we are

_Updated 19 November 2026. A working summary, not legal advice._

This page answers three questions:

1. **What have we finished?**
2. **What do we still need to decide?** Shown screen by screen, so you can open the product and see each one.
3. **What has to happen before customers get the new features?**

Each open question has a short ID (like **B7**). The full list, with space to record answers, is
[governance-decisions.md](governance-decisions.md).

---

## 1. What we have finished

### Built and already in customers' hands

| What | What it means |
|---|---|
| **Delete my account** and **Download my data** | Any user can delete their own account, or download what we hold about them. |
| **Delete a workspace** | An admin can close their firm's account. After 30 days everything is erased and they get a certificate by email. |
| **AI on people** switches | Each firm decides whether the AI may guess gender or nationality, draft assessments, or fill in profiles. New firms start with everything off. |
| **Gender and nationality kept back from other apps** | Apps connected to Uncava get gender and nationality only if they were given that specific permission. |
| **Accepting our terms** | Everyone ticks a box to accept our terms when they sign up, including through Google or LinkedIn. When we change the terms, everyone is asked to accept again. |
| **Personal details kept out of our logs** | Our system logs no longer record names, emails, phone numbers or LinkedIn links. |
| **No hidden tracking** | The app no longer loads fonts from Google, map usage reporting is off, and the browser extension explains what it reads. |

### Built, but switched off until a few go-live steps are done (section 4)

| What | What it means |
|---|---|
| **Delete a person for good** | An admin can erase a candidate completely, and optionally make sure they're never added again. |
| **Send someone a copy of their data** | An admin can download, as one file, everything held about a candidate, ready to send to them. |
| **Pause using someone's data** | A candidate can ask a firm to stop using their data while keeping it on file. The product then stops all AI, research and emails about them. |
| **"Never add again" list** | People who object can't be added back by anyone, by any route. |
| **Clean-up of old data** | Firms see who has been untouched for too long and can keep or delete them. Old system records are cleaned up automatically. |
| **Telling candidates we hold their data** | Firms can send a privacy notice and see who hasn't been told. Each candidate has a privacy page where they can object, ask for a copy, or ask to be deleted. |
| **A "stop emails" link in every email** | Every email to a candidate ends with the firm's privacy note and a one-click way to stop emails. |
| **Asking for consent** | Firms can ask candidates for permission (to keep their details, to send updates, or to share them with a client) and see the answers. |

### Written, and waiting for our lawyer

- Our record of what data we hold and why, our reasoning for holding it, our risk assessment of the AI, and our
  assessment of sending data to the US.
- Our plan for data breaches, and our procedure for handling people's requests.
- The website's privacy policy, customer data agreement (DPA) and list of suppliers. These are finished but not yet
  published.
- Our decision on where the AI runs: worldwide for now, because the AI models we use aren't offered in one region.

---

## 2. The five most urgent decisions

1. **Who we are (A1, A2).** Which company Uncava legally is, and real privacy, legal and security inboxes. Every
   legal page and every email to a candidate is waiting on this.
2. **Telling people (B2).** Must a firm tell people it holds their details, even if it never contacts them?
3. **Guessing nationality (C2).** May the AI keep guessing nationality for people in Europe and the UK?
4. **Time limits (E1).** How long we keep candidate data. Automatic deleting stays off until this is approved.
5. **Backups (E3).** Backups are switched off. The product already tells users that deleted data "disappears from
   backups", and that isn't true until backups are on.

---

## 3. Questions, screen by screen

**Who decides:** **Legal** (our lawyer), **Leadership** (founders or management), **Design** (needs a screen
designed), **Product team** (we can settle it ourselves).

Screens marked **(live)** are ones customers can already see.

### 3.1 Settings → Data & retention

**How to get there:** click your picture → **Settings** → **Data & retention**. Only admins see it.

| ID | Question | Today | Who decides |
|---|---|---|---|
| C1 | **AI on people (live):** should the AI features be off for older customers too? | Off for new customers only. | Legal |
| C2 | **AI on people (live):** may the AI keep guessing nationality for people in Europe and the UK? | It guesses for everyone while switched on. | Legal |
| C4 | **AI on people (live):** is our risk assessment of the AI approved? | Waiting for review. | Legal |
| B1 | **Candidate privacy:** can firms hold executives' details because of a genuine business need, without asking each one first? | Yes, that's the default. | Legal |
| B13 | **Candidate privacy:** in the UAE, does a LinkedIn profile count as information people made public themselves? | Undecided. If not, UAE firms must ask for consent. | Legal |
| B7 | **Candidate privacy:** if consent is needed but missing, should the product stop or just warn? | Warns only. | Legal |
| B8 | **Candidate privacy:** can no answer ever mean yes? | Never. | Legal |
| B10 | **Candidate privacy:** should privacy emails come from Uncava's address or the firm's own inbox? | Uncava's, in the firm's name. | Legal |
| E1 | **Candidate retention:** is 24 months untouched the right limit before someone is due for deletion? | Set, but automatic deleting is off. | Legal |
| D1 | **Suppression list:** if someone objects to Uncava, should they be blocked for every customer? | Each firm has its own list. | Legal |
| E1 | **Export workspace data:** is 7 days long enough to download a full export? | 7 days. | Legal |

### 3.2 Candidates

**How to get there:** **Candidates** in the left menu. To act on one person, click their name, then **⋯** at the top
right.

| ID | Question | Today | Who decides |
|---|---|---|---|
| B2 | **Not notified** list: must people be told, even if never contacted? | Firms send notices by hand. | Legal |
| D2 | **⋯ → Restrict processing:** should the client also stop seeing a paused person? | The client still sees them. | Legal |
| D3 | **⋯ → Export personal data:** should we hide other people's names in the copy? | The admin removes them by hand. | Legal |
| E3 | **⋯ → Delete person permanently:** is 21 days in backups fine? | Backups are off today. | Legal + Leadership |

### 3.3 A position

**How to get there:** click a position in the left menu.

| ID | Question | Today | Who decides |
|---|---|---|---|
| C3 | **Companies → tick companies → Find executives:** is using fixed rules to add people a "decision made by a machine"? | The rules only add people, never turn anyone down. | Legal |
| C5 | **Companies → open an executive → Assess with AI:** can we say a person always decides? | Yes, the AI only drafts. | Legal |
| B11 | **Companies → Pipeline view → a card:** should we name the client when asking to share someone? | "A confidential client" unless staff choose to name them. | Legal |
| B12 | **Pipeline view:** if someone says no to being shared, should the client stop seeing them? | Staff get a warning only. | Legal, then Design |
| B14 | **Pipeline view:** should sharing without consent be impossible? | Staff can choose "Show anyway". | Legal |
| B3 | **Outreach:** does emailing executives count as marketing? | Every email has a stop link. | Legal |
| B16 | **Outreach:** should a "please remove me" reply prompt staff to stop contacting the person? | Not built; we don't read replies. | Legal |

### 3.4 Settings, sign-up and the extension

| ID | Where | Question | Today | Who decides |
|---|---|---|---|---|
| E4 | **Settings → General → Delete workspace (live)** | Accounts deleted before this feature existed get no confirmation email. Fine? | Erased quietly. | Legal |
| B18 | **Sign-up → create a workspace (live)** | Older customers never signed our data agreement. How do we get it signed? | Nothing asks them. | Legal |
| B17 | **Chrome extension → capture a profile** | Is looking up a LinkedIn profile live acceptable? | We do it. | Legal |

### 3.5 Pages candidates see (no sign-in needed)

| ID | Where | Question | Today | Who decides |
|---|---|---|---|---|
| D7 | **A candidate's privacy page** (from the link in any privacy email) | How carefully do we check who someone is before acting? | Light checks; no ID documents. | Legal |
| D4 | **A "Requests about you" page on our website** (not built; needs Design first) | May we tell someone which customers hold their data? | Undecided. | Legal |
| D5 | Same page | Should a few Uncava staff be able to search every customer for a person? | Not built. | Legal, then Product team |
| F3 | **The booking page** | Should our scheduling supplier run in the US or the EU? | US. | Legal + Leadership |

### 3.6 Decisions with no screen

Who we are, our suppliers, sending data abroad, backups and how we run the company: sections A and E–G of
[governance-decisions.md](governance-decisions.md).

### 3.7 Fixes the product team still owes (no decision needed)

- **The diversity report (live), which clients can see**, still counts gender and nationality the AI guessed and
  nobody confirmed. It should count confirmed values only. Find it at: a position → **Reports** → Diversity.
- **The in-app assistant** can still pass a paused person's name and job title to the AI.
- **Outlook users' emails** don't get the email app's built-in "Unsubscribe" button. The stop link in the footer
  still works.
- **The extension's sign-in storage**: write down why it's acceptable, or change it.

---

## 4. Before customers get the switched-off features

1. **Set up the security key** the "never add again" list needs, then switch the features on.
2. **Turn on database backups**, and remove the old manual backup.
3. **Legal approves the time limits (E1)**, then switch on automatic deleting.
4. **Tell customers who connect other apps** that gender and nationality now need a separate permission.
5. **Publish the website's legal pages** once legal has reviewed them.
