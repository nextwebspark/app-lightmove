import {
  createColumnHelper,
  tableFeatures,
  type ColumnPinningState,
  type ColumnVisibilityState,
} from "@tanstack/react-table";
import {
  DATA_GRID_FEATURES,
  DataGridCell,
  LOCAL_ROW_MODELS,
  type DataGridColumnLayout,
} from "../../../components/ui/DataGrid";
import { TruncatedText } from "../../../components/ui/TruncatedText";
import { formatInstantDate, joined } from "../../../lib/format";
import { compareDate, compareNumber, compareText } from "../../../lib/gridSortFns";
import { SENIORITY_LABELS, SENIORITY_TIERS } from "../../../lib/seniority";
import type { TemplateOverview, TemplateScope } from "../api/types";
import { TemplateActions } from "../components/TemplateActions";
import { LibraryUpdatedPill, TemplateBadge } from "../components/TemplateBadge";
import { reviserOf, statusRank } from "./filtering";
import { DISCIPLINE_LABELS, DISCIPLINES } from "./labels";

interface TemplateTableMeta {
  isToggling: boolean;
  onToggle: (template: TemplateOverview) => void;
}

/** Either Templates list is at most a few dozen rows in one query, so the grid sorts and pages them itself. */
export const templateTableFeatures = tableFeatures({
  ...DATA_GRID_FEATURES,
  ...LOCAL_ROW_MODELS,
  columnMeta: {} as DataGridColumnLayout,
  tableMeta: {} as TemplateTableMeta,
});

const helper = createColumnHelper<typeof templateTableFeatures, TemplateOverview>();

function columnsFor(scope: TemplateScope) {
  return helper.columns([
    helper.accessor("title", {
      id: "title",
      header: "Template",
      enableHiding: false,
      meta: { share: 24, min: 220 },
      sortFn: (a, b) => compareText(a.original.title, b.original.title),
      cell: (info) => (
        <span className="block min-w-0">
          <TruncatedText value={info.getValue()} className="font-sans text-[13px] font-semibold text-u-text" />
          <TruncatedText
            value={info.row.original.code}
            className="mt-0.5 block font-mono text-[11px] text-u-text3"
          />
        </span>
      ),
    }),

    helper.accessor("discipline", {
      id: "discipline",
      header: "Discipline",
      meta: { share: 0, min: 120 },
      sortFn: (a, b) =>
        compareNumber(DISCIPLINES.indexOf(a.original.discipline), DISCIPLINES.indexOf(b.original.discipline)),
      cell: (info) => <DataGridCell value={DISCIPLINE_LABELS[info.getValue()]} />,
    }),

    helper.accessor("seniority", {
      id: "seniority",
      header: "Seniority",
      meta: { share: 0, min: 112 },
      // Tiers are a ladder, not an alphabet: Board sits above C-suite.
      sortFn: (a, b) =>
        compareNumber(SENIORITY_TIERS.indexOf(a.original.seniority), SENIORITY_TIERS.indexOf(b.original.seniority)),
      cell: (info) => <DataGridCell value={SENIORITY_LABELS[info.getValue()]} />,
    }),

    helper.display({
      id: "status",
      header: "Status",
      // "Customised" beside "Library updated" is the widest pairing.
      meta: { share: 0, min: 216 },
      sortFn: (a, b) => compareNumber(statusRank(scope, a.original), statusRank(scope, b.original)),
      cell: (info) => (
        <span className="flex min-w-0 items-center gap-1.5">
          <TemplateBadge scope={scope} template={info.row.original} />
          {info.row.original.libraryChangedSinceCustomised && <LibraryUpdatedPill />}
        </span>
      ),
    }),

    helper.accessor("summary", {
      id: "summary",
      header: "Summary",
      enableSorting: false,
      meta: { share: 30, min: 220 },
      cell: (info) => <DataGridCell value={info.getValue()} muted />,
    }),

    helper.accessor("keywords", {
      id: "keywords",
      header: "Match keywords",
      meta: { share: 18, min: 180 },
      sortFn: (a, b) => compareNumber(a.original.keywords.length, b.original.keywords.length),
      cell: (info) => <DataGridCell value={joined(info.getValue())} muted />,
    }),

    ...(scope === "library"
      ? [
          helper.accessor("customisedByWorkspaces", {
            id: "copies",
            header: "Firm copies",
            meta: { share: 0, min: 112 },
            sortFn: (a, b) =>
              compareNumber(a.original.customisedByWorkspaces, b.original.customisedByWorkspaces),
            cell: (info) => <DataGridCell value={String(info.getValue() ?? 0)} />,
          }),
        ]
      : []),

    helper.accessor("revisedAt", {
      id: "revised",
      header: "Last revised",
      meta: { share: 0, min: 120 },
      sortFn: (a, b) => compareDate(a.original.revisedAt, b.original.revisedAt),
      cell: (info) => <DataGridCell value={formatInstantDate(info.getValue())} muted />,
    }),

    helper.accessor("revisedByName", {
      id: "revisedBy",
      header: "Revised by",
      meta: { share: 0, min: 150 },
      sortFn: (a, b) => compareText(reviserOf(scope, a.original), reviserOf(scope, b.original)),
      cell: (info) => <DataGridCell value={reviserOf(scope, info.row.original)} muted />,
    }),

    helper.display({
      id: "actions",
      header: "",
      enableSorting: false,
      enableHiding: false,
      meta: { share: 0, min: 150 },
      cell: (info) => (
        <TemplateActions
          scope={scope}
          template={info.row.original}
          isToggling={info.table.options.meta?.isToggling ?? false}
          onToggle={(template) => info.table.options.meta?.onToggle(template)}
        />
      ),
    }),
  ]);
}

export const TEMPLATE_COLUMNS = {
  library: columnsFor("library"),
  workspace: columnsFor("workspace"),
};

const WORKSPACE_TEMPLATE_SORT_FIELDS = [
  "title",
  "discipline",
  "seniority",
  "status",
  "keywords",
  "revised",
  "revisedBy",
] as const;

const LIBRARY_TEMPLATE_SORT_FIELDS = [...WORKSPACE_TEMPLATE_SORT_FIELDS, "copies"] as const;

export type TemplateSortField = (typeof LIBRARY_TEMPLATE_SORT_FIELDS)[number];

export const TEMPLATE_SORT_FIELDS: Record<TemplateScope, readonly TemplateSortField[]> = {
  library: LIBRARY_TEMPLATE_SORT_FIELDS,
  workspace: WORKSPACE_TEMPLATE_SORT_FIELDS,
};

export const TEMPLATE_COLUMN_VISIBILITY: ColumnVisibilityState = {};

export const TEMPLATE_COLUMN_PINNING: ColumnPinningState = { start: ["title"], end: [] };
