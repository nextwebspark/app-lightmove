/** The feedback endpoint's contract, mirrored — see features/auth/api/types.ts for why by hand. */

export type FeedbackKind = "BUG" | "FEATURE_REQUEST";

export type FeedbackSeverity = "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";

/** What the browser knows about itself. Collected, never asked for. */
export interface FeedbackContext {
  pageUrl: string;
  userAgent: string;
  viewport: string;
  screenSize: string;
  devicePixelRatio: string;
  language: string;
  timezone: string;
  theme: string;
  reportedAt: string;
}

export interface FeedbackReport {
  kind: FeedbackKind;
  severity: FeedbackSeverity;
  title: string;
  message: string;
  stepsToReproduce: string | null;
  /** Only read by the server for a caller with no session; a signed-in one comes from their token. */
  reporterEmail: string | null;
  context: FeedbackContext;
}

export interface FeedbackResponse {
  /** False means received and logged, not failed — a deployment with no tracker credential. */
  published: boolean;
  issueNumber: number | null;
  issueUrl: string | null;
}
