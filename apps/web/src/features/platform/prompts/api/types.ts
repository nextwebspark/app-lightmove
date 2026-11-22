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

/** `modelId` null is the configured model; `lastChangedBy` null means a release shipped the live version. */
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
  shippedChanged: boolean;
  lastChangedAt: string;
  lastChangedBy: string | null;
}

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
