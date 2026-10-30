import {
  columnOrderingFeature,
  columnPinningFeature,
  columnVisibilityFeature,
  createColumnHelper,
  rowSortingFeature,
  tableFeatures,
  type ColumnPinningState,
  type ColumnVisibilityState,
} from "@tanstack/react-table";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Avatar } from "../../../components/ui/Avatar";
import { NetworkMark } from "../../../components/ui/NetworkMark";
import { CompanyLink } from "../../../components/ui/CompanyLink";
import { CompanyLogo } from "../../../components/ui/CompanyLogo";
import {
  DataGridCell,
  GRID_ICON_BUTTON,
  type DataGridColumnLayout,
  type DataGridSortKind,
} from "../../../components/ui/DataGrid";
import { TruncatedText } from "../../../components/ui/TruncatedText";
import { cn } from "../../../lib/cn";
import { formatInstantDate, formatMoney } from "../../../lib/format";
import type { Candidate, CandidateStatus } from "../../candidates/api/types";
import { CandidateAvatar } from "../../candidates/components/CandidateAvatar";
import { StatusPicker } from "../../candidates/components/StatusPicker";
import { statusStyle } from "../../candidates/lib/pipelineStatus";
import type { CustomColumn, CustomColumnType } from "../../customcolumns/api/types";
import type { TriageCompany, TriageCompanyStatus, TriageSortField } from "../api/types";
import { SHORTLIST_AND_DECLINE_OFFERED } from "./triageStages";
import { candidateMoveTooltip, offeredMoves, removeTooltip, SOURCE_STYLES } from "./triageVocabulary";
import type { TriageCompanyRow } from "./triageRows";

/**
 * Where a grid line is, for the Country and City columns.
 *
 * <p>A line is a person at a company and the person is what it is about, so the executive's own
 * location wins and the company's HQ is what an empty "Add executive" slot falls back to. Taken as a
 * unit rather than field by field: a profile that recorded only a city must not borrow its employer's
 * country and read as somewhere neither of them is.
 */
function rowLocation(row: TriageCompanyRow): { country: string | null; city: string | null } {
  const { candidate, company } = row;
  if (candidate && (candidate.locationCountry || candidate.locationCity)) {
    return { country: candidate.locationCountry, city: candidate.locationCity };
  }
  return { country: company?.companyCountry ?? null, city: company?.companyCity ?? null };
}

/**
 * What the Actions and Executive columns need, supplied per render rather than baked into the column
 * defs — the page owns the mutations, the columns only say which moves exist.
 */
interface TriageTableMeta {
  /** The mandate whose grid this is — the executive avatars fetch their photos under it. */
  projectId: string;
  /** Moves the company and everyone mapped at it — offered on a company's row with nobody listed here. */
  onMove: (company: TriageCompany, status: TriageCompanyStatus) => void;
  onDelete: (company: TriageCompany) => void;
  /** Moves one executive to another stage; their company and its other people stay where they are (#903). */
  onMoveCandidate: (candidate: Candidate, status: TriageCompanyStatus) => void;
  /** Opens the drawer to map someone new at this company. */
  onAddExecutive: (company: TriageCompany) => void;
  /** Opens the company's panel on its Notes tab. */
  onOpenCompanyNotes: (company: TriageCompany) => void;
  /** Changes a mapped executive's status from the grid's own Status column. */
  onChangeCandidateStatus: (candidate: Candidate, status: CandidateStatus) => void;
  /** Opens the drawer on an executive already mapped. */
  onEditCandidate: (candidate: Candidate) => void;
  /** Asks to remove one executive from the mandate; their company and its other people stay. */
  onRemoveCandidate: (candidate: Candidate) => void;
  /** Opens the company's own panel — read-only, whatever the reader is allowed to do to it. */
  onOpenCompany: (company: TriageCompany) => void;
  /** Every row id with a write in flight, so each can be disabled independently without freezing
   *  the grid or letting one row's mutation settling re-enable a different row still in flight. */
  busyIds: ReadonlySet<string>;
  /** False for a client representative, who reads these grids but moves nothing. */
  canWrite: boolean;
}

/**
 * No pagination, filtering or sorted row model registered: all three are the server's, which orders the whole
 * stage's lines, and a client row model would re-sort one page as though it were the whole result.
 */
