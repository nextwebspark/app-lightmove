# Tasks, @mentions and notifications

Epic #862 (phases #863–#866; #867 is later). Mockups: `claude-design/Position.dc.html` (Next action modal,
the drawer's Next actions, the card), `Notifications.dc.html` (the bell), `Workspace.dc.html` (My tasks) and
`Settings.dc.html` (Task presets, Notifications).

## The model

- **A next action is a task.** One `app_lm_task` row per job, on one executive of one position (`project_id` +
  `candidate_id`, the person beside them). The Pipeline card's Next action is that executive's earliest open
  task, so the board, the drawer, My tasks and the bell read the same rows. The executive drawer draws it as a
  bar in its header — on every tab, since the moment to set one is while writing the note or the email — with the
  rest in a popover; the Candidates drawer draws it on each position's card. V133 turned every V132
  `next_action` into an open task; the V132 columns stay, unwritten, until the cleanup migration (#606's).
- **The team is the boundary.** A task is assigned, and a note mentions, only someone who may work the
  position — `WORK_EXECUTE` on it, re-read when saved — which is its staff seats and the workspace's admins.
  Never a client seat. A seat that goes moves its holder's open tasks there to the position's first lead, as does
  losing the admin role or leaving the workspace for a task on a position its holder worked only as an admin.
- **Presets** are the workspace's catalog (`app_lm_workspace_task_preset`): seeded on first read, edited
  under `WORKSPACE_MANAGE`, retired rather than deleted. Free text is a title only.
- **Rules** file tasks from outreach — a reply (`Reply to {name}`), a bounce (`Find a working email for
  {name}`), a booked call (`Debrief {name}`, due when the call ends) — for whoever started the sequence or
  booked the call. Each carries a `rule_key`, unique while open, so a replayed webhook files nothing twice;
  each rule can be switched off per workspace (`app_lm_workspace_task_rule`, absent means on).
- **Mentions** are tokens in a note's body, `@[Full Name](user:<uuid>)` or `@[team](team)`. The server keeps
  only users who may work the note's position; a note about no position mentions nobody.
- **Notifications** (`app_lm_notification`) are one per recipient per event, grouped while unread by
  `group_key` (three replies on one position are one row with `count` 3). They name a note or a task, never
  quote a note. Recipients are re-checked when the row is written and again before any email.
- **Delivery**: the bell reads `/me/notifications` and listens on `/me/notifications/stream` (a per-user
  `pg_notify` channel on `core/stream`'s one LISTEN connection). Email goes only for *assigned to you* and
  *mentioned*, and only if still unread after `lightmove.notifications.email-delay` (10 minutes); a daily
  digest goes at 08:00 in the user's mailbox time zone (else `default-time-zone`), claimed once per user per
  day. Each email is claimed before it is sent, so one the mail service refuses is logged at error with its ids and
  never resent: a second copy is worse than a missed one. When it goes it is asked again whether the person still
  works the position, still wants that kind by email, still holds the task and, for a mention, whether the note is
  still there; a row read before its time never sends, even marked unread later, and an older row of a group stays
  read while a newer one waits. Preferences are per kind × channel, defaulted on. A person holds at most ten live
  streams per workspace; an eleventh closes their oldest.
- **An archived position goes quiet** (V137). Its tasks and bell rows are kept but left out of My tasks, the
  counts, the digest, the due scan and the bell until it is restored; an email due about it is claimed and never
  sent. The position's own reads — the drawer, the board — still show its tasks.
- **Staff-only, all of it.** Nothing here rides `CandidateResponse`, the report, the public API or MCP.

## Wire contract

Dates are ISO-8601 instants. Enum values are lower snake case.

### Tasks — `WORK_EXECUTE` on the position

| Route | Body / query | Answer |
|---|---|---|
| `GET /api/v1/projects/{projectId}/candidates/{candidateId}/tasks` | `allDone=true` for every done task | `TasksResponse` |
| `POST /api/v1/projects/{projectId}/candidates/{candidateId}/tasks` | `CreateTaskRequest` | 201 `TaskResponse` |
| `PATCH /api/v1/tasks/{taskId}` | `UpdateTaskRequest` | `TaskResponse` |
| `DELETE /api/v1/tasks/{taskId}` | | 204 (the task is cancelled) |
| `GET /api/v1/me/tasks?view=&tz=&offset=&size=` | `view` = `today` · `overdue` · `upcoming` · `assigned_by_me`; `tz` an IANA zone (default UTC); `offset` the open rows already held (a row struck done in place no longer counts), `size` 20 by default and at most 50 | `MyTasksResponse` |

```ts
type TaskKind = 'call' | 'email' | 'meeting' | 'follow_up' | 'todo';
type TaskStatus = 'open' | 'done' | 'cancelled';
type TaskRule = 'reply' | 'bounce' | 'booked_call';
interface TaskPerson { userId: string; fullName: string | null; avatarUrl: string | null }
interface TaskNote { id: string; kind: 'general' | 'call' | 'meeting' | 'email'; writtenAt: string }  // never its words
interface TaskResponse {
  id: string; projectId: string; projectTitle: string | null; candidateId: string | null;
  personId: string; personName: string | null;
  title: string; kind: TaskKind; detail: string | null; presetId: string | null;
  note: TaskNote | null;                                   // the note it came from; null while it is deleted
  assignee: TaskPerson; createdBy: TaskPerson | null;      // createdBy null: filed by a rule
  dueAt: string | null; status: TaskStatus; source: 'manual' | 'note' | 'rule'; rule: TaskRule | null;
  completedAt: string | null; completedBy: TaskPerson | null; createdAt: string;
  editable: boolean;                                       // may change, reassign or cancel it
}
interface TasksResponse { open: TaskResponse[]; done: TaskResponse[]; doneCount: number }   // done: the last three, or all with allDone
interface CreateTaskRequest {
  title: string;                // 1–200
  kind: TaskKind; assigneeUserId: string; dueAt: string;
  detail?: string | null;       // ≤ 500
  presetId?: string | null;
  noteId?: string | null;       // a note on this person: the composer's Save & set next action
}
interface UpdateTaskRequest {   // every field optional; status 'done' completes, 'open' reopens
  title?: string; kind?: TaskKind; assigneeUserId?: string; dueAt?: string; detail?: string | null;
  status?: 'open' | 'done';
}
interface MyTasksResponse {
  tasks: TaskResponse[];
  counts: { today: number; overdue: number; upcoming: number; assignedByMe: number };
  hasMore: boolean;   // the tab holds more after this slice
}
```

Changing, reassigning or cancelling a task is its creator's, its assignee's, a `TEAM_MANAGE` holder's on the
position (its lead) or a `WORKSPACE_MANAGE` holder's (`TASK_NOT_YOURS`); completing one is anyone's who works
the position. An assignee who may not work the position is `TASK_ASSIGNEE_NOT_ON_TEAM`.

The Pipeline row's `nextAction` is the earliest open task:

```ts
interface CandidateNextAction {
  taskId: string; text: string; kind: TaskKind; dueAt: string | null;
  assignee: TaskPerson; openCount: number;                 // every open task, this one included
  setByName: string | null;
}
```

`PUT …/candidates/{candidateId}/next-action` is gone; the modal posts a task.

### Presets

| Route | Gate | Body | Answer |
|---|---|---|---|
| `GET /api/v1/task-presets` | staff | | `TaskPresetResponse[]` (retired included) |
| `GET /api/v1/task-presets/suggestions` | `WORKSPACE_MANAGE` | | `{ label, count }[]` |
| `POST /api/v1/task-presets` | `WORKSPACE_MANAGE` | `{ label, kind, dueOffsetDays }` | 201 `TaskPresetResponse` |
| `PATCH /api/v1/task-presets/{presetId}` | `WORKSPACE_MANAGE` | `{ label?, kind?, dueOffsetDays?, clearDueOffset?, retired? }` | `TaskPresetResponse` |
| `PUT /api/v1/task-presets/order` | `WORKSPACE_MANAGE` | `{ presetIds: string[] }` — every live preset; retired ones keep their place after | `TaskPresetResponse[]` |

```ts
interface TaskPresetResponse {
  id: string; label: string; kind: TaskKind; dueOffsetDays: number | null;  // 0 = same day
  position: number; retired: boolean; retiredAt: string | null; usage: number;
}
```

### Rules — `GET` staff, `PUT` `WORKSPACE_MANAGE`

`GET|PUT /api/v1/workspace/task-rules` — `{ reply: boolean; bounce: boolean; bookedCall: boolean }`.

### Notifications — the caller's own, in the session's workspace

| Route | Body / query | Answer |
|---|---|---|
| `GET /api/v1/me/notifications?filter=unread\|all&cursor=` | | `NotificationsResponse` |
| `GET /api/v1/me/notifications/unread-count` | | `{ unreadCount }` |
| `POST /api/v1/me/notifications/read` | `{ ids?: string[]; all?: boolean }` | `{ unreadCount }` |
| `PATCH /api/v1/me/notifications/{id}` | `{ read?: boolean; snoozedUntil?: string; clearSnooze?: boolean; archived?: boolean }` | `NotificationResponse` |
| `GET /api/v1/me/notifications/stream` | SSE: `connected`, then `change` `{"kind":"notifications"}` | |
| `GET\|PUT /api/v1/me/notification-preferences` | `NotificationPreferences` | `NotificationPreferences` |

```ts
type NotificationKind = 'task_assigned' | 'task_due_soon' | 'task_overdue' | 'mentioned'
  | 'outreach_replied' | 'outreach_bounced' | 'call_booked' | 'added_to_team' | 'tasks_moved';
interface NotificationResponse {
  id: string; kind: NotificationKind; count: number;
  actor: TaskPerson | null;                                 // null: Uncava did it
  projectId: string | null; projectTitle: string | null;
  personId: string | null; candidateId: string | null; personName: string | null;
  taskId: string | null; taskTitle: string | null;
  note: TaskNote | null;                                    // mentioned in it, or the assigned task came from it
  names: string[];                                          // a grouped row's latest people, newest first
  dueAt: string | null;                                     // task_due_soon / task_overdue / call_booked
  createdAt: string; read: boolean; snoozedUntil: string | null;
}
interface NotificationsResponse { items: NotificationResponse[]; nextCursor: string | null; unreadCount: number }
interface NotificationPreferences {
  kinds: { kind: NotificationPreferenceKind; inApp: boolean; email: boolean }[];
  digest: boolean; timeZone: string;                        // timeZone is read-only: the mailbox's
}
type NotificationPreferenceKind = 'task_assigned' | 'task_due' | 'mentioned' | 'outreach_replied'
  | 'outreach_bounced' | 'call_booked' | 'added_to_team' | 'tasks_moved';
```

A snoozed row is left out of both lists until its time; an archived one is gone. `email` is honoured only for
`task_assigned` and `mentioned` (the digest carries the rest).
