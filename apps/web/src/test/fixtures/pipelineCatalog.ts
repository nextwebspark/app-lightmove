import type { PipelineCatalog, PipelineStage, PipelineStatus } from "../../features/workspace/api/types";

type StatusSeed = [key: string, label: string, colour: string, outOfProcess?: boolean];

const SYSTEM = new Set(["identified", "contacted", "engaged", "interested", "notInterested", "offLimits", "outOfScope"]);

function stage(key: string, label: string, colour: string, position: number, statuses: StatusSeed[]): PipelineStage {
  const firstInProcess = statuses.findIndex(([, , , outOfProcess]) => !outOfProcess);
  return {
    id: `stage-${key}`,
    key,
    label,
    colour,
    position,
    system: true,
    retired: false,
    statuses: statuses.map(([statusKey, statusLabel, statusColour, outOfProcess], index): PipelineStatus => ({
      id: `status-${statusKey}`,
      key: statusKey,
      label: statusLabel,
      colour: statusColour,
      position: index + 1,
      isDefault: index === firstInProcess,
      system: SYSTEM.has(statusKey),
      outOfProcess: outOfProcess === true,
      retired: false,
    })),
  };
}

/** The catalog a workspace seeded before V163 holds — V131's seed, its Declined stage's statuses out of process. */
export function aSeededCatalog(): PipelineCatalog {
  return {
    stages: [
      stage("identified", "Identified", "neutral", 1, [
        ["identified", "Identified", "neutral"],
        ["researching", "Researching", "neutral"],
        ["readyToApproach", "Ready to approach", "neutral"],
      ]),
      stage("contacted", "Contacted", "accent", 2, [
        ["contacted", "Contacted", "accent"],
        ["followUpSent", "Follow-up sent", "accent"],
        ["engaged", "Engaged", "accent"],
        ["callBooked", "Call booked", "accent"],
      ]),
      stage("interested", "Interested", "green", 3, [
        ["interested", "Interested", "green"],
        ["interestedPackageGap", "Interested – package gap", "green"],
        ["interestedRelocation", "Interested – relocation concern", "green"],
        ["considering", "Considering", "green"],
      ]),
      stage("qualified", "Qualified", "violet", 4, [
        ["screened", "Screened", "violet"],
        ["qualified", "Qualified", "violet"],
        ["referencesInProgress", "References in progress", "violet"],
      ]),
      stage("shortlisted", "Shortlisted", "green", 5, [
        ["shortlisted", "Shortlisted", "green"],
        ["presentedToClient", "Presented to client", "green"],
        ["clientInterview", "Client interview", "green"],
        ["offer", "Offer", "green"],
      ]),
      stage("declined", "Declined", "red", 6, [
        ["notInterested", "Not interested", "neutral", true],
        ["withdrew", "Withdrew", "neutral", true],
        ["notAFit", "Not a fit", "neutral", true],
        ["rejectedByClient", "Rejected by client", "neutral", true],
        ["noResponse", "No response", "neutral", true],
        ["offLimits", "Off-limits", "red", true],
        ["outOfScope", "Out of scope", "signal", true],
      ]),
    ],
  };
}

/** The catalog a workspace is seeded with since V163: five stages, each with its own ways out. */
export function aCurrentCatalog(): PipelineCatalog {
  return {
    stages: [
      stage("identified", "Research", "neutral", 1, [
        ["identified", "Identified", "neutral"],
        ["outOfScope", "Out of scope", "neutral", true],
        ["offLimits", "Off-limits", "neutral", true],
        ["source", "Source", "neutral"],
      ]),
      stage("contacted", "Approach", "violet", 2, [
        ["contacted", "Awaiting reply", "accent"],
        ["engaged", "Confirming interest", "accent"],
        ["callBeingArranged", "Call being arranged", "violet"],
        ["callArranged", "Call arranged", "violet"],
        ["notInterested", "Not interested", "violet", true],
        ["unresponsive", "Unresponsive", "violet", true],
      ]),
      stage("interested", "Assessment", "signal", 3, [
        ["interested", "Screening arranged", "signal"],
        ["screenedOut", "Screened-out", "signal", true],
        ["interviewArranged", "Interview arranged", "signal"],
        ["withdrew", "Withdrew", "signal", true],
      ]),
      stage("qualified", "Shortlist", "adjacent", 4, [
        ["shortlisted", "Shortlisted", "adjacent"],
        ["clientRejected", "Client rejected", "adjacent", true],
      ]),
      stage("shortlisted", "Offer", "violet", 5, [
        ["references", "References", "violet"],
        ["declinedOffer", "Declined offer", "violet", true],
        ["placed", "Placed", "violet"],
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
