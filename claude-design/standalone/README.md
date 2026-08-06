# Standalone build — open in any browser, offline

Six self-contained HTML files. Everything is embedded (runtime, styles, fonts, design
system, all records) — no server, no internet, no other files needed.

**Start with `Workspace.dc.html`.** Keep all six files in the same folder so the links
between screens work:

- `Workspace.dc.html` — project list (My projects / All projects / Team), new-project modal
- `Clients.dc.html` — client list, client drawer, mandate drawer, add-client flow
- `Project.dc.html` — open project: Position, Strategy, Sourcing, Candidates, Team, Client
- `Settings.dc.html` — workspace settings
- `Login.dc.html` / `Signup.dc.html` — auth screens

Notes:
- The records are demo/seed data held in memory. Anything you add or edit resets on reload.
  Theme and sidebar-collapse are remembered.
- `Workspace.dc.html?fresh=1` opens the empty-workspace state; `?view=all`, `?view=team`
  and `Project.dc.html?page=team` deep-link to those views.
- These are design prototypes, not production code. For the data model and record dump,
  see the `handoff/` folder.
