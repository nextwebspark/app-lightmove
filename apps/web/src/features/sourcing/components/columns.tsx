import {
  columnPinningFeature,
  columnSizingFeature,
  columnVisibilityFeature,
  createColumnHelper,
  rowSortingFeature,
  tableFeatures,
} from "@tanstack/react-table";
import type { VisibilityState } from "@tanstack/react-table";
import { ICONS } from "../../../components/layout/Icon";
import { CompanyLogo } from "../../../components/ui/CompanyLogo";
import type { CompanyResult, MatchTier } from "../api/types";
import { ExternalLink } from "./ExternalLink";

/**
 * In v9 a feature's state and methods do not exist until it is registered. No sorted row model is
 * registered on purpose: the server has already ordered the page, so a client-side sort would reorder
 * it on top and quietly disagree with the header.
 */
export const sourcingTableFeatures = tableFeatures({
  rowSortingFeature,
  columnVisibilityFeature,
  columnPinningFeature,
  columnSizingFeature,
});

export type SourcingTableFeatures = typeof sourcingTableFeatures;

const helper = createColumnHelper<SourcingTableFeatures, CompanyResult>();

const TIER_META: Record<MatchTier, { label: string; className: string }> = {
  DIRECT: { label: "Direct", className: "text-sky bg-sky-dim" },
  ADJACENT: { label: "Adjacent", className: "text-amber bg-amber-dim" },
  INFERRED: { label: "AI Inferred", className: "text-text3 bg-line-soft" },
};

const MONO_CELL = "font-mono text-[12px] text-text2";

function orDash(value: string | number | null | undefined) {
  return value === null || value === undefined || value === "" ? "—" : String(value);
}

function listOrDash(values: string[]) {
  return values.length === 0 ? "—" : values.join(", ");
}

/**
 * A sortable column's id is the backend's own sort token (`CompanySortField`), so a header click passes
 * `sorting[0].id` straight to the API with no lookup table in between; display-only columns are free to
 * use any id.
 *
 * Sizes are hints, not pixels — `CompanyTable` spends them proportionally when the visible set fits and
 * literally when it doesn't, and `minSize` is what decides between those.
 */
