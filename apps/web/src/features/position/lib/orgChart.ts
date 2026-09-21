import type { OrgNode } from "../api/types";

/**
 * Reading and rearranging the org chart.
 *
 * The chart is a tree of seats with exactly one flagged as the mandate's own. Everything the rest of
 * the screen used to hold as its own field is a question about that seat: its parent is the manager,
 * its children are the direct reports. Deriving them rather than storing them is what stops the
 * summary rail and the canvas disagreeing about the same role.
 */

/** Node box size and spacing — shared by the layout and the canvas so they cannot drift apart. */
export const NODE_WIDTH = 210;
export const NODE_HEIGHT = 62;
const COLUMN_GAP = 26;
const ROW_GAP = 74;

/** `PutReportingStructureRequest.orgChart`'s own ceiling — the canvas must never grow the chart past it. */
export const MAX_ORG_CHART_SEATS = 60;

export function mandateSeatOf(chart: OrgNode[]): OrgNode | null {
  return chart.find((node) => node.mandateSeat) ?? null;
}

export function managerOf(chart: OrgNode[]): OrgNode | null {
  const seat = mandateSeatOf(chart);
  if (!seat?.parentNodeId) return null;
  return chart.find((node) => node.nodeId === seat.parentNodeId) ?? null;
}

export function childrenOf(chart: OrgNode[], parentNodeId: string | null): OrgNode[] {
  return chart.filter((node) => node.parentNodeId === parentNodeId);
}

export function directReportsOf(chart: OrgNode[]): OrgNode[] {
  const seat = mandateSeatOf(chart);
  return seat ? childrenOf(chart, seat.nodeId) : [];
}

/** How a seat reads in a summary: the person if known, else the seat, else nothing. */
export function labelOfNode(node: OrgNode | null): string | null {
  if (!node) return null;
  return node.name?.trim() || node.title?.trim() || null;
}

/**
 * Removes one seat and re-attaches whatever reported to it to the seat above.
 *
 * Deleting a box must not delete work nobody asked to lose: a manager typed in by mistake sits above
 * the whole chart, and taking its branch with it would take the mandate seat and every report anybody
 * had drawn. Splicing it out instead leaves every other seat where it was, one tier up. A seat with no
 * parent leaves its children as roots, which the layout and the server both allow.
 */
export function removeSeat(chart: OrgNode[], nodeId: string): OrgNode[] {
  const removed = chart.find((node) => node.nodeId === nodeId);
  if (!removed) return chart;
  return chart
    .filter((node) => node.nodeId !== nodeId)
    .map((node) =>
      node.parentNodeId === nodeId ? { ...node, parentNodeId: removed.parentNodeId } : node,
    );
}

/** Every seat under `nodeId`, and the seat itself, dropped — the reading behind branchHoldsMandateSeat. */
export function removeBranch(chart: OrgNode[], nodeId: string): OrgNode[] {
  const doomed = new Set<string>([nodeId]);
  let grew = true;
  while (grew) {
    grew = false;
    for (const node of chart) {
      if (node.parentNodeId && doomed.has(node.parentNodeId) && !doomed.has(node.nodeId)) {
        doomed.add(node.nodeId);
        grew = true;
      }
    }
  }
  return chart.filter((node) => !doomed.has(node.nodeId));
}

/** True when the branch under `nodeId` contains the mandate seat — which must never be removed. */
export function branchHoldsMandateSeat(chart: OrgNode[], nodeId: string): boolean {
  const seat = mandateSeatOf(chart);
  if (!seat) return false;
  return !removeBranch(chart, nodeId).some((node) => node.nodeId === seat.nodeId);
}

/**
 * Where each box sits when nobody has dragged it: a tidy tree, roots across the top, each parent
 * centred over its children. A node that *has* been dragged keeps its own coordinates, so arranging
 * one corner of a chart never rearranges the rest.
 */
