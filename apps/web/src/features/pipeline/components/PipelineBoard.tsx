import { useState, type DragEvent } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { SelectionCheckbox } from "../../../components/ui/SelectionCheckbox";
import { cn } from "../../../lib/cn";
import type { CandidatePipelineStage } from "../../candidates/api/types";
import { CandidateAvatar } from "../../candidates/components/CandidateAvatar";
import { defaultStatusOf, findStatus, pipelineSwatchClass } from "../../candidates/lib/pipelineStatus";
import type { PipelineCatalog } from "../../workspace/api/types";
import type { PipelineRow, PipelineStageCount } from "../api/types";
import {
  boardPlacementOf,
  candidateStageOf,
  daysInStage,
  inactiveGroupsOf,
  outOfProcessChoicesFor,
  shortDate,
  type BoardPlacement,
  type BoardTarget,
  type OutOfProcessChoice,
} from "../lib/pipelineView";
import { VisibilityToggle } from "./PipelineControls";
import { BoardNextAction } from "./PipelineNextAction";

/** The drop the pointer is over: a column and the card it would land before, or an Inactive group. */
interface DropSlot {
  key: string;
  beforeId: string | null;
  lands: boolean;
}

/**
 * The pipeline as a board: one column per active stage holding those in process, and Inactive beside them
 * holding everyone at a status out of process, grouped by status. A card dropped in its own column is
 * reordered; on another column — or an inactive card on its own — it takes that stage's default status and
 * lands where it was dropped; on an Inactive group, that status; on Inactive itself, the way out chosen from
 * those offered. A stage with no default to give accepts only its own cards.
 */
