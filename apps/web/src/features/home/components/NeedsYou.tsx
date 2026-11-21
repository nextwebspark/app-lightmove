import { useMutation } from "@tanstack/react-query";
import { useState } from "react";
import { Link } from "react-router-dom";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Button, buttonClassName, EmptyState, LinesSkeleton, SegmentedControl, useToast } from "../../../components/ui";
import { Avatar } from "../../../components/ui/Avatar";
import { cn } from "../../../lib/cn";
import { messageFor } from "../../../lib/errorCodes";
import type { ConversationScope } from "../../outreach/api/conversationApi";
import { positionPipelinePath } from "../../pipeline/lib/pipelinePath";
import * as tasksApi from "../../tasks/api/tasksApi";
import { DuePill } from "../../tasks/components/TaskBits";
import { useTasksChanged } from "../../tasks/lib/useTasksChanged";
import type { NeedsKind } from "../lib/homeLayout";
import type { NeedsItem } from "../lib/needsYou";

const VISIBLE = 6;

/** A row action drawn as a link: the `xs` button's own size. */
const ROW_ACTION = "px-3.5 py-1.5 text-note";

const GROUP_STYLE: Record<NeedsKind, { label: string; tone: string }> = {
  NEEDS_REPLY: { label: "Replied", tone: "bg-u-inferred-tint text-u-inferred" },
  FOLLOW_UP: { label: "Follow-up", tone: "bg-u-signal-tint text-u-signal" },
  PREPARATION: { label: "To prepare", tone: "bg-u-raised text-u-text2" },
  ATTENTION: { label: "Needs attention", tone: "bg-u-offlimits-tint text-u-offlimits" },
};

const PRIMARY: Record<NeedsKind, string> = {
  NEEDS_REPLY: "Reply",
  FOLLOW_UP: "Follow up",
  PREPARATION: "Open",
  ATTENTION: "Fix it",
};

/** Needs you: one ranked list of what is owed today — outreach waiting on the caller, then their tasks. */
export function NeedsYou({
  items,
  total,
  pending,
  refused,
  scope,
  onScopeChange,
  showScope,
}: {
  items: NeedsItem[];
  total: number;
  pending: boolean;
  refused: boolean;
  scope: ConversationScope;
  onScopeChange: (scope: ConversationScope) => void;
  showScope: boolean;
}) {
  const [expanded, setExpanded] = useState(false);
  const shown = expanded ? items : items.slice(0, VISIBLE);

  return (
    <section aria-label="Needs you" className="min-w-0 overflow-hidden rounded-u-card border border-u-border-strong bg-u-surface">
      <div className="flex flex-wrap items-center gap-x-3.5 gap-y-2 border-b border-u-border px-4 py-3">
        <h2 className="text-body font-semibold">
          Needs you <span className="font-mono text-note font-medium text-u-text3">· {total}</span>
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
        <ul aria-label="Needs you">
          {shown.map((item, index) => (
            <li key={item.key} className={cn("px-4 py-3", index > 0 && "border-t border-u-border")}>
              {item.kind === "conversation" ? <ConversationItem item={item} /> : <TaskItem item={item} />}
            </li>
          ))}
        </ul>
      )}

      {!pending && !refused && items.length > 0 && (
        <div className="flex flex-wrap items-center gap-x-4 gap-y-1.5 border-t border-u-border px-4 py-2.5 text-note font-medium">
          {items.length > VISIBLE && (
            <button type="button" onClick={() => setExpanded(!expanded)} className="text-u-accent hover:underline">
              {expanded ? "Show fewer" : `Show ${items.length - VISIBLE} more`}
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

function ConversationItem({ item }: { item: Extract<NeedsItem, { kind: "conversation" }> }) {
  const { entry, group } = item;
  const { row } = entry;
  const style = GROUP_STYLE[group];
  const conversation = `/projects/${entry.projectId}/outreach?candidate=${row.candidateId}&who=mine`;
  const detail = row.action?.title ?? row.latest?.preview ?? null;
  const why = row.action?.rule === "reply" ? "Filed when they replied" : row.action ? "Their next action" : null;

  return (
    <div className="grid grid-cols-[28px_minmax(0,1fr)] gap-x-3 gap-y-2 md:grid-cols-[28px_minmax(0,1fr)_auto]">
      <Avatar id={row.personId} name={row.fullName} size="md" />
      <div className="min-w-0">
        <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
          <span className="text-body font-semibold">{row.fullName}</span>
          <span className={cn("rounded-full px-2 py-px text-meta font-medium", style.tone)}>{style.label}</span>
          {row.action?.assignee && <span className="text-meta text-u-text3">{row.action.assignee.fullName}</span>}
        </div>
        <div className="truncate text-meta text-u-text3">
          {[row.title, row.companyName, entry.positionTitle].filter(Boolean).join(" · ")}
        </div>
        {detail && <div className="mt-1.5 text-note text-u-text2">{detail}</div>}
        {why && <div className="mt-1 text-meta text-u-text3">{why}</div>}
      </div>
      <div className="col-start-2 flex items-center gap-1.5 md:col-start-3 md:justify-end">
        <Link to={conversation} className={buttonClassName("primary", ROW_ACTION)}>
          {PRIMARY[group]}
        </Link>
      </div>
    </div>
  );
}

function TaskItem({ item }: { item: Extract<NeedsItem, { kind: "task" }> }) {
  const { task } = item;
  const toast = useToast();
  const tasksChanged = useTasksChanged();
  const [done, setDone] = useState(false);
  const complete = useMutation({
    mutationFn: () => tasksApi.updateTask(task.id, { status: "done" }),
    onSuccess: (saved) => {
      setDone(saved.status === "done");
      tasksChanged(saved.projectId, { keepMyTasks: false });
      if (saved.status === "cancelled") toast("Someone already deleted this task");
    },
    onError: (error) => toast.error(messageFor(error)),
  });
  const open = task.candidateId
    ? positionPipelinePath(task.projectId, { candidate: task.candidateId })
    : positionPipelinePath(task.projectId);

  return (
    <div className={cn("grid grid-cols-[28px_minmax(0,1fr)] gap-x-3 gap-y-2 md:grid-cols-[28px_minmax(0,1fr)_auto]", done && "opacity-55")}>
      <span className="grid size-[26px] place-items-center rounded-lg bg-u-raised text-u-text2">
        <Icon d={ICONS.checkCircle} size={14} />
      </span>
      <div className="min-w-0">
        <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
          <span className={cn("text-body font-semibold", done && "line-through")}>{task.title}</span>
          <DuePill dueAt={task.dueAt} />
        </div>
        <div className="truncate text-meta text-u-text3">
          {[task.personName, task.projectTitle].filter(Boolean).join(" · ")}
        </div>
      </div>
      <div className="col-start-2 flex items-center gap-1.5 md:col-start-3 md:justify-end">
        {done ? (
          <span className="inline-flex items-center gap-1.5 text-note font-medium text-u-direct">
            <Icon d={ICONS.check} size={13} />
            Done
          </span>
        ) : (
          <>
            <Link to={open} className={buttonClassName("secondary", ROW_ACTION)}>
              Open
            </Link>
            <Button size="xs" onClick={() => complete.mutate()} disabled={complete.isPending}>
              Done
            </Button>
          </>
        )}
      </div>
    </div>
  );
}
