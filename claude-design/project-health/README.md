# Project health — layout reference

The four screenshots here are the design for the mandate's type, its milestone dates and the health
derived from them: the New-project modal, the Active Projects list, and the project drawer in two
scrolls. They stand in for a `*.dc.html`, the way `claude-design/position/*.png` records the brief's
design — issue-supplied rather than built from the design system.

**They are the source of truth for layout, content and behaviour only.** Every colour, font, size,
weight, radius and spacing comes from `apps/web/src/styles/tokens.css` and the idioms already on
these screens; the greens, oranges and purples the screenshots happen to use are not sampled and no
token was added for them. Projects is on the first palette — amber for the brand and the selected
state, green/amber/red for the three health states, sky for the search badge — and stays there. The
UNCAVA `--color-u-*` palette remains confined to Reports and the Position brief, as `tokens.css`
says.

Two things the screenshots draw are deliberately **not** built:

- **"Ahmad viewed candidate profiles"** in the activity feed. Nothing records a read, and adding
  that would be surveillance nobody asked for. The feed narrates the acts the audit trail already
  holds.
- **Stage gates advancing.** Nothing in the app moves a mandate's stage, so the ladder shows every
  mandate at its first gate. The phase pipeline above it is the live one, derived from coverage.
