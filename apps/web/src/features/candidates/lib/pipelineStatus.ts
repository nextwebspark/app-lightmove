import type { PipelineCatalog, PipelineStage, PipelineStatus } from "../../workspace/api/types";
import { TAG_COLOURS } from "./tagStyle";

/** A pipeline swatch as a pill and a dot draw it: the tag palette, plus the two the fixed statuses wore. */
export const PIPELINE_COLOURS: { value: string; label: string; className: string; swatch: string }[] = [
  ...TAG_COLOURS,
  { value: "signal", label: "Amber", className: "text-u-signal bg-u-signal-tint", swatch: "bg-u-signal" },
  { value: "red", label: "Red", className: "text-u-offlimits bg-u-offlimits-tint", swatch: "bg-u-offlimits" },
];

const COLOUR_BY_VALUE = new Map(PIPELINE_COLOURS.map((colour) => [colour.value, colour]));
const NEUTRAL = COLOUR_BY_VALUE.get("neutral")!;

/** Neutral for a colour this build has not heard of, so whatever the server stores still draws. */
export function pipelineColourClass(colour: string | null | undefined): string {
  return (COLOUR_BY_VALUE.get(colour ?? "") ?? NEUTRAL).className;
}

export function pipelineSwatchClass(colour: string | null | undefined): string {
  return (COLOUR_BY_VALUE.get(colour ?? "") ?? NEUTRAL).swatch;
}

/**
 * The seven statuses every workspace has, under the stages they were seeded in. Drawn while the catalog
 * is still loading or could not be read, and for a line written before the catalog existed: the seven keys
 * are system statuses, so they can never be retired and are always a valid choice.
 */
export const LEGACY_CATALOG: PipelineCatalog = {
  stages: [
    legacyStage("identified", "Identified", "neutral", "active", 1, [["identified", "Identified", "neutral"]]),
    legacyStage("contacted", "Contacted", "accent", "active", 2, [
      ["contacted", "Contacted", "accent"],
      ["engaged", "Engaged", "accent"],
    ]),
    legacyStage("interested", "Interested", "green", "active", 3, [["interested", "Interested", "green"]]),
    legacyStage("declined", "Declined", "red", "closed", 6, [
      ["notInterested", "Not interested", "neutral"],
      ["offLimits", "Off-limits", "red"],
      ["outOfScope", "Out of scope", "signal"],
    ]),
  ],
};

export interface StatusStyle {
  label: string;
  colour: string;
  className: string;
}

/** What a row carries about its own status — preferred over the catalog, so a retired status still draws. */
export interface HeldStatus {
  statusLabel?: string | null;
  statusColour?: string | null;
}

/**
 * How a status key reads: the row's own label and colour first, then the catalog, then the seven built-in
 * statuses, and last the key itself in neutral — a status the server stores always draws.
 */
export function statusStyle(key: string, held?: HeldStatus | null, catalog?: PipelineCatalog | null): StatusStyle {
  const known = (catalog && findStatus(catalog, key)?.status) ?? findStatus(LEGACY_CATALOG, key)?.status;
  const label = held?.statusLabel || known?.label || key;
  const colour = held?.statusColour || known?.colour || "neutral";
  return { label, colour, className: pipelineColourClass(colour) };
}

/** A status's label off the catalog or the seven, for a key no row carries a label with (an old timeline line). */
export function statusLabelOf(key: string, catalog?: PipelineCatalog | null): string {
  return statusStyle(key, null, catalog).label;
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

export function liveStages(catalog: PipelineCatalog): PipelineStage[] {
  return catalog.stages.filter((stage) => !stage.retired);
}

export function liveStatuses(stage: PipelineStage): PipelineStatus[] {
  return stage.statuses.filter((status) => !status.retired);
}

/** The status a candidate dropped on a stage is given; null when the stage has no live default to give. */
export function defaultStatusOf(stage: PipelineStage): PipelineStatus | null {
  return stage.statuses.find((status) => status.isDefault && !status.retired) ?? null;
}

/** Where someone added with no status stands, as the server decides it: the first live active stage's default. */
export function startingStatusKey(catalog: PipelineCatalog): string | null {
  for (const stage of liveStages(catalog)) {
    if (stage.kind !== "active") continue;
    const status = defaultStatusOf(stage);
    if (status) return status.key;
  }
  return null;
}

export function closedStage(catalog: PipelineCatalog): PipelineStage | null {
  return liveStages(catalog).find((stage) => stage.kind === "closed") ?? null;
}

function legacyStage(
  key: string,
  label: string,
  colour: string,
  kind: "active" | "closed",
  position: number,
  statuses: [string, string, string][],
): PipelineStage {
  return {
    id: key,
    key,
    label,
    colour,
    kind,
    position,
    system: true,
    retired: false,
    statuses: statuses.map(([statusKey, statusLabel, statusColour], index) => ({
      id: statusKey,
      key: statusKey,
      label: statusLabel,
      colour: statusColour,
      position: index + 1,
      isDefault: index === 0,
      system: true,
      retired: false,
    })),
  };
}
