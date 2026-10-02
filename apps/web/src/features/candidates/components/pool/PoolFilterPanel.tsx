import { Icon, ICONS } from "../../../../components/layout/Icon";
import { Select } from "../../../../components/ui";
import { Chip } from "../../../../components/ui/Chip";
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
    <section aria-label="Filters" className="mb-3 rounded-[10px] border border-u-border-strong bg-u-raised px-4 py-3.5">
      <div className="flex flex-wrap items-center gap-2.5">
        <span className="type-micro-label w-[72px] flex-none text-u-text3">Tags</span>
        <SegmentedControl
          label="Match tags"
          options={TAG_MATCHES}
          value={filters.tagMatch}
          onChange={(tagMatch) => onChange({ ...filters, tagMatch })}
        />
        <div className="flex flex-wrap gap-1.5">
          {tags.map((tag) => {
            const on = filters.tagIds.includes(tag.id);
            return (
              <Chip
                key={tag.id}
                size="sm"
                role="checkbox"
                aria-checked={on}
                selected={on}
                count={tag.holders}
                selectedClassName={cn("border-transparent", tagClassName(tag.colour))}
                className={cn("gap-1", !on && "bg-u-surface")}
                onClick={() => toggleTag(tag.id)}
              >
                {on && <Icon d={ICONS.check} size={11} />}
                {tag.label}
              </Chip>
            );
          })}
        </div>
      </div>

      <div className="mt-3 grid grid-cols-1 gap-2.5 sm:grid-cols-2 lg:grid-cols-4">
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
      <p className="mt-2.5 text-[11px] text-u-text3">
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
      <span className="type-micro-label text-u-text3">{label}</span>
      <Select density="compact" value={value} onChange={(event) => onChange(event.target.value)} className="bg-u-surface">
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
        onClick={() => onChange({ ...filters, tagIds: [], position: "", status: "", owner: "", country: "" })}
        className="ms-1 text-xs font-medium text-u-accent hover:underline"
      >
        Clear all
      </button>
      {shown !== null && pool !== null && (
        <span className="ms-auto text-xs text-u-text3">
          {shown} of {pool} people
        </span>
      )}
    </div>
  );
}
