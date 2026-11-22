import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useMemo, useState, type ReactNode } from "react";
import { Link } from "react-router-dom";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { buttonClassName, EmptyState, LinesSkeleton, SegmentedControl, useToast } from "../../../components/ui";
import { Avatar } from "../../../components/ui/Avatar";
import { cn } from "../../../lib/cn";
import { messageFor } from "../../../lib/errorCodes";
import type { ConversationScope } from "../../outreach/api/conversationApi";
import * as tasksApi from "../../tasks/api/tasksApi";
import { DueText } from "../../tasks/components/TaskBits";
import { taskKindOf } from "../../tasks/lib/taskKinds";
import { useTasksChanged } from "../../tasks/lib/useTasksChanged";
import type { NeedsKind } from "../lib/homeLayout";
import * as homeApi from "../api/homeApi";
import { blocksOf, type NeedsItem, type TaskLine } from "../lib/needsYou";
import { recordPath } from "../lib/taskPath";

/** Each block shows its first few, so a long outreach queue never hides that tasks wait too. */
const VISIBLE = { conversations: 3, tasks: 5 } as const;

/** The To do groups by the glossary's names, as the Outreach page calls them. */
const GROUP_STYLE: Record<NeedsKind, { label: string; tone: string; icon: string }> = {
  NEEDS_REPLY: { label: "Needs reply", tone: "bg-u-inferred-tint text-u-inferred", icon: ICONS.reply },
  FOLLOW_UP: { label: "Follow-up", tone: "bg-u-signal-tint text-u-signal", icon: taskKindOf("follow_up").icon },
  PREPARATION: { label: "Before first email", tone: "bg-u-raised text-u-text2", icon: ICONS.mail },
  ATTENTION: { label: "Delivery problem", tone: "bg-u-offlimits-tint text-u-offlimits", icon: ICONS.warning },
};

const PRIMARY: Record<NeedsKind, string> = {
  NEEDS_REPLY: "Reply",
  FOLLOW_UP: "Follow up",
  PREPARATION: "Open",
  ATTENTION: "Open",
};

/**
 * Needs you: what is owed today, in two blocks (`blocksOf`) — replies and delivery problems as cards, each a decision
 * read in place, then every task as a one-line row, the next actions outreach carries among them.
 */
