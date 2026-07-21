import type { CatalogOption } from "../lib/catalogOption";
import { Pill } from "./Pill";

/**
 * A single-group fixed-catalog scope panel — the shared shape of Ownership Type and Location, which
 * render identically in the mockup: one title, one subtitle, one labelled row of toggle pills. Every
 * option always shows; selection is whether its value is in the strategy's selected list. Sections
 * with several groups (Company Size) or a typeahead (Sector Scope) keep their own panels.
 */
export function ScopeChipPanel({
  title,
  subtitle,
  groupLabel,
  options,
  selected,
  onToggle,
}: {
  title: string;
  subtitle: string;
  groupLabel: string;
  options: CatalogOption[];
  selected: string[];
  onToggle: (value: string) => void;
}) {
  const inScope = new Set(selected);
  return (
    <div className="min-h-[360px] rounded-[10px] border border-line-soft bg-panel2 px-5 py-[18px]">
      <div className="mb-1 flex items-center gap-2">
        <span className="font-sans text-[13px] font-semibold">{title}</span>
      </div>
      <div className="mb-1.5 mt-1 font-mono text-[11.5px] text-text3">{subtitle}</div>

      <div className="mt-4 mb-2 flex items-baseline gap-2">
        <span className="font-mono text-[10.5px] font-bold uppercase tracking-[0.06em] text-text2">
          {groupLabel}
        </span>
      </div>
      <div className="flex flex-wrap gap-2">
        {options.map((option) => (
          <Pill
            key={option.value}
            label={option.label}
            selected={inScope.has(option.value)}
            onToggle={() => onToggle(option.value)}
          />
        ))}
      </div>
    </div>
  );
}
