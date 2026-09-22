import { DateInput, Field } from "../../../components/ui";
import { formatDate } from "../../../lib/format";
import type { ProjectType } from "../api/types";
import { daysFromStart } from "../lib/timeline";

/**
 * When the mandate starts and what it owes by when — the dates its health will be measured against.
 *
 * <p>A mapping mandate states one: the day its client expects the map. A search states the day the
 * shortlist is due and the mapping target follows at 60% of that window, shown so it can be read and
 * edited rather than discovered later.
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
      <SectionLabel>Timeline</SectionLabel>

      <Field
        label="Project start date"
        hint="Defaults to today. Edit if the project starts later or was kicked off earlier."
      >
        <DateInput value={startDate} onChange={onStartDateChange} />
      </Field>

      {searching ? (
        <>
          <Field
            label="Shortlist delivery date"
            hint="When does the client expect the shortlist?"
            error={errors.shortlistTargetDate}
          >
            <DateInput value={shortlistTargetDate} onChange={onShortlistTargetChange} />
          </Field>

          <div className="mb-4 rounded-[10px] border border-line bg-panel2 p-3">
            <SummaryLabel>Milestones</SummaryLabel>
            <Field label="Mapping target" error={errors.mappingTargetDate}>
              <DateInput value={mappingTargetDate} onChange={onMappingTargetChange} />
            </Field>
            <p className="-mt-2 font-mono text-[11px] text-text3">
              {describeMilestone(startDate, mappingTargetDate, "Auto-calculated from the shortlist window — edit to override.")}
            </p>
            <p className="mt-2.5 font-mono text-[11px] text-text3">
              Shortlist target{" "}
              <b className="font-semibold text-text2">{formatDate(shortlistTargetDate || null)}</b> ·
              matches the shortlist delivery date
            </p>
          </div>
        </>
      ) : (
        <>
          <Field
            label="Map delivery date"
            hint="When does the client expect the completed universe map?"
            error={errors.mappingTargetDate}
          >
            <DateInput value={mappingTargetDate} onChange={onMappingTargetChange} />
          </Field>

          <div className="mb-4 rounded-[10px] border border-line bg-panel2 p-3">
            <SummaryLabel>Target</SummaryLabel>
            <p className="text-[12.5px] font-semibold text-text">
              Map delivery: {formatDate(mappingTargetDate || null)} · starting{" "}
              {formatDate(startDate || null)}
            </p>
            <p className="mt-0.5 font-mono text-[11px] text-text3">
              {describeMilestone(startDate, mappingTargetDate, "No delivery date stated yet.")}
            </p>
          </div>
        </>
      )}
    </>
  );
}

function describeMilestone(startDate: string, targetDate: string, fallback: string): string {
  const days = startDate && targetDate ? daysFromStart(startDate, targetDate) : null;
  return days === null ? fallback : `${days} day${days === 1 ? "" : "s"} from start`;
}

function SectionLabel({ children }: { children: string }) {
  return (
    <div className="mb-2 mt-1 border-t border-line-soft pt-3.5 font-mono text-[10px] font-semibold uppercase tracking-[0.14em] text-text3">
      {children}
    </div>
  );
}

function SummaryLabel({ children }: { children: string }) {
  return (
    <div className="mb-1.5 font-mono text-[10px] font-semibold uppercase tracking-[0.12em] text-text3">
      {children}
    </div>
  );
}
