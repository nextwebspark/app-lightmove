# Client message templates

_Draft for counsel, 2026-11-19, unreviewed. Requested by Alac Partners (gap analysis §5b, item 10: first-contact message
with data source, consent confirmation, consent renewal request, response to access requests; shown in the platform).
Part A quotes wording that **already exists in the product**, read from `PrivacyNoticeWording`, `ConsentWording` and
`EmailTemplates` on 2026-11-19, with the variables made visible. Part B is **new wording that is not in the product**.
The product's wording was written for the EU GDPR; whether it meets PDPL, DIFC and ADGM is for counsel
([Counsel to confirm])._

## 1. Where each template stands

| Template the thread asks for | In product? | Where |
|---|---|---|
| First contact, with data source | **Partly.** An outreach email's footer states the source, links the notice and carries a stop link. A privacy-notice email can be sent on its own. No first-contact message body or script for other channels | A.1, A.2; new B.1 |
| Consent confirmation | **Yes, for answers given on the consent page** (an email lists what was answered). **No** for a consent given by reply, call or message and recorded by hand | A.6; new B.2 |
| Consent renewal request | **Yes** (a keep-on-file consent is asked once more `consent_renewal_days` before it ends; 0 to 90, 0 never) | A.5 |
| Reply to a candidate access request | **No.** The privacy page tells the firm's admins by bell and email; the admin exports and replies by hand | new B.3 |

All of A is sent through Uncava's own sender (Resend) as "{firm} via Uncava", with the firm's privacy contact as
reply-to. The notices and footer sit behind `DATA_PROTECTION_ENABLED`; consent requests also need the firm in consent
mode with its details saved. The control is off in production today (`ropa.md` §3). The firm's text cannot be edited
today: the planned "own message templates" feature (gap analysis P12) is not built. Consultants can save their own
email templates in outreach (`app_lm_email_template`); whether those support the variables below is
[Owner to confirm].

**Variables.** `{firm}` the firm's legal name (controller); `{first_name}`; `{source_phrase}`; `{basis_phrase}`;
`{page_link}` the person's privacy or consent page (valid 30 days; a manage link as long as the longest consent in
force); `{notice_url}` the firm's privacy notice; `{privacy_contact}` the firm's contact, which receives replies;
`{link_until}` a date; `{role}` a position title; `{client}` the client's name or "a confidential client".

`{source_phrase}` is one of (by how the person first entered the workspace):

| Door | Phrase |
|---|---|
| typed in by a colleague | "our own research" |
| spreadsheet import | "a list our team imported from its own research" |
| Chrome extension | "your public professional profile" |
| AI-sourced or people search | "a search of public professional profiles" |

`{basis_phrase}` is "our legitimate interest in finding people for senior roles", "your consent" or "a contract with
you". Legitimate interest is an EU basis, not a PDPL one; for UAE-regulated workspaces consent mode is the intended
path ([Counsel to confirm]).

## 2. Part A: already in the product

### A.1 Privacy notice email (#923)

Subject: `How {firm} holds your details`. Heading: `How we hold your details`.

> {firm} holds some of your professional details, such as your name, career history and work contact details, which
> came from {source_phrase}. We hold them to consider you for senior roles, on the basis of {basis_phrase}.
>
> You can object, ask for a copy of what we hold, or ask us to delete it at any time.
>
> [See what we hold and your choices]({page_link})
>
> Our full privacy notice is at {notice_url}. Questions go to {privacy_contact}, or reply to this email.

### A.2 Outreach email footer (#924)

Added to every outreach email as it leaves, after the signature; nobody can edit it out. The email also carries a
one-click `List-Unsubscribe` header (not for Microsoft mailboxes).

> {firm} holds your professional details, which came from {source_phrase}. Our privacy notice · Stop emails from us

Until the firm's name, contact and notice link are all set it begins "We hold your professional details…". "Stop
emails from us" asks once ("Stop emails from {firm}?") before it opts the person out.

### A.3 Consent request email (#1267)

Subject: `{firm} would like your permission`. Heading: `Your permission`.

> Dear {first_name},
>
> {firm} holds some of your professional details, which came from {source_phrase}.
>
> We'd like your permission to:
> • keep your details on file for future roles, for {months} months
> • send you career and market updates
>
> Each is a separate choice, and you can say no to any of them. If you don't answer, nothing changes.
>
> [Choose your answers]({page_link})
>
> This link works until {link_until}. Our privacy notice at {notice_url} explains how we use your details. Questions go
> to {privacy_contact}, or reply to this email.

