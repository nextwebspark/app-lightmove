import { keepPreviousData, useMutation, useQuery } from "@tanstack/react-query";
import { useEffect, useRef, useState, type KeyboardEvent } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Avatar } from "../../../components/ui/Avatar";
import { Popover } from "../../../components/ui/Popover";
import { Spinner, useToast } from "../../../components/ui";
import { cn } from "../../../lib/cn";
import { messageFor } from "../../../lib/errorCodes";
import { useEscapeKey } from "../../../lib/useEscapeKey";
import * as tasksApi from "../api/tasksApi";
import type { TaskResponse, TasksResponse } from "../api/types";
import { taskKindOf } from "../lib/taskKinds";
import { groupByUrgency } from "../lib/taskUrgency";
import { useTasksChanged } from "../lib/useTasksChanged";
import { DueText } from "./TaskBits";
import { DoneTaskRow, OpenTaskRow, TaskCheckbox } from "./TaskRow";

/** How long "Done. Set the next one?" stays before the bar settles to its empty state. */
const SET_NEXT_PROMPT_MS = 6000;

const POPOVER_WIDTH = 380;

/**
 * One executive's next action on one position, as a single fixed-height bar: the earliest open task —
 * ticked done in place — and every other one in a popover, so the header holding it never grows and
 * the tabs under it never move. Staff-only.
 */
export function NextActionBar({
  projectId,
  candidateId,
  firstName,
  justCompleted,
  onCompleted,
  onPromptSettled,
  onOpen,
  onShowNote,
  className,
}: {
  projectId: string;
  candidateId: string;
  firstName: string;
  /** A task was just ticked done, here or in the modal: offer the next one. */
  justCompleted: boolean;
  onCompleted: () => void;
  onPromptSettled: () => void;
  /** Opens the modal on a task, or on a new one for null. */
  onOpen: (taskId: string | null) => void;
  /** Brings the note a task was set from into view. */
  onShowNote?: (noteId: string) => void;
  className?: string;
}) {
  const tasks = useCandidateTasks(projectId, candidateId);
  const completion = useTaskCompletion(projectId, onCompleted);
  const open = tasks.data?.open ?? [];
  const first = open[0];

  useEffect(() => {
    if (!justCompleted || open.length > 0) return;
    const timer = window.setTimeout(onPromptSettled, SET_NEXT_PROMPT_MS);
    return () => window.clearTimeout(timer);
  }, [justCompleted, open.length, onPromptSettled]);

  const frame = "flex h-10 items-center gap-2.5 rounded-u-card border ps-3 pe-1.5";

  if (tasks.isError) {
    return (
      <div className={cn(frame, "border-u-border bg-u-sunken text-note text-u-text3", className)}>
        {messageFor(tasks.error)}
      </div>
    );
  }

  if (tasks.isPending) {
    return (
      <div aria-busy="true" className={cn(frame, "border-u-border bg-u-sunken", className)}>
        <span className="h-2.5 w-40 animate-pulse rounded-full bg-u-raised" />
      </div>
    );
  }

  if (!first && justCompleted) {
    return (
      <div
        role="status"
        className={cn(frame, "border-transparent bg-u-direct-tint text-note text-u-text2 motion-safe:animate-[fade-up_220ms_ease_both]", className)}
      >
        <Icon d={ICONS.checkCircle} size={15} className="flex-none text-u-direct" />
        <span className="min-w-0 flex-1 truncate">Done. Set the next one?</span>
        <button
          type="button"
          onClick={() => onOpen(null)}
          className="flex-none rounded-u-control bg-u-accent-solid px-2.5 py-1 text-note font-semibold text-u-surface hover:bg-u-accent-solid-hover"
        >
          Set next action
        </button>
      </div>
    );
  }

  if (!first) {
    return (
      <div className={cn(frame, "border-dashed border-u-border-strong text-note text-u-text3", className)}>
        <Icon d={ICONS.calendar} size={14} className="flex-none" />
        <span className="min-w-0 flex-1 truncate">No next action for {firstName}</span>
        <button
          type="button"
          onClick={() => onOpen(null)}
          className="flex flex-none items-center gap-1 rounded-u-control px-2 py-1 text-note font-semibold text-u-accent hover:bg-u-accent-tint"
        >
          <Icon d={ICONS.plus} size={12} />
          Set next action
        </button>
      </div>
    );
  }

  const more = open.length - 1;
  const completingFirst = completion.completingId === first.id;

  return (
    <div
      role="group"
      aria-label="Next action"
      className={cn(frame, "border-u-border bg-u-sunken transition-colors hover:border-u-border-strong", className)}
    >
      <TaskCheckbox title={first.title} completing={completingFirst} onComplete={() => completion.complete(first)} />
      <Popover
        width={POPOVER_WIDTH}
        hasPopup="dialog"
        label={`Next action: ${first.title}. ${open.length} open — show all`}
        triggerClassName="flex h-full min-w-0 flex-1 items-center gap-2 rounded-u-control pe-1.5 text-start outline-offset-2"
        trigger={(isOpen) => (
          <span
            key={first.id}
            className="flex min-w-0 flex-1 items-center gap-2 motion-safe:animate-[fade-up_220ms_ease_both]"
          >
            <Icon d={taskKindOf(first.kind).icon} size={13} className="hidden flex-none text-u-text3 sm:block" />
            <span
              title={first.title}
              className={cn(
                "min-w-0 truncate text-body font-medium text-u-text",
                completingFirst && "text-u-text3 line-through",
              )}
            >
              {first.title}
            </span>
            <DueText dueAt={first.dueAt} className="flex-none font-mono text-meta font-semibold" />
            <span className="ms-auto flex flex-none items-center gap-1.5">
              <span className="hidden sm:contents">
                <Avatar id={first.assignee.userId} name={first.assignee.fullName ?? "Someone"} src={first.assignee.avatarUrl} size="xs" />
              </span>
              {more > 0 && (
                <span className="rounded-full bg-u-raised px-1.5 font-mono text-meta font-semibold text-u-text2">
                  +{more}
                </span>
              )}
              <Icon
                d={ICONS.chevronDown}
                size={13}
                className={cn("text-u-text3 transition-transform", isOpen && "rotate-180")}
              />
            </span>
          </span>
        )}
      >
        {(close) => (
          <NextActionsPanel
            tasks={tasks.data}
            projectId={projectId}
            candidateId={candidateId}
            completion={completion}
            onClose={close}
            onOpen={(taskId) => {
              close();
              onOpen(taskId);
            }}
            onShowNote={
              onShowNote &&
              ((noteId) => {
                close();
                onShowNote(noteId);
              })
            }
          />
        )}
      </Popover>
    </div>
  );
}

