import { Fragment, useState } from "react";
import { FilterAccordion } from "../../../../components/ui/FilterAccordion";
import { FilterCheckRow } from "../../../../components/ui/FilterCheckRow";
import { FilterRail } from "../../../../components/ui/FilterRail";
import { SegmentedControl } from "../../../../components/ui/SegmentedControl";
import type { Project } from "../../../projects/api/types";
import type { Member } from "../../../workspace/api/types";
import type { CandidateTag, PoolFilters } from "../../api/types";
import { liveStages, liveStatuses } from "../../lib/pipelineStatus";
import { countActiveFilters, TAG_MATCHES, withoutRailFilters } from "../../lib/poolFilters";
import { usePipelineCatalog } from "../../../workspace/lib/usePipelineCatalog";

type SingleValueAxis = "position" | "owner" | "country";
type RailSection = "tags" | "status" | SingleValueAxis;

interface Row {
  value: string;
  label: string;
  count?: number;
}

/** Past this many rows a section offers a box to narrow them. */
const NARROW_FROM = 8;

/**
 * The Candidates filter rail, Strategy's shape: who matches at the top, one accordion per axis, and
 * Clear all at the foot. Every axis but tags is a single value on the server, so ticking a second row
 * replaces the first and ticking the chosen one clears it.
 */
export function PoolFilterRail({
  open,
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
  open: boolean;
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
  const [openSection, setOpenSection] = useState<RailSection | null>(null);
  const toggleSection = (section: RailSection) =>
    setOpenSection((current) => (current === section ? null : section));
  const selectSingle = (axis: SingleValueAxis, value: string) =>
    onChange({ ...filters, [axis]: filters[axis] === value ? "" : value });
  const toggleTag = (tagId: string) =>
    onChange({
      ...filters,
      tagIds: filters.tagIds.includes(tagId)
        ? filters.tagIds.filter((id) => id !== tagId)
        : [...filters.tagIds, tagId],
    });

  const singleValueSections: { axis: SingleValueAxis; label: string; rows: Row[] }[] = [
    {
      axis: "position",
      label: "Position",
      rows: positions.map((position) => ({ value: position.id, label: position.positionTitle })),
    },
    {
      axis: "owner",
      label: "Owner",
      rows: [
        { value: "nobody", label: "Nobody" },
        ...staff.map((member) => ({ value: member.userId, label: member.fullName })),
      ],
    },
    { axis: "country", label: "Country", rows: countries.map((country) => ({ value: country, label: country })) },
  ];

  return (
    <FilterRail label="Filters" open={open} onClose={onClose}>
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
          onReset={() => onChange({ ...filters, tagIds: [] })}
          open={openSection === "tags"}
          onToggleOpen={() => toggleSection("tags")}
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

        {singleValueSections.map(({ axis, label, rows }) => {
          const value = filters[axis];
          const chosen = rows.find((row) => row.value === value);
          return (
            <Fragment key={axis}>
              {axis === "owner" && (
                <StageStatusSection
                  filters={filters}
                  onChange={onChange}
                  open={openSection === "status"}
                  onToggleOpen={() => toggleSection("status")}
                />
              )}
              <FilterAccordion
                label={label}
                selected={value ? [{ value, label: chosen?.label ?? value }] : []}
                onRemove={() => onChange({ ...filters, [axis]: "" })}
                onReset={() => onChange({ ...filters, [axis]: "" })}
                open={openSection === axis}
                onToggleOpen={() => toggleSection(axis)}
              >
                <NarrowableRows
                  label={label.toLowerCase()}
                  rows={rows}
                  isChecked={(row) => value === row}
                  onToggle={(row) => selectSingle(axis, row)}
                />
              </FilterAccordion>
            </Fragment>
          );
        })}
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

/**
 * Stage and status as one facet: a stage narrows to everyone at any of its statuses, a status to that
 * status alone, and choosing one clears the other.
 */
function StageStatusSection({
  filters,
  onChange,
  open,
  onToggleOpen,
}: {
  filters: PoolFilters;
  onChange: (filters: PoolFilters) => void;
  open: boolean;
  onToggleOpen: () => void;
}) {
  const { catalog } = usePipelineCatalog();
  const stages = liveStages(catalog);
  const allStatuses = catalog.stages.flatMap((stage) => stage.statuses);
  const selected = filters.pipelineStage
    ? [
        {
          value: filters.pipelineStage,
          label: stages.find((stage) => stage.key === filters.pipelineStage)?.label ?? filters.pipelineStage,
        },
      ]
    : filters.status
      ? [{ value: filters.status, label: allStatuses.find((s) => s.key === filters.status)?.label ?? filters.status }]
      : [];
  const clear = () => onChange({ ...filters, status: "", pipelineStage: "" });

  return (
    <FilterAccordion
      label="Stage & status"
      selected={selected}
      onRemove={clear}
      onReset={clear}
      open={open}
      onToggleOpen={onToggleOpen}
    >
      <div className="max-h-[320px] overflow-y-auto">
        {stages.map((stage) => (
          <div key={stage.id}>
            <FilterCheckRow
              label={`All ${stage.label}`}
              checked={filters.pipelineStage === stage.key}
              onToggle={() =>
                onChange({ ...filters, status: "", pipelineStage: filters.pipelineStage === stage.key ? "" : stage.key })
              }
            />
            <div className="ps-5">
              {liveStatuses(stage).map((status) => (
                <FilterCheckRow
                  key={status.id}
                  size="sm"
                  label={status.label}
                  checked={filters.status === status.key}
                  onToggle={() =>
                    onChange({ ...filters, pipelineStage: "", status: filters.status === status.key ? "" : status.key })
                  }
                />
              ))}
            </div>
          </div>
        ))}
      </div>
      <p className="mt-2 text-meta text-u-text3">Reads from the positions a person is in.</p>
    </FilterAccordion>
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
  rows: Row[];
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
