import { formatDate } from "../../../lib/format";
import type { Project } from "../../projects/api/types";
import type { PositionDetails } from "../api/types";
import { employmentTypeLabel } from "../lib/labels";
import type { SaveStatus } from "../../../lib/useAutosave";

/**
 * The header card: title and client from the project, the at-a-glance chips, the confidentiality
 * toggle, the Draft/Locked badge, the live save indicator and the completion meter.
 */
export function PositionHero({
  project,
  details,
  locked,
  completionPct,
  saveStatus,
  onToggleConfidential,
}: {
  project: Project;
  details: PositionDetails;
  locked: boolean;
  completionPct: number;
  saveStatus: SaveStatus;
  onToggleConfidential: () => void;
}) {
  const chips = [
    "Seniority N-1",
    compRange(details),
    details.startTarget ? `Target start ${formatDate(details.startTarget)}` : null,
    employmentTypeLabel(details.employmentType),
  ].filter(Boolean) as string[];

  return (
    <div className="relative mb-[18px] flex gap-[18px] overflow-hidden rounded-[10px] border border-line-soft bg-panel2 py-[22px] pl-7 pr-6">
      <span className="absolute bottom-0 left-0 top-0 w-1 bg-sky" />

      <div className="min-w-0 flex-1">
        <div className="mb-1 text-[21px] font-bold text-text">{project.positionTitle}</div>
        <div className="font-mono text-[12.5px] text-text3">
          <b className="font-semibold text-text2">{project.clientName}</b>
          {details.reportsTo && <> · Reports to {details.reportsTo}</>}
          {details.location && <> · {details.location}</>}
        </div>
        <div className="mt-3.5 flex flex-wrap gap-[7px]">
          {chips.map((chip) => (
            <span
              key={chip}
              className="inline-flex items-center gap-1.5 rounded-full border border-line bg-panel px-[11px] py-[5px] font-mono text-xs font-medium text-text2"
            >
              {chip}
            </span>
          ))}
          <span className="inline-flex items-center rounded-full border border-line bg-panel px-[11px] py-[5px] font-mono text-xs font-medium text-text3">
            {completionPct}% complete
          </span>
        </div>
      </div>

      <div className="flex flex-none flex-col items-end gap-2.5">
        <button
          type="button"
          onClick={onToggleConfidential}
          disabled={locked}
          title="Click to toggle confidentiality"
          className={`inline-flex items-center gap-1.5 whitespace-nowrap rounded-md border px-[11px] py-1 font-mono text-[10.5px] font-semibold uppercase tracking-[0.05em] transition disabled:cursor-not-allowed ${
            details.confidential
              ? "border-red bg-red-dim text-red"
              : "border-line bg-panel text-text3"
          }`}
        >
          {details.confidential ? "Confidential" : "Standard"}
        </button>

        <span
          className={`inline-flex items-center gap-1.5 whitespace-nowrap rounded-md border px-[11px] py-1 font-mono text-[10.5px] font-semibold uppercase tracking-[0.06em] ${
            locked ? "border-transparent bg-green-dim text-green" : "border-line bg-panel text-text2"
          }`}
        >
          {locked ? "✓ Locked" : "Draft"}
        </span>

        <span aria-live="polite" className="font-mono text-[11px] text-text3">
          {saveStatus === "saving" ? "Saving…" : saveStatus === "saved" ? "Saved" : " "}
        </span>
      </div>
    </div>
  );
}

function compRange(details: PositionDetails): string | null {
  if (details.salaryMin === null || details.salaryMax === null) return null;
  return `${details.currency} ${abbreviate(details.salaryMin)}–${abbreviate(details.salaryMax)}`;
}

function abbreviate(value: number): string {
  return value >= 1000 ? `${Math.round(value / 1000)}K` : String(value);
}
