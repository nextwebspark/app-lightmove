import type { ReactNode } from "react";
import type { Chip } from "../api/types";
import { Pill } from "./Pill";

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
          <Pill
            key={chip.label}
            label={chip.label}
            selected={chip.selected}
            onToggle={() => onToggle(chip.label)}
          />
        ))}
        {children}
      </div>
    </div>
  );
}
