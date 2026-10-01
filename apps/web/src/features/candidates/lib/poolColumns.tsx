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
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Avatar } from "../../../components/ui/Avatar";
import { CompanyLink } from "../../../components/ui/CompanyLink";
import { CompanyLogo } from "../../../components/ui/CompanyLogo";
import { DataGridCell, type DataGridColumnLayout } from "../../../components/ui/DataGrid";
import { NetworkMark } from "../../../components/ui/NetworkMark";
import { TruncatedText } from "../../../components/ui/TruncatedText";
import { cn } from "../../../lib/cn";
import type { Member } from "../../workspace/api/types";
import type { CandidateTag, PoolRow } from "../api/types";
import { lastActivityOf } from "./candidateActivity";
import { candidateStatusStyle } from "./candidateVocabulary";
import { PersonAvatar } from "../components/pool/PersonAvatar";
import { TagPill } from "../components/pool/TagPill";

export const poolTableFeatures = tableFeatures({
  columnOrderingFeature,
  columnPinningFeature,
  columnVisibilityFeature,
  rowSelectionFeature,
  rowSortingFeature,
  columnMeta: {} as DataGridColumnLayout,
});

const helper = createColumnHelper<typeof poolTableFeatures, PoolRow>();

export const POOL_COLUMN_PINNING: ColumnPinningState = { start: ["name"], end: [] };

/** How many chips a cell draws before it says "+N". */
const CHIPS_SHOWN = 2;

/**
 * The Candidates page's columns. The person reads as Strategy → People draws one — photo and name, their
 * LinkedIn, title, company, location, experience, which contacts are on file — so someone looks the same
 * before and after they are filed; then what the team knows: positions, tags, owner and the latest line.
 * Built per render because the tags and the owner are read off the workspace's catalog and roster.
 */
