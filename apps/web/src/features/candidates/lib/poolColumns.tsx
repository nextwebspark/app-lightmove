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
import { DataGridCell, type DataGridColumnLayout } from "../../../components/ui/DataGrid";
import { TruncatedText } from "../../../components/ui/TruncatedText";
import { cn } from "../../../lib/cn";
import type { Member } from "../../workspace/api/types";
import type { CandidateTag, PoolRow, PoolSortField } from "../api/types";
import { lastActivityOf } from "./candidateActivity";
import { candidateStatusStyle } from "./candidateVocabulary";
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

/** The columns the server sorts by — the grid's allowlist, as `PoolSortField` spells them. */
export const POOL_SORT_FIELDS: readonly PoolSortField[] = ["name", "location", "positions", "activity"];

export const POOL_COLUMN_PINNING: ColumnPinningState = { start: ["name"], end: [] };

/** How many chips a cell draws before it says "+N". */
const CHIPS_SHOWN = 2;

/**
 * The Candidates page's columns, as `Candidates.dc.html` draws them: the person with their headline,
 * where they live, the positions they are in, the team's tags, the owner, and the latest line of their
 * history. Built per render because the tags and the owner are read off the workspace's catalog and roster.
 */
export function poolColumnsFor(tagsById: Map<string, CandidateTag>, membersByUserId: Map<string, Member>) {
  return helper.columns([
    helper.accessor("fullName", {
      id: "name",
      header: "Candidate",
      enableHiding: false,
      meta: { share: 24, min: 250 },
      cell: (info) => {
        const row = info.row.original;
        const headline = [row.title, row.companyName].filter(Boolean).join(" · ");
        return (
          <span className="flex min-w-0 items-center gap-2.5">
            <Avatar id={row.personId} name={row.fullName} size="lg" />
            <span className="flex min-w-0 flex-col">
              <span className="flex min-w-0 items-center gap-1.5">
                <TruncatedText value={row.fullName} className="font-sans text-[13px] font-semibold text-u-text" />
                {row.doNotContact && (
                  <span title="Do not contact" className="flex-none text-u-offlimits">
                    <Icon d={ICONS.ban} size={13} />
                    <span className="sr-only">Do not contact</span>
                  </span>
                )}
              </span>
              <TruncatedText value={headline || "—"} className="font-sans text-[12px] text-u-text3" />
            </span>
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
