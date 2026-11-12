import { findStatus } from "../../features/candidates/lib/pipelineStatus";
import type { CandidateNextAction, PipelinePage, PipelineRow } from "../../features/pipeline/api/types";
import type { PipelineCatalog } from "../../features/workspace/api/types";
import { aSeededCatalog } from "./pipelineCatalog";

/** A pipeline row at a seeded status, its label, stage and flag read off the catalog (the legacy seed by default). */
export function aPipelineRow(
  overrides: Partial<PipelineRow> & { candidateId: string; status: string },
  catalog: PipelineCatalog = aSeededCatalog(),
): PipelineRow {
  const found = findStatus(catalog, overrides.status)!;
  return {
    personId: `person-${overrides.candidateId}`,
    fullName: `Executive ${overrides.candidateId.toUpperCase()}`,
    title: "Chief Financial Officer",
    companyName: "Emirates NBD",
    triageCompanyId: null,
    statusLabel: found.status.label,
    statusColour: found.status.colour,
    outOfProcess: found.status.outOfProcess,
    pipelineStage: {
      key: found.stage.key,
      label: found.stage.label,
      colour: found.stage.colour,
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

/**
 * A page of the given rows as the server answers it: a column for every stage with a status in process, counting
 * those in process, and the rest counted inactive. A stage of ways out alone, the old Declined, is no column.
 */
export function aPipelinePage(rows: PipelineRow[], catalog: PipelineCatalog = aSeededCatalog()): PipelinePage {
  return {
    rows,
    total: rows.length,
    inactiveCount: rows.filter((row) => row.outOfProcess).length,
    stageCounts: catalog.stages
      .filter((stage) => stage.statuses.some((status) => !status.outOfProcess))
      .map((stage) => ({
        key: stage.key,
        label: stage.label,
        colour: stage.colour,
        position: stage.position,
        count: rows.filter((row) => row.pipelineStage.key === stage.key && !row.outOfProcess).length,
      })),
  };
}
