import type { PipelineCatalog, PipelineStage, PipelineStatus } from "../api/types";

// Ported in part from apps/web/src/features/candidates/lib/pipelineStatus.ts; keep the two in step.

/** A pipeline or tag colour as a dot and a pill draw it; neutral for one this build has not heard of. */
const COLOURS: Record<string, { pill: string; dot: string }> = {
  neutral: { pill: "text-u-text2 bg-u-raised", dot: "bg-u-text3" },
  accent: { pill: "text-u-accent bg-u-accent-tint", dot: "bg-u-accent" },
  green: { pill: "text-u-direct bg-u-direct-tint", dot: "bg-u-direct" },
  violet: { pill: "text-u-chart-5 bg-u-chart-5/15", dot: "bg-u-chart-5" },
  adjacent: { pill: "text-u-adjacent bg-u-adjacent-tint", dot: "bg-u-adjacent" },
  signal: { pill: "text-u-signal bg-u-signal-tint", dot: "bg-u-signal" },
  red: { pill: "text-u-offlimits bg-u-offlimits-tint", dot: "bg-u-offlimits" },
};

export function colourPillClass(colour: string | null | undefined): string {
  return (COLOURS[(colour ?? "").toLowerCase()] ?? COLOURS.neutral).pill;
}

export function colourDotClass(colour: string | null | undefined): string {
  return (COLOURS[(colour ?? "").toLowerCase()] ?? COLOURS.neutral).dot;
}

export function liveStages(catalog: PipelineCatalog): PipelineStage[] {
  return catalog.stages.filter((stage) => !stage.retired);
}

export function liveStatuses(stage: Pick<PipelineStage, "statuses">): PipelineStatus[] {
  return stage.statuses.filter((status) => !status.retired);
}

export function findStatus(
  catalog: PipelineCatalog,
  key: string,
): { stage: PipelineStage; status: PipelineStatus } | null {
  for (const stage of catalog.stages) {
    const status = stage.statuses.find((candidate) => candidate.key === key);
    if (status) return { stage, status };
  }
  return null;
}

/** Where someone added with no status stands, as the server decides it: the first live stage's default in process. */
export function startingStatusKey(catalog: PipelineCatalog): string | null {
  for (const stage of liveStages(catalog)) {
    const status = stage.statuses.find((each) => each.isDefault && !each.retired && !each.outOfProcess);
    if (status) return status.key;
  }
  return null;
}

/** A way out says so only beside statuses in process; a stage of ways out alone needs no tag on every row. */
export function tagsOutOfProcess(stage: Pick<PipelineStage, "statuses">, status: Pick<PipelineStatus, "outOfProcess">) {
  return status.outOfProcess && liveStatuses(stage).some((other) => !other.outOfProcess);
}

export interface StatusGroup {
  stage: PipelineStage;
  statuses: PipelineStatus[];
}

/**
 * What the picker offers: the live stages in order, each with its live statuses that match the query. A status the
 * person holds is kept though retired, so the picker never reads as if they stood nowhere.
 */
export function pickableGroups(catalog: PipelineCatalog, query: string, heldKey: string | null): StatusGroup[] {
  const typed = query.trim().toLowerCase();
  return [...catalog.stages]
    .sort((a, b) => a.position - b.position)
    .filter((stage) => !stage.retired || stage.statuses.some((status) => status.key === heldKey))
    .map((stage) => ({
      stage,
      statuses: [...stage.statuses]
        .sort((a, b) => a.position - b.position)
        .filter((status) => !status.retired || status.key === heldKey)
        .filter((status) => !typed || status.label.toLowerCase().includes(typed) || stage.label.toLowerCase().includes(typed)),
    }))
    .filter((group) => group.statuses.length > 0);
}
