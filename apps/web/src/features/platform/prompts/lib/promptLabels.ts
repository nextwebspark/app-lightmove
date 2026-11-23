import { formatDate } from "../../../../lib/datetime/format";
import { formatNumber } from "../../../../lib/format";
import type {
  AiModel,
  AiProvider,
  ModelCapability,
  PromptOverview,
  PromptVersionSummary,
  SettingKey,
  SettingRange,
} from "../api/types";

export const PROMPTS_PATH = "/platform/prompts";
export const promptPath = (promptId: string) => `${PROMPTS_PATH}/${encodeURIComponent(promptId)}`;

export const VERSION_PARAM = "version";
export const PUT_LIVE_PARAM = "putLive";
/** A prompt's page opened on one version, with Put live asked for at once: where a run's page sends its candidate. */
export const putLivePath = (promptId: string, version: number) =>
  `${promptPath(promptId)}?${VERSION_PARAM}=${version}&${PUT_LIVE_PARAM}=1`;

/** How long before a model's stated retirement the console starts warning, as the server's list does. */
export const RETIREMENT_WARNING_DAYS = 60;

export const SETTINGS: readonly { key: SettingKey; label: string; unit: string; integer: boolean }[] = [
  { key: "temperature", label: "Temperature", unit: "", integer: false },
  { key: "thinkingBudget", label: "Thinking budget", unit: " tokens", integer: true },
  { key: "maxOutputTokens", label: "Max output tokens", unit: "", integer: true },
];

export const PROVIDER_LABEL: Record<AiProvider, string> = {
  VERTEX_GEMINI: "Vertex AI",
};

export function versionLabel(version: number): string {
  return `v${version}`;
}

/** "04 Nov 2026 · Noor Rahman", or "· Shipped" for a version a release brought. */
export function changedLabel(at: string, by: string | null): string {
  return `${formatDate(at)} · ${by ?? "Shipped"}`;
}

export function needsAttention(prompt: PromptOverview): boolean {
  return prompt.shippedChanged || prompt.modelUnavailable || isRetiringSoon(prompt.modelRetiresInDays);
}

export function isRetiringSoon(retiresInDays: number | null): boolean {
  return retiresInDays !== null && retiresInDays <= RETIREMENT_WARNING_DAYS;
}

export function retirementLabel(retiresInDays: number): string {
  if (retiresInDays <= 0) return "Model retired";
  return `Model retires in ${retiresInDays} ${retiresInDays === 1 ? "day" : "days"}`;
}

/** Said wherever a live version's model can no longer answer it. */
export const MODEL_UNAVAILABLE_HINT =
  "This version's model is retired, no longer offered or cannot answer this prompt. The default model answers it instead.";

export function rangeLabel(range: SettingRange, unit: string): string {
  return `${formatNumber(range.min)} to ${formatNumber(range.max)}${unit}`;
}

export function settingLabel(value: number | null, fallback: number | null): string {
  if (value !== null) return formatNumber(value);
  return fallback === null ? "Default" : `Default (${formatNumber(fallback)})`;
}

/** "Gemini 3.5 Flash · temperature 0.3", as a version's history line names what it ran on. */
export function versionModelLine(version: PromptVersionSummary): string {
  const model = version.modelName ?? "Default model";
  return version.settings.temperature === null ? model : `${model} · temperature ${version.settings.temperature}`;
}

/** Why a prompt cannot use a model, or null when it can. */
export function unusableReason(model: AiModel, needs: ModelCapability | null): string | null {
  if (model.retired) return "retired";
  if (needs && !model.capabilities.includes(needs)) {
    return needs === "TOOL_CALLING" ? "no tool calling" : "no structured answer";
  }
  return null;
}

export function modelOptionLabel(model: AiModel, needs: ModelCapability | null): string {
  const reason = unusableReason(model, needs);
  return `${model.displayName} · retires ${formatDate(model.retiresOn)}${reason ? ` · ${reason}` : ""}`;
}

const WHOLE_NUMBER = /^\d+$/;
const DECIMAL = /^(\d+(\.\d*)?|\.\d+)$/;

/**
 * A setting's field as typed: empty is "not set", anything else must read as a number, and a whole one where the
 * server binds an integer. A comma is refused, never stripped: "0,2" typed on a decimal-comma keyboard once saved as 2.
 */
export function parseSetting(text: string, integer: boolean): number | null | "invalid" {
  const trimmed = text.trim();
  if (trimmed === "") return null;
  return (integer ? WHOLE_NUMBER : DECIMAL).test(trimmed) ? Number(trimmed) : "invalid";
}
