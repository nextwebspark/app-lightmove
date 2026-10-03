import { useState } from "react";
import { Icon, ICONS } from "../../../../components/layout/Icon";
import { FilterAccordion } from "../../../../components/ui/FilterAccordion";
import { FilterCheckRow } from "../../../../components/ui/FilterCheckRow";
import { FilterRail } from "../../../../components/ui/FilterRail";
import { SegmentedControl } from "../../../../components/ui/SegmentedControl";
import type { Project } from "../../../projects/api/types";
import type { Member } from "../../../workspace/api/types";
import type { CandidateStatus, CandidateTag, PoolFilters, TagMatch } from "../../api/types";
import { CANDIDATE_STATUSES, candidateStatusStyle } from "../../lib/candidateVocabulary";
import { countActiveFilters, withoutRailFilters } from "../../lib/poolFilters";

const TAG_MATCHES: { value: TagMatch; label: string }[] = [
  { value: "any", label: "Any of" },
  { value: "all", label: "All of" },
  { value: "none", label: "None of" },
];

type RailSection = "tags" | "position" | "status" | "owner" | "country";

/** Past this many rows a section offers a box to narrow them. */
const NARROW_FROM = 8;

/**
 * The Candidates filter rail, Strategy's shape: who matches at the top, one accordion per axis, and
 * Clear all at the foot. Every axis but tags is a single value on the server, so ticking a second row
 * replaces the first and ticking the chosen one clears it.
 */
