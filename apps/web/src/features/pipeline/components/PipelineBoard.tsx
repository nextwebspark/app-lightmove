import { useRef, useState, type DragEvent } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { cn } from "../../../lib/cn";
import type { CandidatePipelineStage } from "../../candidates/api/types";
import { defaultStatusOf, pipelineSwatchClass } from "../../candidates/lib/pipelineStatus";
import type { PipelineCatalog, PipelineStatus } from "../../workspace/api/types";
import type { PipelineRow, PipelineStageCount } from "../api/types";
import {
  boardPlacementOf,
  outOfProcessCellsOf,
  type BoardPlacement,
  type BoardTarget,
  type OutOfProcessCell,
} from "../lib/pipelineView";
import { stageMarkOf } from "../lib/stageIcons";
import { useBandHeight } from "../lib/useBandHeight";
import { OutOfProcessBandHeader, OutOfProcessChooser } from "./OutOfProcessBand";
import { OutOfProcessCard, PipelineCard, type CardDrag } from "./PipelineCard";
import { PipelineCardActions, type CardActionKind, type CardActionsUnavailable } from "./PipelineCardActions";

/** The drop the pointer is over: a column and the card it would land before, or a cell of the band. */
interface DropSlot {
  key: string;
  beforeId: string | null;
  lands: boolean;
}

/**
 * The pipeline as a board (`Position.dc.html`): one column per active stage holding those in process, and under
 * them the Out of process band, a cell under each stage holding whoever left the search at one of its ways out.
 * A card dropped in its own column is reordered; on another column — or a card out of process on any column — it
 * takes that stage's default status and lands where it was dropped; on a cell, that stage's way out, asked about
 * where there are several. A stage with no default to give accepts only its own cards, a cell with no way out none.
 */
