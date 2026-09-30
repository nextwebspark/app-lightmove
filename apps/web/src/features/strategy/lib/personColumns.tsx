import {
  columnOrderingFeature,
  columnPinningFeature,
  columnVisibilityFeature,
  createColumnHelper,
  rowSelectionFeature,
  rowSortingFeature,
  tableFeatures,
  type ColumnPinningState,
} from "@tanstack/react-table";
import { Avatar } from "../../../components/ui/Avatar";
import { CompanyLinks } from "../../../components/ui/CompanyLink";
import { CompanyLogo } from "../../../components/ui/CompanyLogo";
import { DataGridCell, type DataGridColumnLayout } from "../../../components/ui/DataGrid";
import { DetailPill } from "../../../components/ui/DetailList";
import { TruncatedText } from "../../../components/ui/TruncatedText";
import type { PersonResult } from "../api/types";
import { ContactAvailabilityMarks, PersonLinks } from "../components/people/PersonLinks";

/** Registered for the shared grid's shape only: nothing sorts, since ContactOut states no order but its own. */
export const personTableFeatures = tableFeatures({
  columnOrderingFeature,
  columnPinningFeature,
  columnVisibilityFeature,
  rowSelectionFeature,
  rowSortingFeature,
  columnMeta: {} as DataGridColumnLayout,
});

const helper = createColumnHelper<typeof personTableFeatures, PersonResult>();

/**
 * A page of People search in the Companies grid's own columns and cells: the person where the In
 * universe grid puts the executive, their employer with its logo, and the LinkedIn marks in a Links
 * column rather than beside a name — the same reading, before and after someone is filed.
 */
export const personColumns = helper.columns([
  helper.accessor((person) => person.fullName ?? person.linkedinSlug, {
    id: "name",
    header: "Person",
    enableHiding: false,
    enableSorting: false,
    meta: { share: 20, min: 220 },
    cell: (info) => (
      <span className="flex min-w-0 items-center gap-2.5">
        <Avatar id={info.row.original.linkedinSlug} name={info.getValue()} src={info.row.original.photoUrl} size="md" />
        <TruncatedText value={info.getValue()} className="font-sans text-[13px] font-medium text-u-text" />
      </span>
    ),
  }),

  helper.display({
    id: "links",
    header: "Links",
    enableSorting: false,
    meta: { share: 0, min: 88 },
    cell: (info) => <PersonLinks person={info.row.original} reserve />,
  }),

  helper.accessor("title", {
    id: "title",
    header: "Title",
    enableSorting: false,
    meta: { share: 18, min: 160 },
    cell: (info) => <DataGridCell value={info.getValue()} />,
  }),

  helper.accessor("companyName", {
    id: "company",
    header: "Company",
    enableSorting: false,
    meta: { share: 20, min: 200 },
    cell: (info) => {
      const person = info.row.original;
      const name = info.getValue();
      if (!name) return <DataGridCell value={null} />;
      return (
        <span className="flex min-w-0 items-center gap-2.5">
          <CompanyLogo name={name} logo={person.companyLogoUrl} size={28} />
          <TruncatedText value={name} className="min-w-0 flex-1 font-sans text-[13px] text-u-text2" />
          <CompanyLinks
            companyName={name}
            website={person.details?.company?.website ?? null}
            linkedinUrl={person.companyLinkedinUrl}
          />
        </span>
      );
    },
  }),

  helper.accessor("location", {
    id: "location",
    header: "Location",
    enableSorting: false,
    meta: { share: 16, min: 150 },
    cell: (info) => <DataGridCell value={info.getValue()} />,
  }),

  helper.accessor((person) => person.career.length, {
    id: "experience",
    header: "Experience",
    enableSorting: false,
    meta: { share: 8, min: 96 },
    cell: (info) => <DataGridCell value={info.getValue() > 0 ? `${info.getValue()} roles` : null} />,
  }),

  helper.display({
    id: "contact",
    header: "Contact",
    enableSorting: false,
    meta: { share: 0, min: 96 },
    cell: (info) => <ContactAvailabilityMarks person={info.row.original} />,
  }),

  helper.accessor("held", {
    id: "held",
    header: "Status",
    enableSorting: false,
    meta: { share: 0, min: 104 },
    cell: (info) =>
      info.getValue() ? <DetailPill label="In mandate" className="bg-u-accent-tint text-u-accent" /> : null,
  }),
]);

export const PERSON_COLUMN_PINNING: ColumnPinningState = { start: ["name"], end: [] };