export function layoutChart(chart: OrgNode[]): Map<string, { x: number; y: number }> {
  const placed = new Map<string, { x: number; y: number }>();
  const byDepth = depthsOf(chart);
  let cursor = 0;

  /** Post-order: children are placed first, then the parent is centred over the span they occupy. */
  const place = (node: OrgNode): { left: number; right: number } => {
    const children = childrenOf(chart, node.nodeId);
    const depth = byDepth.get(node.nodeId) ?? 0;
    const y = depth * (NODE_HEIGHT + ROW_GAP);

    if (children.length === 0) {
      const x = cursor;
      cursor += NODE_WIDTH + COLUMN_GAP;
      placed.set(node.nodeId, { x, y });
      return { left: x, right: x };
    }

    const spans = children.map(place);
    const left = spans[0].left;
    const right = spans[spans.length - 1].right;
    placed.set(node.nodeId, { x: (left + right) / 2, y });
    return { left, right };
  };

  for (const root of childrenOf(chart, null)) place(root);
  // A node whose parent is missing would otherwise never be placed and would vanish from the canvas.
  for (const node of chart) {
    if (!placed.has(node.nodeId)) {
      placed.set(node.nodeId, { x: cursor, y: 0 });
      cursor += NODE_WIDTH + COLUMN_GAP;
    }
  }
  return placed;
}

/**
 * Why a merge helper below declined to change the chart — `null` means it applied cleanly. The caller
 * reads this instead of comparing the returned chart by reference, since more than one reason can
 * produce the same "chart came back unchanged" outcome.
 */
export type ChartMergeBlock = "noMandateSeat" | "full" | "duplicate";

export interface ChartMergeResult {
  chart: OrgNode[];
  blocked: ChartMergeBlock | null;
}

/**
 * Folds a whole reporting-section reading into the chart in one pass — a reports-to title and every
 * proposed direct report together, never a chart of its own. Source-aware throughout, so a fill can be
 * run again after somebody has started editing without clobbering what they typed.
 *
 * **Manager.** No manager yet → mint `{ title, source: "DOCUMENT" }` and re-parent the mandate seat
 * under it (refused past the seat ceiling). A `MANUAL` manager — a person's own — is left exactly as
 * typed. Anything else (`TEMPLATE`, a previous reading's `DOCUMENT`, or unmarked legacy data) is
 * renamed and stamped `DOCUMENT`. The rename never touches `name`: the proposal is title-only (see
 * `PositionReportingProposer`'s class doc), and a manager somebody had already named keeps that name
 * paired with the new title. Canvas coordinates are never touched either.
 *
 * **Direct reports**, only attempted when at least one is proposed: the mandate seat's non-`MANUAL`
 * children with no reports of their own are dropped first — a seat somebody built under stays, since
 * dropping it would orphan its own children and 400 on `requireParentsResolve` — then every proposed
 * title is de-duplicated case-insensitively against what is kept and appended as `DOCUMENT`, up to the
 * seat ceiling.
 */
