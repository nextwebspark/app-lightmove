import { formatRelativeTime } from "../../../lib/format";
import type { ResearcherRole } from "../api/types";

const ROLE_LABELS: Record<ResearcherRole, string> = {
  LEAD: "Lead",
  RESEARCHER: "Researcher",
  FORMER: "Former member",
};

export function roleLabel(role: ResearcherRole): string {
  return ROLE_LABELS[role];
}

/** "filed 2 days ago" — coarse on purpose, like every relative time in the app. */
export function sinceLabel(lastAddedAt: string | null): string {
  if (!lastAddedAt) return "nothing filed yet";
  const relative = formatRelativeTime(lastAddedAt);
  return relative === "active now" ? "filed just now" : `filed ${relative}`;
}