export function poolColumnsFor(tagsById: Map<string, CandidateTag>, membersByUserId: Map<string, Member>) {
  return helper.columns([
    helper.accessor("fullName", {
      id: "name",
      header: "Person",
      enableHiding: false,
      meta: { share: 18, min: 220 },
      cell: (info) => {
        const row = info.row.original;
        return (
          <span className="flex min-w-0 items-center gap-2.5">
            <PersonAvatar person={row} size="md" />
            <TruncatedText value={row.fullName} className="font-sans text-[13px] font-medium text-u-text" />
            {row.doNotContact && (
              <span title="Do not contact" className="flex-none text-u-offlimits">
                <Icon d={ICONS.ban} size={13} />
                <span className="sr-only">Do not contact</span>
              </span>
            )}
          </span>
        );
      },
    }),

    helper.display({
      id: "links",
      header: "Links",
      enableSorting: false,
      meta: { share: 0, min: 72 },
      cell: (info) => (
        <CompanyLink
          url={info.row.original.linkedinUrl}
          icon={<NetworkMark network="linkedin" size={14} />}
          label="LinkedIn"
          companyName={info.row.original.fullName}
          reserve
        />
      ),
    }),

    helper.accessor("title", {
      id: "title",
      header: "Title",
      enableSorting: false,
      meta: { share: 14, min: 160 },
      cell: (info) => <DataGridCell value={info.getValue()} />,
    }),

    helper.accessor("companyName", {
      id: "company",
      header: "Company",
      enableSorting: false,
      meta: { share: 14, min: 180 },
      cell: (info) => {
        const name = info.getValue();
        if (!name) return <DataGridCell value={null} />;
        return (
          <span className="flex min-w-0 items-center gap-2.5">
            <CompanyLogo name={name} logo={info.row.original.companyLogoUrl} size={28} />
            <TruncatedText value={name} className="min-w-0 flex-1 font-sans text-[13px] text-u-text2" />
          </span>
        );
      },
    }),

    helper.accessor((row) => [row.locationCity, row.locationCountry].filter(Boolean).join(", "), {
      id: "location",
      header: "Location",
      meta: { share: 11, min: 150 },
      cell: (info) =>
        info.getValue() ? <DataGridCell value={info.getValue()} /> : <DataGridCell value="Not recorded" muted />,
    }),

    helper.display({
      id: "experience",
      header: "Experience",
      enableSorting: false,
      meta: { share: 0, min: 120 },
      cell: (info) => <DataGridCell value={experienceOf(info.row.original)} />,
    }),

    helper.display({
      id: "contact",
      header: "Contact",
      enableSorting: false,
      meta: { share: 0, min: 88 },
      cell: (info) => <ContactMarks row={info.row.original} />,
    }),

    helper.accessor((row) => row.positions.length, {
      id: "positions",
      header: "Positions",
      meta: { share: 17, min: 200 },
      cell: (info) => {
        const positions = info.row.original.positions;
        if (positions.length === 0) return <DataGridCell value="Not in a position" muted />;
        return (
          <span className="flex min-w-0 items-center gap-1.5 overflow-hidden">
            {positions.slice(0, CHIPS_SHOWN).map((position) => {
              const status = candidateStatusStyle(position.status);
              return (
                <span
                  key={position.candidateId}
                  title={`${position.positionTitle ?? "A position"} — ${status.label}`}
                  className="flex min-w-0 items-center gap-1 rounded-full border border-u-border px-2 py-px font-mono text-[10.5px] text-u-text2"
                >
                  <span aria-hidden className={cn("size-1.5 flex-none rounded-full", status.className)} />
                  <span className="truncate">
                    {position.positionTitle ?? "A position"} · {status.label}
                  </span>
                </span>
              );
            })}
            {positions.length > CHIPS_SHOWN && (
              <span className="flex-none font-mono text-[11px] text-u-text3">+{positions.length - CHIPS_SHOWN}</span>
            )}
          </span>
        );
      },
    }),

    helper.display({
      id: "tags",
      header: "Tags",
      enableSorting: false,
      meta: { share: 14, min: 190 },
      cell: (info) => {
        const tags = info.row.original.tagIds
          .map((id) => tagsById.get(id))
          .filter((tag): tag is CandidateTag => tag !== undefined);
        if (tags.length === 0) return <DataGridCell value={null} />;
        return (
          <span className="flex min-w-0 items-center gap-1 overflow-hidden">
            {tags.slice(0, CHIPS_SHOWN).map((tag) => (
              <TagPill key={tag.id} tag={tag} />
            ))}
            {tags.length > CHIPS_SHOWN && (
              <span className="flex-none font-mono text-[11px] text-u-text3">+{tags.length - CHIPS_SHOWN}</span>
            )}
          </span>
        );
      },
    }),

    helper.display({
      id: "owner",
      header: "Owner",
      enableSorting: false,
      meta: { share: 0, min: 88 },
      cell: (info) => {
        const ownerId = info.row.original.ownerUserId;
        const owner = ownerId ? membersByUserId.get(ownerId) : undefined;
        if (!owner) return <DataGridCell value="—" muted />;
        return (
          <Avatar id={owner.userId} name={owner.fullName} src={owner.avatarUrl} size="sm" title={`Owner · ${owner.fullName}`} />
        );
      },
    }),

    helper.accessor((row) => row.lastActivity?.occurredAt ?? "", {
      id: "activity",
      header: "Last activity",
      meta: { share: 15, min: 210 },
      cell: (info) => {
        const latest = info.row.original.lastActivity;
        if (!latest) return <DataGridCell value="Nothing recorded" muted />;
        const line = lastActivityOf(latest);
        return (
          <span className="flex min-w-0 flex-col">
            <TruncatedText value={line.text} className="font-sans text-[12.5px] text-u-text2" />
            <TruncatedText value={line.meta} className="font-mono text-[11px] text-u-text3" />
          </span>
        );
      },
    }),
  ]);
}

function experienceOf(row: PoolRow): string | null {
  const parts = [
    row.yearsExperience != null && `${row.yearsExperience} yrs`,
    row.careerRoles > 0 && `${row.careerRoles} ${row.careerRoles === 1 ? "role" : "roles"}`,
  ].filter(Boolean);
  return parts.length > 0 ? parts.join(" · ") : null;
}

/** The Contact section's glyphs for the channels on file, as Strategy marks what ContactOut holds. */
function ContactMarks({ row }: { row: PoolRow }) {
  if (!row.hasEmail && !row.hasPhone) return <DataGridCell value={null} />;
  return (
    <span className="flex items-center gap-2 text-u-text2">
      {row.hasEmail && (
        <span title="An email on file" className="inline-flex items-center gap-1 font-mono text-[11px]">
          <Icon d={ICONS.mail} size={13} />✓
        </span>
      )}
      {row.hasPhone && (
        <span title="A phone on file" className="inline-flex items-center gap-1 font-mono text-[11px]">
          <Icon d={ICONS.phone} size={13} />✓
        </span>
      )}
    </span>
  );
}