The bullets come from `ConsentWording.askingOf`; the third is "share your profile with {client} for {a/an role} role"
(A.4). The page's own sentence per purpose, which is what an answer keeps (version `2026-11-16`):

| Purpose | Sentence on the page |
|---|---|
| Keep on file | "We would keep your details for {months} months, so we can think of you when a suitable role comes up." |
| Share with a client | "We would like to send your profile to {client} for {a/an role} role." |
| Marketing | "Occasional emails from us about roles and the market. You can stop them at any time." |

### A.4 Share-with-client request (#1267 phase 3)

Subject: `May we share your profile for {a/an role} role?`

> Dear {first_name},
>
> {firm} is searching for {a/an role} for {client}. We hold some of your professional details, which came from
> {source_phrase}.
>
> We think your experience fits, and we'd like your permission to share your profile with {the client or client name}.
> It's your choice. If you don't answer, nothing changes.
>
> [Choose your answer]({page_link})
>
> This link works until {link_until}. Our privacy notice at {notice_url} explains how we use your details. Questions go
> to {privacy_contact}, or reply to this email.

The client is "a confidential client" unless staff choose to name it, and never when the position is confidential.

### A.5 Consent renewal request (#1267)

Subject: `Your consent to keep your details ends on {ends_on}`.

> Dear {first_name},
>
> On {agreed_on} you agreed that {firm} could keep your details on file for future roles. That consent ends on
> {ends_on}.
>
> Would you like us to keep them for another {months} months? If you don't answer, your consent ends on that day and
> nothing else changes.
>
> [Choose your answer]({page_link})
>
> This link works until {link_until}. Our privacy notice at {notice_url} explains how we use your details. Questions go
> to {privacy_contact}, or reply to this email.

Page sentence for a renewal: "You agreed on {agreed_on} that we could keep your details on file for future roles. If
you say yes, we keep them until {renewed_until}."

### A.6 Consent confirmation (#1267)

Subject: `Your answers to {firm}`. Heading: `Your answers are saved`. Sent after every answer on the page.

> Dear {first_name},
>
> Thank you. On {recorded_on} {firm} recorded your answers:
> • {label}: {answer}
>
> You can change your mind at any time, and withdrawing is as easy as agreeing.
>
> [Manage your consents]({manage_link})
>
> This link works until {manage_until}. Keep this email so you can use it. Our privacy notice at {notice_url}
> explains how we use your details. Questions go to {privacy_contact}, or reply to this email.

Labels: "Keep your details on file for future roles"; "Share your profile with {client} for {a/an role} role";
"Career and market updates". A refusal to renew reads "Not renewed, ends on {ends_on}". The manage link and its sentence
are left out once nothing agreed to is in force.

### A.7 For context: what the firm's admins receive

When a person uses the privacy page, each admin gets a bell and an email, "Answer within one month": "A candidate asked
for a copy of their data" (export from the ⋯ menu on the record, then send it), "A candidate asked for their data to be
deleted" (delete permanently, or say what must be kept and why), or "A candidate objected" (the person is marked do not
contact and suppressed). Nothing is sent to the person.

## 3. Part B: new, not in the product

Written for counsel's review; deadlines and legal references are placeholders. None of B is sent by the product.

### B.1 First contact with data source (new)

For a consultant's first email body (the footer in A.2 is added after it automatically, so the email states the source
twice, which is intended), and as a script for LinkedIn, phone or WhatsApp, where no footer exists.

> Subject: {role} role at {client_or_firm}
>
> Dear {first_name},
>
> I am {consultant_name} at {firm}. I am writing about {a/an role}. I found your professional details in {source_phrase}.
> We hold them only to consider you for senior roles like this one, and you do not have to reply.
>
> If you would rather not hear from us, say so, or use the link at the foot of this email, and we will not contact you
> again. You can also see what we hold and ask for a copy or for deletion at {page_link_or_notice_url}.
>
> {signature}

Short form for other channels: "Hello {first_name}, this is {consultant_name} from {firm}. I found your professional
details through {source_phrase} and wanted to ask about {a/an role}. Tell me if you would rather I did not contact you.
Our privacy notice: {notice_url}."

