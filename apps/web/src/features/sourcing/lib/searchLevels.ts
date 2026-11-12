import type { SearchLevel } from "../api/sourcingApi";

/** The levels a search may take, highest first, as the panel offers them. */
export const LEVEL_OPTIONS: readonly { level: SearchLevel; label: string; hint: string }[] = [
  { level: "C_LEVEL", label: "C-level", hint: "Chief, President, CEO, Managing Director" },
  { level: "SENIOR_VP", label: "SVP / EVP", hint: "Senior and Executive Vice President" },
  { level: "VP_HEAD_DIRECTOR", label: "VP · Head · Director", hint: "Vice President, Head, Director" },
  { level: "MANAGER", label: "Manager", hint: "Manager, Lead, Principal" },
  { level: "SPECIALIST", label: "Specialist", hint: "Specialist, Officer, Supervisor" },
];

const LABELS = new Map(LEVEL_OPTIONS.map((option) => [option.level, option.label]));

export const levelLabel = (level: SearchLevel) => LABELS.get(level) ?? level;

/** "C-level, SVP / EVP, VP · Head · Director", in the bands' own order whatever order they were picked in. */
export function levelsSummary(levels: readonly SearchLevel[]): string {
  const picked = LEVEL_OPTIONS.filter((option) => levels.includes(option.level));
  return picked.length === 0 ? "Any level" : picked.map((option) => option.label).join(", ");
}

/** The tabs that filter what was found, keyed by the level a title reads at (the LEVEL reason's value). */
export const LEVEL_TABS: readonly { key: string; label: string }[] = [
  { key: "TOP", label: "C-level" },
  { key: "SENIOR_VICE_PRESIDENT", label: "SVP / EVP" },
  { key: "HEAD", label: "VP · Head · Director" },
  { key: "MANAGER", label: "Manager" },
];
