import type { ReactNode } from "react";
import { SegmentedControl } from "../../../components/ui/SegmentedControl";

export type StrategyMode = "companies" | "people";

/** The mode switch, drawn by whichever editor shows it, which knows whether it is in full screen. */
export type ModeToggle = (isFullscreen: boolean) => ReactNode;

const MODES = [
  { value: "companies", label: "Companies" },
  { value: "people", label: "People" },
] as const satisfies readonly { value: StrategyMode; label: string }[];

/** Strategy's two questions: companies in the universe we hold, or people in ContactOut's index. */
export function StrategyModeToggle({
  mode,
  onChange,
  className,
}: {
  mode: StrategyMode;
  onChange: (mode: StrategyMode) => void;
  className?: string;
}) {
  return <SegmentedControl label="Search for" options={MODES} value={mode} onChange={onChange} className={className} />;
}
