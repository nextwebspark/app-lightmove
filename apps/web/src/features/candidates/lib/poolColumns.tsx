import {
  columnOrderingFeature,
  columnPinningFeature,
  columnVisibilityFeature,
  createColumnHelper,
  rowSelectionFeature,
  rowSortingFeature,
  tableFeatures,
  type ColumnPinningState,
  type ColumnVisibilityState,
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
import { formatInstantDate } from "../../../lib/format";
import type { CandidateTag, PoolRow } from "../api/types";
import { CANDIDATE_SOURCE_STYLES } from "./candidateVocabulary";
import { lastActivityOf } from "./candidateActivity";
import { pipelineSwatchClass, statusStyle } from "./pipelineStatus";
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

/** The columns that start hidden; the Columns menu turns them on. */
export const DEFAULT_POOL_COLUMN_VISIBILITY: ColumnVisibilityState = {
  seniority: false,
  status: false,
  source: false,
  added: false,
  doNotContact: false,
};

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
      meta: { share: 18, min: 220, sortKind: "text" },
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
      meta: { share: 14, min: 160, sortKind: "text" },
      cell: (info) => <DataGridCell value={info.getValue()} />,
    }),

    helper.accessor("companyName", {
      id: "company",
      header: "Company",
      meta: { share: 14, min: 180, sortKind: "text" },
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
      meta: { share: 11, min: 150, sortKind: "text" },
      cell: (info) =>
        info.getValue() ? <DataGridCell value={info.getValue()} /> : <DataGridCell value="Not recorded" muted />,
    }),

    helper.accessor("yearsExperience", {
      id: "experience",
      header: "Experience",
      meta: { share: 0, min: 120, sortKind: "number" },
      cell: (info) => <DataGridCell value={experienceOf(info.row.original)} />,
    }),

    helper.accessor("seniority", {
      id: "seniority",
      header: "Seniority",
      meta: { share: 0, min: 110 },
      cell: (info) => <DataGridCell value={info.getValue()} />,
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
      meta: { share: 17, min: 200, sortKind: "number" },
      cell: (info) => {
        const positions = info.row.original.positions;
        if (positions.length === 0) return <DataGridCell value="Not in a position" muted />;
        return (
          <span className="flex min-w-0 items-center gap-1.5 overflow-hidden">
            {positions.slice(0, CHIPS_SHOWN).map((position) => {
              const status = statusStyle(position.status, position);
              return (
                <StatusChip
                  key={position.candidateId}
                  colour={status.colour}
                  label={`${position.positionTitle ?? "A position"} · ${status.label}`}
                  title={`${position.positionTitle ?? "A position"} — ${status.label}`}
                />
              );
            })}
            <MoreCount total={positions.length} />
          </span>
        );
      },
    }),

    helper.display({
      id: "status",
      header: "Status",
      enableSorting: false,
      meta: { share: 12, min: 170 },
      cell: (info) => <StatusChips row={info.row.original} />,
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
            <MoreCount total={tags.length} />
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

    helper.accessor("source", {
      id: "source",
      header: "Source",
      enableSorting: false,
      meta: { share: 0, min: 120 },
      cell: (info) => {
        const style = CANDIDATE_SOURCE_STYLES[info.getValue()];
        if (!style) return <DataGridCell value={null} />;
        return (
          <span className={cn("rounded-full px-2 py-px font-mono text-meta font-medium", style.className)}>
            {style.label}
          </span>
        );
      },
    }),

    helper.accessor("createdAt", {
      id: "added",
      header: "Added",
      meta: { share: 0, min: 120, sortKind: "date" },
      cell: (info) => <DataGridCell value={formatInstantDate(info.getValue())} />,
    }),

    helper.accessor("doNotContact", {
      id: "doNotContact",
      header: "Do not contact",
      enableSorting: false,
      meta: { share: 0, min: 130 },
      cell: (info) =>
        info.getValue() ? (
          <span className="inline-flex items-center gap-1 font-mono text-meta font-medium text-u-offlimits">
            <Icon d={ICONS.ban} size={12} />
            Do not contact
          </span>
        ) : (
          <DataGridCell value="—" muted />
        ),
    }),

    helper.accessor((row) => row.lastActivity?.occurredAt ?? "", {
      id: "activity",
      header: "Last activity",
      meta: { share: 15, min: 210, sortKind: "date" },
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

/**
 * The distinct statuses someone holds across their positions, latest first — what the Status filter matches,
 * since it reads every position rather than only the newest.
 */
function StatusChips({ row }: { row: PoolRow }) {
  const seen = new Map<string, ReturnType<typeof statusStyle>>();
  for (const position of row.positions) {
    if (!seen.has(position.status)) seen.set(position.status, statusStyle(position.status, position));
  }
  const statuses = [...seen.entries()];
  if (statuses.length === 0) return <DataGridCell value="Not in a position" muted />;
  return (
    <span className="flex min-w-0 items-center gap-1.5 overflow-hidden">
      {statuses.slice(0, CHIPS_SHOWN).map(([key, status]) => (
        <StatusChip key={key} colour={status.colour} label={status.label} title={status.label} />
      ))}
      <MoreCount total={statuses.length} />
    </span>
  );
}

function StatusChip({ colour, label, title }: { colour: string; label: string; title: string }) {
  return (
    <span
      title={title}
      className="flex min-w-0 items-center gap-1 rounded-full border border-u-border px-2 py-px font-mono text-[10.5px] text-u-text2"
    >
      <span aria-hidden className={cn("size-1.5 flex-none rounded-full", pipelineSwatchClass(colour))} />
      <span className="truncate">{label}</span>
    </span>
  );
}

/** "+N" for the chips a cell had no room to draw. */
function MoreCount({ total }: { total: number }) {
  if (total <= CHIPS_SHOWN) return null;
  return <span className="flex-none font-mono text-meta text-u-text3">+{total - CHIPS_SHOWN}</span>;
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
