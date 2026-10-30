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
 * is still loading or could not be read, and for a line written before the catalog existed: every workspace
 * holds the seven keys, though it may since have retired any of them.
 */
export const LEGACY_CATALOG: PipelineCatalog = {
  stages: [
    legacyStage("identified", "Identified", "neutral", false, 1, [["identified", "Identified", "neutral"]]),
    legacyStage("contacted", "Contacted", "accent", false, 2, [
      ["contacted", "Contacted", "accent"],
      ["engaged", "Engaged", "accent"],
    ]),
    legacyStage("interested", "Interested", "green", false, 3, [["interested", "Interested", "green"]]),
    legacyStage("declined", "Declined", "red", true, 6, [
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

/**
 * Whether a status should say it is a way out: beside statuses in process it would read as one of them. A stage of
 * ways out alone, an old Declined say, needs no tag on every row.
 */
export function tagsOutOfProcess(stage: Pick<PipelineStage, "statuses">, status: Pick<PipelineStatus, "outOfProcess">) {
  return status.outOfProcess && liveStatuses(stage).some((other) => !other.outOfProcess);
}

/** A status as a filter offers it, a way out beside statuses in process saying so. */
export function offeredStatusLabel(
  stage: Pick<PipelineStage, "statuses">,
  status: Pick<PipelineStatus, "label" | "outOfProcess">,
): string {
  return tagsOutOfProcess(stage, status) ? `${status.label} · Out of process` : status.label;
}

export function liveStages(catalog: PipelineCatalog): PipelineStage[] {
  return catalog.stages.filter((stage) => !stage.retired);
}

export function liveStatuses(stage: Pick<PipelineStage, "statuses">): PipelineStatus[] {
  return stage.statuses.filter((status) => !status.retired);
}

/** The status a candidate dropped on a stage is given; null when the stage has no live default in process. */
export function defaultStatusOf(stage: PipelineStage): PipelineStatus | null {
  return stage.statuses.find((status) => status.isDefault && !status.retired && !status.outOfProcess) ?? null;
}

/** Where someone added with no status stands, as the server decides it: the first live stage's default in process. */
export function startingStatusKey(catalog: PipelineCatalog): string | null {
  for (const stage of liveStages(catalog)) {
    const status = defaultStatusOf(stage);
    if (status) return status.key;
  }
  return null;
}

/** Whoever holds this key is out of process — Inactive on the board; an unknown key reads as in process. */
export function isOutOfProcess(key: string, catalog?: PipelineCatalog | null): boolean {
  const known = (catalog && findStatus(catalog, key)?.status) ?? findStatus(LEGACY_CATALOG, key)?.status;
  return known?.outOfProcess ?? false;
}

function legacyStage(
  key: string,
  label: string,
  colour: string,
  waysOut: boolean,
  position: number,
  statuses: [string, string, string][],
): PipelineStage {
  return {
    id: key,
    key,
    label,
    colour,
    position,
    system: true,
    retired: false,
    statuses: statuses.map(([statusKey, statusLabel, statusColour], index) => ({
      outOfProcess: waysOut,
      id: statusKey,
      key: statusKey,
      label: statusLabel,
      colour: statusColour,
      position: index + 1,
      isDefault: index === 0 && !waysOut,
      system: true,
      retired: false,
    })),
  };
}