/**
 * Every task on one executive here, grouped as a reader triages them — overdue, today, upcoming — then
 * the latest done behind a disclosure. The body of the bar's popover and of a Positions card's.
 */
export function NextActionsPanel({
  tasks,
  projectId,
  candidateId,
  completion,
  onClose,
  onOpen,
  onShowNote,
}: {
  tasks: TasksResponse;
  projectId: string;
  candidateId: string;
  completion: TaskCompletion;
  onClose: () => void;
  onOpen: (taskId: string | null) => void;
  onShowNote?: (noteId: string) => void;
}) {
  useEscapeKey(true, onClose);
  const list = useRef<HTMLDivElement>(null);
  const [showDone, setShowDone] = useState(false);
  const [allDone, setAllDone] = useState(false);
  const everyDone = useQuery({
    queryKey: [...tasksApi.CANDIDATE_TASKS_KEY(projectId, candidateId), "all-done"],
    queryFn: ({ signal }) => tasksApi.getCandidateTasks(projectId, candidateId, signal, true),
    enabled: allDone,
  });
  const groups = groupByUrgency(tasks.open);
  const done = allDone && everyDone.data ? everyDone.data.done : tasks.done;

  useEffect(() => {
    list.current?.querySelector<HTMLElement>("[data-task-row]")?.focus();
  }, []);

  const handleKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    if (event.key !== "ArrowDown" && event.key !== "ArrowUp") return;
    const rows = Array.from(list.current?.querySelectorAll<HTMLElement>("[data-task-row]") ?? []);
    const at = rows.indexOf(document.activeElement as HTMLElement);
    const next = rows[at + (event.key === "ArrowDown" ? 1 : -1)];
    if (!next) return;
    event.preventDefault();
    next.focus();
  };

  return (
    <div role="dialog" aria-label="Next actions" onKeyDown={handleKeyDown}>
      <div className="flex items-center gap-2 px-2 pb-2 pt-1">
        <h3 className="text-body font-semibold text-u-text">Next actions</h3>
        <span className="font-mono text-meta text-u-text3">{tasks.open.length} open</span>
        <button
          type="button"
          onClick={() => onOpen(null)}
          className="ms-auto flex items-center gap-1 rounded-u-control px-2 py-1 text-note font-semibold text-u-accent hover:bg-u-accent-tint"
        >
          <Icon d={ICONS.plus} size={12} />
          Add
        </button>
      </div>

      <div ref={list} className="flex flex-col gap-1">
        {tasks.open.length === 0 && (
          <p className="px-2 py-3 text-note text-u-text3">Nothing open. Every executive in play should have a next action.</p>
        )}
        {groups.map((group) => (
          <section key={group.urgency} aria-label={group.label}>
            <h4
              className={cn(
                "type-micro-label px-2 pb-0.5 pt-2 font-mono",
                group.urgency === "overdue" ? "text-u-offlimits" : group.urgency === "today" ? "text-u-signal" : "text-u-text3",
              )}
            >
              {group.label} · {group.tasks.length}
            </h4>
            {group.tasks.map((task) => (
              <OpenTaskRow
                key={task.id}
                task={task}
                completing={completion.completingId === task.id}
                onComplete={() => completion.complete(task)}
                onOpen={() => onOpen(task.id)}
                onShowNote={onShowNote}
              />
            ))}
          </section>
        ))}
      </div>

      {tasks.doneCount > 0 && (
        <div className="mt-2 border-t border-u-border pt-1.5">
          <button
            type="button"
            aria-expanded={showDone}
            onClick={() => setShowDone((shown) => !shown)}
            className="flex w-full items-center gap-1.5 rounded-u-control px-2 py-1.5 text-start text-note font-medium text-u-text3 hover:text-u-text2"
          >
            <Icon d={ICONS.chevronRight} size={12} className={cn("transition-transform", showDone && "rotate-90")} />
            Done
            <span className="font-mono text-meta">{tasks.doneCount}</span>
          </button>
          {showDone && (
            <div className="flex flex-col">
              {done.map((task) => (
                <DoneTaskRow
                  key={task.id}
                  task={task}
                  reopening={completion.reopeningId === task.id}
                  onReopen={() => completion.reopen(task)}
                />
              ))}
              {tasks.doneCount > tasks.done.length && !allDone && (
                <button
                  type="button"
                  onClick={() => setAllDone(true)}
                  className="mx-2 mt-1 self-start text-note font-medium text-u-accent hover:underline"
                >
                  Show all {tasks.doneCount}
                </button>
              )}
              {allDone && everyDone.isPending && (
                <span className="mx-2 my-1 text-u-text3">
                  <Spinner />
                </span>
              )}
            </div>
          )}
        </div>
      )}
    </div>
  );
}

