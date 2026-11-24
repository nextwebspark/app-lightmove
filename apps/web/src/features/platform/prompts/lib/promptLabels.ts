import { formatDate, formatShortDate } from "../../../../lib/datetime/format";
import { formatNumber } from "../../../../lib/format";
import type {
  AiModel,
  AiProvider,
  ModelCapability,
  PromptHealthWarning,
  PromptLiveHealth,
  PromptModelUsage,
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
  return (
    prompt.shippedChanged ||
    prompt.modelUnavailable ||
    isRetiringSoon(prompt.modelRetiresInDays) ||
    (prompt.health?.warnings.length ?? 0) > 0
  );
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

/** The spans the Usage tab offers, in days; the server reads at most 90. */
export const USAGE_SPANS = [7, 30, 90] as const;
export type UsageSpan = (typeof USAGE_SPANS)[number];
export const DEFAULT_USAGE_SPAN: UsageSpan = 30;

/** 0.0812 → "8.1%". */
export function percentLabel(fraction: number): string {
  return `${Math.round(fraction * 1000) / 10}%`;
}

// en-US: en-GB writes "4.1m", which reads as metres beside a token count.
const COMPACT = new Intl.NumberFormat("en-US", { notation: "compact", maximumFractionDigits: 1 });

/** 4_100_000 → "4.1M": a token total, where the order of magnitude is the point. */
export function compactNumber(value: number): string {
  return COMPACT.format(value);
}

/** A call cut off at the output limit, stopped by the safety filter, or answered in a way the feature could not read. */
export function failuresOf(usage: PromptModelUsage): number {
  return usage.finishMaxTokens + usage.finishSafety + usage.schemaFailures;
}

/** "Fails more than v2": the list's pill for one warning. */
export function healthPillLabel(warning: PromptHealthWarning, health: PromptLiveHealth): string {
  return `${warning === "MORE_FAILURES" ? "Fails more than" : "Kept less than"} ${versionLabel(health.comparedWithVersion)}`;
}

function daysBefore(days: number): string {
  return days === 1 ? "the day before" : `the ${days} days before`;
}

function shareOf(rate: number | null, calls: number): string {
  return `${percentLabel(rate ?? 0)} of ${formatNumber(calls)}`;
}

/** The pill's title: both sides' figures, as the server compared them. */
export function healthPillTitle(warning: PromptHealthWarning, health: PromptLiveHealth): string {
  const before = `version ${health.comparedWithVersion} over ${daysBefore(health.days)}`;
  return warning === "MORE_FAILURES"
    ? `Since it went live, ${shareOf(health.liveFailureRate, health.liveCalls)} calls failed, against ` +
        `${shareOf(health.previousFailureRate, health.previousCalls)} for ${before}.`
    : `Since it went live, people kept ${shareOf(health.liveAcceptRate, health.liveDecisions)} answers as ` +
        `written, against ${shareOf(health.previousAcceptRate, health.previousDecisions)} for ${before}.`;
}

/** The prompt page's note for one warning, which points at Usage. */
export function healthNote(warning: PromptHealthWarning, health: PromptLiveHealth, liveVersion: number): string {
  const since = formatShortDate(health.liveSince);
  const before = `in ${daysBefore(health.days)}. Usage has the detail.`;
  return warning === "MORE_FAILURES"
    ? `Version ${liveVersion} fails more often than version ${health.comparedWithVersion} did: ` +
        `${shareOf(health.liveFailureRate, health.liveCalls)} calls since it went live on ${since}, against ` +
        `${shareOf(health.previousFailureRate, health.previousCalls)} ${before}`
    : `Version ${liveVersion} is kept as written less often than version ${health.comparedWithVersion} was: ` +
        `${shareOf(health.liveAcceptRate, health.liveDecisions)} answers since it went live on ${since}, against ` +
        `${shareOf(health.previousAcceptRate, health.previousDecisions)} ${before}`;
}
