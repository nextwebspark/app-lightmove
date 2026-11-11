import type { ColumnVisibilityState, OnChangeFn } from "@tanstack/react-table";
import { useMemo, useState } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { CompanyLogo } from "../../../components/ui/CompanyLogo";
import { DataGrid, type DataGridColumnFilter } from "../../../components/ui/DataGrid";
import { SelectionCheckbox } from "../../../components/ui/SelectionCheckbox";
import { useDataGridTable } from "../../../lib/useDataGridTable";
import { isBelowTableWidth } from "../../../lib/viewport";
import type { GridLayout } from "../../../lib/useGridLayout";
import type { GridSort } from "../../../lib/useGridSort";
import type { Candidate, CandidateStatus } from "../../candidates/api/types";
import { StatusPicker } from "../../candidates/components/StatusPicker";
import type { CustomColumn } from "../../customcolumns/api/types";
import type { TriageCompany, TriageCompanyStatus, TriageSortField } from "../api/types";
import {
  createTriageCompanyColumns,
  customColumnId,
  TRIAGE_COLUMN_PINNING,
  triageTableFeatures,
} from "../lib/triageCompanyColumns";
import { triageRowId, type TriageCompanyRow } from "../lib/triageRows";
import { AddExecutiveChoice } from "./AddExecutiveChoice";

/**
 * The Companies half of the company grid: the mandate's own columns and its triage actions, over the
 * shared {@link DataGrid}. Everything about how the grid looks lives there, so this stage and
 * Strategy render identically without either owning a copy.
 *
 * <p>A row is a person at a company rather than a company — see {@link TriageCompanyRow} — so the page
 * hands this the expanded lines and the grid never has to know how they were paired up.
 *
 * <p>Sorting and paging are the server's, which orders the whole stage's lines; a header opens the column's
 * menu, and a sort there changes the query rather than the array.
 */
