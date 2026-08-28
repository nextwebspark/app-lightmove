import { describe, expect, it } from "vitest";
import type { OrgNode } from "../api/types";
import {
  branchHoldsMandateSeat,
  childrenOf,
  directReportsOf,
  labelOfNode,
  layoutChart,
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
