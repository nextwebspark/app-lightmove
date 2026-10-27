# Uncava on a phone

The desktop layout is untouched: everything here is below `lg` (1024px) or `md` (768px), in Tailwind variants.

## Who it is for

A researcher between meetings. Their loop on a position is: open it → see who is in it → change someone's status →
write the note from the call → book the next call → add a company or an executive they just heard about. A phone
is for that loop, not for building a Strategy filter or editing a brief — those stay reachable, through More.

## v1 (built)

| Job | Before | Now |
|---|---|---|
| Move between screens | Hamburger → rail | **Bottom bar**: in a position Companies · Pipeline · **+** · Outreach · More (a client seat: Brief · Companies · Reports · More); in the workspace and Settings Positions · Tasks · Candidates · More |
| Every other screen | The desktop rail squeezed into a left-hand card | **More** opens a panel from the right: the rail's screens as full-width rows under their groups, then the account (profile, dark mode, help, sign out) |
| My tasks | A card under the positions list | The **Tasks** tab: `/tasks`, every task as a two-line card with its tick |
| Notifications | — | The bell stays in the topbar on every screen; its panel spans the phone |
| Add company / executive | Toolbar buttons that wrap | **+** → Quick actions → the Companies page's own add form (`?add=company\|executive`) |
| Note / call / status on one person | Find them in a 960px table | **+** → pick the executive → their drawer on Notes, on Meetings, or on Career with the status picker |
| Read the Companies stage | 20-column grid scrolled sideways | One **card** per line: the company, the person at it with their status picker, or Add executive |
| Read the pipeline | Board of columns | Opens on the **list**, drawn as cards below `md` |
| Read the Candidates page | Wide grid | Cards: person, role, place, latest position |
| Work an executive | Drawer inset under the topbar | Every drawer is **full-screen** on a phone, clear of the notch and the home bar; the executive's footer reads Note · Call · Next action |
| Book a call | Seven 104px columns | A day strip and **one day**; a swipe scrolls the day, a tap picks |
| Toasts, selection bar | Under the bottom bar | Lifted above it (`--mobile-nav-clearance`) |
| Typing | iOS zoomed into every field under 16px | Fields are 16px below `sm` |
| Home screen | A browser tab | `manifest.webmanifest` (192, 512 and maskable icons), standalone, `viewport-fit=cover`; the shell pads every edge by its safe-area inset |

Pieces: `components/layout/mobile/` (`MobileTabBar.tsx`, `MobileMoreSheet.tsx`, `PositionQuickActions.tsx`,
`mobileTabs.ts`), `components/ui/BottomSheet.tsx`, `components/layout/useAccountActions.ts` (the account actions the
avatar menu and More both draw). Every shell passes `mobileTabs` to `AppShell`, Settings included; below `lg` there
is no rail and no hamburger. The bar's height is `--mobile-nav-height`, and what clears it reads
`--mobile-nav-clearance` (`global.css`).

A screen that draws a different structure on a phone reads `lib/viewport.ts`'s `isBelowTableWidth()` once when it
mounts (the `react` skill's Responsive section has the rule): the Pipeline list and My tasks draw cards *or* the
table, and Book a call one day or seven. The `DataGrid` lists (Companies, Candidates) get their cards only on a phone,
but `DataGrid` keeps its CSS swap, so a phone still carries the hidden grid beside them. A rotation or a resize keeps
the layout a screen opened with until it is opened again.

## v2 (next)

- Swipe a pipeline card to move its status; long-press for the next action.
- The note composer full-screen with its kind chips on top, and voice to note.
- Offline drafts of a note, sent when the phone is back online.
- Push notifications for a reply, a booked call or a task due (the manifest is the first step).
- The assistant on a phone (`AssistantDock` is `hidden lg:block` today).
- An Outreach conversation laid out as a messaging thread.
- A mockup in `claude-design/` for each of the above before it is built.
