import type * as TriageStages from "../features/triage/lib/triageStages";

/**
 * The Companies stages with Shortlisted and Declined offered again, for the tests that keep that half covered while
 * it is off. Every export derived from the switch is re-derived here, since the original module computed it as off.
 */
export function withShortlistAndDecline(actual: typeof TriageStages): typeof TriageStages {
  const offered = actual.TRIAGE_STAGES;
  return {
    ...actual,
    SHORTLIST_AND_DECLINE_OFFERED: true,
    OFFERED_TRIAGE_STAGES: offered,
    offeredStageBySlug: (slug) => offered.find((stage) => stage.slug === slug),
  };
}
