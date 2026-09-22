import { Icon, ICONS } from "../../../components/layout/Icon";
import type { OrgNode } from "../api/types";
import { suggestedSeats } from "../lib/orgChart";
import { Eyebrow } from "./BriefFields";

/**
 * The matched template's own usual direct reports the chart does not already carry — offered under the
 * canvas, never added on its own. Only shown once a document has actually been read this session
 * (`usualDirectReports` starts `null` and stays that until a reporting reading names a matched
 * template), so a brief nobody has read from yet does not nag about seats it never proposed.
 */
export function SuggestedSeatsRow({
  chart,
  usualDirectReports,
  onAdd,
}: {
  chart: OrgNode[];
  usualDirectReports: readonly string[] | null;
  onAdd: (title: string) => void;
}) {
  if (!usualDirectReports) return null;
  const suggestions = suggestedSeats(chart, usualDirectReports);
  if (suggestions.length === 0) return null;

  return (
    <div>
      <Eyebrow tone="inferred" className="mb-2">
        Suggested seats
      </Eyebrow>
      <div className="flex flex-wrap gap-2">
        {suggestions.map((title) => (
          <button
            key={title}
            type="button"
            onClick={() => onAdd(title)}
            className="inline-flex items-center gap-1.5 rounded-full border border-u-inferred/40 bg-u-inferred-tint px-3 py-1.5 text-note font-medium text-u-inferred transition hover:brightness-95"
          >
            <Icon d={ICONS.plus} size={12} />
            {title}
          </button>
        ))}
      </div>
    </div>
  );
}