export function mergeReportingProposals(
  chart: OrgNode[],
  reportsTo: string | null | undefined,
  directReports: readonly string[],
): ChartMergeResult {
  const seat = mandateSeatOf(chart);
  if (!seat) return { chart, blocked: "noMandateSeat" };

  let next = chart;
  let blocked: ChartMergeBlock | null = null;
  const title = reportsTo?.trim();

  if (title) {
    const manager = managerOf(next);
    if (!manager) {
      if (next.length >= MAX_ORG_CHART_SEATS) {
        blocked = "full";
      } else {
        const nodeId = crypto.randomUUID();
        next = [
          ...next.map((node) => (node.nodeId === seat.nodeId ? { ...node, parentNodeId: nodeId } : node)),
          {
            nodeId,
            parentNodeId: null,
            title,
            name: null,
            mandateSeat: false,
            canvasX: null,
            canvasY: null,
            source: "DOCUMENT",
          },
        ];
      }
    } else if (manager.source !== "MANUAL") {
      next = next.map((node) =>
        node.nodeId === manager.nodeId ? { ...node, title, source: "DOCUMENT" } : node,
      );
    }
  }

  if (directReports.length > 0) {
    const seatNow = mandateSeatOf(next)!;
    const currentChildren = childrenOf(next, seatNow.nodeId);
    const keptChildren = currentChildren.filter(
      // Absent provenance is treated as protected here, unlike the manager rename above: dropping a
      // seat is destructive and unrecoverable, so a seat of unknown origin is kept rather than guessed
      // at — only an explicit DOCUMENT or TEMPLATE stamp makes one eligible to be replaced.
      (child) => (child.source ?? "MANUAL") === "MANUAL" || childrenOf(next, child.nodeId).length > 0,
    );
    const droppedIds = new Set(
      currentChildren.filter((child) => !keptChildren.includes(child)).map((child) => child.nodeId),
    );
    const base = droppedIds.size > 0 ? next.filter((node) => !droppedIds.has(node.nodeId)) : next;
    const keptTitles = new Set(
      keptChildren.map((child) => (child.title ?? "").trim().toLowerCase()).filter(Boolean),
    );

    const toAppend: OrgNode[] = [];
    let total = base.length;
    for (const reportTitle of directReports) {
      const normalised = reportTitle.trim().toLowerCase();
      if (!normalised || keptTitles.has(normalised)) continue;
      if (total >= MAX_ORG_CHART_SEATS) {
        blocked = blocked ?? "full";
        break;
      }
      keptTitles.add(normalised);
      toAppend.push({
        nodeId: crypto.randomUUID(),
        parentNodeId: seatNow.nodeId,
        title: reportTitle.trim(),
        name: null,
        mandateSeat: false,
        canvasX: null,
        canvasY: null,
        source: "DOCUMENT",
      });
      total++;
    }
    next = toAppend.length > 0 ? [...base, ...toAppend] : base;
  }

  return { chart: next, blocked };
}

/**
 * The matched template's own usual direct reports the chart does not already carry — the row #398
 * draws under "Suggested seats". Case-insensitive against every current child, not just the kept ones
 * a merge would keep: an offer somebody can add by hand should not repeat a report already on screen
 * for any reason.
 */
export function suggestedSeats(chart: OrgNode[], usualDirectReports: readonly string[]): string[] {
  const existing = new Set(
    directReportsOf(chart).map((node) => (node.title ?? "").trim().toLowerCase()),
  );
  return usualDirectReports.filter((title) => !existing.has(title.trim().toLowerCase()));
}

/**
 * Adds one suggested seat as a person's own choice — `source: "MANUAL"`, never `"DOCUMENT"`. A later
 * fill drops every non-`MANUAL` childless direct report before re-proposing; stamping this `DOCUMENT`
 * would make a seat somebody just clicked to add vanish the next time the document is read again.
 */
export function addSuggestedSeat(chart: OrgNode[], title: string): ChartMergeResult {
  const seat = mandateSeatOf(chart);
  if (!seat) return { chart, blocked: "noMandateSeat" };
  const duplicate = childrenOf(chart, seat.nodeId).some(
    (node) => node.title?.trim().toLowerCase() === title.trim().toLowerCase(),
  );
  if (duplicate) return { chart, blocked: "duplicate" };
  if (chart.length >= MAX_ORG_CHART_SEATS) return { chart, blocked: "full" };
  return {
    chart: [
      ...chart,
      {
        nodeId: crypto.randomUUID(),
        parentNodeId: seat.nodeId,
        title,
        name: null,
        mandateSeat: false,
        canvasX: null,
        canvasY: null,
        source: "MANUAL",
      },
    ],
    blocked: null,
  };
}

/** Depth from the chart's root, so every tier lines up even across separate branches. */
function depthsOf(chart: OrgNode[]): Map<string, number> {
  const byId = new Map(chart.map((node) => [node.nodeId, node]));
  const depths = new Map<string, number>();

  const depthOf = (node: OrgNode, seen: Set<string>): number => {
    const known = depths.get(node.nodeId);
    if (known !== undefined) return known;
    // Guarded even though the server refuses a cycle: a client-side edit is checked here first.
    if (!node.parentNodeId || seen.has(node.nodeId)) return 0;
    const parent = byId.get(node.parentNodeId);
    if (!parent) return 0;
    seen.add(node.nodeId);
    const depth = depthOf(parent, seen) + 1;
    depths.set(node.nodeId, depth);
    return depth;
  };

  for (const node of chart) depths.set(node.nodeId, depthOf(node, new Set()));
  return depths;
}