export const triageTableFeatures = tableFeatures({
  columnOrderingFeature,
  columnPinningFeature,
  columnVisibilityFeature,
  rowSortingFeature,
  columnMeta: {} as DataGridColumnLayout,
  tableMeta: {} as TriageTableMeta,
});

const helper = createColumnHelper<typeof triageTableFeatures, TriageCompanyRow>();

const PILL =
  "inline-flex items-center rounded-[4px] px-[6px] py-[2px] font-mono text-[10px] font-bold uppercase tracking-[0.04em]";

/**
 * The Companies grid's columns — the same widths and shapes as Strategy's, so moving between the market and what a
 * mandate took from it does not feel like moving between two products.
 *
 * <p>Each sortable column's id is its wire sort token, the same string `TriageSortField` allowlists. A row is a person
 * at a company, and the server orders the stage's lines, so Executive or Title sorts the people themselves.
 *
 * <p>Two columns Strategy has are deliberately absent: `Fit`, which has no score to show, and the
 * Facebook and X links, which the snapshot does not carry. One is added — `Source`, last in the order
 * and off by default, because a headcount from Apollo and one typed in by hand are not equally
 * trustworthy and a reader questioning a figure should be able to tell which they are looking at.
 */
const BUILT_IN_COLUMNS = helper.columns([
  helper.accessor((row) => row.company?.companyName ?? row.candidate?.companyName ?? null, {
    id: "name",
    header: "Company",
    enableHiding: false,
    // The floor covers a twenty-odd-character name: logo and gutters eat 54px before a letter draws.
    meta: { share: 22, min: 230, sortKind: "text" },
    cell: (info) => {
      const { company } = info.row.original;
      const name = info.getValue();

      // An executive whose employer is not in this mandate's universe. The name is what the
      // researcher typed rather than a company this screen can act on, so it reads as a caption.
      if (!company) {
        return (
          <span className="flex min-w-0 flex-col justify-center">
            <TruncatedText
              value={name ?? "No employer named"}
              className="font-sans text-[13px] text-u-text2"
            />
            <span className="font-mono text-[10px] uppercase tracking-[0.04em] text-u-text3">
              Not in universe
            </span>
          </span>
        );
      }

      // Drawn the same on every line, however many executives sit at the company: each row is a
      // person, and a dimmed continuation line read as a company that could not be acted on.
      return (
        <button
          type="button"
          onClick={() => info.table.options.meta?.onOpenCompany(company)}
          title={`Open ${company.companyName}`}
          aria-label={`Open ${company.companyName}`}
          className="flex w-full min-w-0 items-center gap-2.5 text-start"
        >
          <CompanyLogo name={company.companyName} logo={company.logoUrl} size={28} />
          <TruncatedText
            value={name}
            className="font-sans text-[13px] font-medium text-u-text transition group-hover:text-u-accent"
          />
        </button>
      );
    },
  }),

  helper.display({
    id: "actions",
    header: "Actions",
    enableSorting: false,
    enableHiding: false,
    meta: { share: 0, min: SHORTLIST_AND_DECLINE_OFFERED ? 134 : 80 },
    cell: (info) => {
      const row = info.row.original;
      const meta = info.table.options.meta;
      if (!meta?.canWrite) {
        return <span className="font-sans text-[13px] text-u-text3">—</span>;
      }
      // A line with a person on it acts on that person alone (#903): moving or removing Khaled at Alshaya
      // leaves his colleagues where they stand. The company's own moves and removal are its drawer's, and
      // the line of a company with nobody listed on this stage.
      if (row.candidate) {
        const { candidate } = row;
        const candidateBusy = meta.busyIds.has(candidate.id);
        const moves = offeredMoves(candidate.triageStatus ?? row.company?.status ?? "inUniverse");
        return (
          <span className="flex justify-start gap-1.5">
            {moves.map((move) => (
              <button
                key={move.status}
                type="button"
                title={candidateMoveTooltip(move)}
                aria-label={`${move.label}: ${candidate.fullName}`}
                disabled={candidateBusy}
                onClick={() => meta.onMoveCandidate(candidate, move.status)}
                className={`${GRID_ICON_BUTTON} disabled:opacity-40`}
              >
                <Icon d={move.icon} size={14} />
              </button>
            ))}
            {moves.length > 0 && <span aria-hidden className="mx-0.5 w-px self-stretch bg-u-border" />}
            <button
              type="button"
              title={removeTooltip(candidate.fullName)}
              aria-label={`Remove ${candidate.fullName} from this mandate`}
              disabled={candidateBusy}
              onClick={() => meta.onRemoveCandidate(candidate)}
              className={cn(GRID_ICON_BUTTON, "hover:text-u-offlimits disabled:opacity-40")}
            >
              <Icon d={ICONS.trash} size={14} />
            </button>
          </span>
        );
      }
      // What is left is the "Add executive" slot, which is always a company's.
      const { company } = row;
      if (!company) return null;
      const busy = meta.busyIds.has(company.id);
      const moves = offeredMoves(company.status);
      return (
        <span className="flex justify-start gap-1.5">
          {moves.map((move) => (
            <button
              key={move.status}
              type="button"
              title={move.tooltip ?? move.label}
              aria-label={`${move.label}: ${company.companyName}`}
              disabled={busy}
              onClick={() => meta.onMove(company, move.status)}
              className={`${GRID_ICON_BUTTON} disabled:opacity-40`}
            >
              <Icon d={move.icon} size={14} />
            </button>
          ))}
          {moves.length > 0 && <span aria-hidden className="mx-0.5 w-px self-stretch bg-u-border" />}
          <button
            type="button"
            title={removeTooltip(company.companyName)}
            aria-label={`Remove ${company.companyName} from this mandate`}
            disabled={busy}
            onClick={() => meta.onDelete(company)}
            className={cn(GRID_ICON_BUTTON, "hover:text-u-offlimits disabled:opacity-40")}
          >
            <Icon d={ICONS.trash} size={14} />
          </button>
        </span>
      );
    },
  }),

  helper.display({
    id: "links",
    header: "Links",
    enableSorting: false,
    meta: { share: 0, min: 56 },
    cell: (info) => {
      const { company } = info.row.original;
      if (!company) return <span className="font-sans text-[13px] text-u-text3">—</span>;
      return (
        <span className="flex justify-start gap-1">
          <CompanyLink
            url={company.website}
            icon={<Icon d={ICONS.globe} size={13} />}
            label="website"
            companyName={company.companyName}
            reserve
          />
          <CompanyLink
            url={company.companyLinkedinUrl}
            icon={<NetworkMark network="linkedin" size={14} />}
            label="LinkedIn"
            companyName={company.companyName}
            reserve
          />
        </span>
      );
    },
  }),

  helper.accessor((row) => rowLocation(row).country, {
    id: "country",
    header: "Country",
    meta: { share: 12, min: 82, sortKind: "text" },
    cell: (info) => <DataGridCell value={info.getValue()} />,
  }),

  helper.accessor((row) => row.candidate?.fullName ?? null, {
    id: "executive",
    header: "Executive",
    meta: { share: 14, min: 150, sortKind: "text" },
    cell: (info) => {
      const { company, candidate } = info.row.original;
      const meta = info.table.options.meta;

      if (candidate) {
        return (
          <button
            type="button"
            onClick={() => meta?.onEditCandidate(candidate)}
            title={meta?.canWrite ? "Open this profile" : "View this profile"}
            // `w-full`: a button sizes to its content even as a flex container, so without it the
            // row never squeezes and the name paints over the next column instead of clipping.
            className="flex w-full min-w-0 items-center gap-2 rounded-[4px] text-start font-sans text-[13px] font-medium text-u-text transition hover:text-u-accent"
          >
            {meta && <CandidateAvatar projectId={meta.projectId} candidate={candidate} size="sm" />}
            <TruncatedText value={candidate.fullName} />
          </button>
        );
      }
      // A company with nobody mapped is the most useful thing this grid shows, so the empty cell is
      // the invitation rather than a dash.
      if (company && meta?.canWrite) {
        // Flagged: the mandate already looked and found nobody. Still opens the same form — one
        // action per cell — so the flag has nothing separate to unset; it clears itself the moment
        // an executive is actually saved against this company.
        if (company.noExecutiveFound) {
          return (
            <button
              type="button"
              onClick={() => meta.onAddExecutive(company)}
              title="Researched — nobody suitable found. Click to search again."
              aria-label={`No executive found at ${company.companyName} — search again`}
              className={GRID_ICON_BUTTON}
            >
              <Icon d={ICONS.userX} size={14} />
            </button>
          );
        }
        return (
          <button
            type="button"
            onClick={() => meta.onAddExecutive(company)}
            title="Add executive"
            aria-label={`Add an executive at ${company.companyName}`}
            className={cn(GRID_ICON_BUTTON, "text-u-accent hover:text-u-accent-hover")}
          >
            <Icon d={ICONS.userPlus} size={14} />
          </button>
        );
      }
      return <DataGridCell value={null} />;
    },
  }),

  helper.accessor((row) => row.candidate?.title ?? null, {
    id: "executiveTitle",
    header: "Title",
    meta: { share: 14, min: 130, sortKind: "text" },
    cell: (info) => <DataGridCell value={info.getValue()} />,
  }),

  helper.accessor((row) => row.candidate?.contacts.emails.map((entry) => entry.address) ?? [], {
    id: "executiveEmails",
    header: "Email",
    enableSorting: false,
    meta: { share: 12, min: 170 },
    cell: (info) => <ContactListCell values={info.getValue()} />,
  }),

  helper.accessor((row) => row.candidate?.contacts.phones.map((entry) => entry.number) ?? [], {
    id: "executivePhones",
    header: "Phone",
    enableSorting: false,
    meta: { share: 10, min: 150 },
    cell: (info) => <ContactListCell values={info.getValue()} />,
  }),

  helper.accessor((row) => row.candidate?.status ?? null, {
    id: "executiveStatus",
    header: "Status",
    // Sorts in the workspace's pipeline order, not by the status's name.
    meta: { share: 0, min: 150 },
    cell: (info) => {
      const { candidate } = info.row.original;
      const meta = info.table.options.meta;
      if (!candidate) return <DataGridCell value={null} />;

      if (meta?.canWrite) {
        const busy = meta.busyIds.has(candidate.id);
        return (
          <StatusPicker
            size="sm"
            value={candidate.status}
            currentLabel={candidate.statusLabel}
            aria-label={`Status for ${candidate.fullName}`}
            disabled={busy}
            onChange={(status) => meta.onChangeCandidateStatus(candidate, status)}
            className="w-full"
          />
        );
      }
      const { label, className } = statusStyle(candidate.status, candidate);
      return <span className={`${PILL} ${className}`}>{label}</span>;
    },
  }),

  helper.accessor((row) => row.company?.industry ?? null, {
    id: "sector",
    header: "Sector",
    meta: { share: 18, min: 100, sortKind: "text" },
    cell: (info) => <DataGridCell value={info.getValue()} />,
  }),

  helper.accessor((row) => rowLocation(row).city, {
    id: "location",
    header: "City",
    meta: { share: 11, min: 82, sortKind: "text" },
    cell: (info) => <DataGridCell value={info.getValue()} />,
  }),

  helper.accessor((row) => row.company?.annualRevenue ?? null, {
    id: "revenue",
    header: "Revenue",
    meta: { share: 11, min: 82, sortKind: "number" },
    cell: (info) => <DataGridCell value={formatMoney(info.getValue())} />,
  }),

  helper.accessor((row) => row.company?.numEmployees ?? null, {
    id: "employees",
    header: "Employees",
    meta: { share: 9, min: 74, sortKind: "number" },
    cell: (info) => <DataGridCell value={info.getValue()?.toLocaleString() ?? null} />,
  }),

  // Keeps the old Note column's id, so a grid that hid or widened it still does. Staff-only, as the notes are.
  helper.accessor((row) => row.company?.noteCount ?? null, {
    id: "note",
    header: "Notes",
    meta: { share: 9, min: 88, sortKind: "number" },
    cell: (info) => {
      const { company } = info.row.original;
      const meta = info.table.options.meta;
      if (!company || !meta?.canWrite) return <DataGridCell value={null} />;
      const count = info.getValue() ?? 0;
      return (
        <button
          type="button"
          onClick={() => meta.onOpenCompanyNotes(company)}
          aria-label={
            count > 0 ? `${count} ${count === 1 ? "note" : "notes"} on ${company.companyName}` : `Add a note on ${company.companyName}`
          }
          className={cn(
            "rounded-u-chip px-2 py-0.5 text-note font-medium",
            count > 0 ? "bg-u-accent-tint text-u-accent hover:underline" : "text-u-text3 hover:text-u-text",
          )}
        >
          {count > 0 ? `${count} ${count === 1 ? "note" : "notes"}` : "Add note"}
        </button>
      );
    },
  }),

  helper.accessor((row) => row.company?.foundedYear ?? null, {
    id: "founded",
    header: "Founded",
    meta: { share: 5, min: 84, sortKind: "number" },
    cell: (info) => <DataGridCell value={info.getValue()?.toString() ?? null} />,
  }),

  helper.accessor((row) => row.company?.addedAt ?? row.candidate?.addedAt ?? null, {
    id: "added",
    header: "Added",
    meta: { share: 8, min: 96, sortKind: "date" },
    cell: (info) => <DataGridCell value={formatInstantDate(info.getValue())} />,
  }),

  helper.accessor((row) => row.company?.addedByName ?? row.candidateAddedBy?.name ?? null, {
    id: "addedBy",
    header: "Added by",
    meta: { share: 9, min: 150, sortKind: "text" },
    cell: (info) => <AddedByCell row={info.row.original} />,
  }),

  helper.accessor((row) => row.company?.shortDescription ?? null, {
    id: "description",
    header: "Description",
    enableSorting: false,
    meta: { share: 18, min: 160 },
    cell: (info) => <DataGridCell value={info.getValue()} muted />,
  }),

  helper.accessor((row) => row.company?.source ?? null, {
    id: "source",
    header: "Source",
    meta: { share: 0, min: 84, sortKind: "text" },
    cell: (info) => {
      const source = info.getValue();
      if (!source) return <DataGridCell value={null} />;
      const { label, className } = SOURCE_STYLES[source];
      return <span className={`${PILL} ${className}`}>{label}</span>;
    },
  }),
]);

