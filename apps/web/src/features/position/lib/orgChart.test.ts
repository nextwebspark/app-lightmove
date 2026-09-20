import { describe, expect, it } from "vitest";
import type { OrgNode } from "../api/types";
import {
  addSuggestedSeat,
  branchHoldsMandateSeat,
  childrenOf,
  directReportsOf,
  labelOfNode,
  layoutChart,
  MAX_ORG_CHART_SEATS,
  managerOf,
  mergeReportingProposals,
  removeBranch,
  removeSeat,
  suggestedSeats,
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

describe("merging a proposed reports-to title", () => {
  it("renames the existing manager, keeping their typed name", () => {
    const { chart: merged, blocked } = mergeReportingProposals(
      chart,
      "Group Chief Executive Officer",
      [],
    );
    expect(blocked).toBeNull();
    expect(merged).toHaveLength(chart.length);
    const manager = merged.find((node) => node.nodeId === "manager");
    expect(manager?.title).toBe("Group Chief Executive Officer");
    expect(manager?.source).toBe("DOCUMENT");
    // The old helper cleared a typed name on every rename — the bug this merge fixes: the proposal
    // is title-only, but a name somebody already typed is not the document's to erase.
    expect(manager?.name).toBe("Hassan Al Marri");
    expect(merged.filter((node) => node.parentNodeId === null)).toHaveLength(1);
  });

  it("leaves a MANUAL manager untouched", () => {
    const withManualManager = chart.map((node) =>
      node.nodeId === "manager" ? { ...node, source: "MANUAL" as const } : node,
    );
    const { chart: merged, blocked } = mergeReportingProposals(
      withManualManager,
      "Someone Else Entirely",
      [],
    );
    expect(blocked).toBeNull();
    expect(merged.find((node) => node.nodeId === "manager")).toMatchObject({
      title: "Group CEO",
      name: "Hassan Al Marri",
      source: "MANUAL",
    });
  });

  it("mints a manager and re-parents the mandate seat under it when the chart has none", () => {
    const rootRole = [seat("role", null, { mandateSeat: true })];
    const { chart: merged, blocked } = mergeReportingProposals(rootRole, "Board of Directors", []);
    expect(blocked).toBeNull();
    expect(merged).toHaveLength(2);
    const mintedManager = managerOf(merged);
    expect(mintedManager?.title).toBe("Board of Directors");
    expect(mintedManager?.source).toBe("DOCUMENT");
    expect(merged.find((node) => node.nodeId === "role")?.parentNodeId).toBe(mintedManager?.nodeId);
    // The mandate seat keeps its own id and never gains a title of its own.
    expect(merged.find((node) => node.nodeId === "role")?.title).toBeNull();
  });

  it("preserves canvas positions and the mandate seat untouched by the merge", () => {
    const placed = [
      seat("manager", null, { title: "Group CEO", canvasX: 10, canvasY: 20, source: "DOCUMENT" }),
      seat("role", "manager", { mandateSeat: true, canvasX: 300, canvasY: 20 }),
    ];
    const { chart: merged } = mergeReportingProposals(placed, "Group Chief Executive Officer", []);
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
    const result = mergeReportingProposals(full, "Board of Directors", []);
    expect(result.chart).toBe(full);
    expect(result.blocked).toBe("full");
  });

  it("reports no mandate seat rather than silently doing nothing", () => {
    const result = mergeReportingProposals([], "Board of Directors", []);
    expect(result.chart).toEqual([]);
    expect(result.blocked).toBe("noMandateSeat");
  });

  it("does nothing when neither a manager nor any direct reports are proposed", () => {
    const result = mergeReportingProposals(chart, null, []);
    expect(result.chart).toBe(chart);
    expect(result.blocked).toBeNull();
  });
});

describe("merging proposed direct reports", () => {
  it("appends new children under the mandate seat, leaving other seats untouched", () => {
    const { chart: merged, blocked } = mergeReportingProposals(chart, null, [
      "Head of Investor Relations",
    ]);
    expect(blocked).toBeNull();
    expect(merged).toHaveLength(chart.length + 1);
    const added = merged.find((node) => !chart.some((existing) => existing.nodeId === node.nodeId));
    expect(added?.title).toBe("Head of Investor Relations");
    expect(added?.parentNodeId).toBe("role");
    expect(added?.name).toBeNull();
    expect(added?.mandateSeat).toBe(false);
    expect(added?.source).toBe("DOCUMENT");
    // Untouched pre-existing children are neither dropped nor duplicated.
    expect(directReportsOf(merged).map((node) => node.title)).toEqual([
      "Financial Controller",
      "Group Treasurer",
      "Head of Investor Relations",
    ]);
  });

  it("drops a non-MANUAL childless direct report before appending, but never a MANUAL one", () => {
    // Excludes "analyst": this "controller" must be childless for the test to mean anything — a
    // controller with reports of its own is the next test's case, and would survive for that reason
    // instead of the one this test is checking.
    const withSources = chart
      .filter((node) => node.nodeId !== "analyst")
      .map((node) => {
        if (node.nodeId === "controller") return { ...node, source: "DOCUMENT" as const };
        if (node.nodeId === "treasurer") return { ...node, source: "MANUAL" as const };
        return node;
      });
    const { chart: merged } = mergeReportingProposals(withSources, null, ["Chief of Staff"]);
    const titles = directReportsOf(merged).map((node) => node.title);
    expect(titles).not.toContain("Financial Controller");
    expect(titles).toContain("Group Treasurer");
    expect(titles).toContain("Chief of Staff");
  });

  it("never drops a non-MANUAL child that has its own reports", () => {
    // "controller" (DOCUMENT) has "analyst" beneath it — dropping it would orphan analyst and 400 on
    // requireParentsResolve, so it survives even though it is not MANUAL and nothing proposed it again.
    const withSource = chart.map((node) =>
      node.nodeId === "controller" ? { ...node, source: "DOCUMENT" as const } : node,
    );
    const { chart: merged } = mergeReportingProposals(withSource, null, ["Chief of Staff"]);
    expect(directReportsOf(merged).map((node) => node.title)).toContain("Financial Controller");
    expect(merged.some((node) => node.nodeId === "analyst")).toBe(true);
  });

  it("de-duplicates a proposed title against what is kept, case-insensitively", () => {
    const { chart: merged, blocked } = mergeReportingProposals(chart, null, [
      "financial controller",
      "Group Treasurer",
    ]);
    expect(blocked).toBeNull();
    expect(merged).toHaveLength(chart.length);
  });

  it("leaves the chart untouched when nothing is proposed for it", () => {
    const result = mergeReportingProposals(chart, null, []);
    expect(result.chart).toBe(chart);
  });

  it("stops appending at the 60-seat ceiling, counting kept seats first", () => {
    const rootRole = [seat("role", null, { mandateSeat: true })];
    const full = [
      ...rootRole,
      ...Array.from({ length: MAX_ORG_CHART_SEATS - 1 }, (_, i) =>
        seat(`extra-${i}`, "role", { source: "MANUAL" }),
      ),
    ];
    const result = mergeReportingProposals(full, null, ["One too many"]);
    expect(result.chart).toBe(full);
    expect(result.blocked).toBe("full");
  });
});

describe("suggestedSeats", () => {
  it("offers the template's usual reports the chart does not already carry", () => {
    expect(suggestedSeats(chart, ["Financial Controller", "Head of Tax", "group treasurer"])).toEqual([
      "Head of Tax",
    ]);
  });
});

describe("addSuggestedSeat", () => {
  it("adds a seat as MANUAL, never DOCUMENT", () => {
    const { chart: merged, blocked } = addSuggestedSeat(chart, "Head of Tax");
    expect(blocked).toBeNull();
    const added = merged.find((node) => node.title === "Head of Tax");
    expect(added?.source).toBe("MANUAL");
    expect(added?.parentNodeId).toBe("role");
  });

  it("refuses a title already on the chart, case-insensitively", () => {
    const result = addSuggestedSeat(chart, "financial controller");
    expect(result.chart).toBe(chart);
    expect(result.blocked).toBe("duplicate");
  });

  it("does not add past the 60-seat ceiling", () => {
    const rootRole = [seat("role", null, { mandateSeat: true })];
    const full = [
      ...rootRole,
      ...Array.from({ length: MAX_ORG_CHART_SEATS - 1 }, (_, i) => seat(`extra-${i}`, "role")),
    ];
    const result = addSuggestedSeat(full, "One too many");
    expect(result.chart).toBe(full);
    expect(result.blocked).toBe("full");
  });
});
