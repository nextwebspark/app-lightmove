import {
  keepPreviousData,
  useInfiniteQuery,
  useMutation,
  useQueryClient,
  type InfiniteData,
} from "@tanstack/react-query";
import { useId, useState, type UIEvent } from "react";
import { Link } from "react-router-dom";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { LinesSkeleton, useToast } from "../../../components/ui";
import { Avatar } from "../../../components/ui/Avatar";
import { useRadioGroupKeys } from "../../../components/ui/useRadioGroupKeys";
import { cn } from "../../../lib/cn";
import { messageFor } from "../../../lib/errorCodes";
import * as tasksApi from "../api/tasksApi";
import type { MyTasksResponse, MyTasksView, TaskResponse } from "../api/types";
import { taskKindOf } from "../lib/taskKinds";
import { firstNameOf } from "../lib/team";
import { useTasksChanged } from "../lib/useTasksChanged";
import { NextActionModal, type NextActionTarget } from "./NextActionModal";
import { AutoPill, DuePill } from "./TaskBits";

type Counts = MyTasksResponse["counts"];

const TABS: readonly { view: MyTasksView; label: string; count: keyof Counts; empty: string }[] = [
  { view: "today", label: "Today", count: "today", empty: "Nothing due today." },
  { view: "overdue", label: "Overdue", count: "overdue", empty: "Nothing overdue." },
  { view: "upcoming", label: "Upcoming", count: "upcoming", empty: "Nothing coming up." },
  { view: "assigned_by_me", label: "Assigned by me", count: "assignedByMe", empty: "Nothing assigned by you." },
];

const VISIBLE = 5;
const LOAD_AHEAD_PX = 120;
const COLLAPSED_KEY = "lm-my-tasks-collapsed";

const COLUMNS = "grid-cols-[18px_26px_minmax(200px,1.4fr)_minmax(160px,1fr)_minmax(140px,1fr)_188px]";
const COLUMNS_WITH_ASSIGNEE =
  "grid-cols-[18px_26px_minmax(200px,1.4fr)_minmax(160px,1fr)_minmax(140px,1fr)_188px_112px]";

function firstTabWithTasks(counts: Counts): MyTasksView {
  if (counts.today > 0) return "today";
  if (counts.overdue > 0) return "overdue";
  return counts.upcoming > 0 ? "upcoming" : "today";
}

/** Every row the slices brought, once: a task written meanwhile can push one row into the next slice too. */
function rowsOf(slices: readonly MyTasksResponse[]): TaskResponse[] {
  const byId = new Map<string, TaskResponse>();
  for (const task of slices.flatMap((slice) => slice.tasks)) {
    if (!byId.has(task.id)) byId.set(task.id, task);
  }
  return [...byId.values()];
}

function readCollapsed(): boolean {
  try {
    return localStorage.getItem(COLLAPSED_KEY) === "1";
  } catch {
    return false;
  }
}

function rememberCollapsed(collapsed: boolean) {
  try {
    localStorage.setItem(COLLAPSED_KEY, collapsed ? "1" : "0");
  } catch {
    // Private mode: the choice just doesn't outlive the tab.
  }
}

/**
 * My positions' My tasks (`Workspace.dc.html`, "My tasks"): the caller's own tasks across every position, by
 * Today, Overdue, Upcoming and the ones they gave others. It opens on Today, or on Overdue then Upcoming when
 * nothing is due today, and folds to its header. Five rows show; Show all scrolls the rest inside the card, read
 * twenty at a time as the reader nears the end. Ticking one strikes it where it is and offers the next.
 * Staff-only — the page draws it for nobody else.
 */
