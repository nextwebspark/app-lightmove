# Email to ContactOut: storage use case for compliance review

Draft for Alok to send. Written for ContactOut's account contact and their compliance team. Items in [brackets] are Alok's to fill or confirm. Facts below were checked against the code on 2026-11-19 (`app_lm_vendor_person` 30-day TTL, `app_lm_person_vendor_record` kept while the person is held, `RetentionSweeper`).

---

**Subject:** Uncava API account: storage use case for compliance review, and DPA

Hi [name],

Thank you for the detailed reply. Please send the API agreement and the DPA. We will review them and return signed copies.

You offered to have your compliance team review our storage use case. It is set out below so they can do that.

**Who we are and what we do**

Uncava is software for executive search firms. A consultant builds a position, finds executives at target companies, and keeps the ones they choose in a candidate record in the firm's own workspace. We use ContactOut to:
- find professional profiles for a named company and role, and
- look up one person's work email or phone, only when the consultant presses a button. Each lookup is a deliberate action and costs one credit.

**What we would store, and why**

1. **The profile you return for a person the consultant chose to add.** This is name, title, company, location and profile link, held in that firm's candidate record. A search runs for weeks. Without a stored record the firm cannot track who was contacted and what was said, or avoid contacting the same person twice.
2. **The full profile answer, as returned, on that person's record.** It is kept for as long as the firm holds the person. This is so a field shown later never has to be bought again. [Confirm before sending: this answer can include other people's names, such as "people also viewed". We can drop those fields if you prefer.]
3. **A revealed work email or phone, on the same record.** The firm needs it to reach the person and to see the outreach history. Re-requesting before every message would cost credits and would break if the record is later changed or removed on your side.
4. **A short-lived cache of your search answers, shared across our customers' workspaces.** It is kept for 30 days and then deleted automatically. It stops us paying twice for the same search. [Confirm before sending. If you do not approve cross-customer caching, we will make it per workspace.]

**Safeguards**

- Used only for recruiting. We do not resell it, publish it, or send it to anyone other than the firm's own users.
- Deleted when the firm deletes the person, and when the person asks us to erase their data or to stop using it. Where someone objects we keep only a one-way hash of the profile link and email, so they are not added again.
- Candidate records follow a retention period the firm sets. The default is 24 months untouched, with a review before deletion. [Update when the retention schedule is approved.]
- Every email we send for a firm carries a note saying where the details came from, a privacy notice link and a one-click stop link.
- Data is encrypted in transit and at rest. Access is limited to the firm's own users, with database-level separation between firms.

**Please confirm in writing**

1. Approval of the storage and retention above, or the changes you need.
2. Whether it also covers a firm exporting its own records, for example to a spreadsheet, and sending a person a copy of the data held about them when they ask.
3. That ContactOut collected the data lawfully, or which exclusions apply. Our clients are asking, and we have to pass the answer on.
4. Whether your regional positions cover individuals in the UAE, DIFC and ADGM. Several of our clients are in the Gulf.
5. A way to receive opt-outs (a file, webhook or API), so we can suppress people who opted out with you.
6. Your Legitimate Interests Assessment, and the name of the third-party phone-data provider.
7. Whether you can show, at any plan level, which source category a given email or phone came from.

We are happy to join a call with your compliance team.

Best regards,
Alok
