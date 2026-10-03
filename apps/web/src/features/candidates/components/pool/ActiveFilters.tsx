import { Icon, ICONS } from "../../../../components/layout/Icon";
import type { Project } from "../../../projects/api/types";
import type { Member } from "../../../workspace/api/types";
import type { CandidateTag, PoolFilters } from "../../api/types";
import { candidateStatusStyle } from "../../lib/candidateVocabulary";
import { TAG_MATCHES, withoutRailFilters } from "../../lib/poolFilters";

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