export function NeedsYou({
  items,
  more,
  myUserId,
  pending,
  refused,
  scope,
  onScopeChange,
  showScope,
}: {
  items: NeedsItem[];
  /** More wait than the server sent: the count says so, and the foot points to where they are. */
  more: boolean;
  myUserId: string | undefined;
  pending: boolean;
  refused: boolean;
  scope: ConversationScope;
  onScopeChange: (scope: ConversationScope) => void;
  showScope: boolean;
}) {
  const [expanded, setExpanded] = useState(false);
  const { cards: allConversations, lines: allTasks } = useMemo(() => blocksOf(items), [items]);
  const conversations = expanded ? allConversations : allConversations.slice(0, VISIBLE.conversations);
  const tasks = expanded ? allTasks : allTasks.slice(0, VISIBLE.tasks);
  const hidden = items.length - conversations.length - tasks.length;

  return (
    <section aria-label="Needs you" className="flex h-full min-w-0 flex-col overflow-hidden rounded-u-card border border-u-border-strong bg-u-surface">
      <div className="flex flex-none flex-wrap items-center gap-x-3.5 gap-y-2 border-b border-u-border px-4 py-3">
        <h2 className="text-body font-semibold">
          Needs you <span className="font-mono text-note font-medium text-u-text3">· {items.length}{more ? "+" : ""}</span>
        </h2>
        {showScope && (
          <SegmentedControl
            className="ml-auto"
            label="Whose items"
            value={scope}
            onChange={onScopeChange}
            options={[
              { value: "mine", label: "Mine" },
              { value: "team", label: "Team" },
            ]}
          />
        )}
      </div>

      <div className="min-h-0 flex-1 overflow-y-auto">
      {pending ? (
        <div className="p-4">
          <LinesSkeleton lines={4} />
        </div>
      ) : refused ? (
        <EmptyState
          icon={<Icon d={ICONS.lock} size={22} />}
          title="Couldn't load what needs you"
          body="Reload the page. Your tasks and outreach are still on each position."
        />
      ) : items.length === 0 ? (
        <EmptyState
          icon={<Icon d={ICONS.check} size={22} />}
          title={scope === "team" ? "Nothing waits on your team" : "Nothing needs you right now"}
          body="Replies, follow-ups and tasks land here as they come in."
        />
      ) : (
        <>
          {conversations.length > 0 && (
            <NeedsBlock title="Outreach" count={allConversations.length}>
              <ul aria-label="Outreach">
                {conversations.map((item, index) => (
                  <ConversationCard key={item.key} item={item} scope={scope} divider={index > 0} />
                ))}
              </ul>
            </NeedsBlock>
          )}
          {tasks.length > 0 && (
            <NeedsBlock title="Tasks" count={allTasks.length} divider={conversations.length > 0}>
              <ul aria-label="Tasks" className="grid grid-cols-[4px_18px_26px_minmax(0,1fr)_minmax(0,32%)_auto_4px] gap-x-3">
                {tasks.map((line, index) => (
                  <TaskRow key={line.key} line={line} mine={line.assignee.userId === myUserId} divider={index > 0} />
                ))}
              </ul>
            </NeedsBlock>
          )}
        </>
      )}
      </div>

      {!pending && !refused && items.length > 0 && (
        <div className="flex flex-none flex-wrap items-center gap-x-4 gap-y-1.5 border-t border-u-border px-4 py-2.5 text-note font-medium">
          {more && <span className="text-u-text3">More wait on each position's Outreach page.</span>}
          {(expanded || hidden > 0) && (
            <button type="button" onClick={() => setExpanded(!expanded)} className="text-u-accent hover:underline">
              {expanded ? "Show fewer" : `Show ${hidden} more`}
            </button>
          )}
          <Link to="/tasks" className="ml-auto text-u-text3 hover:text-u-text">
            All my tasks →
          </Link>
        </div>
      )}
    </section>
  );
}

/**
 * One line of the queue on the list's columns — tick, kind, what, who, when — so every line's cells align. The edge
 * columns are the gutter: a subgrid's own padding would push its cells off the tracks. The whole line opens them.
 */
const ROW =
  "relative col-span-full grid min-h-11 grid-cols-subgrid items-center py-1.5 transition-colors hover:bg-u-raised";
const WHEN = "whitespace-nowrap font-mono text-meta font-medium max-sm:hidden";

/** The line's way in: its words, stretched over the row, so a press anywhere but the tick opens the record. */
function RowLink({ to, title, done, children }: { to: string; title: string; done?: boolean; children: string }) {
  return (
    <Link
      to={to}
      title={title}
      className={cn(
        "truncate text-body font-medium text-u-text after:absolute after:inset-0 after:content-['']",
        done && "text-u-text3 line-through",
      )}
    >
      {children}
    </Link>
  );
}

function PersonCell({ id, name }: { id: string; name: string }) {
  return (
    <span className="flex min-w-0 items-center gap-2">
      <Avatar id={id} name={name} size="sm" />
      <span className="truncate text-note text-u-text2">{name}</span>
    </span>
  );
}

function NeedsBlock({
  title,
  count,
  divider = false,
  children,
}: {
  title: string;
  count: number;
  divider?: boolean;
  children: ReactNode;
}) {
  return (
    <div className={cn(divider && "border-t border-u-border")}>
      <h3 className="px-4 pb-1 pt-3 type-micro-label text-u-text3">
        {title} · {count}
      </h3>
      {children}
    </div>
  );
}

/** An executive waiting in outreach: who, why, the words they are waiting on. Reply goes to the position's queue. */
function ConversationCard({
  item,
  scope,
  divider,
}: {
  item: Extract<NeedsItem, { kind: "conversation" }>;
  scope: ConversationScope;
  divider: boolean;
}) {
  const { entry, group } = item;
  const { row } = entry;
  const style = GROUP_STYLE[group];
  // The Outreach page opens on the same queue the row came from, so the person is in the list beside the pane.
  const conversation = `/projects/${entry.projectId}/outreach?view=needs&who=${scope}&candidate=${row.candidateId}`;
  const detail = row.action?.title ?? row.latest?.preview ?? null;
  const why = row.action?.rule === "reply" ? "Filed when they replied" : row.action ? "Their next action" : null;

  return (
    <li
      className={cn(
        "relative grid grid-cols-[28px_minmax(0,1fr)] gap-x-3 gap-y-2 px-4 py-3 transition-colors hover:bg-u-raised",
        "md:grid-cols-[28px_minmax(0,1fr)_auto]",
        divider && "border-t border-u-border",
      )}
    >
      <Avatar id={row.personId} name={row.fullName} size="md" />
      <div className="min-w-0">
        <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
          <Link
            to={recordPath(entry.projectId, row.candidateId, row.personId, "outreach")}
            className="text-body font-semibold text-u-text after:absolute after:inset-0 after:content-['']"
          >
            {row.fullName}
          </Link>
          <span className={cn("inline-flex items-center gap-1 rounded-full px-2 py-px text-meta font-medium", style.tone)}>
            <Icon d={style.icon} size={11} />
            {style.label}
          </span>
          {scope === "team" && row.action?.assignee && (
            <span className="text-meta text-u-text3">{row.action.assignee.fullName}</span>
          )}
        </div>
        <div className="truncate text-meta text-u-text3">
          {[row.title, row.companyName, entry.positionTitle].filter(Boolean).join(" · ")}
        </div>
        {detail && <div className="mt-1.5 line-clamp-2 text-note text-u-text2">{detail}</div>}
        {why && <div className="mt-1 text-meta text-u-text3">{why}</div>}
      </div>
      <div className="relative z-10 col-start-2 flex items-center gap-1.5 md:col-start-3 md:justify-end md:self-start">
        <Link to={conversation} className={buttonClassName("primary", "px-3.5 py-1.5 text-note")}>
          {PRIMARY[group]}
        </Link>
      </div>
    </li>
  );
}

/** One of the caller's own tasks can be ticked here; a teammate's, under Team, is theirs to tick and only opens. */
function TaskRow({ line: task, mine, divider }: { line: TaskLine; mine: boolean; divider: boolean }) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const tasksChanged = useTasksChanged();
  const [done, setDone] = useState(false);
  const complete = useMutation({
    mutationFn: () => tasksApi.updateTask(task.taskId, { status: "done" }),
    onSuccess: (saved) => {
      setDone(saved.status === "done");
      tasksChanged(saved.projectId, { keepMyTasks: false });
      if (task.group) void queryClient.invalidateQueries({ queryKey: homeApi.MY_OUTREACH_NEEDS_PREFIX });
      if (saved.status === "cancelled") toast("Someone already deleted this task");
    },
    onError: (error) => toast.error(messageFor(error)),
  });
  const kind = taskKindOf(task.kind);
  const assignee = mine ? null : `Assigned to ${task.assignee.fullName ?? "someone"}`;
  const group = task.group ? GROUP_STYLE[task.group].label : null;

  return (
    <li className={cn(ROW, divider && "border-t border-u-border")}>
      {mine ? (
        <button
          type="button"
          role="checkbox"
          aria-checked={done}
          aria-label={`Complete ${task.title}`}
          disabled={done || complete.isPending}
          onClick={() => complete.mutate()}
          className={cn(
            "relative z-10 col-start-2 grid size-[18px] place-items-center rounded-sm border-[1.5px] transition",
            "hover:border-u-text2 disabled:cursor-default",
            done ? "border-u-direct bg-u-direct text-white" : "border-u-border-strong bg-u-surface",
          )}
        >
          {done && <Icon d={ICONS.check} size={11} />}
        </button>
      ) : (
        <span aria-hidden="true" className="col-start-2" />
      )}
      <span
        title={kind.label}
        className={cn("grid size-[26px] place-items-center rounded-u-control bg-u-raised text-u-text2", done && "opacity-45")}
      >
        <Icon d={kind.icon} size={14} />
      </span>
      <RowLink
        to={recordPath(task.projectId, task.candidateId, task.personId)}
        title={[task.title, task.projectTitle, group, assignee].filter(Boolean).join(" · ")}
        done={done}
      >
        {task.title}
      </RowLink>
      <span className={cn("min-w-0", done && "opacity-45")}>
        <PersonCell id={task.personId} name={task.personName} />
      </span>
      {done ? (
        <span className="whitespace-nowrap text-note font-medium text-u-direct">Done</span>
      ) : (
        <DueText dueAt={task.dueAt} className={WHEN} />
      )}
    </li>
  );
}