/** How a mandate's own column sorts on the server, by what it holds; a yes/no column has no hint to draw. */
const CUSTOM_SORT_KINDS: Record<CustomColumnType, DataGridSortKind | undefined> = {
  text: "text",
  number: "number",
  date: "date",
  boolean: undefined,
};

/**
 * The grid's columns for one mandate: the built-ins above, then a column per custom column the
 * project has defined.
 *
 * <p>A factory rather than a constant, because the column set is per project — a mandate that
 * imported a file carrying Ethnicity has a column the mandate next door does not. A parallel column
 * list for the custom ones would have to re-implement widths, pinning, ordering and the picker; one
 * list of TanStack columns gets all of that for free, and a custom column behaves exactly like a
 * built-in because it is one.
 *
 * <p>Ids are prefixed `custom:` so a project's "Note" or "Country" column can never collide with the
 * built-in of that name, whose id is the server's own sort token.
 */
export function createTriageCompanyColumns(customColumns: readonly CustomColumn[], { staff = true } = {}) {
  const custom = customColumns
    .filter((column) => !column.hidden)
    .map((column) =>
      helper.accessor((row) => customValueOf(row, column), {
        id: customColumnId(column),
        header: column.label,
        meta: { share: 0, min: 120, sortKind: CUSTOM_SORT_KINDS[column.dataType] },
        cell: (info) => <DataGridCell value={info.getValue()} />,
      }),
    );
  // Back through `helper.columns` rather than a bare spread: it is what re-widens the tuple of
  // per-column value types into the one array shape `useTable` takes, which a spread loses.
  const builtIn = staff ? BUILT_IN_COLUMNS : BUILT_IN_COLUMNS.filter((column) => !STAFF_ONLY_COLUMNS.has(column.id ?? ""));
  return helper.columns([...builtIn, ...custom]);
}

