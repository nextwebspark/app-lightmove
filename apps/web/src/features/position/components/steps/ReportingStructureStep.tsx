import { Input, Select } from "../../../../components/ui";
import type { PositionSeniority, ReportingStructure } from "../../api/types";
import { NOTICE_UNIT_LABELS, SENIORITY_LABELS } from "../../lib/labels";
import { directReportsOf, labelOfNode, managerOf } from "../../lib/orgChart";
import { OrgChartCanvas } from "../OrgChartCanvas";
import { ColumnLabel, FormattedDateField, NumberInput, StepField } from "../fields";

/**
 * Step three: the shape of the org around the seat.
 *
 * The chart is the editor. There are no separate "reports to" or "direct report" fields, because both
 * are readings of the chart — the manager is the mandate seat's parent, the reports are its children —
 * and a second set of inputs over the same structure is a second place for them to disagree.
 */
export function ReportingStructureStep({
  roleTitle,
  seniority,
  reporting,
  onChange,
}: {
  roleTitle: string;
  seniority: PositionSeniority | null;
  reporting: ReportingStructure;
  onChange: (patch: Partial<ReportingStructure>, immediate?: boolean) => void;
}) {
  const manager = labelOfNode(managerOf(reporting.orgChart));
  const reports = directReportsOf(reporting.orgChart);

  return (
    <div className="flex flex-col gap-5">
      <div>
        <div className="mb-1.5 flex flex-wrap items-baseline justify-between gap-2">
          <span className="text-xs font-semibold uppercase tracking-[0.02em] text-text2">
            Org chart
          </span>
          <ColumnLabel>
            drag to arrange · hover a seat to add or remove · drag a handle to re-parent
          </ColumnLabel>
        </div>
        <OrgChartCanvas
          chart={reporting.orgChart}
          roleTitle={roleTitle}
          onChange={(orgChart, immediate) => onChange({ orgChart }, immediate)}
        />
        <p className="mt-2 font-mono text-[11.5px] text-text3">
          {manager ? `Reports to ${manager}` : "No manager named yet"} ·{" "}
          {reports.length} direct report{reports.length === 1 ? "" : "s"}
        </p>
      </div>

      <div className="grid grid-cols-1 gap-4 md:grid-cols-2 md:gap-x-[18px]">
        <StepField label="Total team size">
          <Input
            value={reporting.teamSize ?? ""}
            placeholder="e.g. 38 across the finance function"
            onChange={(event) => onChange({ teamSize: event.target.value || null })}
          />
        </StepField>
        <StepField label="Seniority">
          <div className="rounded-lg border border-line bg-panel2 px-3 py-2.5 font-mono text-[13px] text-text3">
            {seniority ? SENIORITY_LABELS[seniority] : "Set on step one"}
          </div>
        </StepField>
      </div>

      <div className="grid grid-cols-1 gap-4 md:grid-cols-2 md:gap-x-[18px]">
        <StepField label="Target start">
          <div className="rounded-lg border border-line bg-panel2 px-3 py-1.5">
            <FormattedDateField
              value={reporting.targetStart}
              onChange={(targetStart) => onChange({ targetStart }, true)}
            />
          </div>
        </StepField>
        <StepField label="Notice period to plan for">
          <div className="flex gap-2">
            <div className="min-w-0 flex-1 rounded-lg border border-line bg-panel2 px-3 py-1.5">
              <NumberInput
                value={reporting.noticeValue}
                aria-label="Notice period"
                placeholder="3"
                max={365}
                onChange={(noticeValue) => onChange({ noticeValue })}
              />
            </div>
            <Select
              aria-label="Notice period unit"
              value={reporting.noticeUnit ?? "MONTHS"}
              onChange={(event) =>
                onChange({ noticeUnit: event.target.value as ReportingStructure["noticeUnit"] }, true)
              }
              className="w-[130px] flex-none"
            >
              {Object.entries(NOTICE_UNIT_LABELS).map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </Select>
          </div>
        </StepField>
      </div>
    </div>
  );
}
