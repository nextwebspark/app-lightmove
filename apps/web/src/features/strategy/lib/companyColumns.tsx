import {
  columnPinningFeature,
  columnVisibilityFeature,
  createColumnHelper,
  rowSortingFeature,
  tableFeatures,
  type ColumnPinningState,
  type ColumnVisibilityState,
} from "@tanstack/react-table";
import { CompanyLogo } from "../../../components/ui/CompanyLogo";
import { TruncatedText } from "../../../components/ui/TruncatedText";
import { Icon, ICONS } from "../../../components/layout/Icon";
import type { CompanyResult } from "../api/types";

interface CompanyColumnMeta {
  /** Percentage of the table's flexible width. 0 pins the column at {@code min}. */
  share: number;
  /** The width the column will not shrink below, in px. */
  min: number;
  align?: "right";
}

/** What the Actions column needs, supplied per render rather than baked into the column defs. */
interface CompanyTableMeta {
  onAddToUniverse: (company: CompanyResult) => void;
  addingId: string | null;
}

/**
 * No pagination, filtering or sorted row model registered: all three are the server's, and a client
 * row model would re-sort the 25 rows this page holds as though they were the whole result.
 */
export const companyTableFeatures = tableFeatures({
  columnPinningFeature,
  columnVisibilityFeature,
  rowSortingFeature,
  columnMeta: {} as CompanyColumnMeta,
  tableMeta: {} as CompanyTableMeta,
});

const helper = createColumnHelper<typeof companyTableFeatures, CompanyResult>();

/** The Add button's shape, shared with the link icons so the two rows of glyphs match. */
const ICON_BUTTON =
  "grid size-6 place-items-center rounded-[5px] text-text3 transition hover:bg-panel2 hover:text-text";

/**
 * Each sortable column's id is its wire sort token, the same string `CompanySortField` allowlists,
 * so there is no click-to-field mapping that can drift. The columns below Notes carry no token and
 * are unsortable by construction: they exist so the Columns menu has the rest of the universe to
 * offer, and the server's ORDER BY allowlist stays the eight it was written for.
 */
