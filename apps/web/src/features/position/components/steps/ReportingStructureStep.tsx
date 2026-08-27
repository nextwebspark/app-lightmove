import { Input, Select } from "../../../../components/ui";
import { Avatar } from "../../../../components/ui";
import type { DirectReport, ReportingStructure } from "../../api/types";
import { NOTICE_UNIT_LABELS, SENIORITY_LABELS } from "../../lib/labels";
import {
  AddRowButton,
  ColumnLabel,
  FormattedDateField,
  NumberInput,
  RemoveRowButton,
  StepField,
  SubCard,
} from "../fields";
import type { PositionSeniority } from "../../api/types";

/** Step three: the shape of the org around the seat. */
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
  const patchReport = (index: number, changes: Partial<DirectReport>) =>
    onChange({
      directReports: reporting.directReports.map((report, i) =>
        i === index ? { ...report, ...changes } : report,
      ),
    });

  return (
    <div className="flex flex-col gap-5">
      <div>
        <span className="mb-1.5 block text-xs font-semibold uppercase tracking-[0.02em] text-text2">
          Reports to
        </span>
        <SubCard className="flex flex-wrap items-center gap-3">
          <Avatar id="reports-to" name={reporting.reportsToName ?? "?"} size="lg" />
          <Input
            value={reporting.reportsToName ?? ""}
            aria-label="Reports to name"
            placeholder="Name"
            onChange={(event) => onChange({ reportsToName: event.target.value || null })}
            className="min-w-[140px] flex-1 bg-panel"
          />
          <Input
            value={reporting.reportsTo ?? ""}
            aria-label="Reports to title"
            placeholder="Title — e.g. Group CEO"
            onChange={(event) => onChange({ reportsTo: event.target.value || null })}
            className="min-w-[140px] flex-1 bg-panel"
          />
        </SubCard>
      </div>

      <OrgChart
        roleTitle={roleTitle}
        reportsTo={reporting.reportsTo}
        directReports={reporting.directReports}
      />

      <div>
        <div className="mb-1.5 flex items-baseline justify-between gap-2">
          <span className="text-xs font-semibold uppercase tracking-[0.02em] text-text2">
            Direct reports
          </span>
          <ColumnLabel>{reporting.directReports.length} seats</ColumnLabel>
        </div>
        <div className="flex flex-col gap-2">
          {reporting.directReports.map((report, index) => (
            <SubCard key={index} className="flex flex-wrap items-center gap-3 py-3">
              <Input
                value={report.title ?? ""}
                aria-label={`Direct report ${index + 1} title`}
                placeholder="Title"
                onChange={(event) => patchReport(index, { title: event.target.value || null })}
                className="min-w-[140px] flex-1 bg-panel"
              />
              <Input
                value={report.name ?? ""}
                aria-label={`Direct report ${index + 1} name`}
                placeholder="Direct report"
                onChange={(event) => patchReport(index, { name: event.target.value || null })}
                className="min-w-[140px] flex-1 bg-panel"
              />
              <RemoveRowButton
                label={`Remove direct report ${index + 1}`}
                onClick={() =>
                  onChange(
                    { directReports: reporting.directReports.filter((_, i) => i !== index) },
                    true,
                  )
                }
              />
            </SubCard>
          ))}
          <AddRowButton
            className="self-start"
            onClick={() =>
              onChange({ directReports: [...reporting.directReports, { title: null, name: null }] })
            }
          >
            + Add direct report
          </AddRowButton>
        </div>
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

/** The three-tier sketch the mockup draws: who this seat answers to, the seat, and what it leads. */
function OrgChart({
  roleTitle,
  reportsTo,
  directReports,
}: {
  roleTitle: string;
  reportsTo: string | null;
  directReports: DirectReport[];
}) {
  return (
    <SubCard>
      <ColumnLabel>Visual org chart</ColumnLabel>
      <div className="mt-3 flex flex-col items-center gap-2">
        <OrgNode label={reportsTo ?? "—"} caption="Reports to" />
        <span className="h-4 w-px bg-line" aria-hidden="true" />
        <OrgNode label={roleTitle || "Untitled role"} caption="This position" accent />
        {directReports.length > 0 && (
          <>
            <span className="h-4 w-px bg-line" aria-hidden="true" />
            <div className="flex w-full flex-wrap justify-center gap-2 overflow-x-auto">
              {directReports.map((report, index) => (
                <OrgNode
                  key={index}
                  label={report.title ?? report.name ?? "Unnamed seat"}
                  caption={report.title && report.name ? report.name : undefined}
                />
              ))}
            </div>
          </>
        )}
      </div>
    </SubCard>
  );
}

function OrgNode({
  label,
  caption,
  accent = false,
}: {
  label: string;
  caption?: string;
  accent?: boolean;
}) {
  return (
    <div
      className={`min-w-0 max-w-full rounded-lg border px-3.5 py-2 text-center ${
        accent ? "border-sky bg-sky-dim" : "border-line bg-panel"
      }`}
    >
      <span
        className={`block truncate text-[13px] font-semibold ${accent ? "text-sky" : "text-text"}`}
      >
        {label}
      </span>
      {caption && (
        <span className="mt-px block truncate font-mono text-[10.5px] text-text3">{caption}</span>
      )}
    </div>
  );
}
