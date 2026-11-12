import type { HTMLAttributes } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import type { PipelineStatus } from "../../workspace/api/types";

/** The band's head across every column: the grip that resizes it, and its title. */
export function OutOfProcessBandHeader({ gripProps }: { gripProps: HTMLAttributes<HTMLDivElement> }) {
  return (
    <div className="col-span-full row-start-2 flex flex-col">
      <div
        role="separator"
        aria-orientation="horizontal"
        aria-label="Resize Out of process"
        title="Drag to resize · double-click to reset"
        tabIndex={0}
        {...gripProps}
        className="group relative flex cursor-row-resize touch-none items-center justify-center px-1 pb-1.5 pt-2 rounded-u-control focus-visible:outline-2 focus-visible:outline-u-accent"
      >
        <span className="h-px flex-1 bg-u-border" />
        <span className="absolute h-1 w-9 rounded-full bg-u-border-strong group-hover:bg-u-text3" />
      </div>
      <div className="flex items-center gap-2 px-1 pb-2.5 pt-0.5">
        <Icon d={ICONS.eyeOff} size={14} className="flex-none text-u-text2" />
        <span className="whitespace-nowrap text-body font-semibold text-u-text">Out of process</span>
      </div>
    </div>
  );
}

/** Which of a stage's ways out a card dropped in its cell takes, where the stage offers more than one. */
export function OutOfProcessChooser({
  name,
  choices,
  onChoose,
  onCancel,
}: {
  name: string;
  choices: readonly PipelineStatus[];
  onChoose: (status: PipelineStatus) => void;
  onCancel: () => void;
}) {
  return (
    <div
      role="group"
      aria-label={`Mark ${name} out of process`}
      onClick={(event) => event.stopPropagation()}
      className="flex flex-col gap-1 rounded-u-chip border border-u-border-strong bg-u-surface p-2"
    >
      <span className="px-1 text-meta text-u-text2">Mark {name} as</span>
      {choices.map((status) => (
        <button
          key={status.id}
          type="button"
          onClick={() => onChoose(status)}
          className="rounded-u-control px-2 py-1.5 text-start text-meta font-medium text-u-text hover:bg-u-raised"
        >
          {status.label}
        </button>
      ))}
      <button type="button" onClick={onCancel} className="self-start px-2 py-1 text-meta text-u-text3 hover:text-u-text">
        Cancel
      </button>
    </div>
  );
}