Rules for the firm: use recruitment only; do not pass the details on; state the source at first contact (the thread's
mitigation 2). Where the details include a private email or phone from ContactOut, whether that contact may be used at
all is an open legal question ([Counsel to confirm]).

### B.2 Confirmation of a consent given outside the page (new)

For a consent received by reply, call or message and recorded by hand (`RecordConsentDialog`), where no confirmation
email is sent today. The thread asks the register to hold type, channel and evidence; this text repeats them to the
person.

> Subject: Your consent, recorded by {firm}
>
> Dear {first_name},
>
> Thank you. On {recorded_on}, by {channel}, you agreed that {firm} may {what_agreed, for example keep your details on
> file for future roles, for {months} months}. {If for one role only: This applies to {role} only.}
>
> You can change your mind at any time by replying to this email or writing to {privacy_contact}. If you say nothing,
> your consent ends on {ends_on}.
>
> Our privacy notice is at {notice_url}.

### B.3 Reply to a candidate access request (new)

**Before sending** (internal; from `subject-requests-procedure.md` and `subject-access-export.md`):

| Check | Note |
|---|---|
| Identity | reply from the address on file, or ask the person to confirm control of the LinkedIn profile or email; no identity documents unless doubt remains |
| Deadline | one month from receipt under GDPR Art. 12(3); the PDPL, DIFC and ADGM deadlines are [Counsel to confirm]; log the request |
| Role | if the request reached Uncava rather than the firm, Uncava is processor and forwards it (`subject-requests-procedure.md` §4); this reply is the firm's |
| Export | an Admin: Candidates, the person, ⋯, **Export personal data…**, Download (`PERSON_SUBJECT_ACCESS`); the zip holds `README.txt`, `person.json`, `person.html`, `documents/`, the photo, and the fixed answers about purposes, recipients, retention, sources, rights, AI logic and automated decisions. The audit line records that the export was made, not that it was received |
| Other people | colleagues stay named where they acted; notes, emails and transcripts go as written and may mention third parties, and provider answers can name others (people also viewed); the export's README tells the admin to check and redact before sending ([Counsel to confirm] what to withhold) |
| Uncava's own copy | the shared 30-day vendor cache is not in the export; Uncava answers for it through its own channel (`subject-requests-procedure.md` §3) |
| Delivery | the product does not send the export. Channel: [Owner to confirm, for example a password-protected link, password sent separately] |

> Subject: Your request for a copy of your data, {firm}
>
> Dear {first_name},
>
> Thank you for your request of {received_on}. We confirmed it was you by {verification_method}.
>
> We have attached {or: you can download, until {link_until}} everything {firm} holds about you. It contains:
> • a readable copy (person.html) and a machine-readable copy (person.json) of your record: your details, career
>   profile, contacts, tags, what data providers returned about you, our notes, tasks, meetings, call transcripts,
>   scorecards, emails sent and received, the positions you were considered for, and the history of what our team did
> • the documents on file, such as a CV, and your photo
> • {if any: our readings made with the help of AI, with how they were made}
> • {if any: your consent records}
>
> It also answers, in its first section: why we hold your data, where it came from ({source_phrase}), who receives it,
> how long we keep it, and your rights.
>
> {If anything is withheld: We have left out {what}, because {reason}.}
>
> You can ask us to correct anything that is wrong, to stop contacting you, to restrict how we use your data, or to
> delete it. Write to {privacy_contact} and we will answer within {period}. You may also complain to {authority}
> [Counsel to confirm].
>
> {Name}, for {firm}

Holding reply while verifying: "Thank you for your request of {received_on}. To be sure we send your data only to you,
please {verification_step}. We will answer within {period} of receiving it."

## 4. Gaps

1. No editable template library: the firm cannot change A (planned P12), and B is not in the product.
2. A.1 to A.6 are behind `DATA_PROTECTION_ENABLED`, off in production.
3. A names a legitimate-interest basis, which has no PDPL equivalent; a UAE variant is not drafted.
4. B.1 has no home: no first-contact template is preloaded, and non-email channels have no automatic source statement.
5. A hand-recorded consent sends nothing (B.2 is a proposal); the planned channel and evidence fields are not built.
6. No reply to a deletion, objection or correction request; none is drafted here.
7. The privacy-notice version a person received is not yet recorded or managed (gap analysis P5), apart from the
   consent sentence's own version.
8. The access export is not sent by the product, and delivery is not confirmed by it.
9. Languages: English only; Arabic is not provided [Owner to confirm].
