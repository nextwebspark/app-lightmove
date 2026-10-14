import { useState, type DragEvent, type KeyboardEvent } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { SelectionCheckbox } from "../../../components/ui/SelectionCheckbox";
import { cn } from "../../../lib/cn";
import { CandidateAvatar } from "../../candidates/components/CandidateAvatar";
import { pipelineColourClass, pipelineSwatchClass } from "../../candidates/lib/pipelineStatus";
import type { PipelineRow } from "../api/types";
import { rankMoveOf, shortDate } from "../lib/pipelineView";
import { VisibilityToggle } from "./PipelineControls";
import { ListNextAction } from "./PipelineNextAction";

const ROW_GRID =
  "grid grid-cols-[28px_20px_minmax(220px,2fr)_140px_170px_minmax(180px,1.2fr)_90px] items-center gap-2.5 px-5";

/**
 * The pipeline as a list (`Position.dc.html`, `pipeline`): one row per executive in stage order, dragged
 * by its handle to reorder within its stage, its client visibility set in place and its next action in the modal.
 */
export function PipelineList({
  projectId,
  rows,
  selectedIds,
  onToggleSelected,
  onToggleAll,
  onOpen,
  onMove,
  onToggleVisible,
  onOpenNextAction,
}: {
  projectId: string;
  rows: readonly PipelineRow[];
  selectedIds: ReadonlySet<string>;
  onToggleSelected: (candidateId: string) => void;
  onToggleAll: (candidateIds: string[]) => void;
  onOpen: (candidateId: string) => void;
  onMove: (move: NonNullable<ReturnType<typeof rankMoveOf>>, candidateId: string) => void;
  onToggleVisible: (row: PipelineRow) => void;
  onOpenNextAction: (row: PipelineRow) => void;
}) {
  const [draggingId, setDraggingId] = useState<string | null>(null);
  const ids = rows.map((row) => row.candidateId);
  const allSelected = ids.length > 0 && ids.every((id) => selectedIds.has(id));
  const someSelected = ids.some((id) => selectedIds.has(id));

  const handleDrop = (targetId: string) => (event: DragEvent<HTMLElement>) => {
    if (!draggingId) return;
    event.preventDefault();
    const moved = rankMoveOf(rows, draggingId, targetId);
    setDraggingId(null);
    if (moved) onMove(moved, draggingId);
  };

  const handleHandleKey = (row: PipelineRow) => (event: KeyboardEvent<HTMLElement>) => {
    const step = event.key === "ArrowUp" ? -1 : event.key === "ArrowDown" ? 1 : 0;
    if (step === 0) return;
    event.preventDefault();
    const neighbour = rows[rows.indexOf(row) + step];
    if (!neighbour) return;
    const moved = rankMoveOf(rows, row.candidateId, neighbour.candidateId);
    if (moved) onMove(moved, row.candidateId);
  };

  return (
    <div className="min-h-0 flex-1 overflow-auto">
      <div role="table" aria-label="Pipeline" className="min-w-[960px]">
        <div
          role="row"
          className={cn(ROW_GRID, "sticky top-0 z-[2] border-b border-u-border bg-u-surface py-2.5")}
        >
          <span role="columnheader">
            <SelectionCheckbox
              checked={allSelected}
              indeterminate={someSelected && !allSelected}
              label="Select all"
              onChange={() => onToggleAll(ids)}
            />
          </span>
          <span role="columnheader" />
          <ColumnHead>Executive</ColumnHead>
          <ColumnHead>Stage</ColumnHead>
          <ColumnHead>Dates</ColumnHead>
          <ColumnHead>Next action</ColumnHead>
          <ColumnHead>Visible</ColumnHead>
        </div>
        {rows.map((row) => (
          <div
            key={row.candidateId}
            role="row"
            aria-label={row.fullName}
            draggable
            onDragStart={(event) => {
              if (event.dataTransfer) {
                event.dataTransfer.effectAllowed = "move";
                event.dataTransfer.setData("text/plain", row.candidateId);
              }
              setDraggingId(row.candidateId);
            }}
            onDragOver={(event) => draggingId && event.preventDefault()}
            onDrop={handleDrop(row.candidateId)}
            onDragEnd={() => setDraggingId(null)}
            onClick={() => onOpen(row.candidateId)}
            className={cn(
              ROW_GRID,
              "cursor-pointer border-b border-u-border py-3 hover:bg-u-raised",
              draggingId === row.candidateId && "opacity-40",
            )}
          >
            <span role="cell" onClick={(event) => event.stopPropagation()}>
              <SelectionCheckbox
                checked={selectedIds.has(row.candidateId)}
                label={`Select ${row.fullName}`}
                onChange={() => onToggleSelected(row.candidateId)}
              />
            </span>
            <span role="cell">
              <button
                type="button"
                aria-label={`Reorder ${row.fullName}`}
                title="Drag to reorder, or focus and use the arrow keys"
                onClick={(event) => event.stopPropagation()}
                onKeyDown={handleHandleKey(row)}
                className="grid cursor-grab place-items-center text-u-text3 hover:text-u-text2"
              >
                <Icon d={ICONS.dragHandle} size={14} />
              </button>
            </span>
            <span role="cell" className="flex min-w-0 items-center gap-2.5">
              <CandidateAvatar
                projectId={projectId}
                candidate={{ id: row.candidateId, fullName: row.fullName, enrichedAt: row.enrichedAt ?? null }}
              />
              <span className="min-w-0">
                <button
                  type="button"
                  onClick={(event) => {
                    event.stopPropagation();
                    onOpen(row.candidateId);
                  }}
                  className="block max-w-full truncate text-start text-body font-semibold text-u-text hover:underline"
                >
                  {row.fullName}
                </button>
                <span className="mt-0.5 block truncate text-meta text-u-text3">
                  {[row.title, row.companyName].filter(Boolean).join(" · ") || "—"}
                </span>
              </span>
            </span>
            <span role="cell" className="flex min-w-0 flex-col items-start gap-1">
              <StageBadge row={row} />
              <span title="Status" className="max-w-full truncate ps-0.5 text-meta text-u-text3">
                {row.statusLabel}
              </span>
            </span>
            <span role="cell" className="font-mono text-meta text-u-text3">
              Added {shortDate(row.addedAt)}
              <br />
              {row.pipelineStage.label} {shortDate(row.statusChangedAt)}
            </span>
            <span role="cell" className="min-w-0">
              <ListNextAction row={row} onOpen={onOpenNextAction} />
            </span>
            <span role="cell">
              <VisibilityToggle visible={row.clientVisible} name={row.fullName} onToggle={() => onToggleVisible(row)} />
            </span>
          </div>
        ))}
      </div>
    </div>
  );
}

/** The stage a row sits in, in the stage's own colour; the first stage drawn as an outline. */
export function StageBadge({ row }: { row: PipelineRow }) {
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-meta font-semibold",
        pipelineColourClass(row.pipelineStage.colour),
      )}
    >
      <span aria-hidden className={cn("size-1.5 flex-none rounded-full", pipelineSwatchClass(row.pipelineStage.colour))} />
      {row.pipelineStage.label}
    </span>
  );
}

function ColumnHead({ children }: { children: string }) {
  return (
    <span role="columnheader" className="type-summary-label text-u-text3">
      {children}
    </span>
  );
}
