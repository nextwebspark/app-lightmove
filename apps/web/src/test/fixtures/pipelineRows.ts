import { findStatus } from "../../features/candidates/lib/pipelineStatus";
import type { CandidateNextAction, PipelinePage, PipelineRow } from "../../features/pipeline/api/types";
import { aSeededCatalog } from "./pipelineCatalog";

/** A pipeline row at a seeded status, its label and stage read off the seeded catalog. */
export function aPipelineRow(overrides: Partial<PipelineRow> & { candidateId: string; status: string }): PipelineRow {
  const found = findStatus(aSeededCatalog(), overrides.status)!;
  return {
    personId: `person-${overrides.candidateId}`,
    fullName: `Executive ${overrides.candidateId.toUpperCase()}`,
    title: "Chief Financial Officer",
    companyName: "Emirates NBD",
    triageCompanyId: null,
    statusLabel: found.status.label,
    statusColour: found.status.colour,
    pipelineStage: {
      key: found.stage.key,
      label: found.stage.label,
      colour: found.stage.colour,
      kind: found.stage.kind,
      position: found.stage.position,
    },
    addedAt: "2026-09-01T09:00:00Z",
    statusChangedAt: "2026-09-20T09:00:00Z",
    nextAction: null,
    clientVisible: true,
    enrichedAt: null,
    ...overrides,
  };
}

/** An executive's earliest open task, as the pipeline row carries it: one of one, Yara's, due tomorrow. */
export function aNextAction(overrides: Partial<CandidateNextAction> = {}): CandidateNextAction {
  const tomorrow = new Date();
  tomorrow.setDate(tomorrow.getDate() + 1);
  tomorrow.setHours(9, 0, 0, 0);
  return {
    taskId: "task-1",
    text: "Call to qualify interest",
    kind: "call",
    dueAt: tomorrow.toISOString(),
    assignee: { userId: "u1", fullName: "Yara Haddad", avatarUrl: null },
    openCount: 1,
    setByName: "Yara Haddad",
    ...overrides,
  };
}

/** A page of the given rows, with every seeded stage counted. */
export function aPipelinePage(rows: PipelineRow[]): PipelinePage {
  return {
    rows,
    total: rows.length,
    stageCounts: aSeededCatalog().stages.map((stage) => ({
      key: stage.key,
      label: stage.label,
      colour: stage.colour,
      kind: stage.kind,
      position: stage.position,
      count: rows.filter((row) => row.pipelineStage.key === stage.key).length,
    })),
  };
}
