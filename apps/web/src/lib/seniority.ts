/**
 * The seniority ladder, shared by both halves of a mandate.
 *
 * A brief states the seniority of the seat it is searching for; a candidate row states the seniority
 * of the person who might fill it. Those are the same claim about the same ladder, so one list answers
 * both — two copies would let a tier be added to one screen and not the other, and the two would
 * quietly stop matching.
 *
 * The two APIs speak different tokens for it, which is why both spellings live here: the candidate
 * contract sends the label ("N-1", what a consultant writes), the position contract sends the tier
 * name. The server stores the name either way.
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