export function PoolFilterRail({
  filters,
  onChange,
  onClose,
  tags,
  staff,
  positions,
  countries,
  shown,
  pool,
}: {
  filters: PoolFilters;
  onChange: (filters: PoolFilters) => void;
  onClose: () => void;
  tags: CandidateTag[];
  staff: Member[];
  positions: Project[];
  countries: string[];
  shown: number | null;
  pool: number | null;
}) {
  const [open, setOpen] = useState<RailSection | null>(null);
  const toggleOpen = (section: RailSection) => setOpen((current) => (current === section ? null : section));
  const pick = <K extends "position" | "status" | "owner" | "country">(key: K, value: PoolFilters[K]) =>
    onChange({ ...filters, [key]: filters[key] === value ? "" : value });
  const toggleTag = (tagId: string) =>
    onChange({
      ...filters,
      tagIds: filters.tagIds.includes(tagId)
        ? filters.tagIds.filter((id) => id !== tagId)
        : [...filters.tagIds, tagId],
    });

  const positionTitle = positions.find((position) => position.id === filters.position)?.positionTitle;
  const ownerName =
    filters.owner === "nobody" ? "Nobody" : staff.find((member) => member.userId === filters.owner)?.fullName;
  const ownerRows = [
    { value: "nobody", label: "Nobody" },
    ...staff.map((member) => ({ value: member.userId, label: member.fullName })),
  ];

  return (
    <FilterRail label="Filters" onClose={onClose}>
      <div className="border-b border-u-border px-4 py-3" aria-live="polite">
        <span className="type-summary-label text-u-text3">People match</span>
        <p className="mt-0.5 text-title font-semibold tabular-nums tracking-tight text-u-text">
          {shown === null ? "—" : shown.toLocaleString()}
          {pool !== null && (
            <span className="ms-1.5 text-note font-medium text-u-text3">of {pool.toLocaleString()}</span>
          )}
        </p>
      </div>

      <div className="min-h-0 flex-1 overflow-y-auto">
        <FilterAccordion
          label="Tags"
          selected={filters.tagIds.map((id) => ({
            value: id,
            label: tags.find((tag) => tag.id === id)?.label ?? "a tag",
          }))}
          onRemove={toggleTag}
          open={open === "tags"}
          onToggleOpen={() => toggleOpen("tags")}
          onReset={() => onChange({ ...filters, tagIds: [] })}
        >
          <div className="flex flex-col gap-2.5">
            <SegmentedControl
              label="Match tags"
              options={TAG_MATCHES}
              value={filters.tagMatch}
              onChange={(tagMatch) => onChange({ ...filters, tagMatch })}
            />
            {tags.length === 0 ? (
              <p className="text-meta text-u-text3">No tags yet — add one from a person&apos;s drawer.</p>
            ) : (
              <NarrowableRows
                label="tags"
                rows={tags.map((tag) => ({ value: tag.id, label: tag.label, count: tag.holders }))}
                isChecked={(value) => filters.tagIds.includes(value)}
                onToggle={toggleTag}
              />
            )}
          </div>
        </FilterAccordion>

        <FilterAccordion
          label="Position"
          selected={filters.position ? [{ value: filters.position, label: positionTitle ?? "a position" }] : []}
          onRemove={() => onChange({ ...filters, position: "" })}
          open={open === "position"}
          onToggleOpen={() => toggleOpen("position")}
          onReset={() => onChange({ ...filters, position: "" })}
        >
          <NarrowableRows
            label="positions"
            rows={positions.map((position) => ({ value: position.id, label: position.positionTitle }))}
            isChecked={(value) => filters.position === value}
            onToggle={(value) => pick("position", value)}
          />
        </FilterAccordion>

        <FilterAccordion
          label="Status"
          selected={filters.status ? [{ value: filters.status, label: candidateStatusStyle(filters.status).label }] : []}
          onRemove={() => onChange({ ...filters, status: "" })}
          open={open === "status"}
          onToggleOpen={() => toggleOpen("status")}
          onReset={() => onChange({ ...filters, status: "" })}
        >
          <NarrowableRows
            label="statuses"
            rows={CANDIDATE_STATUSES.map((status) => ({ value: status.value, label: status.label }))}
            isChecked={(value) => filters.status === value}
            onToggle={(value) => pick("status", value as CandidateStatus)}
          />
          <p className="mt-2 text-meta text-u-text3">Reads from the positions a person is in.</p>
        </FilterAccordion>

        <FilterAccordion
          label="Owner"
          selected={filters.owner ? [{ value: filters.owner, label: ownerName ?? "someone" }] : []}
          onRemove={() => onChange({ ...filters, owner: "" })}
          open={open === "owner"}
          onToggleOpen={() => toggleOpen("owner")}
          onReset={() => onChange({ ...filters, owner: "" })}
        >
          <NarrowableRows
            label="owners"
            rows={ownerRows}
            isChecked={(value) => filters.owner === value}
            onToggle={(value) => pick("owner", value)}
          />
        </FilterAccordion>

        <FilterAccordion
          label="Country"
          selected={filters.country ? [{ value: filters.country, label: filters.country }] : []}
          onRemove={() => onChange({ ...filters, country: "" })}
          open={open === "country"}
          onToggleOpen={() => toggleOpen("country")}
          onReset={() => onChange({ ...filters, country: "" })}
        >
          <NarrowableRows
            label="countries"
            rows={countries.map((country) => ({ value: country, label: country }))}
            isChecked={(value) => filters.country === value}
            onToggle={(value) => pick("country", value)}
          />
        </FilterAccordion>
      </div>

      <div className="border-t border-u-border bg-u-surface p-3">
        <button
          type="button"
          disabled={countActiveFilters(filters) === 0}
          onClick={() => onChange(withoutRailFilters(filters))}
          className="w-full rounded-[7px] border border-u-border-strong px-4 py-2 text-note font-semibold text-u-text2 transition hover:text-u-text disabled:opacity-40"
        >
          Clear all filters
        </button>
      </div>
    </FilterRail>
  );
}

