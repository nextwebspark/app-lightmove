import { createColumnHelper, tableFeatures, type ColumnPinningState } from "@tanstack/react-table";
import type { ReactNode } from "react";
import { DATA_GRID_FEATURES, DataGrid, type DataGridColumnLayout } from "../../../components/ui/DataGrid";
import { SelectionCheckbox } from "../../../components/ui/SelectionCheckbox";
import { TruncatedText } from "../../../components/ui/TruncatedText";
import { cn } from "../../../lib/cn";
import { useDataGridTable } from "../../../lib/useDataGridTable";
import { layoutColumnsOf, useGridLayout } from "../../../lib/useGridLayout";
import type { GridSort } from "../../../lib/useGridSort";
import { CandidateAvatar } from "../../candidates/components/CandidateAvatar";
import type { RankedRow } from "../lib/ranking";
import { oneDecimal, rankShiftLabel, signedPoints } from "../lib/rankingWording";

interface RankingTableMeta {
  projectId: string;
  onOpen: (candidateId: string) => void;
}

const rankingTableFeatures = tableFeatures({
  ...DATA_GRID_FEATURES,
  columnMeta: {} as DataGridColumnLayout,
  tableMeta: {} as RankingTableMeta,
});

const helper = createColumnHelper<typeof rankingTableFeatures, RankedRow>();

const NUMBER_CELL = "font-u-num text-body tabular-nums";

// The order is the scenario's and nothing else, so no header re-sorts it.
const columns = helper.columns([
  helper.display({
    id: "candidate",
    header: "Candidate",
    enableSorting: false,
    enableHiding: false,
    meta: { share: 2, min: 160 },
    cell: (info) => <CandidateCell row={info.row.original} meta={info.table.options.meta} />,
  }),
  helper.display({
    id: "baseline",
    header: "Baseline",
    enableSorting: false,
    enableHiding: false,
    meta: { share: 1, min: 76 },
    cell: (info) => <span className={cn(NUMBER_CELL, "text-u-text2")}>{oneDecimal(info.row.original.baselineTotal)}</span>,
  }),
  helper.display({
    id: "scenario",
    header: "Scenario",
    enableSorting: false,
    enableHiding: false,
    meta: { share: 1, min: 80 },
    cell: (info) => (
      <span className={cn(NUMBER_CELL, "font-semibold text-u-inferred")}>{oneDecimal(info.row.original.total)}</span>
    ),
  }),
  helper.display({
    id: "rankShift",
    header: "Rank shift",
    enableSorting: false,
    enableHiding: false,
    meta: { share: 1, min: 84 },
    cell: (info) => <RankShift shift={info.row.original.rankShift} />,
  }),
  helper.display({
    id: "scoreShift",
    header: "Score shift",
    enableSorting: false,
    enableHiding: false,
    meta: { share: 1, min: 88 },
    cell: (info) => <span className={cn(NUMBER_CELL, "text-u-text2")}>{signedPoints(info.row.original.scoreShift)}</span>,
  }),
]);

const LAYOUT_COLUMNS = layoutColumnsOf(columns);
const PINNING: ColumnPinningState = { start: ["candidate"], end: [] };
const SCENARIO_ORDER: GridSort<"scenario"> = { field: "scenario", direction: "desc" };

/**
 * "How the shortlist changes": the pool in the scenario's order beside where the baseline put each one. Drawn on the
 * page rather than in a report card, since the grid's own frame is the card.
 */
