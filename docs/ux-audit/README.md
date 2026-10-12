# UX audit screenshots

Evidence for the UX audit epic ("Make Uncava feel effortless"). One folder per batch; each image is
referenced by a sub-issue of the epic.

- Captured from a local `PROFILE=e2e` stack (`e2e/stack/up.sh`) with the e2e cast and a seeded
  CFO position. Names and companies are synthetic test data.
- 1440×1000, dark theme. Red numbered boxes mark the elements an issue refers to.
- From Batch 3 on, every finding has a **CURRENT** picture (the live app, grey tag) and a **PROPOSED** one (green tag).
  A proposed picture is the same live screen with only the suggested change painted in by editing the page in the
  browser: it is an illustration for a business decision, nothing in the product was changed or built.
- Where a screenshot shows a failure (a refused save, an error toast), the failure was **simulated** by
  intercepting the request in Playwright and answering 503. The screen's behaviour is the app's own.

| Folder | Batch |
|---|---|
| `00-foundations/` | Feedback, errors, vocabulary, confirmation, visual system, focus |
| `01-first-run/` | Signup, first landing, the shell, settings entry, rosters |
| `03-strategy-companies/` | Strategy (companies and people) and the In universe / Shortlisted / Declined pages |
