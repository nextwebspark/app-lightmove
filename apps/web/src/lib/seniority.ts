/**
 * The seniority ladder, shared by brief and candidate so the two never drift. Both tokens live here: the candidate
 * contract sends the label ("N-1"), the position contract the tier name; the server stores the name.
 */

export type SeniorityTier = "BOARD" | "C_SUITE" | "N_MINUS_1" | "N_MINUS_2" | "N_MINUS_3";

/** Ordered from the board down — the order every seniority picker offers. */
export const SENIORITY_LABELS: Record<SeniorityTier, string> = {
  BOARD: "Board",
  C_SUITE: "C-Suite",
  N_MINUS_1: "N-1",
  N_MINUS_2: "N-2",
  N_MINUS_3: "N-3",
};

export const SENIORITY_TIERS = Object.keys(SENIORITY_LABELS) as SeniorityTier[];

/** The label doubles as the candidate contract's wire token. */
export type SeniorityToken = (typeof SENIORITY_LABELS)[SeniorityTier];

export const SENIORITY_TOKENS = Object.values(SENIORITY_LABELS) as SeniorityToken[];
