import { ICONS } from "../../../components/layout/Icon";
import { ToolbarViewSwitch, type ToolbarView } from "../../../components/ui/Toolbar";

export type StrategyMode = "companies" | "people";

const MODES: readonly ToolbarView<StrategyMode>[] = [
  { value: "companies", label: "Companies", icon: ICONS.globe },
  { value: "people", label: "People", icon: ICONS.candidates },
];

/** Strategy's two questions: companies in the universe we hold, or people in ContactOut's index. */
export function StrategyModeToggle({ mode, onChange }: { mode: StrategyMode; onChange: (mode: StrategyMode) => void }) {
  return <ToolbarViewSwitch label="Search for" views={MODES} value={mode} onChange={onChange} />;
}
