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
 * default model is answering it.
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
