import type { DragEvent, ReactNode } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { ActionMenu, MenuItem } from "../../../components/ui/ActionMenu";
import { SelectionCheckbox } from "../../../components/ui/SelectionCheckbox";
import { cn } from "../../../lib/cn";
import { CandidateAvatar } from "../../candidates/components/CandidateAvatar";
import { pipelineSwatchClass } from "../../candidates/lib/pipelineStatus";
import type { PipelineRow } from "../api/types";
import { shortDate } from "../lib/pipelineView";
import { HiddenFromClient, VisibilityToggle } from "./PipelineControls";
import { BoardNextAction } from "./PipelineNextAction";
import { useWorkspaceVocabulary } from "../../workspace/lib/vocabulary";

export interface CardDrag {
  draggable: boolean;
  onDragStart: (event: DragEvent<HTMLElement>) => void;
  onDragEnd: () => void;
}

/**
 * One executive in process on the board (`Position.dc.html`): who they are, their next action, and the foot's
 * visibility switch and actions.
 */
export function PipelineCard({
  projectId,
  clientName,
  row,
  isSelected,
  isDragging,
  drag,
  dropLine,
  actions,
  onOpen,
  onToggleSelected,
  onToggleVisible,
  onOpenNextAction,
  shareBadge,
  onAskShare,
}: {
  projectId: string;
  clientName: string;
  row: PipelineRow;
  isSelected: boolean;
  isDragging: boolean;
  drag: CardDrag;
  /** Where a dragged card would land beside this one. */
  dropLine: ReactNode;
  actions: ReactNode;
  onOpen: (candidateId: string) => void;
  onToggleSelected: (candidateId: string) => void;
  onToggleVisible: (row: PipelineRow) => void;
  onOpenNextAction: (row: PipelineRow) => void;
  shareBadge?: ReactNode;
  onAskShare?: (row: PipelineRow) => void;
}) {
  const vocabulary = useWorkspaceVocabulary();
  return (
    <article
      aria-label={row.fullName}
      data-card-id={row.candidateId}
      {...drag}
      onClick={() => onOpen(row.candidateId)}
      className={cn(
        "relative flex flex-none cursor-grab flex-col gap-3 rounded-u-chip border p-3 transition",
        isSelected
          ? "border-u-accent bg-[color-mix(in_srgb,var(--color-u-accent)_10%,var(--color-u-sunken))] shadow-[inset_3px_0_0_var(--color-u-accent)]"
          : "border-u-border bg-u-sunken hover:border-u-border-strong",
        isDragging && "opacity-40",
      )}
    >
      {dropLine}
      <span className="flex min-w-0 items-start gap-2.5">
        <span className="mt-1 flex" onClick={(event) => event.stopPropagation()}>
          <SelectionCheckbox
            checked={isSelected}
            label={`Select ${row.fullName}`}
            onChange={() => onToggleSelected(row.candidateId)}
          />
        </span>
        <CandidateAvatar
          projectId={projectId}
          candidate={{ id: row.candidateId, fullName: row.fullName, enrichedAt: row.enrichedAt ?? null }}
        />
        <span className="flex min-w-0 flex-1 flex-col gap-0.5">
          <span className="truncate text-body font-semibold text-u-text">{row.fullName}</span>
          <span className="truncate text-meta text-u-text2">{row.title ?? "—"}</span>
          <span className="truncate text-meta text-u-text2">{row.companyName ?? "—"}</span>
        </span>
        {onAskShare ? (
          <span className="flex" onClick={(event) => event.stopPropagation()}>
            <ActionMenu label={`${row.fullName} options`} width={220} triggerClassName="h-[18px] w-[22px] size-auto">
              {(close) => (
                <>
                  <MenuItem
                    icon={ICONS.externalLink}
                    onSelect={() => {
                      close();
                      onOpen(row.candidateId);
                    }}
                  >
                    Open record
                  </MenuItem>
                  <MenuItem
                    icon={ICONS.shield}
                    onSelect={() => {
                      close();
                      onAskShare(row);
                    }}
                  >
                    Ask to share with {vocabulary.unitLower}…
                  </MenuItem>
                </>
              )}
            </ActionMenu>
          </span>
        ) : (
          <button
            type="button"
            aria-label={`Open ${row.fullName}`}
            title="Open record"
            onClick={(event) => {
              event.stopPropagation();
              onOpen(row.candidateId);
            }}
            className="grid h-[18px] w-[22px] flex-none place-items-center rounded-u-control text-u-text2 hover:bg-u-border hover:text-u-text"
          >
            <Icon d={ICONS.more} size={14} />
          </button>
        )}
      </span>
      <BoardNextAction row={row} onOpen={onOpenNextAction} />
      {shareBadge && <span className="flex min-w-0">{shareBadge}</span>}
      <span className="flex items-center gap-2">
        <CardVisibility row={row} clientName={clientName} onToggleVisible={onToggleVisible} />
        {actions}
      </span>
    </article>
  );
}

