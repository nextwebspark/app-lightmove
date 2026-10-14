import type { KeyboardEvent } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Avatar } from "../../../components/ui/Avatar";
import { cn } from "../../../lib/cn";
import { formatInstantDate } from "../../../lib/format";
import { useAuth } from "../../auth/AuthProvider";
import type { TaskResponse } from "../api/types";
import { fromNoteLabel } from "../lib/linkedNote";
import { taskKindOf } from "../lib/taskKinds";
import { AutoPill, DueText } from "./TaskBits";

/** The round tick a task is completed with — the same mark on the bar, the popover and every list. */
export function TaskCheckbox({
  title,
  completing,
  onComplete,
  className,
}: {
  title: string;
  completing: boolean;
  onComplete: () => void;
  className?: string;
}) {
  return (
    <button
      type="button"
      role="checkbox"
      aria-checked={completing}
      aria-label={`Mark ${title} done`}
      disabled={completing}
      onClick={onComplete}
      className={cn(
        "grid size-[18px] flex-none place-items-center rounded-full border-[1.5px] transition-colors",
        completing
          ? "border-u-direct bg-u-direct text-u-surface"
          : "border-u-border-strong text-transparent hover:border-u-direct hover:text-u-direct",
        className,
      )}
    >
      <Icon d={ICONS.check} size={11} />
    </button>
  );
}

/**
 * One open task: tick it done, or open it to edit. Space on the row completes it and Enter opens it,
 * so a keyboard can work down a list without reaching for the tick.
 */
export function OpenTaskRow({
  task,
  completing,
  onComplete,
  onOpen,
  onShowNote,
}: {
  task: TaskResponse;
  completing: boolean;
  onComplete: () => void;
  onOpen: () => void;
  onShowNote?: (noteId: string) => void;
}) {
  const { user } = useAuth();
  const assigneeName = task.assignee.fullName ?? "Someone";
  const { note } = task;
  const handleKeyDown = (event: KeyboardEvent<HTMLButtonElement>) => {
    if (event.key !== " " || completing) return;
    event.preventDefault();
    onComplete();
  };
  return (
    <div className="group flex items-start gap-2.5 rounded-u-control px-2 py-2 hover:bg-u-sunken">
      <TaskCheckbox title={task.title} completing={completing} onComplete={onComplete} className="mt-px" />
      <div className="min-w-0 flex-1">
        <button
          type="button"
          data-task-row
          onClick={onOpen}
          onKeyDown={handleKeyDown}
          className="block w-full min-w-0 rounded-sm text-start"
        >
          <span className="flex min-w-0 items-center gap-1.5">
            <Icon d={taskKindOf(task.kind).icon} size={13} className="flex-none text-u-text3" />
            <span className={cn("truncate text-body font-medium text-u-text", completing && "text-u-text3 line-through")}>
              {task.title}
            </span>
            {task.source === "rule" && <AutoPill />}
            <span className="ms-auto flex-none text-meta font-medium text-u-text3 opacity-0 transition-opacity group-hover:opacity-100 group-focus-within:opacity-100">
              Edit
            </span>
          </span>
          {task.detail && <span className="mt-0.5 block truncate text-note text-u-text3">{task.detail}</span>}
          <span className="mt-1 flex items-center gap-1.5">
            <DueText dueAt={task.dueAt} className="font-mono text-meta font-semibold" />
            {task.dueAt && <span className="text-meta text-u-text3">·</span>}
            <Avatar id={task.assignee.userId} name={assigneeName} src={task.assignee.avatarUrl} size="xs" />
            <span className="text-meta text-u-text2">{task.assignee.userId === user?.id ? "Me" : assigneeName}</span>
          </span>
        </button>
        {note && onShowNote && (
          <button
            type="button"
            onClick={() => onShowNote(note.id)}
            className="mt-1 inline-flex items-center gap-1.5 text-meta font-medium text-u-accent hover:underline"
          >
            <Icon d={ICONS.fileText} size={12} className="flex-none" />
            {fromNoteLabel(note)}
          </button>
        )}
      </div>
    </div>
  );
}

/** A finished task, struck through, with who closed it — and Reopen for one ticked by mistake. */
export function DoneTaskRow({
  task,
  reopening,
  onReopen,
}: {
  task: TaskResponse;
  reopening?: boolean;
  onReopen?: () => void;
}) {
  return (
    <div className="group flex items-center gap-2.5 rounded-u-control px-2 py-1.5 text-note text-u-text3 hover:bg-u-sunken">
      <Icon d={ICONS.check} size={14} className="flex-none text-u-direct" />
      <span className="min-w-0 flex-1 truncate line-through">{task.title}</span>
      <span className="flex-none font-mono text-meta">
        {formatInstantDate(task.completedAt)}
        {task.completedBy?.fullName && ` · ${task.completedBy.fullName}`}
      </span>
      {onReopen && (
        <button
          type="button"
          onClick={onReopen}
          disabled={reopening}
          aria-label={`Reopen ${task.title}`}
          className="flex-none text-meta font-medium text-u-accent opacity-0 transition-opacity hover:underline focus:opacity-100 group-hover:opacity-100 disabled:opacity-50"
        >
          Reopen
        </button>
      )}
    </div>
  );
}