export function TriageCompanyTable({
  rows,
  label,
  sort,
  onSortChange,
  columnVisibility,
  onColumnVisibilityChange,
  customColumns,
  layout,
  onLayoutChange,
  loading,
  error,
  emptyMessage,
  projectId,
  onMove,
  onDelete,
  onAddExecutive,
  onFindExecutives,
  onOpenCompanyNotes,
  onChangeCandidateStatus,
  onEditCandidate,
  onRemoveCandidate,
  onMoveCandidate,
  onOpenCompany,
  busyIds,
  canWrite,
  stageReadOnly = false,
  onEditColumn,
  onDeleteColumn,
  columnFilters,
  selection,
}: {
  rows: TriageCompanyRow[];
  label: string;
  sort: GridSort<TriageSortField>;
  onSortChange: (sort: GridSort<TriageSortField>) => void;
  columnVisibility: ColumnVisibilityState;
  onColumnVisibilityChange: OnChangeFn<ColumnVisibilityState>;
  /** This mandate's own extra columns, appended after the built-ins. */
  customColumns: readonly CustomColumn[];
  layout: GridLayout;
  onLayoutChange: (layout: GridLayout) => void;
  loading: boolean;
  error: boolean;
  emptyMessage: string;
  projectId: string;
  onMove: (company: TriageCompany, status: TriageCompanyStatus) => void;
  onDelete: (company: TriageCompany) => void;
  onAddExecutive: (company: TriageCompany) => void;
  /** Find executives at one company; where given, the empty slot asks which way in. */
  onFindExecutives?: (company: TriageCompany) => void;
  /** Saves the grid's own inline-edited Note cell — the same write the Companies panel makes. */
  /** Opens the company's panel on its Notes tab. */
  onOpenCompanyNotes: (company: TriageCompany) => void;
  /** Changes a mapped executive's status inline, from the grid's own Status column. */
  onChangeCandidateStatus: (candidate: Candidate, status: CandidateStatus) => void;
  onEditCandidate: (candidate: Candidate) => void;
  onRemoveCandidate: (candidate: Candidate) => void;
  /** Moves one executive alone to another stage. */
  onMoveCandidate: (candidate: Candidate, status: TriageCompanyStatus) => void;
  onOpenCompany: (company: TriageCompany) => void;
  busyIds: ReadonlySet<string>;
  canWrite: boolean;
  /** Declined: Back to universe is the one act, so nobody is added, removed or has their status changed here. */
  stageReadOnly?: boolean;
  /** Opens a mandate's own column for rename, from its header menu — the real `CustomColumn` id. */
  onEditColumn?: (customColumnId: string) => void;
  /** Removes a mandate's own column, from its header menu — the real `CustomColumn` id. */
  onDeleteColumn?: (customColumnId: string) => void;
  /** The grid's own Company and Executive header filters, keyed by their built-in column ids. */
  columnFilters?: Record<string, DataGridColumnFilter>;
  /**
   * A tick box on every line — a person at a company, a company nobody is listed at yet, a person at no company — for
   * Find executives (the companies the lines name) and Add to sequence (the people on them).
   */
  selection?: {
    isTicked: (row: TriageCompanyRow) => boolean;
    onToggle: (row: TriageCompanyRow) => void;
    /** Ticks or clears every line on the page, in its order. */
    onToggleAll: (rows: readonly TriageCompanyRow[]) => void;
  };
}) {
  // Rebuilt only when the project's column set changes: a fresh array every render would rebuild
  // every column def and lose the grid's own per-column state with it.
  const columns = useMemo(
    () => createTriageCompanyColumns(customColumns, { staff: canWrite }),
    [customColumns, canWrite],
  );

  const table = useDataGridTable<typeof triageTableFeatures, TriageCompanyRow, TriageSortField>({
    features: triageTableFeatures,
    columns,
    data: rows,
    getRowId: triageRowId,
    pinning: TRIAGE_COLUMN_PINNING,
    sort,
    onSortChange,
    columnVisibility,
    onColumnVisibilityChange,
    layout,
    onLayoutChange,
    meta: {
      projectId,
      onMove,
      onDelete,
      onAddExecutive,
      onFindExecutives,
      onOpenCompanyNotes,
      onChangeCandidateStatus,
      onEditCandidate,
      onRemoveCandidate,
      onMoveCandidate,
      onOpenCompany,
      busyIds,
      canWrite,
      stageReadOnly,
    },
  });

  const allOnPageSelected = rows.length > 0 && rows.every((row) => selection?.isTicked(row));
  const someOnPageSelected = rows.some((row) => selection?.isTicked(row));
  const [asCards] = useState(isBelowTableWidth);
  const customColumnOf = (gridColumnId: string) =>
    customColumns.find((entry) => customColumnId(entry) === gridColumnId);

  return (
    <DataGrid
      table={table}
      label={label}
      headerLead={
        selection && (
          <SelectionCheckbox
            checked={allOnPageSelected}
            indeterminate={!allOnPageSelected && someOnPageSelected}
            label="Select all rows on this page"
            onChange={() => selection.onToggleAll(rows)}
          />
        )
      }
      rowLead={
        selection &&
        ((row: TriageCompanyRow) => (
          <SelectionCheckbox
            checked={selection.isTicked(row)}
            label={`Select ${lineName(row)}`}
            onChange={() => selection.onToggle(row)}
          />
        ))
      }
      renderCard={asCards ? (row) => (
        <TriageLineCard
          row={row}
          canWrite={canWrite && !stageReadOnly}
          busy={!!row.company && busyIds.has(row.company.id)}
          selection={selection && { checked: selection.isTicked(row), onToggle: () => selection.onToggle(row) }}
          onOpenCompany={onOpenCompany}
          onAddExecutive={onAddExecutive}
          onFindExecutives={onFindExecutives}
          onEditCandidate={onEditCandidate}
          onChangeCandidateStatus={onChangeCandidateStatus}
        />
      ) : undefined}
      layout={layout}
      onLayoutChange={onLayoutChange}
      loading={loading}
      error={error}
      errorMessage="That list could not be loaded. Refresh, or check you still have access to this mandate."
      emptyMessage={emptyMessage}
      columnFilters={columnFilters}
      clearFiltersColumnId="actions"
      menuOnHeaderClick
      onEditColumn={
        onEditColumn &&
        ((gridColumnId: string) => {
          const column = customColumnOf(gridColumnId);
          if (column) onEditColumn(column.id);
        })
      }
      onDeleteColumn={
        onDeleteColumn &&
        ((gridColumnId: string) => {
          const column = customColumnOf(gridColumnId);
          if (column) onDeleteColumn(column.id);
        })
      }
    />
  );
}

const CARD_ADD_EXECUTIVE =
  "flex min-h-10 w-full items-center gap-2 text-start text-note font-medium text-u-accent disabled:opacity-60";

