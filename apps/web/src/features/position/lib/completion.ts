import type { Position } from "../api/types";

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