export function RankingTable({
  projectId,
  rows,
  selectedIds,
  notAssessedCount,
  onToggle,
  onClearSelection,
  onOpen,
  filters,
}: {
  projectId: string;
  rows: readonly RankedRow[];
  selectedIds: readonly string[];
  notAssessedCount: number;
  onToggle: (candidateId: string) => void;
  onClearSelection: () => void;
  onOpen: (candidateId: string) => void;
  /** The pool's own filters, at the card's right like every report card's scope controls. */
  filters?: ReactNode;
}) {
  const [layout, setLayout] = useGridLayout("report.ranking.v2", LAYOUT_COLUMNS);
  const table = useDataGridTable<typeof rankingTableFeatures, RankedRow, "scenario">({
    features: rankingTableFeatures,
    columns,
    data: rows,
    getRowId: (row) => row.candidate.candidateId,
    pinning: PINNING,
    sort: SCENARIO_ORDER,
    onSortChange: () => {},
    layout,
    onLayoutChange: setLayout,
    meta: { projectId, onOpen },
  });

  const selected = rows.filter((row) => selectedIds.includes(row.candidate.candidateId));
  const selectionText =
    selected.length === 2
      ? `${selected[0].candidate.fullName} + ${selected[1].candidate.fullName} · compared below`
      : selected.length === 1
        ? `${selected[0].candidate.fullName} · tick one more to compare`
        : "Tick two candidates to compare them";
  const checkbox = (row: RankedRow) => (
    <SelectionCheckbox
      checked={selectedIds.includes(row.candidate.candidateId)}
      label={`Compare ${row.candidate.fullName}`}
      onChange={() => onToggle(row.candidate.candidateId)}
    />
  );

  return (
    <section className="mt-5">
      <div className="flex flex-wrap items-start justify-between gap-3.5">
        <div>
          <h2 className="text-lead font-bold">How the shortlist changes</h2>
          <div className="mt-0.5 text-note text-u-text3">
            {rows.length} {rows.length === 1 ? "candidate" : "candidates"} · Baseline vs scenario
            {notAssessedCount > 0 && ` · ${notAssessedCount} not assessed`}
          </div>
        </div>
        {filters}
      </div>
      <div className="mt-3.5">
        <DataGrid
          table={table}
          label="Candidate ranking"
          fit="content"
          layout={layout}
          onLayoutChange={setLayout}
          loading={false}
          error={false}
          errorMessage="The ranking could not be loaded."
          emptyMessage="No candidate matches these filters."
          headerLead={<span aria-hidden="true" className="size-4 flex-none" />}
          rowLead={checkbox}
          renderCard={(row) => <RankingCard row={row} checkbox={checkbox(row)} meta={{ projectId, onOpen }} />}
        />
      </div>
      <div className="mt-3 flex flex-wrap items-center gap-x-3 gap-y-1.5 text-note">
        <span className="font-semibold text-u-inferred">{selected.length} selected</span>
        <span className="flex-1 text-u-text3">{selectionText}</span>
        {selected.length > 0 && (
          <button type="button" onClick={onClearSelection} className="text-u-text2 transition hover:text-u-text">
            Clear selection
          </button>
        )}
      </div>
      <p className="mt-3 border-t border-dashed border-u-border-strong pt-3 text-note italic leading-relaxed text-u-text2">
        Both levels of weighting are applied together. Score shifts use unrounded totals; assessment scores are
        unchanged. Equal totals order a reviewed assessment first, then the one with more scored.
      </p>
    </section>
  );
}

function CandidateCell({ row, meta }: { row: RankedRow; meta?: RankingTableMeta }) {
  const { candidate } = row;
  const flags = flagsOf(row);
  const subtitle = flags || [candidate.title, candidate.companyName].filter(Boolean).join(" · ");
  return (
    <span className="flex min-w-0 items-center gap-2">
      {meta && (
        <CandidateAvatar
          projectId={meta.projectId}
          candidate={{ id: candidate.candidateId, fullName: candidate.fullName, enrichedAt: candidate.enrichedAt }}
          size="md"
        />
      )}
      <button
        type="button"
        onClick={() => meta?.onOpen(candidate.candidateId)}
        title="Open assessment"
        className="flex w-full min-w-0 flex-col text-start transition hover:text-u-accent"
      >
        <span className="flex min-w-0 items-center gap-1.5">
          <TruncatedText value={candidate.fullName} className="block min-w-0 font-sans text-body font-medium" />
          {candidate.assessment.status !== "reviewed" && (
            <span className="flex-none rounded bg-u-raised px-1.5 py-px text-meta font-medium text-u-text2">Draft</span>
          )}
        </span>
        {subtitle && (
          <TruncatedText
            value={subtitle}
            className={cn("block font-sans text-meta", flags ? "text-u-signal" : "text-u-text3")}
          />
        )}
      </button>
    </span>
  );
}

/** What stands against reading the total at face value, said in place of the role line where there is any. */
function flagsOf(row: RankedRow): string {
  const { processingRestricted, outOfProcess } = row.candidate;
  return [
    row.unscoredCount > 0 ? `${row.unscoredCount} unscored` : null,
    outOfProcess ? "Out of process" : null,
    processingRestricted ? "Processing restricted" : null,
  ]
    .filter(Boolean)
    .join(" · ");
}

function RankShift({ shift }: { shift: number }) {
  return (
    <span className={cn("text-body", shift > 0 ? "text-u-direct" : shift < 0 ? "text-u-signal" : "text-u-text3")}>
      {rankShiftLabel(shift)}
    </span>
  );
}

function RankingCard({ row, checkbox, meta }: { row: RankedRow; checkbox: ReactNode; meta: RankingTableMeta }) {
  return (
    <div className="flex items-center gap-3 rounded-lg border border-u-border-strong bg-u-surface p-3.5">
      {checkbox}
      <div className="min-w-0 flex-1">
        <CandidateCell row={row} meta={meta} />
      </div>
      <div className="flex flex-col items-end gap-0.5">
        <span className={cn(NUMBER_CELL, "font-semibold text-u-inferred")}>{oneDecimal(row.total)}</span>
        <RankShift shift={row.rankShift} />
      </div>
    </div>
  );
}