export const sourcingColumns = helper.columns([
  helper.accessor("name", {
    id: "name",
    header: "Company",
    // Wider than before by the mark plus its gap, so the name keeps the room it had.
    size: 290,
    minSize: 210,
    // The row's identity: hiding it would leave attributes belonging to nobody.
    enableHiding: false,
    cell: (info) => (
      <span className="flex items-center gap-2">
        <CompanyLogo name={info.getValue()} logo={info.row.original.logo} size={20} />
        <span className="font-sans text-[13px] font-semibold text-text">{info.getValue()}</span>
      </span>
    ),
  }),
  helper.accessor("matchTier", {
    id: "tier",
    header: "Tier",
    size: 110,
    minSize: 100,
    cell: (info) => {
      const tier = TIER_META[info.getValue()];
      return (
        <span
          className={`inline-flex items-center rounded-[5px] px-[7px] py-[2px] font-mono text-[9.5px] font-bold uppercase tracking-[0.06em] ${tier.className}`}
        >
          {tier.label}
        </span>
      );
    },
  }),
  helper.accessor("sector", {
    id: "sector",
    header: "Sector",
    size: 200,
    minSize: 140,
    cell: (info) => <span className={MONO_CELL}>{orDash(info.getValue())}</span>,
  }),
  helper.accessor("employeeRange", {
    id: "employees",
    header: "Employees",
    size: 130,
    minSize: 110,
    cell: (info) => <span className={MONO_CELL}>{orDash(info.getValue())}</span>,
  }),
  helper.accessor("revenueRange", {
    id: "revenue",
    header: "Revenue",
    size: 130,
    minSize: 110,
    cell: (info) => <span className={MONO_CELL}>{orDash(info.getValue())}</span>,
  }),
  helper.accessor("location", {
    id: "location",
    header: "Location",
    size: 200,
    minSize: 140,
    cell: (info) => <span className={MONO_CELL}>{orDash(info.getValue())}</span>,
  }),
  helper.accessor("country", {
    id: "country",
    header: "Country",
    size: 110,
    minSize: 100,
    cell: (info) => <span className={MONO_CELL}>{orDash(info.getValue())}</span>,
  }),
  helper.accessor("founded", {
    id: "founded",
    header: "Founded",
    size: 110,
    minSize: 100,
    cell: (info) => <span className={MONO_CELL}>{orDash(info.getValue())}</span>,
  }),
  helper.accessor("ownership", {
    id: "ownership",
    header: "Ownership",
    size: 150,
    minSize: 120,
    enableSorting: false,
    cell: (info) => <span className={MONO_CELL}>{orDash(info.getValue())}</span>,
  }),
  helper.accessor("ipoStatus", {
    id: "ipoStatus",
    header: "IPO status",
    size: 130,
    minSize: 110,
    enableSorting: false,
    cell: (info) => <span className={MONO_CELL}>{orDash(info.getValue())}</span>,
  }),
  helper.accessor("orgType", {
    id: "orgType",
    header: "Org type",
    size: 130,
    minSize: 110,
    enableSorting: false,
    cell: (info) => <span className={MONO_CELL}>{orDash(info.getValue())}</span>,
  }),
  helper.display({
    id: "links",
    header: "Links",
    size: 90,
    minSize: 80,
    enableSorting: false,
    cell: (info) => {
      const company = info.row.original;
      return (
        <span className="flex items-center gap-2">
          <ExternalLink
            url={company.website ?? company.domain}
            icon={ICONS.globe}
            label={`${company.name} website`}
          />
          <ExternalLink
            url={company.linkedinUrl}
            icon={ICONS.linkedin}
            label={`${company.name} on LinkedIn`}
          />
        </span>
      );
    },
  }),
  helper.accessor("description", {
    id: "description",
    header: "Description",
    size: 320,
    minSize: 220,
    enableSorting: false,
    cell: (info) => (
      <span className="font-sans text-[12.5px] text-text2">{orDash(info.getValue())}</span>
    ),
  }),
  helper.accessor("slogan", {
    id: "slogan",
    header: "Slogan",
    size: 220,
    minSize: 160,
    enableSorting: false,
    cell: (info) => (
      <span className="font-sans text-[12.5px] text-text2">{orDash(info.getValue())}</span>
    ),
  }),
  helper.accessor("specialties", {
    id: "specialties",
    header: "Specialties",
    size: 240,
    minSize: 170,
    enableSorting: false,
    cell: (info) => <span className={MONO_CELL}>{listOrDash(info.getValue())}</span>,
  }),
  helper.accessor("industryTags", {
    id: "industryTags",
    header: "Industry tags",
    size: 240,
    minSize: 170,
    enableSorting: false,
    cell: (info) => <span className={MONO_CELL}>{listOrDash(info.getValue())}</span>,
  }),
]);

/** How the picker groups what it offers. */
export const COLUMN_GROUPS: { label: string; columnIds: string[] }[] = [
  { label: "Company", columnIds: ["name", "tier", "sector", "employees", "revenue", "location"] },
  { label: "Firmographics", columnIds: ["country", "founded", "ownership", "ipoStatus", "orgType"] },
  { label: "Web & identity", columnIds: ["links"] },
  { label: "Narrative", columnIds: ["description", "slogan", "specialties", "industryTags"] },
];

/** The table as it opens. Kept apart from the groups above, which say where a column sits in the
 *  picker rather than whether a reader starts with it. */
const DEFAULT_COLUMN_IDS = [...COLUMN_GROUPS[0].columnIds, "links"];

export const DEFAULT_COLUMN_VISIBILITY: VisibilityState = Object.fromEntries(
  sourcingColumns.map((column) => [
    column.id as string,
    DEFAULT_COLUMN_IDS.includes(column.id as string),
  ]),
);
