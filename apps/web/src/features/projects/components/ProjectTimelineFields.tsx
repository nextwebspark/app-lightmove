import { DateInput, Field } from "../../../components/ui";
import { formatDate } from "../../../lib/format";
import type { ProjectType } from "../api/types";
import { daysFromStart } from "../lib/timeline";

/**
 * When the mandate starts and what it owes by when — the dates its health will be measured against.
 *
 * <p>A mapping mandate states one: the day its client expects the map. A search states the day the
 * shortlist is due, and the mapping target follows at 60% of that window, shown so it can be read and
 * edited rather than discovered later.
 *
 * <p>Two dates to a row, and the window summarised in one line rather than in a panel. This form sits
 * in a dialog capped at 90% of the viewport, and every row it grows is a row nearer the point where a
 * consultant has to scroll to reach Create project.
 */
export function ProjectTimelineFields({
  projectType,
  startDate,
  mappingTargetDate,
  shortlistTargetDate,
  errors,
  onStartDateChange,
  onMappingTargetChange,
  onShortlistTargetChange,
}: {
  projectType: ProjectType;
  startDate: string;
  mappingTargetDate: string;
  shortlistTargetDate: string;
  errors: { mappingTargetDate?: string; shortlistTargetDate?: string };
  onStartDateChange: (value: string) => void;
  onMappingTargetChange: (value: string) => void;
  onShortlistTargetChange: (value: string) => void;
}) {
  const searching = projectType === "EXECUTIVE_SEARCH";

  return (
    <>
      <div className="mb-3 border-t border-line-soft pt-3.5 font-mono text-[10px] font-semibold uppercase tracking-[0.14em] text-text3">
        Timeline
      </div>

      <div className="grid grid-cols-1 gap-x-3 sm:grid-cols-2">
        <Field label="Project start date">
          <DateInput value={startDate} onChange={onStartDateChange} />
        </Field>

        {searching ? (
          <>
            <Field label="Shortlist delivery date" error={errors.shortlistTargetDate}>
              <DateInput value={shortlistTargetDate} onChange={onShortlistTargetChange} />
            </Field>
            <Field label="Mapping target" error={errors.mappingTargetDate}>
              <DateInput value={mappingTargetDate} onChange={onMappingTargetChange} />
            </Field>
          </>
        ) : (
          <Field label="Map delivery date" error={errors.mappingTargetDate}>
            <DateInput value={mappingTargetDate} onChange={onMappingTargetChange} />
          </Field>
        )}
      </div>

      <p className="mb-4 font-mono text-[11px] leading-relaxed text-text3">
        {summarise(searching, startDate, mappingTargetDate, shortlistTargetDate)}
      </p>
    </>
  );
}

/** The one line under the dates: what is due when, and how long the mandate has to do it. */
function summarise(
  searching: boolean,
  startDate: string,
  mappingTargetDate: string,
  shortlistTargetDate: string,
): string {
  const deliverable = searching ? shortlistTargetDate : mappingTargetDate;
  if (!deliverable) {
    return searching
      ? "Enter the shortlist date and the mapping target follows at 60% of the window."
      : "No delivery date stated yet.";
  }

  const days = startDate ? daysFromStart(startDate, deliverable) : null;
  const window = days === null ? "" : ` · ${days} day${days === 1 ? "" : "s"} from start`;

  return searching
    ? `Shortlist ${formatDate(deliverable)}${window}. The mapping target is auto-set — edit it to override.`
    : `Map delivery ${formatDate(deliverable)}${window}.`;
}
