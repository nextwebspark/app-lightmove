import { SENIORITY_LABELS } from "../../../../lib/seniority";
import type { PositionDetails, ReportingStructure } from "../../api/types";
import { fieldCountOf, type StepReceipt } from "../../lib/documentFill";
import { labelOf } from "../../lib/labels";
import { directReportsOf, labelOfNode, managerOf } from "../../lib/orgChart";
import { Eyebrow, FieldBlock } from "../BriefFields";
import { DocumentFillStrip } from "../DocumentFillStrip";
import { OrgChartCanvas } from "../OrgChartCanvas";
import { ProvenanceMarker } from "../ProvenanceMarker";
import { SuggestedSeatsRow } from "../SuggestedSeatsRow";

/**
 * Step two: the org chart around the role, and what it reads as. Who the role reports to and how
 * many seats it leads are not fields — they are the mandate seat's parent and children, derived
 * from the chart rather than stored twice.
 */
export function ReportingStep({
  roleTitle,
  seniority,
  reporting,
  receipt,
  stripError,
  extracting,
  usualDirectReports,
  onChange,
  onExtractDocument,
  onUndoAll,
  onDismissStrip,
  onUndoTeamSize,
  onAddSuggestedSeat,
}: {
  roleTitle: string;
  seniority: PositionDetails["seniority"];
  reporting: ReportingStructure;
  /** This session's Reporting-screen receipt — the team size scalar; the org chart's own additions
   *  carry no receipt (see lib/documentFill.ts's fillBrief — the merge is source-aware but untracked). */
  receipt?: StepReceipt;
  stripError?: string;
  extracting: boolean;
  /** The matched template's usual direct reports — null until a reporting reading has run this session. */
  usualDirectReports: readonly string[] | null;
  onChange: (patch: Partial<ReportingStructure>, immediate?: boolean) => void;
  onExtractDocument: () => void;
  onUndoAll: () => void;
  onDismissStrip: () => void;
  onUndoTeamSize: () => void;
  onAddSuggestedSeat: (title: string) => void;
}) {
  const manager = labelOfNode(managerOf(reporting.orgChart));
  const reports = directReportsOf(reporting.orgChart).length;
  const level = labelOf(SENIORITY_LABELS, seniority);
  const teamSizeInfo = receipt?.scalars.teamSize;

  return (
    <div className="flex flex-col gap-6">
      <DocumentFillStrip
        fileName={receipt?.fileName ?? ""}
        count={fieldCountOf(receipt)}
        error={stripError}
        onRetry={onExtractDocument}
        retrying={extracting}
        onUndoAll={onUndoAll}
        onDismiss={onDismissStrip}
      />

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

      <SuggestedSeatsRow chart={reporting.orgChart} usualDirectReports={usualDirectReports} onAdd={onAddSuggestedSeat} />

      <p className="border-t border-u-border pt-4 text-body text-u-text2">
        Reports to <b className="font-semibold text-u-text">{manager ?? "nobody yet"}</b> · Seniority level{" "}
        <b className="font-semibold text-u-text">{level ?? "unset"}</b> · leads{" "}
        <b className="font-semibold text-u-text">
          {reports} direct report{reports === 1 ? "" : "s"}
        </b>
      </p>

      <FieldBlock
        label="Team size"
        aside={
          <ProvenanceMarker
            source={reporting.fieldSources.teamSize}
            confidence={teamSizeInfo?.confidence}
            snippet={teamSizeInfo?.snippet}
            fileName={teamSizeInfo && receipt?.fileName}
            onUndo={teamSizeInfo ? onUndoTeamSize : undefined}
          />
        }
      >
        <span className="flex items-baseline gap-2">
          <span className="font-u-num text-figure font-medium text-u-text">{reports}</span>
          <span className="text-body text-u-text3">direct report{reports === 1 ? "" : "s"}</span>
        </span>
      </FieldBlock>
    </div>
  );
}