/**
 * Who brought the line in: the company's adder by name, and the executive's face beside theirs when someone else
 * filed the person (the tooltip names both). One face when it is the same colleague; a line with no company in the
 * mandate shows the executive's adder alone.
 */
function AddedByCell({ row }: { row: TriageCompanyRow }) {
  const company = row.company?.addedByUserId && row.company.addedByName
    ? { userId: row.company.addedByUserId, name: row.company.addedByName, avatarUrl: row.company.addedByAvatarUrl ?? null }
    : null;
  const executive = row.candidateAddedBy?.name ? row.candidateAddedBy : null;
  const lead = company ?? executive;
  if (!lead?.name) return <DataGridCell value={null} />;
  const second = company && executive && executive.userId !== company.userId ? executive : null;
  const tooltip = [
    company ? `Company: ${company.name}` : null,
    executive && row.candidate ? `Executive: ${executive.name}` : null,
  ]
    .filter(Boolean)
    .join("\n");
  return (
    <span className="flex min-w-0 items-center gap-1.5" title={tooltip} aria-label={tooltip.replace("\n", ", ")}>
      <span aria-hidden="true" className="flex flex-none items-center">
        <Avatar id={lead.userId} name={lead.name} src={lead.avatarUrl} size="xs" />
        {second?.name && (
          <Avatar
            id={second.userId}
            name={second.name}
            src={second.avatarUrl}
            size="xs"
            title={`Executive added by ${second.name}`}
            className="-ms-1.5 ring-2 ring-u-surface"
          />
        )}
      </span>
      <span className="truncate">{shortNameOf(lead.name)}</span>
    </span>
  );
}

