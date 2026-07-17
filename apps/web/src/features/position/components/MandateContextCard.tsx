import { Select } from "../../../components/ui";
import type { MandateReason } from "../api/types";
import { MicroLabel } from "./fields";

const REASON_LABELS: Record<MandateReason, string> = {
  NEW_ROLE: "New role",
  BACKFILL: "Backfill",
  SUCCESSION: "Succession",
  RESTRUCTURING: "Restructuring",
};

/** Why the mandate exists, and anything sensitive — flagged internal-only, never shown to candidates. */
export function MandateContextCard({
  reason,
  internalContext,
  onReason,
  onContext,
}: {
  reason: MandateReason;
  internalContext: string | null;
  onReason: (reason: MandateReason) => void;
  onContext: (value: string) => void;
}) {
  return (
    <div className="mb-[22px] rounded-[10px] border border-line-soft bg-panel2 p-4">
      <div className="mb-1 flex items-center gap-2">
        <span className="text-[13px] font-semibold">Mandate Context</span>
        <span className="rounded-[5px] bg-line-soft px-[7px] py-0.5 font-mono text-[9.5px] font-semibold uppercase tracking-[0.08em] text-text3">
          Internal only — not shown to candidates
        </span>
      </div>
      <div className="mb-3.5 mt-1 font-mono text-[11.5px] text-text3">
        Why this mandate exists and anything sensitive the hiring manager shared.
      </div>
      <div className="grid grid-cols-[200px_1fr] gap-4">
        <div>
          <MicroLabel>Reason for mandate</MicroLabel>
          <Select value={reason} onChange={(e) => onReason(e.target.value as MandateReason)}>
            {Object.entries(REASON_LABELS).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </Select>
        </div>
        <div>
          <MicroLabel>Internal context</MicroLabel>
          <textarea
            value={internalContext ?? ""}
            onChange={(e) => onContext(e.target.value)}
            className="min-h-[70px] w-full resize-y rounded-lg border border-dashed border-line bg-panel px-3.5 py-3 font-mono text-[13px] leading-relaxed text-text2 outline-none transition focus:border-text3"
          />
        </div>
      </div>
    </div>
  );
}