export function PipelineBoard({
  projectId,
  rows,
  stageCounts,
  catalog,
  selectedIds,
  actionsUnavailable,
  onToggleSelected,
  onOpen,
  onMove,
  onToggleVisible,
  onOpenNextAction,
  onAction,
}: {
  projectId: string;
  rows: readonly PipelineRow[];
  stageCounts: readonly PipelineStageCount[];
  catalog: PipelineCatalog;
  selectedIds: ReadonlySet<string>;
  actionsUnavailable: CardActionsUnavailable;
  onToggleSelected: (candidateId: string) => void;
  onOpen: (candidateId: string) => void;
  onMove: (placement: BoardPlacement) => void;
  onToggleVisible: (row: PipelineRow) => void;
  onOpenNextAction: (row: PipelineRow) => void;
  onAction: (kind: CardActionKind, row: PipelineRow) => void;
}) {
  const gridRef = useRef<HTMLDivElement>(null);
  const band = useBandHeight(gridRef);
  const [dragging, setDragging] = useState<PipelineRow | null>(null);
  const [over, setOver] = useState<DropSlot | null>(null);
  const [landed, setLanded] = useState<{ basis: readonly PipelineRow[]; rows: PipelineRow[] } | null>(null);
  const [choosing, setChoosing] = useState<{
    row: PipelineRow;
    stage: CandidatePipelineStage;
    choices: PipelineStatus[];
  } | null>(null);
  if (landed && landed.basis !== rows) setLanded(null);
  const shown = landed?.basis === rows ? landed.rows : rows;

  const columns = stageCounts;
  const cells = outOfProcessCellsOf(shown, columns.map(stageOf), catalog);
  const hasBand = cells.some((cell) => cell.waysOut.length > 0 || cell.rows.length > 0);

  const defaultOf = (stageKey: string) => {
    const stage = catalog.stages.find((candidate) => candidate.key === stageKey);
    return stage ? defaultStatusOf(stage) : null;
  };

  const columnTarget = (stage: PipelineStageCount, beforeId: string | null): BoardTarget | null => {
    if (!dragging) return null;
    if (dragging.pipelineStage.key === stage.key && !dragging.outOfProcess) {
      return { stage: stageOf(stage), status: null, beforeId };
    }
    const fallback = defaultOf(stage.key);
    return fallback ? { stage: stageOf(stage), status: fallback, beforeId } : null;
  };

  const land = (placement: BoardPlacement | null) => {
    if (!placement) return;
    setLanded({ basis: rows, rows: placement.rows });
    onMove(placement);
  };

  /**
   * The browser slides a refused drop's ghost back to where it started, which reads as the card bouncing,
   * so every slot that takes the card accepts the drop, its own included; only a real move is written.
   */
  const dropTarget = (key: string, targetAt: (event: DragEvent<HTMLElement>) => BoardTarget | null) => ({
    onDragOver: (event: DragEvent<HTMLElement>) => {
      if (!dragging) return;
      const target = targetAt(event);
      if (!target) return;
      event.preventDefault();
      if (over?.key === key && over.beforeId === target.beforeId) return;
      setOver({ key, beforeId: target.beforeId, lands: boardPlacementOf(shown, dragging.candidateId, target) !== null });
    },
    onDragLeave: (event: DragEvent<HTMLElement>) => {
      if (event.currentTarget.contains(event.relatedTarget as Node | null)) return;
      setOver((current) => (current?.key === key ? null : current));
    },
    onDrop: (event: DragEvent<HTMLElement>) => {
      const target = targetAt(event);
      if (!target) return;
      event.preventDefault();
      const placement = dragging ? boardPlacementOf(shown, dragging.candidateId, target) : null;
      setDragging(null);
      setOver(null);
      land(placement);
    },
  });

  const giveWayOut = (row: PipelineRow, stage: CandidatePipelineStage, status: PipelineStatus) =>
    land(boardPlacementOf(shown, row.candidateId, { stage, status, beforeId: null }));

  /** Dropped in a cell of the band: the stage's one way out is taken at once, several are asked about. */
  const cellDrop = (cell: OutOfProcessCell) => {
    const key = `band:${cell.stage.key}`;
    const isOwn = (row: PipelineRow) => row.outOfProcess && row.pipelineStage.key === cell.stage.key;
    const takes = (row: PipelineRow) => isOwn(row) || cell.waysOut.length > 0;
    return {
      onDragOver: (event: DragEvent<HTMLElement>) => {
        if (!dragging || !takes(dragging)) return;
        event.preventDefault();
        if (over?.key !== key) setOver({ key, beforeId: null, lands: !isOwn(dragging) });
      },
      onDragLeave: (event: DragEvent<HTMLElement>) => {
        if (event.currentTarget.contains(event.relatedTarget as Node | null)) return;
        setOver((current) => (current?.key === key ? null : current));
      },
      onDrop: (event: DragEvent<HTMLElement>) => {
        const row = dragging;
        if (!row || !takes(row)) return;
        event.preventDefault();
        setDragging(null);
        setOver(null);
        if (isOwn(row)) return;
        if (cell.waysOut.length === 1) giveWayOut(row, cell.stage, cell.waysOut[0]);
        else setChoosing({ row, stage: cell.stage, choices: cell.waysOut });
      },
    };
  };

  const cardDrag = (row: PipelineRow): CardDrag => ({
    draggable: true,
    onDragStart: (event: DragEvent<HTMLElement>) => {
      if (event.dataTransfer) {
        event.dataTransfer.effectAllowed = "move";
        event.dataTransfer.setData("text/plain", row.candidateId);
      }
      setChoosing(null);
      setDragging(row);
    },
    onDragEnd: () => {
      setDragging(null);
      setOver(null);
    },
  });

  const actionsOf = (row: PipelineRow) => (
    <PipelineCardActions row={row} unavailable={actionsUnavailable} onAction={onAction} />
  );

  return (
    <div className="flex min-h-0 flex-1 flex-col px-2 pb-2 pt-3">
      <div
        ref={gridRef}
        style={{
          gridTemplateColumns: `repeat(${cells.length}, minmax(220px, 1fr))`,
          gridTemplateRows: hasBand
            ? `minmax(0, 1fr) auto ${band.height === null ? "auto" : `${band.height}px`}`
            : "minmax(0, 1fr)",
        }}
        className="grid min-h-0 flex-1 gap-x-2 overflow-x-auto [scrollbar-width:none]"
      >
        {columns.map((stage, index) => {
          const fallback = defaultOf(stage.key);
          const cards = shown.filter((row) => row.pipelineStage.key === stage.key && !row.outOfProcess);
          const slot = dragging && over?.key === stage.key && over.lands ? over : null;
          const restages = slot !== null && dragging?.pipelineStage.key !== stage.key;
          const lineBefore = (candidateId: string) => slot?.beforeId === candidateId;
          const lineAfterLast = slot !== null && slot.beforeId === null && cards.length > 0;
          const mark = stageMarkOf(stage.key);
          return (
            <section
              key={stage.key}
              aria-label={stage.label}
              data-drop-target={fallback ? "true" : "false"}
              style={{ gridColumn: index + 1, gridRow: 1 }}
              {...dropTarget(stage.key, (event) => columnTarget(stage, beforeIdAt(event.currentTarget, event.clientY)))}
              className={cn(
                "flex min-h-0 min-w-0 flex-col gap-2 bg-u-raised p-1.5 ring-1 ring-inset transition-shadow",
                hasBand ? "rounded-t-u-chip" : "rounded-u-chip",
                restages ? "ring-u-accent" : "ring-transparent",
              )}
            >
              <header className="flex flex-none items-center gap-2 px-1.5 pb-3 pt-1.5">
                {mark ? (
                  <Icon d={mark.icon} size={15} className={cn("flex-none", mark.colourClass)} />
                ) : (
                  <span aria-hidden className={cn("size-[7px] flex-none rounded-full", pipelineSwatchClass(stage.colour))} />
                )}
                <span className="min-w-0 truncate text-lead font-semibold text-u-text">{stage.label}</span>
                <span className="grid h-5 min-w-[22px] flex-none place-items-center rounded-u-control bg-u-sunken px-1.5 text-meta text-u-text2">
                  {cards.length}
                </span>
                <Icon
                  d={index === columns.length - 1 ? ICONS.chevronDown : ICONS.arrowRight}
                  size={13}
                  className="ms-auto flex-none text-u-text3"
                />
              </header>
              <div className="-my-1.5 flex min-h-0 flex-1 flex-col gap-2.5 overflow-y-auto py-1.5 [scrollbar-width:none]">
                {cards.map((row, cardIndex) => (
                  <PipelineCard
                    key={row.candidateId}
                    projectId={projectId}
                    row={row}
                    isSelected={selectedIds.has(row.candidateId)}
                    isDragging={dragging?.candidateId === row.candidateId}
                    drag={cardDrag(row)}
                    dropLine={
                      lineBefore(row.candidateId) ? (
                        <DropLine edge="top" />
                      ) : lineAfterLast && cardIndex === cards.length - 1 ? (
                        <DropLine edge="bottom" />
                      ) : null
                    }
                    actions={actionsOf(row)}
                    onOpen={onOpen}
                    onToggleSelected={onToggleSelected}
                    onToggleVisible={onToggleVisible}
                    onOpenNextAction={onOpenNextAction}
                  />
                ))}
                {cards.length === 0 && (
                  <div
                    className={cn(
                      "grid min-h-[90px] place-items-center rounded-u-chip border border-dashed p-3 text-center text-meta transition-colors",
                      restages ? "border-u-accent bg-u-accent-tint text-u-accent" : "border-u-border text-u-text3",
                    )}
                  >
                    {fallback ? "Drop an executive here" : "No default status to give"}
                  </div>
                )}
              </div>
            </section>
          );
        })}

        {hasBand && <OutOfProcessBandHeader gripProps={band.gripProps} />}
        {hasBand &&
          cells.map((cell, index) => {
            const isOver = dragging !== null && over?.key === `band:${cell.stage.key}` && over.lands;
            const chooser = choosing?.stage.key === cell.stage.key ? choosing : null;
            return (
              <div
                key={cell.stage.key}
                role="group"
                aria-label={`Out of process · ${cell.stage.label}`}
                data-band-cell
                data-drop-target={cell.waysOut.length > 0 ? "true" : "false"}
                style={{ gridColumn: index + 1, gridRow: 3 }}
                {...cellDrop(cell)}
                className={cn(
                  "flex min-w-0 flex-col gap-2 rounded-b-u-chip bg-u-raised p-1.5 outline-1 -outline-offset-1",
                  band.height === null ? "max-h-64 self-start" : "min-h-0",
                  "overflow-y-auto [scrollbar-width:thin]",
                  !cell.underColumn && "rounded-t-u-chip",
                  isOver ? "outline-dashed outline-u-text3" : "outline-transparent",
                )}
              >
                {!cell.underColumn && (
                  <span className="px-1.5 pt-1 text-note font-semibold text-u-text2">{cell.stage.label}</span>
                )}
                {chooser && (
                  <OutOfProcessChooser
                    name={chooser.row.fullName}
                    choices={chooser.choices}
                    onChoose={(status) => {
                      setChoosing(null);
                      giveWayOut(chooser.row, chooser.stage, status);
                    }}
                    onCancel={() => setChoosing(null)}
                  />
                )}
                {cell.rows.map((row) => (
                  <OutOfProcessCard
                    key={row.candidateId}
                    projectId={projectId}
                    row={row}
                    isDragging={dragging?.candidateId === row.candidateId}
                    drag={cardDrag(row)}
                    actions={actionsOf(row)}
                    onOpen={onOpen}
                    onToggleVisible={onToggleVisible}
                  />
                ))}
                {cell.rows.length === 0 && !chooser && (
                  <span className="grid h-[60px] place-items-center text-meta text-u-text3">—</span>
                )}
              </div>
            );
          })}
      </div>
    </div>
  );
}

/** Where a dragged card would land, drawn in the gap beside a card so nothing shifts under the pointer. */
function DropLine({ edge }: { edge: "top" | "bottom" }) {
  return (
    <span
      aria-hidden
      className={cn(
        "pointer-events-none absolute inset-x-1 h-0.5 rounded-full bg-u-accent",
        edge === "top" ? "-top-[7px]" : "-bottom-[7px]",
      )}
    />
  );
}

/** The card the pointer is above the middle of, in the column under it; null past the last card. */
function beforeIdAt(column: HTMLElement, clientY: number): string | null {
  for (const card of column.querySelectorAll<HTMLElement>("[data-card-id]")) {
    const box = card.getBoundingClientRect();
    if (clientY < box.top + box.height / 2) return card.dataset.cardId ?? null;
  }
  return null;
}

function stageOf({ key, label, colour, position }: PipelineStageCount): CandidatePipelineStage {
  return { key, label, colour, position };
}
