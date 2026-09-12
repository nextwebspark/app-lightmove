import { describe, expect, it } from "vitest";
import type { OrgNode } from "../api/types";
import {
  appendDirectReport,
  applyReportsToTitle,
  branchHoldsMandateSeat,
  childrenOf,
  directReportsOf,
  labelOfNode,
  layoutChart,
  MAX_ORG_CHART_SEATS,
  managerOf,
  removeBranch,
  removeSeat,
} from "./orgChart";

const seat = (
  nodeId: string,
  parentNodeId: string | null,
  overrides: Partial<OrgNode> = {},
): OrgNode => ({
  nodeId,
  parentNodeId,
  title: null,
  name: null,
  mandateSeat: false,
  canvasX: null,
  canvasY: null,
  ...overrides,
});

/** manager → the role → two reports, one of which has a report of its own. */
const chart: OrgNode[] = [
  seat("manager", null, { title: "Group CEO", name: "Hassan Al Marri" }),
  seat("role", "manager", { mandateSeat: true }),
  seat("controller", "role", { title: "Financial Controller", name: "Layla Nasser" }),
  seat("treasurer", "role", { title: "Group Treasurer" }),
  seat("analyst", "controller", { title: "FP&A Analyst" }),
];

describe("reading the chart", () => {
  it("takes the manager from the mandate seat's parent", () => {
    expect(labelOfNode(managerOf(chart))).toBe("Hassan Al Marri");
  });

  it("takes the direct reports from the mandate seat's children, not the whole tree", () => {
    expect(directReportsOf(chart).map((node) => node.title)).toEqual([
      "Financial Controller",
      "Group Treasurer",
    ]);
  });

  it("has no manager when the role sits at the top", () => {
    const rootRole = [seat("role", null, { mandateSeat: true })];
    expect(managerOf(rootRole)).toBeNull();
    expect(labelOfNode(managerOf(rootRole))).toBeNull();
  });

  it("falls back to the seat when nobody has been named for it", () => {
    expect(labelOfNode(seat("x", null, { title: "Group Treasurer" }))).toBe("Group Treasurer");
    expect(labelOfNode(seat("x", null))).toBeNull();
  });
});

describe("removing a seat", () => {
  it("lifts the reports of the seat it removes onto the seat above", () => {
    const left = removeSeat(chart, "controller");
    expect(left.map((node) => node.nodeId)).not.toContain("controller");
    expect(left.find((node) => node.nodeId === "analyst")?.parentNodeId).toBe("role");
  });

  it("keeps the mandate seat when the manager above it goes, as a root of its own", () => {
    const left = removeSeat(chart, "manager");
    expect(left.map((node) => node.nodeId)).toContain("role");
    expect(left.find((node) => node.nodeId === "role")?.parentNodeId).toBeNull();
    expect(left).toHaveLength(chart.length - 1);
  });

  it("leaves a chart it cannot find the seat in exactly as it was", () => {
    expect(removeSeat(chart, "nobody")).toBe(chart);
  });

  it("reads whole branches too, which is how re-parenting refuses to make a loop", () => {
    const left = removeBranch(chart, "controller").map((node) => node.nodeId);
    expect(left).not.toContain("controller");
    expect(left).not.toContain("analyst");
    expect(left).toContain("treasurer");
    expect(branchHoldsMandateSeat(chart, "manager")).toBe(true);
    expect(branchHoldsMandateSeat(chart, "controller")).toBe(false);
  });
});

describe("layout", () => {
  it("puts each tier on its own row", () => {
    const placed = layoutChart(chart);
    const y = (id: string) => placed.get(id)?.y ?? -1;
    expect(y("manager")).toBeLessThan(y("role"));
    expect(y("role")).toBeLessThan(y("controller"));
    expect(y("controller")).toBeLessThan(y("analyst"));
    expect(y("controller")).toBe(y("treasurer"));
  });

  it("centres a parent over the children it spans", () => {
    const placed = layoutChart(chart);
    const role = placed.get("role")!;
    const controller = placed.get("controller")!;
    const treasurer = placed.get("treasurer")!;
    expect(role.x).toBeCloseTo((controller.x + treasurer.x) / 2);
  });

  it("still places a seat whose parent has gone missing, rather than dropping it", () => {
    const orphaned = [...chart, seat("stray", "deleted-node")];
    expect(layoutChart(orphaned).has("stray")).toBe(true);
  });

  it("does not loop on a chart that references itself", () => {
    const looped = [seat("a", "b"), seat("b", "a")];
    expect(layoutChart(looped).size).toBe(2);
  });
});

