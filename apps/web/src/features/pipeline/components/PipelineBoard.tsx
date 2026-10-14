import { useState, type DragEvent } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { SelectionCheckbox } from "../../../components/ui/SelectionCheckbox";
import { cn } from "../../../lib/cn";
import { CandidateAvatar } from "../../candidates/components/CandidateAvatar";
import { defaultStatusOf, findStatus, pipelineSwatchClass } from "../../candidates/lib/pipelineStatus";
import type { PipelineCatalog } from "../../workspace/api/types";
import type { PipelineRow, PipelineStageCount } from "../api/types";
import { daysInStage, inactiveGroupsOf, shortDate } from "../lib/pipelineView";
import { VisibilityToggle } from "./PipelineControls";
import { BoardNextAction } from "./PipelineNextAction";

/** Where a dropped card goes: a status key, and the stage it lands in, for the toast. */
export interface BoardDrop {
  candidateId: string;
  status: string;
  stageLabel: string;
}

/**
 * The pipeline as a board: one column per active stage, and the closed stage beside them as Inactive,
 * grouped by status. Dropping a card on a column gives that stage's default status; on an Inactive group,
 * that status. A stage with no default to give is not a drop target.
 */
export function PipelineBoard({
  projectId,
  rows,
  stageCounts,
  catalog,
  selectedIds,
  onToggleSelected,
  onOpen,
  onDrop,
  onToggleVisible,
  onOpenNextAction,
}: {
  projectId: string;
  rows: readonly PipelineRow[];
  stageCounts: readonly PipelineStageCount[];
  catalog: PipelineCatalog;
  selectedIds: ReadonlySet<string>;
  onToggleSelected: (candidateId: string) => void;
  onOpen: (candidateId: string) => void;
  onDrop: (drop: BoardDrop) => void;
  onToggleVisible: (row: PipelineRow) => void;
  onOpenNextAction: (row: PipelineRow) => void;
}) {
  const [dragging, setDragging] = useState<PipelineRow | null>(null);
  const [overKey, setOverKey] = useState<string | null>(null);
  const [inactiveOpen, setInactiveOpen] = useState(true);
  const columns = stageCounts.filter((stage) => stage.kind === "active");
  const closed = stageCounts.find((stage) => stage.kind === "closed") ?? null;
  const inactive = rows.filter((row) => row.pipelineStage.kind === "closed");
  const groups = inactiveGroupsOf(rows, catalog);
  const activeCount = rows.length - inactive.length;

  const defaultOf = (stageKey: string) => {
    const stage = catalog.stages.find((candidate) => candidate.key === stageKey);
    return stage ? defaultStatusOf(stage) : null;
  };

  const dropTarget = (key: string, target: { status: string; stageLabel: string } | null, accepts: boolean) => ({
    onDragOver: (event: DragEvent<HTMLElement>) => {
      if (!dragging || !target || !accepts) return;
      event.preventDefault();
      if (overKey !== key) setOverKey(key);
    },
    onDragLeave: () => setOverKey((current) => (current === key ? null : current)),
    onDrop: (event: DragEvent<HTMLElement>) => {
      event.preventDefault();
      const moved = dragging;
      setDragging(null);
      setOverKey(null);
      if (!moved || !target || !accepts) return;
      onDrop({ candidateId: moved.candidateId, status: target.status, stageLabel: target.stageLabel });
    },
  });

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
      setOverKey(null);
    },
  });

  return (
    <div className="flex min-h-0 flex-1 flex-col gap-3.5 px-5 pb-4 pt-3.5">
      <div className="flex min-h-0 flex-1 items-stretch gap-2 overflow-x-auto [scrollbar-width:none]">
        {columns.map((stage) => {
          const fallback = defaultOf(stage.key);
          const cards = rows.filter((row) => row.pipelineStage.key === stage.key);
          const accepts = fallback !== null && dragging?.pipelineStage.key !== stage.key;
          const isOver = overKey === stage.key && dragging !== null;
          return (
            <section
              key={stage.key}
              aria-label={stage.label}
              data-drop-target={fallback ? "true" : "false"}
              {...dropTarget(stage.key, fallback && { status: fallback.key, stageLabel: stage.label }, accepts)}
              className={cn(
                "flex min-h-0 min-w-[180px] flex-1 flex-col gap-2 rounded-u-chip border bg-u-raised p-[7px] transition-colors",
                isOver ? "border-u-accent" : "border-transparent",
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
                    isOver ? "opacity-100" : "opacity-35",
                  )}
                />
              </header>
              <div className="flex min-h-0 flex-1 flex-col gap-2.5 overflow-y-auto [scrollbar-width:none]">
                {cards.map((row) => (
                  <article
                    key={row.candidateId}
                    aria-label={row.fullName}
                    {...cardDrag(row)}
                    onClick={() => onOpen(row.candidateId)}
                    className={cn(
                      "flex flex-none cursor-pointer flex-col gap-2.5 rounded-u-chip border bg-u-sunken p-3 transition",
                      selectedIds.has(row.candidateId)
                        ? "border-u-accent"
                        : "border-u-border-strong hover:border-u-text3",
                      dragging?.candidateId === row.candidateId && "opacity-40",
                    )}
                  >
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
                  <div className="grid min-h-[90px] place-items-center rounded-u-chip border border-dashed border-u-border p-3 text-center text-meta text-u-text3">
                    {fallback ? "Drop an executive here" : "No default status to give"}
                  </div>
                )}
              </div>
            </section>
          );
        })}

        {closed && (
          <section
            aria-label="Inactive"
            className={cn(
              "flex flex-none flex-col gap-4 overflow-y-auto rounded-u-chip border border-u-border px-3 py-2.5",
              inactiveOpen ? "w-40" : "w-[100px]",
            )}
          >
            <button
              type="button"
              aria-expanded={inactiveOpen}
              onClick={() => setInactiveOpen((open) => !open)}
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
            {inactiveOpen && (
              <>
                <span className="text-meta text-u-text3">Outside the active search flow</span>
                {groups.length === 0 && (
                  <InactiveGroup
                    label={closed.label}
                    rows={[]}
                    isOver={overKey === closed.key && dragging !== null}
                    dropProps={dropTarget(
                      closed.key,
                      defaultOf(closed.key) && { status: defaultOf(closed.key)!.key, stageLabel: closed.label },
                      true,
                    )}
                  />
                )}
                {groups.map((group) => {
                  const status = findStatus(catalog, group.status)?.status;
                  const accepts = Boolean(status && !status.retired) && dragging?.status !== group.status;
                  return (
                    <InactiveGroup
                      key={group.status}
                      label={group.label}
                      rows={group.rows}
                      isOver={overKey === group.status && dragging !== null}
                      onOpen={onOpen}
                      cardDrag={cardDrag}
                      dropProps={dropTarget(group.status, { status: group.status, stageLabel: closed.label }, accepts)}
                    />
                  );
                })}
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

function InactiveGroup({
  label,
  rows,
  isOver,
  onOpen,
  cardDrag,
  dropProps,
}: {
  label: string;
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
            {label} {shortDate(row.statusChangedAt)}
          </span>
        </div>
      ))}
      {rows.length === 0 && <span className="text-eyebrow text-u-text3">None</span>}
    </div>
  );
}