function CardAddExecutiveFace({ company }: { company: TriageCompany }) {
  return (
    <>
      <Icon d={ICONS.userPlus} size={16} />
      Add executive
      {company.noExecutiveFound && <span className="ms-auto text-meta font-normal text-u-text3">Nobody suitable found</span>}
    </>
  );
}

/** A line by who is on it, and where: "Rania Haddad at ACWA Power", or the company alone where nobody is yet. */
function lineName(row: TriageCompanyRow): string {
  if (!row.candidate) return row.company?.companyName ?? "";
  const employer = row.company?.companyName ?? row.candidate.companyName;
  return employer ? `${row.candidate.fullName} at ${employer}` : row.candidate.fullName;
}

/**
 * One grid line as a phone reads it: the company on top, the person at it underneath with their status in
 * reach of the thumb — or, where nobody is mapped yet, the Add executive a researcher is there to press.
 */
function TriageLineCard({
  row,
  canWrite,
  busy,
  selection,
  onOpenCompany,
  onAddExecutive,
  onFindExecutives,
  onEditCandidate,
  onChangeCandidateStatus,
}: {
  row: TriageCompanyRow;
  canWrite: boolean;
  busy: boolean;
  selection?: { checked: boolean; onToggle: () => void };
  onOpenCompany: (company: TriageCompany) => void;
  onAddExecutive: (company: TriageCompany) => void;
  /** Find executives at one company; where given, the empty slot asks which way in. */
  onFindExecutives?: (company: TriageCompany) => void;
  onEditCandidate: (candidate: Candidate) => void;
  onChangeCandidateStatus: (candidate: Candidate, status: CandidateStatus) => void;
}) {
  const { company, candidate } = row;
  const place = company ? [company.industry, company.companyCity ?? company.companyCountry].filter(Boolean).join(" · ") : null;

  return (
    <article className="rounded-u-card border border-u-border bg-u-surface">
      <div className="flex items-center gap-3 px-3.5 pt-3">
        {selection && (
          <SelectionCheckbox
            checked={selection.checked}
            label={`Select ${lineName(row)}`}
            onChange={selection.onToggle}
          />
        )}
        {company ? (
          <button
            type="button"
            onClick={() => onOpenCompany(company)}
            className="flex min-w-0 flex-1 items-center gap-2.5 text-start"
          >
            <CompanyLogo name={company.companyName} logo={company.logoUrl} size={28} />
            <span className="min-w-0 flex-1">
              <span className="block truncate text-body font-semibold text-u-text">{company.companyName}</span>
              {place && <span className="block truncate text-meta text-u-text3">{place}</span>}
            </span>
            <Icon d={ICONS.chevronRight} size={16} className="flex-none text-u-text3" />
          </button>
        ) : (
          <span className="text-meta text-u-text3">{candidate.companyName ?? "Employer not in the universe"}</span>
        )}
      </div>

      <div className="mt-2.5 border-t border-u-border px-3.5 py-2.5">
        {candidate ? (
          <div className="flex items-center gap-2.5">
            <button
              type="button"
              onClick={() => onEditCandidate(candidate)}
              className="min-w-0 flex-1 text-start"
            >
              <span className="block truncate text-body font-medium text-u-text">{candidate.fullName}</span>
              <span className="block truncate text-meta text-u-text3">{candidate.title ?? "—"}</span>
            </button>
            {canWrite ? (
              <StatusPicker
                value={candidate.status}
                currentLabel={candidate.statusLabel}
                aria-label={`Status of ${candidate.fullName}`}
                onChange={(status) => onChangeCandidateStatus(candidate, status)}
              />
            ) : (
              <span className="flex-none text-meta text-u-text2">{candidate.statusLabel ?? candidate.status}</span>
            )}
          </div>
        ) : canWrite && company && onFindExecutives && !busy ? (
          <AddExecutiveChoice
            company={company}
            label={`Add executive at ${company.companyName}`}
            title="Add executive"
            triggerClassName={CARD_ADD_EXECUTIVE}
            trigger={<CardAddExecutiveFace company={company} />}
            onFind={onFindExecutives}
            onAddByHand={onAddExecutive}
          />
        ) : canWrite && company ? (
          <button
            type="button"
            disabled={busy}
            aria-label={`Add executive at ${company.companyName}`}
            onClick={() => onAddExecutive(company)}
            className={CARD_ADD_EXECUTIVE}
          >
            <CardAddExecutiveFace company={company} />
          </button>
        ) : (
          <span className="text-meta text-u-text3">Nobody mapped yet</span>
        )}
      </div>
    </article>
  );
}