export const companyColumns = helper.columns([
  helper.accessor("companyName", {
    id: "name",
    header: "Company",
    enableHiding: false,
    // The floor covers a twenty-odd-character name: logo and gutters eat 54px before a letter draws.
    meta: { share: 22, min: 230 },
    cell: (info) => (
      <span className="flex min-w-0 items-center gap-2.5">
        <CompanyLogo name={info.getValue()} logo={info.row.original.logoUrl} size={28} />
        <TruncatedText
          value={info.getValue()}
          className="font-sans text-[13px] font-medium text-text"
        />
      </span>
    ),
  }),

  // Beside the name rather than pinned to the far edge: the decision belongs next to what it is about,
  // and a second sticky region costs horizontal room the middle columns need.
  helper.display({
    id: "actions",
    header: "Actions",
    enableSorting: false,
    enableHiding: false,
    meta: { share: 0, min: 72 },
    cell: (info) => {
      const company = info.row.original;
      const meta = info.table.options.meta;
      return (
        <span className="flex justify-start gap-1.5">
          <button
            type="button"
            title="Add to universe"
            aria-label={`Add ${company.companyName} to universe`}
            onClick={() => meta?.onAddToUniverse(company)}
            disabled={meta?.addingId === company.apolloAccountId}
            className={`${ICON_BUTTON} disabled:opacity-40`}
          >
            <Icon d={ICONS.plus} size={14} />
          </button>
        </span>
      );
    },
  }),

  helper.display({
    id: "links",
    header: "Links",
    enableSorting: false,
    meta: { share: 0, min: 108 },
    cell: (info) => {
      const company = info.row.original;
      return (
        <span className="flex justify-start gap-1">
          <CompanyLink url={company.website} icon={ICONS.globe} label="website" company={company} />
          <CompanyLink
            url={company.companyLinkedinUrl}
            icon={ICONS.linkedin}
            label="LinkedIn"
            company={company}
          />
          <CompanyLink
            url={company.facebookUrl}
            icon={ICONS.facebook}
            label="Facebook"
            company={company}
          />
          <CompanyLink url={company.twitterUrl} icon={ICONS.x} label="X" company={company} />
        </span>
      );
    },
  }),

  helper.display({
    id: "fit",
    header: "Fit",
    enableSorting: false,
    meta: { share: 0, min: 44 },
    // No score yet; it fills in when AI Research lands.
    cell: () => <span className="font-sans text-[13px] font-semibold text-text3">—</span>,
  }),

  helper.accessor("companyCountry", {
    id: "country",
    header: "Country",
    meta: { share: 12, min: 96 },
    cell: (info) => <Cell value={info.getValue()} />,
  }),

  helper.accessor("industry", {
    id: "sector",
    header: "Sector",
    meta: { share: 18, min: 120 },
    cell: (info) => <Cell value={info.getValue()} />,
  }),

  helper.accessor("companyCity", {
    id: "location",
    header: "City",
    meta: { share: 11, min: 96 },
    cell: (info) => <Cell value={info.getValue()} />,
  }),

  helper.accessor("annualRevenue", {
    id: "revenue",
    header: "Revenue",
    meta: { share: 11, min: 96 },
    cell: (info) => <Cell value={formatMoney(info.getValue())} />,
  }),

  helper.accessor("numEmployees", {
    id: "employees",
    header: "Employees",
    meta: { share: 9, min: 84 },
    cell: (info) => <Cell value={info.getValue()?.toLocaleString() ?? null} />,
  }),

  helper.accessor("foundedYear", {
    id: "founded",
    header: "Founded",
    meta: { share: 5, min: 84 },
    cell: (info) => <Cell value={info.getValue()?.toString() ?? null} />,
  }),

  helper.accessor("shortDescription", {
    id: "notes",
    header: "Notes",
    // Not in the server's sort allowlist: alphabetising a description answers no question.
    enableSorting: false,
    meta: { share: 12, min: 130 },
    cell: (info) => <Cell value={info.getValue()} muted />,
  }),

  helper.accessor("companyPhone", {
    id: "phone",
    header: "Phone",
    enableSorting: false,
    meta: { share: 10, min: 120 },
    cell: (info) => <Cell value={info.getValue()} />,
  }),

  helper.accessor("companyState", {
    id: "state",
    header: "State",
    enableSorting: false,
    meta: { share: 10, min: 96 },
    cell: (info) => <Cell value={info.getValue()} />,
  }),

  helper.accessor("companyAddress", {
    id: "address",
    header: "Address",
    enableSorting: false,
    meta: { share: 16, min: 160 },
    cell: (info) => <Cell value={info.getValue()} muted />,
  }),

  helper.accessor("parentCompany", {
    id: "parent",
    header: "Parent company",
    enableSorting: false,
    meta: { share: 12, min: 130 },
    cell: (info) => <Cell value={info.getValue()} />,
  }),

  helper.accessor("totalFunding", {
    id: "totalFunding",
    header: "Total funding",
    enableSorting: false,
    meta: { share: 10, min: 110 },
    cell: (info) => <Cell value={formatMoney(info.getValue())} />,
  }),

  helper.accessor("latestFunding", {
    id: "latestFunding",
    header: "Latest funding",
    enableSorting: false,
    meta: { share: 11, min: 120 },
    cell: (info) => <Cell value={info.getValue()} />,
  }),

  helper.accessor("latestFundingAmount", {
    id: "latestFundingAmount",
    header: "Latest round",
    enableSorting: false,
    meta: { share: 10, min: 110 },
    cell: (info) => <Cell value={formatMoney(info.getValue())} />,
  }),

  helper.accessor("lastRaisedAt", {
    id: "lastRaised",
    header: "Last raised",
    enableSorting: false,
    meta: { share: 9, min: 104 },
    cell: (info) => <Cell value={info.getValue()} />,
  }),

  helper.accessor("numberOfRetailLocations", {
    id: "retailLocations",
    header: "Retail locations",
    enableSorting: false,
    meta: { share: 9, min: 120 },
    cell: (info) => <Cell value={info.getValue()?.toLocaleString() ?? null} />,
  }),

  helper.accessor("keywords", {
    id: "keywords",
    header: "Keywords",
    enableSorting: false,
    meta: { share: 18, min: 160 },
    cell: (info) => <Cell value={joined(info.getValue())} muted />,
  }),

  helper.accessor("technologies", {
    id: "technologies",
    header: "Technologies",
    enableSorting: false,
    meta: { share: 18, min: 160 },
    cell: (info) => <Cell value={joined(info.getValue())} muted />,
  }),

  helper.accessor("sicCodes", {
    id: "sicCodes",
    header: "SIC codes",
    enableSorting: false,
    meta: { share: 10, min: 110 },
    cell: (info) => <Cell value={joined(info.getValue())} />,
  }),

  helper.accessor("naicsCodes", {
    id: "naicsCodes",
    header: "NAICS codes",
    enableSorting: false,
    meta: { share: 10, min: 120 },
    cell: (info) => <Cell value={joined(info.getValue())} />,
  }),
]);