/** "Yara Haddad" as "Yara H.": the photo carries who, the column stays narrow. */
function shortNameOf(fullName: string): string {
  const parts = fullName.trim().split(/\s+/);
  return parts.length < 2 ? fullName : `${parts[0]} ${parts[parts.length - 1][0]}.`;
}

/** Columns a client seat never draws: a company's notes, and who at the firm filed it. */
const STAFF_ONLY_COLUMNS = new Set(["note", "addedBy"]);

/** The grid id for a custom column. Prefixed so it cannot collide with a built-in's id. */
export function customColumnId(column: CustomColumn): string {
  return `custom:${column.target}:${column.fieldKey}`;
}

/**
 * A custom value off whichever half of the row the column belongs to.
 *
 * <p>A company column repeats down the company's people, the way the company's own name does — the
 * fact is about the employer, and blanking it on the second and third rows would read as missing
 * data rather than as a repeat.
 */
function customValueOf(row: TriageCompanyRow, column: CustomColumn): string | null {
  const values = column.target === "company" ? row.company?.customFields : row.candidate?.customFields;
  return values?.[column.fieldKey] ?? null;
}

/**
 * What a project's grid shows by default: the built-in defaults below, plus every custom column
 * visible. A column somebody deliberately added to their own grid should not need finding in a picker.
 *
 * <p>`useColumnVisibility` merges a stored record over this, so a column added after that record was
 * written takes the default here rather than being read as hidden.
 */
