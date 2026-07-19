import type { Competency, Criterion, Position } from "../api/types";

/** The live state of the lock gate — what the checklist renders and the Lock button switches on. */
export interface Readiness {
  technicalTotal: number;
  behaviouralTotal: number;
  hasRequired: boolean;
  ready: boolean;
}

export function readiness(input: {
  technical: Competency[];
  behavioural: Competency[];
  criteria: Criterion[];
}): Readiness {
  const technicalTotal = total(input.technical);
  const behaviouralTotal = total(input.behavioural);
  const hasRequired = input.criteria.some((c) => c.mode === "REQUIRED");
  return {
    technicalTotal,
    behaviouralTotal,
    hasRequired,
    ready: technicalTotal === 100 && behaviouralTotal === 100 && hasRequired,
  };
}

function total(rows: Competency[]): number {
  return rows.reduce((sum, row) => sum + row.weight, 0);
}

/**
 * How complete the brief is, as a whole percentage — the hero's completion chip. Counts the fields
 * a consultant would actually fill before calling the brief done; the lists count when non-empty.
 */
export function completion(position: Position): number {
  const filled = [
    position.narrative,
    position.internalContext,
    position.reportsTo,
    position.directReports,
    position.teamSize,
    position.location,
    position.employmentType,
    position.startTarget,
    position.salaryMin,
    position.salaryMax,
    position.noticeValue,
    position.bonusTargetPct,
    position.ltip,
  ].filter((value) => value !== null && value !== undefined && String(value).trim() !== "").length;

  const listScore =
    (position.benefits.length > 0 ? 1 : 0) +
    (position.criteria.length > 0 ? 1 : 0) +
    (position.technical.length > 0 ? 1 : 0) +
    (position.behavioural.length > 0 ? 1 : 0);

  return Math.round(((filled + listScore) / 17) * 100);
}
