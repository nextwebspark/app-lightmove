import { DueText } from "../../tasks/components/TaskBits";
import type { PipelineRow } from "../api/types";

/** The board card's Next action: the earliest open task and when it is due. Opens the modal. */
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
      className="-m-1 flex min-h-[42px] min-w-0 flex-col gap-1 rounded-u-control p-1 text-start hover:bg-u-border"
    >
      <span className="max-w-full truncate text-note font-semibold text-u-text">{next ? next.text : "No next action"}</span>
      {next && <DueText dueAt={next.dueAt} withIcon plainClassName="text-u-text2" className="text-meta" />}
    </button>
  );
}
