import type { CandidatePipelineStage } from "../../candidates/api/types";
import { findStatus, LEGACY_CATALOG } from "../../candidates/lib/pipelineStatus";
import type { PipelineCatalog } from "../../workspace/api/types";

/** Where the mandate has got to with someone, in the four readings the report draws — read off their stage. */
export type StatusTone = "interested" | "inConversation" | "closed" | "untouched";

export const STATUS_TONES: Record<StatusTone, { label: string; fill: string; swatch: string; pill: string }> = {
  interested: { label: "Interested or further", fill: "fill-u-direct", swatch: "bg-u-direct", pill: "bg-u-direct-tint text-u-direct" },
  inConversation: {
    label: "In conversation",
    fill: "fill-u-adjacent",
    swatch: "bg-u-adjacent",
    pill: "bg-u-adjacent-tint text-u-adjacent",
  },
  closed: {
    label: "Declined",
    fill: "fill-u-offlimits",
    swatch: "bg-u-offlimits",
    pill: "bg-u-offlimits-tint text-u-offlimits",
  },
  untouched: {
    label: "Identified",
    fill: "fill-u-text3",
    swatch: "bg-u-text3",
    pill: "border border-u-border-strong bg-u-sunken text-u-text2",
  },
};

/** A row the report draws: its status key, and the stage the server derived from it where it sent one. */
export interface StagedStatus {
  status: string;
  pipelineStage?: Pick<CandidatePipelineStage, "key" | "kind" | "position"> | null;
}

/**
 * The closed stage is a closed door; the first stage is someone not yet approached and the second someone in
 * conversation; every active stage past those is interest or further. The seeded stage keys win over position,
 * so a stage an admin inserted early does not shift them. A row without a stage is read off the catalog.
 */
export function statusTone(row: StagedStatus, catalog?: PipelineCatalog | null): StatusTone {
  const stage = row.pipelineStage ?? stageOf(row.status, catalog);
  if (!stage) return "untouched";
  if (stage.kind === "closed") return "closed";
  const seeded = SEEDED_STAGE_TONES[stage.key];
  if (seeded) return seeded;
  if (stage.position <= 1) return "untouched";
  if (stage.position === 2) return "inConversation";
  return "interested";
}

/** A stage key as the four readings: the team drawers' mix is counted by stage, not by status. */
export function stageTone(stageKey: string, catalog?: PipelineCatalog | null): StatusTone {
  const stage = (catalog ?? LEGACY_CATALOG).stages.find((candidate) => candidate.key === stageKey);
  if (stage) return statusTone({ status: "", pipelineStage: stage });
  return SEEDED_STAGE_TONES[stageKey] ?? statusTone({ status: stageKey }, catalog);
}

const SEEDED_STAGE_TONES: Record<string, StatusTone> = {
  identified: "untouched",
  contacted: "inConversation",
  interested: "interested",
  qualified: "interested",
  shortlisted: "interested",
  declined: "closed",
};

function stageOf(status: string, catalog?: PipelineCatalog | null) {
  return ((catalog && findStatus(catalog, status)) || findStatus(LEGACY_CATALOG, status))?.stage ?? null;
}
