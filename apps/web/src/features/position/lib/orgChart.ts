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

/** Removing a seat takes the branch under it — the alternative is orphans the chart cannot draw. */
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
