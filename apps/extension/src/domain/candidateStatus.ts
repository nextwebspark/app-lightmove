/**
 * What a capture may file a person as.
 *
 * Four of the seven tokens the API's `CandidateStatus` accepts. `engaged`, `interested` and
 * `notInterested` are absent deliberately: each is the outcome of a conversation, judged in the
 * drawer with the whole mandate in view, not something stated in passing from a profile page — the
 * same reasoning that keeps `declined` out of `TRIAGE_DESTINATIONS`.
 *
 * The labels are a deliberate copy of the web app's `candidateVocabulary.ts`; `apps/extension` imports
 * nothing from `apps/web`, and a shared package for four strings would couple two builds for less than
 * it costs.
 */
export const CANDIDATE_CAPTURE_STATUSES = ["identified", "contacted", "offLimits", "outOfScope"] as const;

export type CandidateCaptureStatus = (typeof CANDIDATE_CAPTURE_STATUSES)[number];

/** What each status is called in the panel, matching the Add-executive drawer's own select. */
export const CANDIDATE_CAPTURE_STATUS_LABELS: Record<CandidateCaptureStatus, string> = {
  identified: "Identified",
  contacted: "Contacted",
  offLimits: "Off-limits",
  outOfScope: "Out of scope",
};

/** Where every profile starts, and what the API assumes of a capture that says nothing. */
export const DEFAULT_CANDIDATE_STATUS: CandidateCaptureStatus = "identified";
