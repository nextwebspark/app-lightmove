import type { AiModel, PromptDetail, PromptOverview, PromptVersionSummary } from "../api/types";

/** Test data for the Prompts screens: an assessment draft with a shipped version and a live edit. */
export const overview = (overrides: Partial<PromptOverview> = {}): PromptOverview => ({
  id: "candidate-scorecard-draft",
  name: "Assessment draft",
  description: "Drafts a score and its evidence for each competency of the brief.",
  kind: "STRUCTURED",
  liveVersion: 2,
  origin: "EDITED",
  provider: "VERTEX_GEMINI",
  modelId: "gemini-3.5-flash",
  modelName: "Gemini 3.5 Flash",
  modelRetiresInDays: 179,
  modelUnavailable: false,
  shippedChanged: false,
  lastChangedAt: "2026-11-04T10:00:00Z",
  lastChangedBy: "Noor Rahman",
  ...overrides,
});

export const version = (overrides: Partial<PromptVersionSummary> = {}): PromptVersionSummary => ({
  version: 1,
  origin: "BUNDLED",
  live: false,
  changeNote: null,
  createdAt: "2026-10-21T10:00:00Z",
  author: null,
  provider: null,
  modelId: null,
  modelName: null,
  settings: { temperature: null, thinkingBudget: null, maxOutputTokens: null },
  gate: null,
  ...overrides,
});

export const detail = (overrides: Partial<PromptDetail> = {}): PromptDetail => ({
  prompt: overview(),
  versions: [
    version({
      version: 3,
      origin: "EDITED",
      changeNote: "Ask for the source of each piece of evidence",
      createdAt: "2026-11-10T10:00:00Z",
      author: "Noor Rahman",
      provider: "VERTEX_GEMINI",
      modelId: "gemini-3.8-flash",
      modelName: "Gemini 3.8 Flash",
    }),
    version({
      version: 2,
      origin: "EDITED",
      live: true,
      changeNote: "Shorter evidence",
      createdAt: "2026-11-04T10:00:00Z",
      author: "Noor Rahman",
      provider: "VERTEX_GEMINI",
      modelId: "gemini-3.5-flash",
      modelName: "Gemini 3.5 Flash",
    }),
    version(),
  ],
  newestShippedVersion: 1,
  variables: [],
  needs: "STRUCTURED_ANSWER",
  defaultModelId: "gemini-3.5-flash",
  defaults: { temperature: 0, thinkingBudget: 0, maxOutputTokens: 8192 },
  userMessageTemplate: "THE CANDIDATE\n{profile}\n\nNOTES ON FILE\n{notes}",
  trial: { name: "Strong direct evidence", description: "A fictional finance chief. No workspace's data is used." },
  aiUse: { title: "Assessment draft", logic: "Scores a person against the brief's competencies." },
  ...overrides,
});

export const model = (overrides: Partial<AiModel> = {}): AiModel => ({
  provider: "VERTEX_GEMINI",
  modelId: "gemini-3.5-flash",
  displayName: "Gemini 3.5 Flash",
  status: "GA",
  retiresOn: "2027-05-19",
  retiresInDays: 179,
  retiringSoon: false,
  retired: false,
  dataTerms: "Vertex AI, global endpoint.",
  capabilities: ["STRUCTURED_ANSWER", "TOOL_CALLING"],
  temperature: { min: 0, max: 1 },
  thinkingBudget: { min: 0, max: 8192 },
  maxOutputTokens: { min: 256, max: 16384 },
  ...overrides,
});
