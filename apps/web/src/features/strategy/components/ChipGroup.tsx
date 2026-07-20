import type { ReactNode } from "react";
import type { Chip } from "../api/types";

const CHECK = "M20 6L9 17l-5-5";
const PLUS = "M12 5v14M5 12h14";

/**
 * A labelled group of sector chips (Direct / Adjacent / Inferred). Every chip stays visible whether
 * selected or not — a deselected suggestion sits at reduced opacity, re-selectable, rather than
 * disappearing. Clicking toggles it. An optional trailing slot carries the Direct group's add field.
 */
export function ChipGroup({
  label,
  description,
  chips,
  onToggle,
  children,
}: {
  label: string;
  description: string;
  chips: Chip[];
  onToggle: (label: string) => void;
  children?: ReactNode;
}) {
  return (
    <div>
      <div className="mt-4 mb-2 flex items-baseline gap-2">
        <span className="font-mono text-[10.5px] font-bold uppercase tracking-[0.06em] text-amber">
          {label}
        </span>
        <span className="font-mono text-[11.5px] text-text3">{description}</span>
      </div>
      <div className="flex flex-wrap gap-2">
        {chips.map((chip) => (
          <SectorChip key={chip.label} chip={chip} onToggle={() => onToggle(chip.label)} />
        ))}
        {children}
      </div>
    </div>
  );
}

function SectorChip({ chip, onToggle }: { chip: Chip; onToggle: () => void }) {
  return (
    <button
      type="button"
      aria-pressed={chip.selected}
      onClick={onToggle}
      className={`inline-flex items-center gap-[7px] rounded-full border bg-panel px-[13px] py-[7px] font-sans text-[13px] font-medium transition ${
        chip.selected ? "border-sky text-text" : "border-line text-text2 opacity-[.65]"
      }`}
    >
      <svg
        width="14"
        height="14"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth={2.4}
        strokeLinecap="round"
        strokeLinejoin="round"
        className={`flex-none ${chip.selected ? "text-sky" : "text-text3"}`}
        aria-hidden="true"
      >
        <path d={chip.selected ? CHECK : PLUS} />
      </svg>
      {chip.label}
    </button>
  );
}
