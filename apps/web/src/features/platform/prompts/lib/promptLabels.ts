import { formatDate } from "../../../../lib/datetime/format";
import type {
  AiModel,
  GenerationSettings,
  ModelCapability,
  PromptOverview,
  PromptVersionSummary,
  SettingKey,
  SettingRange,
} from "../api/types";

export const PROMPTS_PATH = "/platform/prompts";
export const promptPath = (promptId: string) => `${PROMPTS_PATH}/${encodeURIComponent(promptId)}`;

/** How long before a model's stated retirement the console starts warning, as the server's list does. */
export const RETIREMENT_WARNING_DAYS = 60;

export const SETTINGS: readonly { key: SettingKey; label: string; unit: string }[] = [
  { key: "temperature", label: "Temperature", unit: "" },
  { key: "thinkingBudget", label: "Thinking budget", unit: " tokens" },
  { key: "maxOutputTokens", label: "Max output tokens", unit: "" },
];

export const UNSET_SETTINGS: GenerationSettings = { temperature: null, thinkingBudget: null, maxOutputTokens: null };

const whole = new Intl.NumberFormat("en-GB");

export function versionLabel(version: number): string {
  return `v${version}`;
}

/** "04 Nov 2026 · Noor Rahman", or "· Shipped" for a version a release brought. */
export function changedLabel(at: string, by: string | null): string {
  return `${formatDate(at)} · ${by ?? "Shipped"}`;
}

export function needsAttention(prompt: PromptOverview): boolean {
  return prompt.shippedChanged || isRetiringSoon(prompt.modelRetiresInDays);
}

export function isRetiringSoon(retiresInDays: number | null): boolean {
  return retiresInDays !== null && retiresInDays <= RETIREMENT_WARNING_DAYS;
}

export function retirementLabel(retiresInDays: number): string {
  if (retiresInDays <= 0) return "Model retired";
  return `Model retires in ${retiresInDays} ${retiresInDays === 1 ? "day" : "days"}`;
}

export function rangeLabel(range: SettingRange, unit: string): string {
  return `${whole.format(range.min)} to ${whole.format(range.max)}${unit}`;
}

export function settingLabel(value: number | null, fallback: number | null): string {
  if (value !== null) return whole.format(value);
  return fallback === null ? "Default" : `Default (${whole.format(fallback)})`;
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

/** A setting's field as typed: empty is "not set", anything else must read as a number. */
export function parseSetting(text: string): number | null | "invalid" {
  const trimmed = text.trim().replace(/,/g, "");
  if (trimmed === "") return null;
  const value = Number(trimmed);
  return Number.isFinite(value) ? value : "invalid";
}
