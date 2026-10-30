import { useSortable } from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { useState } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { useToast } from "../../../components/ui";
import { ActionMenu, MenuItem, MenuSeparator } from "../../../components/ui/ActionMenu";
import { FoldBody } from "../../../components/ui/FoldBody";
import { cn } from "../../../lib/cn";
import { messageFor } from "../../../lib/errorCodes";
import { liveStatuses } from "../../candidates/lib/pipelineStatus";
import * as pipelineApi from "../../workspace/api/pipelineApi";
import type { PipelineRetirePreview, PipelineStage, PipelineStageChange } from "../../workspace/api/types";
import { usePipelineCatalog } from "../../workspace/lib/usePipelineCatalog";
import { statusCountLabel, statusPreview } from "../lib/pipelineStageRows";
import { usePipelineRowChange, useRenameDraft } from "../lib/usePipelineRowEdits";
import { PipelineRetireDialog } from "./PipelineRetireDialog";
import { ColourDot, ColourSwatches, DragHandle, RenameActions, RenameForm } from "./PipelineRowControls";
import { PipelineStatusList } from "./PipelineStatusList";

/** One stage as a row that opens onto its statuses. A stage given no `onStep` is not reordered: a member's view. */
export function PipelineStageCard({
  stage,
  isAdmin,
  open,
  onToggle,
  onStep,
}: {
  stage: PipelineStage;
  isAdmin: boolean;
  open: boolean;
  onToggle: () => void;
  onStep?: (step: number) => void;
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
}: {
  stage: PipelineStage;
  isAdmin: boolean;
  open: boolean;
  shown: boolean;
  bodyId: string;
  sortable: ReturnType<typeof useSortable>;
  onToggle: () => void;
  onStep?: (step: number) => void;
}) {
  const rename = useRenameDraft(stage.label);
  const { change, isPending } = usePipelineRowChange(
    (patch: PipelineStageChange) => pipelineApi.updateStage(stage.id, patch),
    rename,
  );
  const statuses = liveStatuses(stage);
  // Nothing a card could stand at, so the board draws no column for it — an old Declined stage, say.
  const offTheBoard = statuses.length > 0 && statuses.every((status) => status.outOfProcess);
  const renameFormId = `rename-stage-${stage.id}`;

  const toast = useToast();
  const { catalog } = usePipelineCatalog();
  const [preview, setPreview] = useState<PipelineRetirePreview | null>(null);
  const [checking, setChecking] = useState(false);

  const handleRetire = async () => {
    if (statuses.length === 0) {
      change({ retired: true }, `${stage.label} retired`);
      return;
    }
    setChecking(true);
    try {
      setPreview(await pipelineApi.stageRetirePreview(stage.id));
    } catch (error) {
      toast.error(messageFor(error));
    } finally {
      setChecking(false);
    }
  };

  return (
    <div className={cn("flex flex-wrap items-center gap-2.5 px-4 py-2", shown && "border-b border-u-border")}>
      {onStep && <DragHandle label={`Reorder ${stage.label} stage`} sortable={sortable} onStep={onStep} />}
      <ColourDot colour={stage.colour} size="stage" />
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
            {offTheBoard && (
              <span
                title="Every status here is out of process, so the board draws no column for it"
                className="flex-none rounded-full bg-u-raised px-2 py-0.5 font-mono text-meta font-medium text-u-text3"
              >
                Not on the board
              </span>
            )}{" "}
            {/* Shrinks on a phone rather than running under the ⋯. */}
            <span className="min-w-0 truncate font-mono text-meta text-u-text3 sm:shrink-0">
              {statusCountLabel(statuses.length, statuses.filter((status) => status.outOfProcess).length)}
            </span>{" "}
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
        <span className="ms-auto flex flex-none items-center gap-1">
          <RenameActions formId={renameFormId} rename={rename} saving={isPending} />
          {!rename.renaming && (
            <ActionMenu label={`${stage.label} stage options`} width={220}>
              {(close) => (
                <>
                  <ColourSwatches
                    colour={stage.colour}
                    onChange={(colour) => {
                      close();
                      change({ colour });
                    }}
                  />
                  <MenuItem
                    icon={ICONS.pencil}
                    onSelect={() => {
                      close();
                      rename.begin();
                    }}
                  >
                    Rename
                  </MenuItem>
                  <MenuSeparator />
                  <MenuItem
                    icon={ICONS.archive}
                    tone="danger"
                    disabled={isPending || checking}
                    onSelect={() => {
                      close();
                      void handleRetire();
                    }}
                  >
                    Retire stage
                  </MenuItem>
                </>
              )}
            </ActionMenu>
          )}
        </span>
      )}
      <PipelineRetireDialog
        open={preview !== null}
        title={`Retire ${stage.label} and its ${statusCountLabel(statuses.length)}?`}
        confirmLabel="Retire stage and statuses"
        catalog={catalog}
        preview={preview}
        pending={isPending}
        onConfirm={() => {
          setPreview(null);
          change({ retired: true, retireStatuses: true }, `${stage.label} and its statuses retired`);
        }}
        onClose={() => setPreview(null)}
      />
    </div>
  );
}
