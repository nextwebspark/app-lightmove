import type { Report } from "../api/types";

export interface NextAction {
  title: string;
  body: string;
  /** Where the reader goes to act on it, when there is somewhere to go. */
  to?: string;
}

/**
 * What the report can honestly recommend. Every rule reads a gap the report itself just measured, so
 * an action only appears when the thing it asks for is genuinely missing — the mockup's narrative
 * advice ("prioritise direct-relevance targets in food service") reads well and would be invented,
 * since nothing in the data supports it.
 */
export function nextActionsFor(report: Report, projectId: string): NextAction[] {
  const strategy = `/projects/${projectId}/strategy`;
  const actions: NextAction[] = [];

  if (report.sectorsInScope === 0) {
    actions.push({
      title: "Set the search scope",
      body: "This mandate has no sectors selected, so no company is in its universe yet.",
      to: strategy,
    });
  } else if (report.universeCount === 0) {
    actions.push({
      title: "Widen the scope",
      body: "The saved scope matches no company. Loosen a size band, or add an adjacent sector.",
      to: strategy,
    });
  }

  if (report.mandateBand === null) {
    actions.push({
      title: "State the compensation band",
      body: "The brief carries no salary range, so the report cannot say what this mandate can win.",
      to: `/projects/${projectId}`,
    });
  }

  actions.push({
    title: "Map executives against the universe",
    body:
      report.universeCount > 0
        ? `${report.universeCount} companies are in scope and none has a mapped executive yet.`
        : "Candidate mapping is the half of this report that is still to be built.",
  });

  return actions;
}
