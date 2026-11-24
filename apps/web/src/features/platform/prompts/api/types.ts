export type PromptKind = "STRUCTURED" | "PROSE" | "TOOL_CALLING";
export type PromptOrigin = "BUNDLED" | "EDITED";
export type AiProvider = "VERTEX_GEMINI";
export type ModelCapability = "STRUCTURED_ANSWER" | "TOOL_CALLING";
export type FinishKind = "STOP" | "MAX_TOKENS" | "SAFETY" | "OTHER";

/** A null field is "not set": the feature's own default applies. */
export interface GenerationSettings {
  temperature: number | null;
  thinkingBudget: number | null;
  maxOutputTokens: number | null;
}

export type SettingKey = keyof GenerationSettings;

export interface SettingRange {
  min: number;
  max: number;
}

/**
 * `modelId` null is the configured model; `lastChangedBy` null means a release shipped the live version.
 * `modelUnavailable`: the live version's model is retired, no longer offered or cannot answer this prompt, so the
 * default model is answering it. `health` is null unless the live version replaced another.
 */
export interface PromptOverview {
  id: string;
  name: string;
  description: string;
  kind: PromptKind;
  liveVersion: number;
  origin: PromptOrigin;
  provider: AiProvider | null;
  modelId: string | null;
  modelName: string | null;
  modelRetiresInDays: number | null;
  modelUnavailable: boolean;
  shippedChanged: boolean;
  lastChangedAt: string;
  lastChangedBy: string | null;
  health: PromptLiveHealth | null;
}

export type PromptHealthWarning = "MORE_FAILURES" | "FEWER_ACCEPTED";

/**
 * The live version since it went live (`liveSince`, a UTC day) beside the version it replaced, over the same number
 * of days before. A rate is a fraction, null where nothing was counted; `warnings` is empty unless both sides have
 * enough to compare.
 */
export interface PromptLiveHealth {
  warnings: PromptHealthWarning[];
  comparedWithVersion: number;
  liveSince: string;
  days: number;
  liveCalls: number;
  liveFailureRate: number | null;
  previousCalls: number;
  previousFailureRate: number | null;
  liveDecisions: number;
  liveAcceptRate: number | null;
  previousDecisions: number;
  previousAcceptRate: number | null;
}

/** One version's calls on one model. Counts and token totals only. */
export interface PromptModelUsage {
  provider: AiProvider;
  modelId: string;
  modelName: string;
  calls: number;
  inputTokens: number;
  outputTokens: number;
  finishStop: number;
  finishMaxTokens: number;
  finishSafety: number;
  finishOther: number;
  guardRefusals: number;
  schemaReasks: number;
  schemaFailures: number;
}

export interface PromptVersionUsage {
  version: number;
  live: boolean;
  accepted: number;
  changed: number;
  models: PromptModelUsage[];
}

/** `decisionsCounted` false: no screen asks a person to keep or change this prompt's answer. Newest version first. */
export interface PromptUsage {
  days: number;
  decisionsCounted: boolean;
  versions: PromptVersionUsage[];
}

/** `NO_RESULT`: a run finished and nothing it measured counts, so it asks for a reason as no run does. */
export type PromotionGateState = "PASSED" | "FAILED" | "NO_RESULT" | "NONE";

/**
 * What the evals say about putting a version live in place of the one serving: its latest finished run on the
 * holdout cases against that version. Everything but `state` is null, and `failedChecks` empty, when there is none.
 */
export interface PromotionGate {
  state: PromotionGateState;
  runId: string | null;
  runNumber: number | null;
  finishedAt: string | null;
  cases: number | null;
  repeats: number | null;
  checksBetter: number | null;
  checksSame: number | null;
  checksWorse: number | null;
  regressedCases: number | null;
  /** Pass rates in percent. */
  failedChecks: { label: string; candidate: number; baseline: number }[];
  datasetId: string | null;
  /** Null: no judge scored the run. `state` is the rubric's when the run finished. */
  judged: {
    counted: boolean;
    state: string;
    criteria: number;
    forCandidate: number;
    forBaseline: string[];
  } | null;
}

/** `gate` is null for the version serving. */
export interface PromptVersionSummary {
  version: number;
  origin: PromptOrigin;
  live: boolean;
  changeNote: string | null;
  createdAt: string;
  author: string | null;
  provider: AiProvider | null;
  modelId: string | null;
  modelName: string | null;
  settings: GenerationSettings;
  gate: PromotionGate | null;
}

export interface PromptDetail {
  prompt: PromptOverview;
  versions: PromptVersionSummary[];
  newestShippedVersion: number | null;
  variables: string[];
  needs: ModelCapability | null;
  defaultModelId: string | null;
  defaults: GenerationSettings;
  userMessageTemplate: string | null;
  trial: { name: string; description: string } | null;
  aiUse: { title: string; logic: string } | null;
}

export interface PromptVersionBody {
  version: number;
  body: string;
}

export interface SavePromptVersionRequest {
  body: string;
  provider: AiProvider | null;
  modelId: string | null;
  settings: GenerationSettings;
  changeNote: string;
}

export interface PromptTrialResult {
  version: number;
  rawAnswer: string;
  parsedAnswer: unknown;
  model: string | null;
  inputTokens: number;
  outputTokens: number;
  finish: FinishKind;
  tookMillis: number;
}

export interface AiModel {
  provider: AiProvider;
  modelId: string;
  displayName: string;
  status: string;
  retiresOn: string;
  retiresInDays: number;
  retiringSoon: boolean;
  retired: boolean;
  dataTerms: string;
  capabilities: ModelCapability[];
  temperature: SettingRange;
  thinkingBudget: SettingRange;
  maxOutputTokens: SettingRange;
}
