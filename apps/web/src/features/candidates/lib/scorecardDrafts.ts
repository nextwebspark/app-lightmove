/**
 * Where the Assessment tab keeps a consultant's unsaved scorecard. It holds what they think of a candidate, so it
 * goes when the session does (#927) — a shared computer's next user must not reopen it.
 */
const DRAFT_KEY_PREFIX = "lm.scorecard-draft.";

export const scorecardDraftKey = (candidateId: string) => `${DRAFT_KEY_PREFIX}${candidateId}`;

export function forgetScorecardDrafts(): void {
  try {
    const kept = Array.from({ length: sessionStorage.length }, (_, index) => sessionStorage.key(index));
    for (const key of kept) {
      if (key?.startsWith(DRAFT_KEY_PREFIX)) sessionStorage.removeItem(key);
    }
  } catch {
    // A private window refuses storage, so nothing was kept to forget.
  }
}
