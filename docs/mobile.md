# Uncava on a phone

The desktop layout is untouched: everything here is below `lg` (1024px) or `md` (768px), in Tailwind variants.

## Who it is for

A researcher between meetings. Their loop on a position is: open it → see who is in it → change someone's status →
write the note from the call → book the next call → add a company or an executive they just heard about. A phone
is for that loop, not for building a Strategy filter or editing a brief — those stay reachable, through More.

## v1 (built)

| Job | Before | Now |
|---|---|---|
| Move between screens | Hamburger → rail | **Bottom bar**: in a position Companies · Pipeline · **+** · Outreach · More (a client seat: Brief · Companies · Reports · More); in the workspace Positions · Tasks · Candidates · More |
| Every other screen | The desktop rail squeezed into a left-hand card | **More** opens a panel from the right: the rail's screens as full-width rows under their groups, then the account (profile, dark mode, help, sign out) |
| My tasks | A card under the positions list | The **Tasks** tab: `/tasks`, every task as a two-line card with its tick |
| Notifications | — | The bell stays in the topbar on every screen; its panel spans the phone |
| Add company / executive | Toolbar buttons that wrap | **+** → Quick actions → the Companies page's own add form (`?add=company\|executive`) |
| Note / call / status on one person | Find them in a 960px table | **+** → pick the executive → their drawer on Notes, on Meetings, or on Career with the status picker |
| Read the Companies stage | 20-column grid scrolled sideways | One **card** per line: the company, the person at it with their status picker, or Add executive |
| Read the pipeline | Board of columns | Opens on the **list**, drawn as cards below `md` |
| Read the Candidates page | Wide grid | Cards: person, role, place, latest position |
| Work an executive | Drawer inset under the topbar | **Full-screen** drawer clear of the notch; footer Note · Call · Next action in thumb reach |
| Book a call | Seven 104px columns | A day strip and **one day**; a swipe scrolls the day, a tap picks |
| Toasts, selection bar | Under the bottom bar | Lifted above it (`--mobile-nav-clearance`) |
| Typing | iOS zoomed into every field under 16px | Fields are 16px below `sm` |
| Home screen | A browser tab | `manifest.webmanifest`, standalone, `viewport-fit=cover` |

Pieces: `components/layout/mobile/` (`MobileTabBar.tsx`, `MobileMoreSheet.tsx`, `PositionQuickActions.tsx`), `components/ui/BottomSheet.tsx`; the
layouts pass `mobileTabs` to `AppShell`, which hides the topbar's menu button when a bar is drawn. A list draws cards
or a table, never both: `lib/viewport.ts`'s `readsAsCards()` is read once when it mounts, so a desktop never pays for
a hidden phone copy of a long grid.

## v2 (next)

- Swipe a pipeline card to move its status; long-press for the next action.
- The note composer full-screen with its kind chips on top, and voice to note.
- Offline drafts of a note, sent when the phone is back online.
- Push notifications for a reply, a booked call or a task due (the manifest is the first step).
- The assistant on a phone (`AssistantDock` is `hidden lg:block` today).
- An Outreach conversation laid out as a messaging thread.
- A mockup in `claude-design/` for each of the above before it is built.
