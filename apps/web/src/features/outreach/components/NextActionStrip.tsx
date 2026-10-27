import { Icon, ICONS } from "../../../components/layout/Icon";
import { Button } from "../../../components/ui";
import { cn } from "../../../lib/cn";
import type { TaskResponse } from "../../tasks/api/types";
import { dueChipOf } from "../lib/conversationVocabulary";

/**
 * "Required next action" over the conversation: the executive's task the queue put them in for — a reply to write, a
 * follow-up, a bounce to fix — with when it is due, Mark handled, and the rest of their open tasks one click away.
 */
export function NextActionStrip({
  task,
  otherOpenCount,
  isCompleting,
  onComplete,
  onOpenTasks,
}: {
  task: TaskResponse;
  otherOpenCount: number;
  isCompleting: boolean;
  onComplete: () => void;
  onOpenTasks: () => void;
}) {
  const due = dueChipOf(task.dueAt);
  return (
    <div className="border-b border-u-border bg-u-accent-tint px-5 py-2.5">
      <div className="flex items-center gap-2">
        <span className="inline-flex items-center gap-1.5 text-meta font-semibold text-u-accent">
          <Icon d={ICONS.arrowLeft} size={12} />
          Required next action
        </span>
        {due && (
          <span className={cn("ms-auto font-mono text-eyebrow", due.isLate ? "text-u-offlimits" : "text-u-text3")}>
            {due.text}
          </span>
        )}
      </div>
      <div className="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1.5">
        <span className="min-w-0 flex-1 text-body text-u-text">{task.title}</span>
        <button type="button" className="text-note text-u-accent hover:underline" onClick={onOpenTasks}>
          {otherOpenCount > 0 ? `${otherOpenCount} more open` : "Tasks"}
        </button>
        <Button variant="secondary" size="xs" loading={isCompleting} onClick={onComplete}>
          Mark handled
        </Button>
      </div>
    </div>
  );
}
