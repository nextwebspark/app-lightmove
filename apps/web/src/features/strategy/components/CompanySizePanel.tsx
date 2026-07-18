import type { Strategy } from "../api/types";
import type { BandDef } from "../lib/companySizeBands";
import { EMPLOYEE_BANDS, REVENUE_BANDS } from "../lib/companySizeBands";
import { Pill } from "./ChipGroup";

type Axis = "employee" | "revenue";

/**
 * The Company Size panel: two axes of band pills — Employees and Revenue — each a fixed catalog rendered
 * from lib/companySizeBands (no typeahead, no suggestions). A band is in scope when its value is in the
 * strategy's selected list for that axis; clicking toggles it.
 */
export function CompanySizePanel({
  strategy,
  onToggle,
}: {
  strategy: Strategy;
  onToggle: (axis: Axis, value: string) => void;
}) {
  return (
    <div className="min-h-[360px] rounded-[10px] border border-line-soft bg-panel2 px-5 py-[18px]">
      <div className="mb-1 flex items-center gap-2">
        <span className="font-sans text-[13px] font-semibold">Company Size</span>
      </div>
      <div className="mb-1.5 mt-1 font-mono text-[11.5px] text-text3">
        Revenue and headcount bands in scope
      </div>

      <BandGroup
        label="Employees"
        bands={EMPLOYEE_BANDS}
        selected={strategy.employee}
        onToggle={(value) => onToggle("employee", value)}
      />
      <BandGroup
        label="Revenue"
        bands={REVENUE_BANDS}
        selected={strategy.revenue}
        onToggle={(value) => onToggle("revenue", value)}
      />
    </div>
  );
}

function BandGroup({
  label,
  bands,
  selected,
  onToggle,
}: {
  label: string;
  bands: BandDef[];
  selected: string[];
  onToggle: (value: string) => void;
}) {
  const inScope = new Set(selected);
  return (
    <div>
      <div className="mt-4 mb-2 flex items-baseline gap-2">
        <span className="font-mono text-[10.5px] font-bold uppercase tracking-[0.06em] text-amber">
          {label}
        </span>
      </div>
      <div className="flex flex-wrap gap-2">
        {bands.map((band) => (
          <Pill
            key={band.value}
            label={band.label}
            selected={inScope.has(band.value)}
            onToggle={() => onToggle(band.value)}
          />
        ))}
      </div>
    </div>
  );
}