export function PipelineBoard({
  projectId,
  rows,
  stageCounts,
  catalog,
  selectedIds,
  onToggleSelected,
  onOpen,
  onMove,
  onToggleVisible,
  onOpenNextAction,
  inactiveOpen,
  onInactiveOpenChange,
}: {
  projectId: string;
  rows: readonly PipelineRow[];
  stageCounts: readonly PipelineStageCount[];
  catalog: PipelineCatalog;
  selectedIds: ReadonlySet<string>;
  onToggleSelected: (candidateId: string) => void;
  onOpen: (candidateId: string) => void;
  onMove: (placement: BoardPlacement) => void;
  onToggleVisible: (row: PipelineRow) => void;
  onOpenNextAction: (row: PipelineRow) => void;
  /** Whether the Inactive column is unfolded; the toolbar's Advanced menu switches it too. */
  inactiveOpen: boolean;
  onInactiveOpenChange: (open: boolean) => void;
}) {
  const [dragging, setDragging] = useState<PipelineRow | null>(null);
  const [over, setOver] = useState<DropSlot | null>(null);
  const [landed, setLanded] = useState<{ basis: readonly PipelineRow[]; rows: PipelineRow[] } | null>(null);
  const [choosing, setChoosing] = useState<{ row: PipelineRow; choices: OutOfProcessChoice[] } | null>(null);
  if (landed && landed.basis !== rows) setLanded(null);
  const shown = landed?.basis === rows ? landed.rows : rows;

  const columns = stageCounts;
  const inactive = shown.filter((row) => row.outOfProcess);
  const groups = inactiveGroupsOf(shown, catalog);
  const activeCount = shown.length - inactive.length;
  const waysOut = outOfProcessChoicesFor(catalog, "");
  const offersAWayOut = waysOut.length > 0 || inactive.length > 0;

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
      // Only a slot that takes the card claims the drop; Inactive around a group must still hear a refused one.
      if (!target) return;
      event.preventDefault();
      const placement = dragging ? boardPlacementOf(shown, dragging.candidateId, target) : null;
      setDragging(null);
      setOver(null);
      if (!placement) return;
      setLanded({ basis: rows, rows: placement.rows });
      onMove(placement);
    },
  });

  const place = (row: PipelineRow, choice: OutOfProcessChoice) => {
    const target = { stage: choice.stage, status: choice.status, beforeId: null };
    const placement = boardPlacementOf(shown, row.candidateId, target);
    if (!placement) return;
    setLanded({ basis: rows, rows: placement.rows });
    onMove(placement);
  };

  /** Dropped on Inactive itself: one way out is taken at once, several are asked about. */
  const inactiveDrop = {
    // A group inside the column claims its own drag and drop first; the column takes only what none did.
    onDragOver: (event: DragEvent<HTMLElement>) => {
      if (!dragging || dragging.outOfProcess || waysOut.length === 0 || event.defaultPrevented) return;
      event.preventDefault();
      if (over?.key !== "inactive") setOver({ key: "inactive", beforeId: null, lands: true });
    },
    onDragLeave: (event: DragEvent<HTMLElement>) => {
      if (event.currentTarget.contains(event.relatedTarget as Node | null)) return;
      setOver((current) => (current?.key === "inactive" ? null : current));
    },
    onDrop: (event: DragEvent<HTMLElement>) => {
      if (event.defaultPrevented) return;
      event.preventDefault();
      const row = dragging;
      setDragging(null);
      setOver(null);
      if (!row || row.outOfProcess) return;
      const choices = outOfProcessChoicesFor(catalog, row.pipelineStage.key);
      if (choices.length === 1) place(row, choices[0]);
      else if (choices.length > 1) setChoosing({ row, choices });
    },
  };

  const cardDrag = (row: PipelineRow) => ({
    draggable: true,
    onDragStart: (event: DragEvent<HTMLElement>) => {
      if (event.dataTransfer) {
        event.dataTransfer.effectAllowed = "move";
        event.dataTransfer.setData("text/plain", row.candidateId);
      }
      setDragging(row);
    },
    onDragEnd: () => {
      setDragging(null);
      setOver(null);
    },
  });

  return (
    <div className="flex min-h-0 flex-1 flex-col gap-3.5 px-5 pb-4 pt-3.5">
      <div className="flex min-h-0 flex-1 items-stretch gap-2 overflow-x-auto [scrollbar-width:none]">
        {columns.map((stage) => {
          const fallback = defaultOf(stage.key);
          const cards = shown.filter((row) => row.pipelineStage.key === stage.key && !row.outOfProcess);
          const slot = dragging && over?.key === stage.key && over.lands ? over : null;
          const restages = slot !== null && dragging?.pipelineStage.key !== stage.key;
          const lineBefore = (candidateId: string) => slot?.beforeId === candidateId;
          const lineAfterLast = slot !== null && slot.beforeId === null && cards.length > 0;
          return (
            <section
              key={stage.key}
              aria-label={stage.label}
              data-drop-target={fallback ? "true" : "false"}
              {...dropTarget(stage.key, (event) => columnTarget(stage, beforeIdAt(event.currentTarget, event.clientY)))}
              className={cn(
                "flex min-h-0 min-w-[180px] flex-1 flex-col gap-2 rounded-u-chip border bg-u-raised p-[7px] transition-colors",
                restages ? "border-u-accent" : "border-transparent",
              )}
            >
              <header className="relative flex flex-none flex-col gap-1 px-1.5 pb-2.5">
                <span className="flex min-h-[18px] items-center gap-1.5">
                  <span aria-hidden className={cn("size-[7px] flex-none rounded-full", pipelineSwatchClass(stage.colour))} />
                  <span className="whitespace-nowrap text-note font-semibold text-u-text">{stage.label}</span>
                  <span className="grid h-[18px] min-w-[18px] place-items-center rounded-sm bg-u-border px-1 font-mono text-eyebrow text-u-text3">
                    {cards.length}
                  </span>
                </span>
                <span
                  aria-hidden
                  className={cn(
                    "absolute inset-x-1.5 bottom-0 h-0.5 rounded-sm",
                    pipelineSwatchClass(stage.colour),
                    restages ? "opacity-100" : "opacity-35",
                  )}
                />
              </header>
              <div className="-my-1.5 flex min-h-0 flex-1 flex-col gap-2.5 overflow-y-auto py-1.5 [scrollbar-width:none]">
                {cards.map((row, index) => (
                  <article
                    key={row.candidateId}
                    aria-label={row.fullName}
                    data-card-id={row.candidateId}
                    {...cardDrag(row)}
                    onClick={() => onOpen(row.candidateId)}
                    className={cn(
                      "relative flex flex-none cursor-pointer flex-col gap-2.5 rounded-u-chip border bg-u-sunken p-3 transition",
                      selectedIds.has(row.candidateId)
                        ? "border-u-accent"
                        : "border-u-border-strong hover:border-u-text3",
                      dragging?.candidateId === row.candidateId && "opacity-40",
                    )}
                  >
                    {lineBefore(row.candidateId) && <DropLine edge="top" />}
                    {lineAfterLast && index === cards.length - 1 && <DropLine edge="bottom" />}
                    <span className="flex self-start" onClick={(event) => event.stopPropagation()}>
                      <SelectionCheckbox
                        checked={selectedIds.has(row.candidateId)}
                        label={`Select ${row.fullName}`}
                        onChange={() => onToggleSelected(row.candidateId)}
                      />
                    </span>
                    <span className="flex min-w-0 items-center gap-2">
                      <CandidateAvatar
                        projectId={projectId}
                        candidate={{ id: row.candidateId, fullName: row.fullName, enrichedAt: row.enrichedAt ?? null }}
                      />
                      <span className="flex min-w-0 flex-col gap-0.5">
                        <span className="truncate text-note font-semibold text-u-text">{row.fullName}</span>
                        <span className="truncate text-meta text-u-text2">{row.title ?? "—"}</span>
                      </span>
                    </span>
                    <span className="truncate text-meta text-u-text3">{row.companyName ?? "—"}</span>
                    <BoardNextAction row={row} onOpen={onOpenNextAction} />
                    <span className="flex items-center justify-between gap-2 border-t border-u-border pt-2">
                      <span className="whitespace-nowrap text-eyebrow text-u-text3">
                        {daysInStage(row.statusChangedAt)}d in stage
                      </span>
                      <VisibilityToggle
                        compact
                        visible={row.clientVisible}
                        name={row.fullName}
                        onToggle={() => onToggleVisible(row)}
                      />
                    </span>
                  </article>
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

        {offersAWayOut && (
          <section
            aria-label="Inactive"
            {...inactiveDrop}
            className={cn(
              "flex flex-none flex-col gap-4 overflow-y-auto rounded-u-chip border px-3 py-2.5 transition-colors",
              inactiveOpen ? "w-40" : "w-[100px]",
              over?.key === "inactive" && dragging ? "border-u-text3" : "border-u-border",
            )}
          >
            <button
              type="button"
              aria-expanded={inactiveOpen}
              onClick={() => onInactiveOpenChange(!inactiveOpen)}
              className="flex items-center gap-1.5 text-start"
            >
              <Icon
                d={ICONS.chevronDown}
                size={12}
                className={cn("flex-none text-u-text2 transition-transform", !inactiveOpen && "-rotate-90")}
              />
              <span className="text-note font-semibold text-u-text2">Inactive</span>
              <span className="text-meta text-u-text3">{inactive.length}</span>
            </button>
            {choosing && (
              <OutOfProcessChooser
                name={choosing.row.fullName}
                choices={choosing.choices}
                onChoose={(choice) => {
                  setChoosing(null);
                  place(choosing.row, choice);
                }}
                onCancel={() => setChoosing(null)}
              />
            )}
            {inactiveOpen && !choosing && (
              <>
                <span className="text-meta text-u-text3">Outside the active search flow</span>
                {groups.map((group) => {
                  const found = findStatus(catalog, group.status);
                  const status = found?.status;
                  const accepts = Boolean(status && !status.retired) && dragging?.status !== group.status;
                  return (
                    <InactiveGroup
                      key={group.status}
                      label={`${group.label} · ${group.stageLabel}`}
                      outcome={group.label}
                      rows={group.rows}
                      isOver={dragging !== null && over?.key === `status:${group.status}` && over.lands}
                      onOpen={onOpen}
                      cardDrag={cardDrag}
                      dropProps={dropTarget(`status:${group.status}`, () =>
                        found && status && accepts
                          ? { stage: candidateStageOf(found.stage), status, beforeId: null }
                          : null,
                      )}
                    />
                  );
                })}
                {waysOut.length > 0 && (
                  <span
                    className={cn(
                      "grid min-h-[60px] place-items-center rounded-u-chip border border-dashed p-2 text-center text-eyebrow transition-colors",
                      over?.key === "inactive" && dragging
                        ? "border-u-text3 text-u-text2"
                        : "border-u-border text-u-text3",
                    )}
                  >
                    Drop here to mark out of process
                  </span>
                )}
                {inactive.length > 0 && inactive.every((row) => !row.clientVisible) && (
                  <span className="flex items-center gap-1.5 text-eyebrow text-u-text3">
                    <Icon d={ICONS.eyeOff} size={12} />
                    All internal only
                  </span>
                )}
              </>
            )}
          </section>
        )}
      </div>
      <div className="flex flex-none flex-wrap items-center justify-between gap-2 text-meta text-u-text3">
        <span>
          {activeCount} active executive{activeCount === 1 ? "" : "s"} across {columns.length} stage
          {columns.length === 1 ? "" : "s"}
        </span>
        <span className="flex items-center gap-1.5">
          <Icon d={ICONS.lock} size={12} />
          Stage moves don&apos;t send emails or change visibility
        </span>
      </div>
    </div>
  );
}

/** Which way out a card dropped on Inactive takes: the card's own stage's statuses come first. */
function OutOfProcessChooser({
  name,
  choices,
  onChoose,
  onCancel,
}: {
  name: string;
  choices: readonly OutOfProcessChoice[];
  onChoose: (choice: OutOfProcessChoice) => void;
  onCancel: () => void;
}) {
  return (
    <div role="group" aria-label={`Mark ${name} out of process`} className="flex flex-col gap-1.5">
      <span className="text-meta text-u-text2">Mark {name} as</span>
      {choices.map((choice) => (
        <button
          key={choice.status.id}
          type="button"
          onClick={() => onChoose(choice)}
          className="flex flex-col items-start rounded-u-control px-2 py-1.5 text-start hover:bg-u-raised"
        >
          <span className="text-meta font-medium text-u-text">{choice.status.label}</span>
          <span className="text-eyebrow text-u-text3">{choice.stage.label}</span>
        </button>
      ))}
      <button type="button" onClick={onCancel} className="self-start px-2 py-1 text-meta text-u-text3 hover:text-u-text">
        Cancel
      </button>
    </div>
  );
}

function InactiveGroup({
  label,
  outcome,
  rows,
  isOver,
  onOpen,
  cardDrag,
  dropProps,
}: {
  label: string;
  /** What each card's date line says it became. */
  outcome: string;
  rows: readonly PipelineRow[];
  isOver: boolean;
  onOpen?: (candidateId: string) => void;
  cardDrag?: (row: PipelineRow) => Record<string, unknown>;
  dropProps: Record<string, unknown>;
}) {
  return (
    <div
      role="group"
      aria-label={label}
      {...dropProps}
      className={cn(
        "flex flex-col gap-3 rounded-u-control outline-1 outline-offset-4",
        isOver ? "outline-dashed outline-u-text3" : "outline-transparent",
      )}
    >
      <span className="flex items-center justify-between">
        <span className="text-meta font-medium text-u-text2">{label}</span>
        <span className="text-meta text-u-text3">{rows.length}</span>
      </span>
      {rows.map((row) => (
        <div
          key={row.candidateId}
          aria-label={row.fullName}
          {...cardDrag?.(row)}
          onClick={() => onOpen?.(row.candidateId)}
          className="flex cursor-pointer flex-col gap-1 border-b border-u-border pb-3 hover:opacity-80"
        >
          <span className="truncate text-meta font-medium text-u-text">{row.fullName}</span>
          <span className="truncate text-eyebrow text-u-text3">
            {[row.title, row.companyName].filter(Boolean).join(" · ")}
          </span>
          <span className="text-eyebrow text-u-text3">
            {outcome} {shortDate(row.statusChangedAt)}
          </span>
        </div>
      ))}
      {rows.length === 0 && <span className="text-eyebrow text-u-text3">None</span>}
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
