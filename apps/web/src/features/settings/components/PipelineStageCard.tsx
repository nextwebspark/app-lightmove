import { useSortable } from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Button } from "../../../components/ui";
import { FoldBody } from "../../../components/ui/FoldBody";
import { cn } from "../../../lib/cn";
import { liveStatuses } from "../../candidates/lib/pipelineStatus";
import * as pipelineApi from "../../workspace/api/pipelineApi";
import type { PipelineStage, PipelineStageChange } from "../../workspace/api/types";
import { statusCountLabel, statusPreview } from "../lib/pipelineStageRows";
import { usePipelineRowChange, useRenameDraft } from "../lib/usePipelineRowEdits";
import { ColourDot, DragHandle, RenameActions, RenameForm } from "./PipelineRowControls";
import { PipelineStatusList } from "./PipelineStatusList";

/**
 * One stage as a row that opens onto its statuses. A stage given no `onStep` is not reordered: the closed
 * stage, which stays last, and every stage for a member who is not an admin.
 */
export function PipelineStageCard({
  stage,
  isAdmin,
  open,
  onToggle,
  onStep,
  onRetireBlocked,
}: {
  stage: PipelineStage;
  isAdmin: boolean;
  open: boolean;
  onToggle: () => void;
  onStep?: (step: number) => void;
  onRetireBlocked: () => void;
}) {
  const sortable = useSortable({ id: stage.id, disabled: !onStep });
  // Folded while it is carried, so it moves as one line; `open` is still what the toggle reports.
  const shown = open && !sortable.isDragging;
  const bodyId = `stage-${stage.id}-statuses`;

  return (
    <section
      ref={sortable.setNodeRef}
      style={{ transform: CSS.Translate.toString(sortable.transform), transition: sortable.transition }}
      aria-label={stage.label}
      className={cn(
        "overflow-hidden rounded-u-card border border-u-border bg-u-surface",
        sortable.isDragging && "relative z-10 shadow-u-e3",
      )}
    >
      <StageHeader
        stage={stage}
        isAdmin={isAdmin}
        open={open}
        shown={shown}
        bodyId={bodyId}
        sortable={sortable}
        onToggle={onToggle}
        onStep={onStep}
        onRetireBlocked={onRetireBlocked}
      />
      <FoldBody id={bodyId} open={shown} animated={!sortable.isDragging}>
        <PipelineStatusList stage={stage} isAdmin={isAdmin} />
      </FoldBody>
    </section>
  );
}

function StageHeader({
  stage,
  isAdmin,
  open,
  shown,
  bodyId,
  sortable,
  onToggle,
  onStep,
  onRetireBlocked,
}: {
  stage: PipelineStage;
  isAdmin: boolean;
  open: boolean;
  shown: boolean;
  bodyId: string;
  sortable: ReturnType<typeof useSortable>;
  onToggle: () => void;
  onStep?: (step: number) => void;
  onRetireBlocked: () => void;
}) {
  const rename = useRenameDraft(stage.label);
  const { change, isPending } = usePipelineRowChange(
    (patch: PipelineStageChange) => pipelineApi.updateStage(stage.id, patch),
    rename,
  );
  const statuses = liveStatuses(stage);
  const isClosed = stage.kind === "closed";
  const renameFormId = `rename-stage-${stage.id}`;

  const handleRetire = () => {
    if (statuses.length > 0) {
      onRetireBlocked();
      return;
    }
    change({ retired: true }, `${stage.label} retired`);
  };

  return (
    <div className={cn("flex flex-wrap items-center gap-2.5 px-4 py-2", shown && "border-b border-u-border")}>
      {onStep && <DragHandle label={`Reorder ${stage.label} stage`} sortable={sortable} onStep={onStep} />}
      {isAdmin && isClosed && (
        <span title="The closed stage stays last" className="grid w-3.5 place-items-center text-u-text3">
          <Icon d={ICONS.lock} size={12} />
        </span>
      )}
      <ColourDot
        colour={stage.colour}
        name={`${stage.label} stage`}
        size="stage"
        editable={isAdmin}
        onChange={(colour) => change({ colour })}
      />
      {/* Named by what it shows, the spaces between its parts included; with the name being retyped it shows
          only the chevron, so it is named outright. */}
      <button
        type="button"
        aria-expanded={open}
        aria-controls={bodyId}
        aria-label={rename.renaming ? `${stage.label} stage` : undefined}
        title={open ? "Hide statuses" : "Show statuses"}
        onClick={onToggle}
        className={cn(
          "flex min-w-0 items-center gap-2.5 rounded-u-control px-1.5 py-1.5 text-left transition hover:bg-u-raised",
          !rename.renaming && "flex-1",
        )}
      >
        <Icon
          d={ICONS.chevronDown}
          size={15}
          className={cn(
            "flex-none text-u-text2 transition-transform duration-200 motion-reduce:transition-none",
            !shown && "-rotate-90",
          )}
        />
        {!rename.renaming && (
          <>
            <span className="flex-none text-body font-semibold text-u-text">{stage.label}</span>{" "}
            <span
              className={cn(
                "flex-none rounded-full px-2 py-0.5 font-mono text-meta font-medium",
                isClosed ? "bg-u-raised text-u-text3" : "bg-u-accent-tint text-u-accent",
              )}
            >
              {isClosed ? "Closed" : "Active"}
            </span>{" "}
            <span className="flex-none font-mono text-meta text-u-text3">{statusCountLabel(statuses.length)}</span>{" "}
            {!shown && (
              <span className="min-w-0 truncate font-mono text-meta text-u-text3">
                {statusPreview(statuses.map((status) => status.label))}
              </span>
            )}
          </>
        )}
      </button>
      {rename.renaming && (
        <RenameForm
          formId={renameFormId}
          label="Stage name"
          rename={rename}
          onSave={(label) => change({ label }, "Renamed on every position")}
        />
      )}
      {isAdmin && (
        <span className="ms-auto flex flex-none gap-1">
          <RenameActions formId={renameFormId} rename={rename} saving={isPending} />
          {!isClosed && (
            <Button
              variant="ghost"
              size="xs"
              title="Retire this stage"
              aria-label={`Retire ${stage.label} stage`}
              disabled={isPending}
              onClick={handleRetire}
            >
              <Icon d={ICONS.archive} size={14} />
            </Button>
          )}
        </span>
      )}
    </div>
  );
}