describe("childrenOf", () => {
  it("reads the roots as the children of nothing", () => {
    expect(childrenOf(chart, null).map((node) => node.nodeId)).toEqual(["manager"]);
  });
});

describe("applying a proposed reports-to title", () => {
  it("renames the existing manager rather than adding a second root", () => {
    const merged = applyReportsToTitle(chart, "Group Chief Executive Officer");
    expect(merged).toHaveLength(chart.length);
    expect(merged.find((node) => node.nodeId === "manager")?.title).toBe(
      "Group Chief Executive Officer",
    );
    // The manager's name — a person, not this proposal's business — is left exactly as it was.
    expect(merged.find((node) => node.nodeId === "manager")?.name).toBe("Hassan Al Marri");
    expect(merged.filter((node) => node.parentNodeId === null)).toHaveLength(1);
  });

  it("mints a manager and re-parents the mandate seat under it when the chart has none", () => {
    const rootRole = [seat("role", null, { mandateSeat: true })];
    const merged = applyReportsToTitle(rootRole, "Board of Directors");
    expect(merged).toHaveLength(2);
    const mintedManager = managerOf(merged);
    expect(mintedManager?.title).toBe("Board of Directors");
    expect(merged.find((node) => node.nodeId === "role")?.parentNodeId).toBe(mintedManager?.nodeId);
    // The mandate seat keeps its own id and never gains a title of its own.
    expect(merged.find((node) => node.nodeId === "role")?.title).toBeNull();
  });

  it("preserves canvas positions and the mandate seat untouched by the merge", () => {
    const placed = [
      seat("manager", null, { title: "Group CEO", canvasX: 10, canvasY: 20 }),
      seat("role", "manager", { mandateSeat: true, canvasX: 300, canvasY: 20 }),
    ];
    const merged = applyReportsToTitle(placed, "Group Chief Executive Officer");
    expect(merged.find((node) => node.nodeId === "manager")).toMatchObject({ canvasX: 10, canvasY: 20 });
    expect(merged.find((node) => node.nodeId === "role")).toMatchObject({
      mandateSeat: true,
      canvasX: 300,
      canvasY: 20,
    });
  });

  it("does not mint a manager past the 60-seat ceiling", () => {
    const rootRole = [seat("role", null, { mandateSeat: true })];
    const full = [
      ...rootRole,
      ...Array.from({ length: MAX_ORG_CHART_SEATS - 1 }, (_, i) => seat(`extra-${i}`, "role")),
    ];
    expect(applyReportsToTitle(full, "Board of Directors")).toBe(full);
  });
});

describe("appending a proposed direct report", () => {
  it("appends a new child under the mandate seat, leaving other seats untouched", () => {
    const merged = appendDirectReport(chart, "Head of Investor Relations");
    expect(merged).toHaveLength(chart.length + 1);
    const added = merged.find((node) => !chart.some((existing) => existing.nodeId === node.nodeId));
    expect(added?.title).toBe("Head of Investor Relations");
    expect(added?.parentNodeId).toBe("role");
    expect(added?.name).toBeNull();
    expect(added?.mandateSeat).toBe(false);
    expect(directReportsOf(merged).map((node) => node.title)).toContain(
      "Head of Investor Relations",
    );
  });

  it("does not append past the 60-seat ceiling", () => {
    const rootRole = [seat("role", null, { mandateSeat: true })];
    const full = [
      ...rootRole,
      ...Array.from({ length: MAX_ORG_CHART_SEATS - 1 }, (_, i) => seat(`extra-${i}`, "role")),
    ];
    expect(appendDirectReport(full, "One too many")).toBe(full);
  });
});
