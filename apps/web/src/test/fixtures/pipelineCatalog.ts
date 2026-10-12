import type { PipelineCatalog, PipelineStage, PipelineStatus } from "../../features/workspace/api/types";

type StatusSeed = [key: string, label: string, colour: string, system?: boolean];

const SYSTEM = new Set(["identified", "contacted", "engaged", "interested", "notInterested", "offLimits", "outOfScope"]);

function stage(
  key: string,
  label: string,
  colour: string,
  kind: "active" | "closed",
  position: number,
  statuses: StatusSeed[],
): PipelineStage {
  return {
    id: `stage-${key}`,
    key,
    label,
    colour,
    kind,
    position,
    system: true,
    retired: false,
    statuses: statuses.map(([statusKey, statusLabel, statusColour], index): PipelineStatus => ({
      id: `status-${statusKey}`,
      key: statusKey,
      label: statusLabel,
      colour: statusColour,
      position: index + 1,
      isDefault: index === 0,
      system: SYSTEM.has(statusKey),
      retired: false,
    })),
  };
}

/** The catalog every workspace is seeded with — the server's V130 seed, mirrored for tests. */
export function aSeededCatalog(): PipelineCatalog {
  return {
    stages: [
      stage("identified", "Identified", "neutral", "active", 1, [
        ["identified", "Identified", "neutral"],
        ["researching", "Researching", "neutral"],
        ["readyToApproach", "Ready to approach", "neutral"],
      ]),
      stage("contacted", "Contacted", "accent", "active", 2, [
        ["contacted", "Contacted", "accent"],
        ["followUpSent", "Follow-up sent", "accent"],
        ["engaged", "Engaged", "accent"],
        ["callBooked", "Call booked", "accent"],
      ]),
      stage("interested", "Interested", "green", "active", 3, [
        ["interested", "Interested", "green"],
        ["interestedPackageGap", "Interested – package gap", "green"],
        ["interestedRelocation", "Interested – relocation concern", "green"],
        ["considering", "Considering", "green"],
      ]),
      stage("qualified", "Qualified", "violet", "active", 4, [
        ["screened", "Screened", "violet"],
        ["qualified", "Qualified", "violet"],
        ["referencesInProgress", "References in progress", "violet"],
      ]),
      stage("shortlisted", "Shortlisted", "green", "active", 5, [
        ["shortlisted", "Shortlisted", "green"],
        ["presentedToClient", "Presented to client", "green"],
        ["clientInterview", "Client interview", "green"],
        ["offer", "Offer", "green"],
      ]),
      stage("declined", "Declined", "red", "closed", 6, [
        ["notInterested", "Not interested", "neutral"],
        ["withdrew", "Withdrew", "neutral"],
        ["notAFit", "Not a fit", "neutral"],
        ["rejectedByClient", "Rejected by client", "neutral"],
        ["noResponse", "No response", "neutral"],
        ["offLimits", "Off-limits", "red"],
        ["outOfScope", "Out of scope", "signal"],
      ]),
    ],
  };
}

/** The seeded catalog with one status retired. */
export function withRetiredStatus(catalog: PipelineCatalog, statusKey: string): PipelineCatalog {
  return {
    stages: catalog.stages.map((candidate) => ({
      ...candidate,
      statuses: candidate.statuses.map((status) =>
        status.key === statusKey ? { ...status, retired: true, isDefault: false } : status,
      ),
    })),
  };
}
