import { vi } from "vitest";
import type * as TriageStages from "../features/triage/lib/triageStages";

/** The Companies stages with Shortlisted and Declined offered again, for the tests that keep that half covered while it is off. */
export async function triageStagesWithShortlistAndDecline(): Promise<typeof TriageStages> {
  const actual = await vi.importActual<typeof TriageStages>("../features/triage/lib/triageStages");
  return {
    ...actual,
    SHORTLIST_AND_DECLINE_OFFERED: true,
    OFFERED_TRIAGE_STAGES: actual.TRIAGE_STAGES,
    offeredStageBySlug: actual.stageBySlug,
  };
}