/** One executive's tasks on one position — the read every Next action surface shares, so they agree. */
export function useCandidateTasks(projectId: string, candidateId: string, enabled = true) {
  return useQuery({
    queryKey: [...tasksApi.CANDIDATE_TASKS_KEY(projectId, candidateId), "latest-done"],
    queryFn: ({ signal }) => tasksApi.getCandidateTasks(projectId, candidateId, signal),
    placeholderData: keepPreviousData,
    enabled,
  });
}

export interface TaskCompletion {
  completingId: string | null;
  reopeningId: string | null;
  complete: (task: TaskResponse) => void;
  reopen: (task: TaskResponse) => void;
}

/** Ticks a task done — with Undo on the toast — or opens it again. */
export function useTaskCompletion(projectId: string, onCompleted?: () => void): TaskCompletion {
  const toast = useToast();
  const tasksChanged = useTasksChanged();
  // Held past the write until the refetch drops the row, so a ticked task never flickers back unticked.
  const [completedId, setCompletedId] = useState<string | null>(null);
  const reopening = useMutation({
    mutationFn: (taskId: string) => tasksApi.updateTask(taskId, { status: "open" }),
    onMutate: () => setCompletedId(null),
    onSuccess: () => tasksChanged(projectId),
    onError: (error) => toast.error(messageFor(error)),
  });
  const completing = useMutation({
    mutationFn: (taskId: string) => tasksApi.updateTask(taskId, { status: "done" }),
    onSuccess: (task) => {
      setCompletedId(task.id);
      tasksChanged(projectId);
      onCompleted?.();
      toast.success(`Done · ${task.title}`, { undo: () => reopening.mutate(task.id) });
    },
    onError: (error) => toast.error(messageFor(error)),
  });
  return {
    completingId: completing.isPending ? completing.variables : completedId,
    reopeningId: reopening.isPending ? reopening.variables : null,
    complete: (task) => completing.mutate(task.id),
    reopen: (task) => reopening.mutate(task.id),
  };
}