/** A section's rows, with a box to narrow them once there are more than a glance takes in. */
function NarrowableRows({
  label,
  rows,
  isChecked,
  onToggle,
}: {
  label: string;
  rows: { value: string; label: string; count?: number }[];
  isChecked: (value: string) => boolean;
  onToggle: (value: string) => void;
}) {
  const [query, setQuery] = useState("");
  const needle = query.trim().toLowerCase();
  const shownRows = needle ? rows.filter((row) => row.label.toLowerCase().includes(needle)) : rows;

  if (rows.length === 0) return <p className="text-meta text-u-text3">None on file yet.</p>;
  return (
    <div className="flex flex-col gap-1">
      {rows.length > NARROW_FROM && (
        <input
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder="Type to narrow…"
          aria-label={`Narrow ${label}`}
          className="mb-1 w-full rounded-[6px] border border-u-border-strong bg-u-raised px-2.5 py-1.5 text-note text-u-text outline-none placeholder:text-u-text3 focus:border-u-text3"
        />
      )}
      <div className="max-h-[280px] overflow-y-auto">
        {shownRows.map((row) => (
          <FilterCheckRow
            key={row.value}
            label={row.label}
            count={row.count}
            checked={isChecked(row.value)}
            onToggle={() => onToggle(row.value)}
          />
        ))}
        {shownRows.length === 0 && <p className="px-1 py-1.5 text-meta text-u-text3">Nothing matches.</p>}
      </div>
    </div>
  );
}

/** One removable chip per filter in force, then Clear all — what the hidden rail is narrowing by. */
export function ActiveFilters({
  filters,
  onChange,
  tagsById,
  membersByUserId,
  positions,
}: {
  filters: PoolFilters;
  onChange: (filters: PoolFilters) => void;
  tagsById: Map<string, CandidateTag>;
  membersByUserId: Map<string, Member>;
  positions: Project[];
}) {
  const chips: { key: string; label: string; clear: () => void }[] = [];
  if (filters.tagIds.length > 0) {
    const matchLabel = TAG_MATCHES.find((match) => match.value === filters.tagMatch)?.label ?? "Any of";
    const labels = filters.tagIds.map((id) => tagsById.get(id)?.label ?? "a tag").join(", ");
    chips.push({ key: "tags", label: `${matchLabel}: ${labels}`, clear: () => onChange({ ...filters, tagIds: [] }) });
  }
  if (filters.position) {
    const title = positions.find((position) => position.id === filters.position)?.positionTitle ?? "a position";
    chips.push({ key: "position", label: `Position: ${title}`, clear: () => onChange({ ...filters, position: "" }) });
  }
  if (filters.status) {
    chips.push({
      key: "status",
      label: `Status: ${candidateStatusStyle(filters.status).label}`,
      clear: () => onChange({ ...filters, status: "" }),
    });
  }
  if (filters.owner) {
    const name = filters.owner === "nobody" ? "Nobody" : (membersByUserId.get(filters.owner)?.fullName ?? "someone");
    chips.push({ key: "owner", label: `Owner: ${name}`, clear: () => onChange({ ...filters, owner: "" }) });
  }
  if (filters.country) {
    chips.push({ key: "country", label: `Country: ${filters.country}`, clear: () => onChange({ ...filters, country: "" }) });
  }
  if (chips.length === 0) return null;

  return (
    <div className="flex flex-wrap items-center gap-1.5 px-1">
      {chips.map((chip) => (
        <span
          key={chip.key}
          className="flex items-center gap-1.5 rounded-full border border-u-border-strong bg-u-raised py-[3px] pe-1 ps-2.5 text-xs font-medium text-u-text2"
        >
          {chip.label}
          <button
            type="button"
            aria-label={`Remove filter ${chip.label}`}
            onClick={chip.clear}
            className="grid size-[18px] place-items-center rounded-full text-u-text3 hover:bg-u-surface hover:text-u-text"
          >
            <Icon d={ICONS.close} size={10} />
          </button>
        </span>
      ))}
      <button
        type="button"
        onClick={() => onChange(withoutRailFilters(filters))}
        className="ms-1 text-xs font-medium text-u-accent hover:underline"
      >
        Clear all
      </button>
    </div>
  );
}
