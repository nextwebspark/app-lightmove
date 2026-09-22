import type { MandateProgress, Project } from "../features/projects/api/types";

/**
 * A mandate as the API returns one, for the screens that render a list of them. Shared so a field
 * added to the contract is filled in one place rather than six.
 */
export function sampleProject(overrides: Partial<Project> = {}): Project {
  return {
    id: "p1",
    clientId: "c1",
    clientName: "Meridian Energy",
    clientLogoUrl: null,
    positionTitle: "CFO",
    stage: "BRIEF",
    projectType: "MAPPING",
    health: "OK",
    startDate: "2026-07-01",
    mappingTargetDate: null,
    shortlistTargetDate: null,
    targetDate: null,
    progress: sampleProgress(),
    team: [],
    representatives: [],
    companies: 0,
    candidates: 0,
    createdAt: "2026-07-01T00:00:00Z",
    ...overrides,
  };
}

export function sampleProgress(overrides: Partial<MandateProgress> = {}): MandateProgress {
  return {
    activePhase: "MAP",
    mappingComplete: false,
    mapPercent: 0,
    engagePercent: 0,
    universeCompanies: 0,
    companiesResearched: 0,
    candidatesMapped: 0,
    candidatesEngaged: 0,
    qualifiedMatches: 0,
    mappingVelocityPerWeek: 0,
    governingMilestone: null,
    daysRemaining: null,
    ...overrides,
  };
}