export function MyTasksCard() {
  const queryClient = useQueryClient();
  const toast = useToast();
  const tasksChanged = useTasksChanged();
  const bodyId = useId();
  const [chosen, setChosen] = useState<MyTasksView | null>(null);
  const view = chosen ?? "today";
  const [expanded, setExpanded] = useState(false);
  const [collapsed, setCollapsed] = useState(readCollapsed);
  const [nextFor, setNextFor] = useState<NextActionTarget | null>(null);
  const tab = TABS.find((candidate) => candidate.view === view) ?? TABS[0];

  const mine = useInfiniteQuery({
    queryKey: tasksApi.MY_TASKS_KEY(view),
    queryFn: ({ pageParam, signal }) => tasksApi.getMyTasks(view, { offset: pageParam, signal }),
    initialPageParam: 0,
    getNextPageParam: (last, slices) =>
      last.hasMore ? rowsOf(slices).filter((task) => task.status === "open").length : undefined,
    placeholderData: keepPreviousData,
  });
  if (chosen === null && mine.data && !mine.isPlaceholderData) {
    setChosen(firstTabWithTasks(mine.data.pages[0].counts));
  }
  // The tab travels with the tick: the reader may move to another before the answer comes back. Every tab read so
  // far carries the counts, so each is told; only the tab ticked on holds the row.
  const toggle = useMutation({
    mutationFn: ({ task }: { task: TaskResponse; on: (typeof TABS)[number] }) =>
      tasksApi.updateTask(task.id, { status: task.status === "done" ? "open" : "done" }),
    onSuccess: (saved, { on }) => {
      queryClient.setQueriesData<InfiniteData<MyTasksResponse, number>>(
        { queryKey: [...tasksApi.TASKS_KEY_PREFIX, "mine"] },
        (current) =>
          current
            ? {
                ...current,
                pages: current.pages.map((slice) => ({
                  ...slice,
                  tasks: slice.tasks.map((task) => (task.id === saved.id ? saved : task)),
                  counts: { ...slice.counts, [on.count]: slice.counts[on.count] + (saved.status === "done" ? -1 : 1) },
                })),
              }
            : current,
      );
      tasksChanged(saved.projectId, { keepMyTasks: true });
    },
    onError: (error) => toast.error(messageFor(error)),
  });

  const fold = (next: boolean) => {
    setCollapsed(next);
    rememberCollapsed(next);
  };
  const pick = (next: MyTasksView) => {
    setChosen(next);
    setExpanded(false);
    if (collapsed) fold(false);
  };
  const keys = useRadioGroupKeys(
    TABS.map((candidate) => candidate.view),
    view,
    pick,
  );

  const counts = mine.data?.pages.at(-1)?.counts;
  const rows = mine.isPlaceholderData ? [] : rowsOf(mine.data?.pages ?? []);
  const struck = rows.filter((task) => task.status === "done").length;
  const listed = (counts?.[tab.count] ?? 0) + struck;
  const shown = expanded ? rows : rows.slice(0, VISIBLE);
  const withAssignee = view === "assigned_by_me";
  const loadingMore = expanded && (mine.hasNextPage || mine.isFetchingNextPage);
  // A slice or a refetch that fails leaves the rows already read standing.
  const failed = mine.isError && mine.data === undefined;
  const moreFailed = mine.isFetchNextPageError;

  const loadAhead = (event: UIEvent<HTMLDivElement>) => {
    const box = event.currentTarget;
    const nearEnd = box.scrollHeight - box.scrollTop - box.clientHeight < LOAD_AHEAD_PX;
    if (nearEnd && mine.hasNextPage && !mine.isFetching) void mine.fetchNextPage();
  };

  return (
    <section aria-label="My tasks" className="mb-5 overflow-hidden rounded-u-card border border-u-border-strong bg-u-surface">
      <div
        className={cn(
          "flex flex-wrap items-center gap-x-4 gap-y-3 py-3 pe-3.5 ps-4",
          !collapsed && "border-b border-u-border",
        )}
      >
        <h2 className="text-body font-semibold text-u-text">My tasks</h2>
        <div
          ref={keys.ref}
          role="tablist"
          aria-label="Task views"
          onKeyDown={keys.onKeyDown}
          className="flex flex-wrap gap-0.5 rounded-u-control border border-u-border bg-u-raised p-0.5"
        >
          {TABS.map((candidate) => {
            const selected = candidate.view === view;
            return (
              <button
                key={candidate.view}
                type="button"
                role="tab"
                aria-selected={selected}
                tabIndex={selected ? 0 : -1}
                onClick={() => pick(candidate.view)}
                className={cn(
                  "inline-flex items-center gap-1.5 whitespace-nowrap rounded-u-control px-2.5 py-1 text-note font-medium hover:text-u-text",
                  selected ? "bg-u-surface text-u-text shadow-sm" : "text-u-text2",
                )}
              >
                {candidate.label}{" "}
                <span className={cn("font-mono text-meta", selected ? "text-u-text2" : "text-u-text3")}>
                  · {counts ? counts[candidate.count] : "–"}
                </span>
              </button>
            );
          })}
        </div>
        <button
          type="button"
          aria-expanded={!collapsed}
          aria-controls={bodyId}
          aria-label={collapsed ? "Expand My tasks" : "Collapse My tasks"}
          onClick={() => fold(!collapsed)}
          className="ms-auto grid size-7 place-items-center rounded-u-control text-u-text3 transition hover:bg-u-raised hover:text-u-text"
        >
          <Icon
            d={ICONS.chevronDown}
            size={15}
            className={cn("transition-transform duration-200 motion-reduce:transition-none", collapsed && "-rotate-90")}
          />
        </button>
      </div>

      <div id={bodyId} hidden={collapsed}>
        {failed ? (
          <p className="px-4 py-6 text-note text-u-text3">{messageFor(mine.error)}</p>
        ) : mine.isPending || mine.isPlaceholderData ? (
          <div className="px-4 py-3">
            <LinesSkeleton lines={3} />
          </div>
        ) : rows.length === 0 ? (
          <p className="flex items-center justify-center gap-2 px-4 py-7 text-body text-u-text3">
            <Icon d={ICONS.check} size={16} />
            {tab.empty}
          </p>
        ) : (
          <div
            onScroll={expanded ? loadAhead : undefined}
            className={cn("overflow-x-auto", expanded && "max-h-[460px] overflow-y-auto")}
          >
            <div role="list" aria-label={`${tab.label} tasks`}>
              {shown.map((task, index) => (
                <TaskRow
                  key={task.id}
                  task={task}
                  withAssignee={withAssignee}
                  divider={index < shown.length - 1 || loadingMore}
                  busy={toggle.isPending && toggle.variables?.task.id === task.id}
                  onToggle={() => toggle.mutate({ task, on: tab })}
                  onSetNext={
                    task.candidateId
                      ? () =>
                          setNextFor({
                            projectId: task.projectId,
                            candidateId: task.candidateId!,
                            personId: task.personId,
                            executiveName: task.personName ?? "This executive",
                          })
                      : null
                  }
                />
              ))}
            </div>
            {loadingMore && (
              <div className="flex items-center justify-center gap-2 px-4 py-2.5 text-note text-u-text3">
                {mine.isFetchingNextPage ? (
                  "Loading more…"
                ) : (
                  <>
                    {moreFailed && <span>{messageFor(mine.error)}</span>}
                    <button
                      type="button"
                      onClick={() => void mine.fetchNextPage()}
                      className="font-medium text-u-accent hover:underline"
                    >
                      {moreFailed ? "Try again" : "Load more"}
                    </button>
                  </>
                )}
              </div>
            )}
          </div>
        )}

        {!mine.isPlaceholderData && listed > VISIBLE && (
          <button
            type="button"
            onClick={() => setExpanded((current) => !current)}
            className="flex w-full items-center justify-center gap-1.5 border-t border-u-border px-4 py-2 text-note font-medium text-u-text2 hover:bg-u-raised hover:text-u-text"
          >
            {expanded ? "Show fewer" : `Show all ${listed}`}
            <Icon d={ICONS.chevronDown} size={13} className={cn(expanded && "rotate-180")} />
          </button>
        )}
      </div>

      <NextActionModal target={nextFor} onClose={() => setNextFor(null)} />
    </section>
  );
}

