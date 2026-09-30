import { SegmentedControl } from "../../../components/ui/SegmentedControl";

export type StrategyMode = "companies" | "people";

const MODES = [
  { value: "companies", label: "Companies" },
  { value: "people", label: "People" },
] as const satisfies readonly { value: StrategyMode; label: string }[];

/** Strategy's two questions: companies in the universe we hold, or people in ContactOut's index. */
export function StrategyModeToggle({ mode, onChange }: { mode: StrategyMode; onChange: (mode: StrategyMode) => void }) {
  return <SegmentedControl label="Search for" options={MODES} value={mode} onChange={onChange} />;
}
