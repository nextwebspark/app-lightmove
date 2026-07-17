import type { EmploymentType, PositionDetails } from "../api/types";
import { EMPLOYMENT_TYPE_LABELS } from "../lib/labels";
import { FormattedDateField, InlineInput, InlineSelect, MicroLabel, NumberInput, SectionHeading } from "./fields";

/** The org row (reports-to → this role → direct reports) and the inline organisational fields. */
export function ReportingStructureCard({
  positionTitle,
  details,
  onChange,
}: {
  positionTitle: string;
  details: PositionDetails;
  onChange: (patch: Partial<PositionDetails>) => void;
}) {
  return (
    <div className="mb-[22px]">
      <SectionHeading title="Reporting Structure" />

      <div className="mb-4 flex items-stretch">
        <OrgBox label="Reports to" value={details.reportsTo ?? "—"} />
        <Chevron />
        <OrgBox label="This role" value={positionTitle} highlight />
        <Chevron />
        <OrgBox label="Direct reports" value={details.directReports?.toString() ?? "—"} />
      </div>

      <div className="grid grid-cols-2 gap-x-5 gap-y-3.5">
        <div>
          <MicroLabel>Reports to</MicroLabel>
          <InlineInput
            value={details.reportsTo ?? ""}
            onChange={(e) => onChange({ reportsTo: e.target.value || null })}
          />
        </div>
        <div>
          <MicroLabel>Direct reports</MicroLabel>
          <NumberInput
            value={details.directReports}
            onChange={(directReports) => onChange({ directReports })}
          />
        </div>
        <div>
          <MicroLabel>Team size</MicroLabel>
          <NumberInput value={details.teamSize} onChange={(teamSize) => onChange({ teamSize })} />
        </div>
        <div>
          <MicroLabel>Location</MicroLabel>
          <InlineInput
            value={details.location ?? ""}
            onChange={(e) => onChange({ location: e.target.value || null })}
          />
        </div>
        <div>
          <MicroLabel>Employment type</MicroLabel>
          <InlineSelect
            value={details.employmentType ?? ""}
            onChange={(e) => onChange({ employmentType: (e.target.value || null) as EmploymentType | null })}
          >
            <option value="">—</option>
            {Object.entries(EMPLOYMENT_TYPE_LABELS).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </InlineSelect>
        </div>
        <div>
          <MicroLabel>Target start</MicroLabel>
          <FormattedDateField
            value={details.startTarget}
            onChange={(startTarget) => onChange({ startTarget })}
          />
        </div>
      </div>
    </div>
  );
}

function OrgBox({ label, value, highlight }: { label: string; value: string; highlight?: boolean }) {
  return (
    <div
      className={`min-w-0 flex-1 rounded-[10px] border px-3.5 py-3 ${
        highlight ? "border-sky bg-sky-dim" : "border-line-soft bg-panel"
      }`}
    >
      <div className="mb-1.5 font-mono text-[9.5px] font-semibold uppercase tracking-[0.1em] text-text3">
        {label}
      </div>
      <div className="overflow-hidden text-ellipsis whitespace-nowrap text-[13px] font-semibold text-text">
        {value}
      </div>
    </div>
  );
}

function Chevron() {
  return (
    <div className="flex w-[34px] flex-none items-center justify-center text-text3">
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4">
        <path d="m9 6 6 6-6 6" />
      </svg>
    </div>
  );
}