function TaskRow({
  task,
  withAssignee,
  divider,
  busy,
  onToggle,
  onSetNext,
}: {
  task: TaskResponse;
  withAssignee: boolean;
  divider: boolean;
  busy: boolean;
  onToggle: () => void;
  onSetNext: (() => void) | null;
}) {
  const done = task.status === "done";
  const kind = taskKindOf(task.kind);
  const personName = task.personName ?? "Someone";
  const assigneeName = task.assignee.fullName ?? "Someone";
  const pipeline = `/projects/${task.projectId}/pipeline`;
  // An executive since unmapped from the position is still the workspace's person: open their record there.
  const executiveHref = task.candidateId
    ? `${pipeline}?candidate=${task.candidateId}`
    : `/candidates?person=${encodeURIComponent(task.personId)}`;

  return (
    <div
      role="listitem"
      aria-label={task.title}
      className={cn(
        "grid min-h-[50px] items-center gap-3 px-4 py-1.5 hover:bg-u-raised",
        withAssignee ? cn(COLUMNS_WITH_ASSIGNEE, "min-w-[1000px]") : cn(COLUMNS, "min-w-[880px]"),
        divider && "border-b border-u-border",
      )}
    >
      <button
        type="button"
        role="checkbox"
        aria-checked={done}
        aria-label={`${done ? "Reopen" : "Complete"} ${task.title}`}
        disabled={busy}
        onClick={onToggle}
        className={cn(
          "grid size-[18px] place-items-center rounded-sm border-[1.5px] transition hover:border-u-text2 disabled:opacity-60",
          done ? "border-u-direct bg-u-direct text-white" : "border-u-border-strong",
        )}
      >
        {done && <Icon d={ICONS.check} size={11} />}
      </button>
      <span
        title={kind.label}
        className={cn("grid size-[26px] place-items-center rounded-u-control bg-u-raised text-u-text2", done && "opacity-45")}
      >
        <Icon d={kind.icon} size={14} />
      </span>
      <span className={cn("flex min-w-0 items-center gap-2", done && "opacity-45")}>
        <Link
          to={executiveHref}
          className={cn("truncate text-body font-medium text-u-text hover:underline", done && "line-through")}
        >
          {task.title}
        </Link>
        {task.source === "rule" && <AutoPill />}
      </span>
      <span className={cn("flex min-w-0 items-center gap-2", done && "opacity-45")}>
        <Avatar id={task.personId} name={personName} size="sm" />
        <Link to={executiveHref} className="truncate text-body text-u-text2 hover:text-u-text hover:underline">
          {personName}
        </Link>
      </span>
      <Link
        to={pipeline}
        title={task.projectTitle ?? undefined}
        className={cn("truncate text-note text-u-text3 hover:text-u-text hover:underline", done && "opacity-45")}
      >
        {task.projectTitle ?? "Position"}
      </Link>
      <span className="flex min-w-0 items-center">
        {done ? (
          <span className="whitespace-nowrap text-note font-medium text-u-direct">
            Done
            {onSetNext && (
              <>
                {" · "}
                <button type="button" onClick={onSetNext} className="font-medium text-u-accent hover:underline">
                  Set the next one?
                </button>
              </>
            )}
          </span>
        ) : (
          <DuePill dueAt={task.dueAt} />
        )}
      </span>
      {withAssignee && (
        <span
          title={`Assigned to ${assigneeName}`}
          className={cn("flex min-w-0 items-center gap-2", done && "opacity-45")}
        >
          <Avatar id={task.assignee.userId} name={assigneeName} src={task.assignee.avatarUrl} size="sm" />
          <span className="truncate text-note text-u-text2">{firstNameOf(assigneeName)}</span>
        </span>
      )}
    </div>
  );
}