export function defaultTriageColumnVisibility(
  customColumns: readonly CustomColumn[],
): ColumnVisibilityState {
  const visibility: ColumnVisibilityState = { ...DEFAULT_TRIAGE_COLUMN_VISIBILITY };
  for (const column of customColumns) {
    visibility[customColumnId(column)] = !column.hidden;
  }
  return visibility;
}

/**
 * Founded and Description start hidden, matching Strategy: a grid that opened with every column would
 * be a spreadsheet. The rest stay visible. Notes, because they are the one thing here the market cannot
 * supply and the reason a consultant opens this screen rather than that one; Added, because the grid
 * opens sorted by it, and a default ordering whose column is hidden shows its sort indicator nowhere;
 * and the three executive columns, because who sits in the seat is what a talent map is for.
 */
export const DEFAULT_TRIAGE_COLUMN_VISIBILITY: ColumnVisibilityState = {
  founded: false,
  description: false,
  source: false,
  executiveEmails: false,
  executivePhones: false,
};

/**
 * Every value the person holds, in a row one line tall: the first in the cell, the rest as a
 * count, and all of them in the tooltip. A cell that grew a line per address would make the grid
 * a different height on every row.
 */
function ContactListCell({ values }: { values: string[] }) {
  if (values.length === 0) return <DataGridCell value={null} />;
  return (
    <span className="flex min-w-0 items-center gap-1.5" title={values.join("\n")}>
      <TruncatedText value={values[0]} className="font-sans text-[13px] text-u-text2" />
      {values.length > 1 && (
        <span className="flex-none rounded-[4px] bg-u-raised px-1.5 py-px font-mono text-[9.5px] font-semibold text-u-text3">
          +{values.length - 1}
        </span>
      )}
    </span>
  );
}

/**
 * A scrolled row without its name is a line of anonymous figures, so the name travels with it. v9
 * names the pinned regions logically, so `start` follows the reading direction rather than the CSS.
 */
export const TRIAGE_COLUMN_PINNING: ColumnPinningState = {
  start: ["name"],
  end: [],
};

/** The built-in sortable column ids, which are also the server's sort tokens; a mandate's own columns sort too. */
export const TRIAGE_SORT_FIELDS = [
  "name",
  "executive",
  "executiveTitle",
  "country",
  "location",
  "executiveStatus",
  "sector",
  "revenue",
  "employees",
  "note",
  "founded",
  "added",
  "addedBy",
  "source",
] as const satisfies readonly TriageSortField[];
