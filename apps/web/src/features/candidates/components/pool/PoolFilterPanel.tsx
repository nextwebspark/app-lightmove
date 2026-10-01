import { Icon, ICONS } from "../../../../components/layout/Icon";
import { Select } from "../../../../components/ui";
import { SegmentedControl } from "../../../../components/ui/SegmentedControl";
import { cn } from "../../../../lib/cn";
import type { Project } from "../../../projects/api/types";
import type { Member } from "../../../workspace/api/types";
import type { CandidateStatus, CandidateTag, PoolFilters, TagMatch } from "../../api/types";
import { CANDIDATE_STATUSES, candidateStatusStyle } from "../../lib/candidateVocabulary";
import { tagClassName } from "../../lib/tagStyle";

const TAG_MATCHES: { value: TagMatch; label: string }[] = [
  { value: "any", label: "Any of" },
  { value: "all", label: "All of" },
  { value: "none", label: "None of" },
];

/** The Filters panel: tags matched any/all/none, then position, status, owner and country. */
export function PoolFilterPanel({
  filters,
  onChange,
  tags,
  staff,
  positions,
  countries,
}: {
  filters: PoolFilters;
  onChange: (filters: PoolFilters) => void;
  tags: CandidateTag[];
  staff: Member[];
  positions: Project[];
  countries: string[];
}) {
  const toggleTag = (tagId: string) =>
    onChange({
      ...filters,
      tagIds: filters.tagIds.includes(tagId)
        ? filters.tagIds.filter((id) => id !== tagId)
        : [...filters.tagIds, tagId],
    });

  return (
    <section aria-label="Filters" className="mb-3 rounded-[10px] border border-u-border bg-u-surface p-4">
      <div className="flex flex-wrap items-center gap-2">
        <span className="type-label me-1 text-u-text3">Tags</span>
        <SegmentedControl
          label="Match tags"
          options={TAG_MATCHES}
          value={filters.tagMatch}
          onChange={(tagMatch) => onChange({ ...filters, tagMatch })}
        />
        {tags.map((tag) => {
          const on = filters.tagIds.includes(tag.id);
          return (
            <button
              key={tag.id}
              type="button"
              role="checkbox"
              aria-checked={on}
              onClick={() => toggleTag(tag.id)}
              className={cn(
                "flex items-center gap-1 rounded-full border px-2.5 py-0.5 font-mono text-[11px]",
                on ? cn("border-transparent", tagClassName(tag.colour)) : "border-u-border-strong text-u-text2",
              )}
            >
              {on && <Icon d={ICONS.check} size={11} />}
              {tag.label}
              <span className="text-u-text3">{tag.holders}</span>
            </button>
          );
        })}
      </div>

      <div className="mt-3 grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <FilterSelect
          label="Position"
          value={filters.position}
          onChange={(position) => onChange({ ...filters, position })}
          options={[
            { value: "", label: "Any position" },
            ...positions.map((position) => ({ value: position.id, label: position.positionTitle })),
          ]}
        />
        <FilterSelect
          label="Status"
          value={filters.status}
          onChange={(status) => onChange({ ...filters, status: status as CandidateStatus | "" })}
          options={[
            { value: "", label: "Any status" },
            ...CANDIDATE_STATUSES.map((status) => ({ value: status.value, label: status.label })),
          ]}
        />
        <FilterSelect
          label="Owner"
          value={filters.owner}
          onChange={(owner) => onChange({ ...filters, owner })}
          options={[
            { value: "", label: "Anyone" },
            { value: "nobody", label: "Nobody" },
            ...staff.map((member) => ({ value: member.userId, label: member.fullName })),
          ]}
        />
        <FilterSelect
          label="Country"
          value={filters.country}
          onChange={(country) => onChange({ ...filters, country })}
          options={[{ value: "", label: "Any country" }, ...countries.map((country) => ({ value: country, label: country }))]}
        />
      </div>
      <p className="mt-3 text-[12px] text-u-text3">
        Tags are your team&apos;s own labels — set them from a person&apos;s drawer or on many people at once. Status reads
        from the positions a person is in.
      </p>
    </section>
  );
}

function FilterSelect({
  label,
  value,
  onChange,
  options,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  options: { value: string; label: string }[];
}) {
  return (
    <label className="flex flex-col gap-1">
      <span className="type-label text-u-text3">{label}</span>
      <Select value={value} onChange={(event) => onChange(event.target.value)} className="text-[13px]">
        {options.map((option) => (
          <option key={option.value || "any"} value={option.value}>
            {option.label}
          </option>
        ))}
      </Select>
    </label>
  );
}

/** One removable chip per filter in force, then Clear all and the count of people shown. */
export function ActiveFilters({
  filters,
  onChange,
  tagsById,
  membersByUserId,
  positions,
  shown,
  pool,
}: {
  filters: PoolFilters;
  onChange: (filters: PoolFilters) => void;
  tagsById: Map<string, CandidateTag>;
  membersByUserId: Map<string, Member>;
  positions: Project[];
  shown: number | null;
  pool: number | null;
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
    <div className="mb-3 flex flex-wrap items-center gap-1.5">
      {chips.map((chip) => (
        <span
          key={chip.key}
          className="flex items-center gap-1 rounded-full border border-u-border-strong bg-u-raised py-0.5 pe-1 ps-2.5 font-mono text-[11px] text-u-text2"
        >
          {chip.label}
          <button
            type="button"
            aria-label={`Remove filter ${chip.label}`}
            onClick={chip.clear}
            className="grid size-4 place-items-center rounded-full text-u-text3 hover:text-u-text"
          >
            <Icon d={ICONS.close} size={10} />
          </button>
        </span>
      ))}
      <button
        type="button"
        onClick={() => onChange({ ...filters, tagIds: [], position: "", status: "", owner: "", country: "" })}
        className="ms-1 font-mono text-[11px] font-semibold text-u-accent hover:underline"
      >
        Clear all
      </button>
      {shown !== null && pool !== null && (
        <span className="ms-auto font-mono text-[11px] text-u-text3">
          {shown} of {pool} people
        </span>
      )}
    </div>
  );
}
