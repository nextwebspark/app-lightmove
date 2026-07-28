import type { MatchTier } from "../api/types";

/** How each scope bucket a company matched through reads on its badge — shared by card and drawer. */
export const TIER_META: Record<MatchTier, { label: string; className: string }> = {
  DIRECT: { label: "Direct", className: "text-sky bg-sky-dim" },
  ADJACENT: { label: "Adjacent", className: "text-amber bg-amber-dim" },
  INFERRED: { label: "AI Inferred", className: "text-text3 bg-line-soft" },
};
