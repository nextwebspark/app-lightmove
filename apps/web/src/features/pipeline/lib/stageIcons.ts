import { ICONS } from "../../../components/layout/Icon";

export interface StageMark {
  icon: string;
  colourClass: string;
}

/**
 * The seeded stages' own marks, keyed on the stage key the seed gave them. The colour is the mark's, not the
 * catalog's: the seed paints Approach and Offer the same violet, and the board tells them apart.
 */
const SEEDED_STAGE_MARKS: Record<string, StageMark> = {
  identified: { icon: ICONS.stageResearch, colourClass: "text-u-text2" },
  contacted: {
    icon: ICONS.stageApproach,
    colourClass: "text-[color:color-mix(in_srgb,var(--color-u-accent)_62%,var(--color-u-text3))]",
  },
  interested: { icon: ICONS.stageAssessment, colourClass: "text-u-adjacent" },
  qualified: { icon: ICONS.stageShortlist, colourClass: "text-u-chart-3" },
  shortlisted: { icon: ICONS.stageOffer, colourClass: "text-u-direct" },
};

/** A seeded stage's mark; null for a stage a workspace added, which keeps its colour dot. */
export function stageMarkOf(stageKey: string): StageMark | null {
  return SEEDED_STAGE_MARKS[stageKey] ?? null;
}
