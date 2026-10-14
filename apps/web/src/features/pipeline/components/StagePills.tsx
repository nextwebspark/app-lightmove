import { cn } from "../../../lib/cn";
import { pipelineColourClass, pipelineSwatchClass } from "../../candidates/lib/pipelineStatus";
import type { PipelineStageCount } from "../api/types";

/** What each seeded stage is for, as the mockup captions it; a stage the workspace added has none. */
const STAGE_CAPTIONS: Record<string, string> = {
  identified: "Research & review",
  contacted: "Outreach & follow-up",
  interested: "Explore the opportunity",
  qualified: "Assess fit & readiness",
  shortlisted: "Ready for client review",
};

/**
 * The pipeline's stages across the top of the list, each with its count, after an All pill and with the
 * closed stage last as Inactive. A pill narrows the list to its stage; All, or the same pill again, shows
 * everyone.
 */
export function StagePills({
  stageCounts,
  selected,
  onSelect,
}: {
  stageCounts: readonly PipelineStageCount[];
  selected: string;
  onSelect: (stageKey: string) => void;
}) {
  const active = stageCounts.filter((stage) => stage.kind === "active");
  const closed = stageCounts.filter((stage) => stage.kind === "closed");
  const total = stageCounts.reduce((sum, stage) => sum + stage.count, 0);
  const allPressed = selected === "";

  return (
    <div
      role="group"
      aria-label="Stages"
      className="flex flex-none gap-2 overflow-x-auto border-b border-u-border px-5 pt-3.5 [scrollbar-width:none]"
    >
      <button
        type="button"
        aria-pressed={allPressed}
        onClick={() => onSelect("")}
        className={cn(
          "flex flex-none items-center gap-1.5 self-center rounded-u-control px-2.5 py-1.5 transition",
          allPressed ? "bg-u-raised ring-1 ring-u-border-strong" : "hover:bg-u-raised",
        )}
      >
        <span className={cn("whitespace-nowrap text-note font-semibold", allPressed ? "text-u-text" : "text-u-text2")}>
          All
        </span>
        <Count value={total} swatch={allPressed ? "bg-u-text" : null} />
      </button>
      {active.map((stage) => (
        <Pill
          key={stage.key}
          stage={stage}
          label={stage.label}
          caption={STAGE_CAPTIONS[stage.key]}
          pressed={selected === stage.key}
          onSelect={onSelect}
          className="min-w-[180px] flex-1"
        />
      ))}
      {closed.length > 0 && <span aria-hidden className="my-2 w-px flex-none bg-u-border" />}
      {closed.map((stage) => (
        <Pill
          key={stage.key}
          stage={stage}
          label="Inactive"
          caption={`${stage.label} · ruled out`}
          pressed={selected === stage.key}
          onSelect={onSelect}
          className="min-w-40 flex-none"
        />
      ))}
    </div>
  );
}

function Pill({
  stage,
  label,
  caption,
  pressed,
  onSelect,
  className,
}: {
  stage: PipelineStageCount;
  label: string;
  caption?: string;
  pressed: boolean;
  onSelect: (stageKey: string) => void;
  className?: string;
}) {
  return (
    <button
      type="button"
      aria-pressed={pressed}
      onClick={() => onSelect(pressed ? "" : stage.key)}
      className={cn(
        "relative flex flex-col items-start gap-1 overflow-hidden rounded-t-u-control px-3.5 pb-2.5 pt-2 text-start transition",
        pressed ? pipelineColourClass(stage.colour) : "hover:bg-u-raised",
        className,
      )}
    >
      <span className="flex w-full min-w-0 items-center gap-1.5">
        <span aria-hidden className={cn("size-[7px] flex-none rounded-full", pipelineSwatchClass(stage.colour))} />
        <span className={cn("whitespace-nowrap text-note font-semibold", pressed ? "text-current" : "text-u-text2")}>
          {label}
        </span>
        <Count value={stage.count} swatch={pressed ? pipelineSwatchClass(stage.colour) : null} />
      </span>
      {caption && (
        <span className={cn("w-full truncate text-eyebrow", pressed ? "text-u-text2" : "text-u-text3")}>{caption}</span>
      )}
      <span
        aria-hidden
        className={cn(
          "absolute inset-x-0 bottom-0 rounded-sm",
          pipelineSwatchClass(stage.colour),
          pressed ? "h-[3px] opacity-100" : "h-0.5 opacity-35",
        )}
      />
    </button>
  );
}

/** The count chip, filled in the stage's own colour while its pill is the one pressed. */
function Count({ value, swatch }: { value: number; swatch: string | null }) {
  return (
    <span
      className={cn(
        "grid h-[18px] min-w-[18px] flex-none place-items-center rounded-sm px-1 font-mono text-eyebrow font-medium",
        swatch ? cn(swatch, "text-u-bg") : "bg-u-border text-u-text3",
      )}
    >
      {value}
    </span>
  );
}
