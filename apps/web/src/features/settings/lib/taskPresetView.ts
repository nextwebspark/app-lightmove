import { formatInstantDate } from "../../../lib/format";
import type { TaskKind, TaskPreset, TaskPresetSuggestion } from "../../tasks/api/types";

/** A task kind as a chip draws it (`Settings.dc.html`'s TYPES): its word, its glyph, its colour. */
export const TASK_KIND_CHIPS: Record<TaskKind, { label: string; glyph: string; className: string }> = {
  call: {
    label: "Call",
    glyph:
      "M22 16.9v3a2 2 0 0 1-2.2 2 19.8 19.8 0 0 1-8.6-3.1 19.5 19.5 0 0 1-6-6A19.8 19.8 0 0 1 2.1 4.2 2 2 0 0 1 4.1 2h3a2 2 0 0 1 2 1.7c.1.9.4 1.8.7 2.7a2 2 0 0 1-.5 2.1L8 9.8a16 16 0 0 0 6 6l1.3-1.3a2 2 0 0 1 2.1-.4c.9.3 1.8.6 2.7.7a2 2 0 0 1 1.7 2Z",
    className: "bg-u-direct-tint text-u-direct",
  },
  email: { label: "Email", glyph: "M4 4h16v16H4zM4 6l8 7 8-7", className: "bg-u-accent-tint text-u-accent" },
  meeting: { label: "Meeting", glyph: "M3 5h18v16H3zM16 3v4M8 3v4M3 10h18", className: "bg-u-chart-5/15 text-u-chart-5" },
  follow_up: {
    label: "Follow-up",
    glyph: "M15 14l5-5-5-5M20 9H9a5 5 0 0 0-5 5v6",
    className: "bg-u-adjacent-tint text-u-adjacent",
  },
  todo: { label: "To-do", glyph: "M3 3h18v18H3zM8 12l3 3 5-6", className: "bg-u-surface text-u-text2" },
};

export const TASK_KINDS: readonly TaskKind[] = ["call", "email", "meeting", "follow_up", "todo"];

/** The default dues a preset offers; null leaves the date to whoever sets the task. */
export const DUE_OFFSETS: readonly (number | null)[] = [null, 0, 1, 2, 5, 30];

export function dueOffsetLabel(days: number | null): string {
  if (days === null) return "None";
  if (days === 0) return "Same day";
  return days === 1 ? "+1 day" : `+${days} days`;
}

/** The offsets a select offers, with a preset's own odd one kept rather than silently changed. */
export function dueOffsetsFor(current: number | null): (number | null)[] {
  return DUE_OFFSETS.includes(current)
    ? [...DUE_OFFSETS]
    : [...DUE_OFFSETS, current].sort((a, b) => (a ?? -1) - (b ?? -1));
}

export function timesLabel(count: number): string {
  return count === 1 ? "once" : `${count} times`;
}

export function usageLabel(usage: number): string {
  return usage > 0 ? `Used ${timesLabel(usage)}` : "Not used yet";
}

/** "Retired 03 Oct 2026 · used 4 times", as the Retired fold says it. */
export function retiredLabel(preset: TaskPreset): string {
  const since = formatInstantDate(preset.retiredAt);
  const retired = since ? `Retired ${since}` : "Retired";
  return `${retired} · ${preset.usage > 0 ? `used ${timesLabel(preset.usage)}` : "never used"}`;
}

/** The typed titles worth offering: the ones no preset, live or retired, already spells. */
export function openSuggestions(
  suggestions: readonly TaskPresetSuggestion[],
  presets: readonly TaskPreset[],
): TaskPresetSuggestion[] {
  const taken = new Set(presets.map((preset) => preset.label.trim().toLowerCase()));
  return suggestions.filter((suggestion) => !taken.has(suggestion.label.trim().toLowerCase()));
}

/** The presets in the order a reorder leaves them: the live ones as given, the retired after, as they were. */
export function inPresetOrder(presets: readonly TaskPreset[], liveIds: readonly string[]): TaskPreset[] {
  const byId = new Map(presets.map((preset) => [preset.id, preset]));
  const live = liveIds.flatMap((id) => {
    const preset = byId.get(id);
    return preset ? [preset] : [];
  });
  return [...live, ...presets.filter((preset) => !liveIds.includes(preset.id))].map((preset, index) => ({
    ...preset,
    position: index,
  }));
}
