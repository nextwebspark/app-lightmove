/**
 * Where a captured company lands in a mandate.
 *
 * The two wire tokens the API's `TriageCompanyStatus` accepts from a capture, and the two buttons in
 * the popup's footer. `declined` is deliberately absent: ruling a company out is a triage decision
 * taken with the whole mandate in view, not something a browser popup does in passing.
 */
export const TRIAGE_DESTINATIONS = ["inUniverse", "shortlisted"] as const;

export type TriageDestination = (typeof TRIAGE_DESTINATIONS)[number];

/** What each destination is called on its button, matching Extension.dc.html. */
export const DESTINATION_LABELS: Record<TriageDestination, string> = {
  inUniverse: "Add to universe",
  shortlisted: "Add to shortlist",
};

/** How the same destination reads once the company is there — for the confirmation line. */
export const DESTINATION_PAST_TENSE: Record<TriageDestination, string> = {
  inUniverse: "the universe",
  shortlisted: "the shortlist",
};
