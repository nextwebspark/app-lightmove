/** A consultant's assessment of one executive against the position's brief. Staff-only. */
export type ScorecardPanel = "technical" | "behavioural";

export type ScorecardStatus = "draft" | "reviewed";

export interface ScorecardCriterion {
  panel: ScorecardPanel;
  name: string;
  /** The brief's own words for what it measures; null on a retired criterion. */
  description: string | null;
  weight: number;
  /** Its weight's part of its panel, 0–100. */
  weightInPanel: number;
  /** What a 10 earns, out of the assessment's 100. */
  maxPoints: number;
  /** 0–10, or null: not scored yet, which is an open question rather than a zero. */
  score: number | null;
  points: number;
  evidence: string | null;
  /** Still the AI's draft, untouched by anyone: it wears a sparkle until a consultant changes it. */
  aiDrafted: boolean;
}

export interface ScorecardPanelTally {
  panel: ScorecardPanel;
  /** The panel's part of the assessment's 100: what a 10 on every criterion earns. */
  share: number;
  points: number;
  average: number | null;
  scoredCount: number;
  criteria: ScorecardCriterion[];
}

export interface ScorecardReport {
  careerSummary: string | null;
  coreValue: string | null;
  concerns: string | null;
  workingStyle: string | null;
  motivation: string | null;
}

export interface CandidateScorecard {
  status: ScorecardStatus;
  /** Null until the first save; sent back with the next one. */
  version: number | null;
  overall: number;
  scoredCount: number;
  criteriaCount: number;
  panels: ScorecardPanelTally[];
  /** Scores given against criteria the brief no longer has: kept and shown, never counted. */
  retired: ScorecardCriterion[];
  report: ScorecardReport;
  /** The report sections still holding the AI's untouched draft. */
  reportAiDrafted: (keyof ScorecardReport)[];
  /** When the AI last drafted it; null when it never has. */
  aiDraftedAt: string | null;
  updatedAt: string | null;
  updatedByName: string | null;
  reviewedAt: string | null;
  reviewedByName: string | null;
}

export interface CriterionScoreInput {
  panel: ScorecardPanel;
  name: string;
  score: number | null;
  evidence: string | null;
}

export interface SaveScorecardPayload {
  version: number | null;
  scores: CriterionScoreInput[];
  report: ScorecardReport;
  markReviewed: boolean;
}
