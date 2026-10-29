import { Avatar } from "../../../components/ui/Avatar";
import { cn } from "../../../lib/cn";
import { DueText } from "../../tasks/components/TaskBits";
import type { PipelineRow } from "../api/types";

/** The board card's Next action: the earliest open task, when it is due and whose it is. Opens the modal. */
export function BoardNextAction({ row, onOpen }: { row: PipelineRow; onOpen: (row: PipelineRow) => void }) {
  const next = row.nextAction;
  return (
    <button
      type="button"
      title={next ? "Edit next action" : "Set next action"}
      onClick={(event) => {
        event.stopPropagation();
        onOpen(row);
      }}
      className="-m-1 flex min-w-0 flex-col gap-1 rounded-u-control p-1 text-start hover:bg-u-border"
    >
      <span className={cn("max-w-full truncate text-note font-medium", next ? "text-u-text" : "text-u-text3")}>
        {next ? next.text : "No next action · Set one"}
      </span>
      {next && (
        <span className="flex w-full min-w-0 items-center gap-1.5">
          <DueText dueAt={next.dueAt} withIcon plainClassName="text-u-text2" className="text-meta" />
          <span className="flex-1" />
          {next.openCount > 1 && (
            <span className="text-eyebrow font-medium text-u-text3">+{next.openCount - 1}</span>
          )}
          <Assignee next={next} />
        </span>
      )}
    </button>
  );
}

function Assignee({ next }: { next: NonNullable<PipelineRow["nextAction"]> }) {
  const name = next.assignee.fullName ?? "Someone";
  return <Avatar id={next.assignee.userId} name={name} src={next.assignee.avatarUrl} size="sm" title={`Assigned to ${name}`} />;
}