/** Someone out of process, in the band under the stage their way out sits in: who, which way out, and since when. */
export function OutOfProcessCard({
  projectId,
  clientName,
  row,
  isDragging,
  drag,
  actions,
  onOpen,
  onToggleVisible,
  shareBadge,
}: {
  projectId: string;
  clientName: string;
  row: PipelineRow;
  isDragging: boolean;
  drag: CardDrag;
  actions: ReactNode;
  onOpen: (candidateId: string) => void;
  onToggleVisible: (row: PipelineRow) => void;
  shareBadge?: ReactNode;
}) {
  return (
    <article
      aria-label={row.fullName}
      {...drag}
      onClick={() => onOpen(row.candidateId)}
      className={cn(
        "flex flex-none cursor-grab flex-col gap-2 rounded-u-chip border border-u-border bg-u-sunken p-3 transition hover:border-u-border-strong",
        isDragging && "opacity-40",
      )}
    >
      <span className="flex min-w-0 items-start gap-2.5">
        <CandidateAvatar
          projectId={projectId}
          candidate={{ id: row.candidateId, fullName: row.fullName, enrichedAt: row.enrichedAt ?? null }}
        />
        <span className="flex min-w-0 flex-1 flex-col gap-0.5">
          <span className="truncate text-note font-medium text-u-text">{row.fullName}</span>
          <span className="truncate text-meta text-u-text2">{[row.title, row.companyName].filter(Boolean).join(" · ")}</span>
          <span className="mt-1 flex min-w-0 items-center gap-1.5 text-meta text-u-text2">
            <span aria-hidden className={cn("size-1.5 flex-none rounded-full", pipelineSwatchClass(row.statusColour))} />
            <span className="truncate" title={row.exitReason?.note ?? undefined}>
              {[row.statusLabel, row.exitReason?.label, shortDate(row.statusChangedAt)].filter(Boolean).join(" · ")}
            </span>
          </span>
        </span>
      </span>
      {shareBadge && <span className="flex min-w-0">{shareBadge}</span>}
      <span className="flex items-center gap-2">
        <CardVisibility row={row} clientName={clientName} onToggleVisible={onToggleVisible} />
        {actions}
      </span>
    </article>
  );
}

/** The foot's switch, or, while a refusal or a restriction hides them from the client, the switch locked with why. */
function CardVisibility({
  row,
  clientName,
  onToggleVisible,
}: {
  row: PipelineRow;
  clientName: string;
  onToggleVisible: (row: PipelineRow) => void;
}) {
  if (row.clientHiddenReason) {
    return (
      <HiddenFromClient
        reason={row.clientHiddenReason}
        name={row.fullName}
        clientName={clientName}
      />
    );
  }
  return <VisibilityToggle visible={row.clientVisible} name={row.fullName} onToggle={() => onToggleVisible(row)} />;
}
