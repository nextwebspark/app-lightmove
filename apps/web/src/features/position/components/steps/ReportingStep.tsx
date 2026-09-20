import { SENIORITY_LABELS } from "../../../../lib/seniority";
import type { PositionDetails, ReportingStructure } from "../../api/types";
import { labelOf } from "../../lib/labels";
import { directReportsOf, labelOfNode, managerOf } from "../../lib/orgChart";
import { Eyebrow, FieldBlock } from "../BriefFields";
import { OrgChartCanvas } from "../OrgChartCanvas";

/**
 * Step two: the org chart around the role, and what it reads as. Who the role reports to and how
 * many seats it leads are not fields — they are the mandate seat's parent and children, derived
 * from the chart rather than stored twice.
 */
export function ReportingStep({
  roleTitle,
  seniority,
  reporting,
  onChange,
}: {
  roleTitle: string;
  seniority: PositionDetails["seniority"];
  reporting: ReportingStructure;
  onChange: (patch: Partial<ReportingStructure>, immediate?: boolean) => void;
}) {
  const manager = labelOfNode(managerOf(reporting.orgChart));
  const reports = directReportsOf(reporting.orgChart).length;
  const level = labelOf(SENIORITY_LABELS, seniority);

  return (
    <div className="flex flex-col gap-6">
      <div>
        <div className="mb-2 flex justify-end">
          <Eyebrow>Drag to arrange · Hover a seat to add or remove · Drag a handle to re-parent</Eyebrow>
        </div>
        <OrgChartCanvas
          chart={reporting.orgChart}
          roleTitle={roleTitle}
          onChange={(orgChart, immediate) => onChange({ orgChart }, immediate)}
        />
      </div>

      <p className="border-t border-u-border pt-4 text-[14px] text-u-text2">
        Reports to <b className="font-semibold text-u-text">{manager ?? "nobody yet"}</b> · Seniority level{" "}
        <b className="font-semibold text-u-text">{level ?? "unset"}</b> · leads{" "}
        <b className="font-semibold text-u-text">
          {reports} direct report{reports === 1 ? "" : "s"}
        </b>
      </p>

      <FieldBlock label="Team size">
        <span className="flex items-baseline gap-2">
          <span className="font-u-num text-[28px] font-medium text-u-text">{reports}</span>
          <span className="text-[13px] text-u-text3">direct report{reports === 1 ? "" : "s"}</span>
        </span>
      </FieldBlock>
    </div>
  );
}