/**
 * What starts off. The visible set is the eight the table was designed around plus Links; everything
 * below Notes in the definitions above is offered by the Columns menu and shown only on request —
 * a table that opened with twenty-two columns would be a spreadsheet, not a triage screen.
 */
export const DEFAULT_COLUMN_VISIBILITY: ColumnVisibilityState = {
  location: false,
  founded: false,
  phone: false,
  state: false,
  address: false,
  parent: false,
  totalFunding: false,
  latestFunding: false,
  latestFundingAmount: false,
  lastRaised: false,
  retailLocations: false,
  keywords: false,
  technologies: false,
  sicCodes: false,
  naicsCodes: false,
};

/**
 * A scrolled row without its name is a line of anonymous figures, so the name travels with it. v9
 * names the pinned regions logically, so `start` follows the reading direction rather than the CSS.
 * Nothing is pinned to the end: Actions sits beside the name instead.
 */
export const COLUMN_PINNING: ColumnPinningState = {
  start: ["name"],
  end: [],
};

/** Absent on most rows for most networks, and a greyed placeholder would be four dead glyphs a row. */
function CompanyLink({
  url,
  icon,
  label,
  company,
}: {
  url: string | null;
  icon: string;
  label: string;
  company: CompanyResult;
}) {
  if (!url) return null;
  return (
    <a
      href={url}
      target="_blank"
      rel="noopener noreferrer"
      title={label}
      aria-label={`${company.companyName} on ${label}`}
      className={ICON_BUTTON}
    >
      <Icon d={icon} size={13} />
    </a>
  );
}

function Cell({ value, muted }: { value: string | null; muted?: boolean }) {
  return (
    <TruncatedText
      value={value}
      className={muted ? "font-sans text-[13px] text-text3" : "font-sans text-[13px] text-text2"}
    />
  );
}

/** Empty reads as unknown, not as an empty string — the column is absent on the row, not blank. */
function joined(values: string[]): string | null {
  return values.length > 0 ? values.join(", ") : null;
}

/** Null is the common case — nine rows in ten publish no figure — and reads as unknown, not zero. */
function formatMoney(amount: number | null): string {
  if (amount === null) return "—";
  if (amount >= 1_000_000_000) return `$${trim(amount / 1_000_000_000)}B`;
  if (amount >= 1_000_000) return `$${trim(amount / 1_000_000)}M`;
  return `$${amount.toLocaleString()}`;
}

function trim(value: number): string {
  return value >= 10 ? String(Math.round(value)) : value.toFixed(1).replace(/\.0$/, "");
}
