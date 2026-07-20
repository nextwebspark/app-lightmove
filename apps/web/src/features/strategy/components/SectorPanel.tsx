import type { Chip, SectorCount, SectorGroup, Strategy } from "../api/types";
import { ChipGroup } from "./ChipGroup";
import { SectorCombobox } from "./SectorCombobox";

/**
 * The Sector Scope panel: the three chip groups plus the Direct-sector typeahead. Direct sectors are
 * the must-have core; Adjacent and Inferred are AI suggestions the consultant keeps or rejects.
 */
export function SectorPanel({
  strategy,
  sectors,
  onToggle,
  onAddDirect,
}: {
  strategy: Strategy;
  sectors: SectorCount[];
  onToggle: (kind: SectorGroup, label: string) => void;
  onAddDirect: (label: string) => void;
}) {
  const directLabels = strategy.direct.map((chip: Chip) => chip.label);

  return (
    <div className="min-h-[360px] rounded-[10px] border border-line-soft bg-panel2 px-5 py-[18px]">
      <div className="mb-1 flex items-center gap-2">
        <span className="font-sans text-[13px] font-semibold">Sector Scope</span>
      </div>
      <div className="mb-1.5 mt-1 font-mono text-[11.5px] text-text3">
        Direct is must-have · Adjacent and Inferred widen the pool
      </div>

      <ChipGroup
        label="Direct"
        description="Core sector — must-have"
        chips={strategy.direct}
        onToggle={(label) => onToggle("direct", label)}
      >
        <SectorCombobox sectors={sectors} existing={directLabels} onAdd={onAddDirect} />
      </ChipGroup>

      <ChipGroup
        label="Adjacent"
        description="AI-suggested — transferable experience"
        chips={strategy.adjacent}
        onToggle={(label) => onToggle("adjacent", label)}
      />

      <ChipGroup
        label="Inferred"
        description="AI-suggested — wider talent pool"
        chips={strategy.inferred}
        onToggle={(label) => onToggle("inferred", label)}
      />
    </div>
  );
}
