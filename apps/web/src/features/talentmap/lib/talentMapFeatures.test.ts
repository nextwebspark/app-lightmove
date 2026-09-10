import { describe, expect, it } from "vitest";
import type { TalentMapPage } from "../api/types";
import { buildTree } from "./talentMapTree";
import { boundsOf, countOf, spreadPoints, toFeatureCollection } from "./talentMapFeatures";

describe("spreadPoints", () => {
  it("leaves a lone point where it is and steps shared points apart, the same way every time", () => {
    const rows = [
      { kind: "company" as const, id: "b", longitude: 46.7, latitude: 24.7, parentId: null },
      { kind: "company" as const, id: "a", longitude: 46.7, latitude: 24.7, parentId: null },
      { kind: "company" as const, id: "c", longitude: 55.3, latitude: 25.3, parentId: null },
    ];
    const first = spreadPoints(rows);
    const again = spreadPoints([...rows].reverse());

    expect(first.get("c")).toEqual([55.3, 25.3]);
    // Ordered by id, so "a" keeps the centroid whichever order the rows arrived in.
    expect(first.get("a")).toEqual([46.7, 24.7]);
    expect(first.get("b")).not.toEqual([46.7, 24.7]);
    expect(first.get("b")).toEqual(again.get("b"));

    const [longitude, latitude] = first.get("b")!;
    const distance = Math.hypot(longitude - 46.7, latitude - 24.7);
    expect(distance).toBeGreaterThan(0.02);
    expect(distance).toBeLessThan(0.05);
  });

  it("rings the executives seated at a company around that company's spread point", () => {
    const rows = [
      { kind: "company" as const, id: "u1", longitude: 46.7, latitude: 24.7, parentId: null },
      { kind: "executive" as const, id: "c1", longitude: 0, latitude: 0, parentId: "u1" },
      { kind: "executive" as const, id: "c2", longitude: 0, latitude: 0, parentId: "u1" },
      { kind: "executive" as const, id: "c9", longitude: 0, latitude: 0, parentId: "missing" },
    ];
    const placed = spreadPoints(rows);

    for (const id of ["c1", "c2"]) {
      const [longitude, latitude] = placed.get(id)!;
      const distance = Math.hypot((longitude - 46.7) * Math.cos((24.7 * Math.PI) / 180), latitude - 24.7);
      expect(distance).toBeCloseTo(0.018, 3);
    }
    expect(placed.get("c1")).not.toEqual(placed.get("c2"));
    // Seated at a company the map does not draw: nowhere to ring, so not drawn.
    expect(placed.has("c9")).toBe(false);
  });
});

describe("toFeatureCollection", () => {
  const page = {
    companies: [
      { id: "u1", companyName: "ACWA Power", companyCountry: "Saudi Arabia", companyCity: "Riyadh", industry: "oil & energy", numEmployees: 3000, logoUrl: null },
      { id: "u4", companyName: "Gulf Trader", companyCountry: null, companyCity: null, industry: null, numEmployees: null, logoUrl: null },
    ],
    totalCompanies: 2,
    candidates: [
      { id: "c1", triageCompanyId: "u1", companyName: "ACWA Power", fullName: "Yasmin El-Sayed", title: "VP Finance", seniority: "N-1", locationCity: null, locationCountry: null },
      { id: "c4", triageCompanyId: null, companyName: "Untriaged", fullName: "Lina Said", title: null, seniority: null, locationCity: null, locationCountry: "Oman" },
    ],
    totalCandidates: 2,
    locations: {
      u1: { latitude: 24.7, longitude: 46.7, precision: "CITY", placeLabel: "Riyadh, Saudi Arabia" },
      c4: { latitude: 21, longitude: 57, precision: "COUNTRY", placeLabel: "Oman" },
    },
    geocodingPending: 0,
  } as unknown as TalentMapPage;

  it("draws every located row with the label the pill and the popup read", () => {
    const collection = toFeatureCollection(buildTree(page), true);
    const byId = Object.fromEntries(collection.features.map((f) => [f.id, f.properties]));

    expect(Object.keys(byId).sort()).toEqual(["c1", "c4", "u1"]);
    expect(byId.u1).toMatchObject({ kind: "company", employees: 3000, executives: 1, hoverLabel: "ACWA Power · 1 exec" });
    expect(byId.u1.sublabel).toBe("oil & energy · Riyadh, Saudi Arabia");
    expect(byId.c1).toMatchObject({ kind: "executive", parentId: "u1", hoverLabel: "Yasmin El-Sayed — VP Finance" });
    expect(byId.c4).toMatchObject({ kind: "executive", parentId: null, hoverLabel: "Lina Said" });
  });

  it("leaves the executives out when they are hidden, and reports the box the rest fit in", () => {
    const companiesOnly = toFeatureCollection(buildTree(page), false);
    expect(companiesOnly.features.map((f) => f.id)).toEqual(["u1"]);
    expect(boundsOf(companiesOnly)).toEqual([[46.7, 24.7], [46.7, 24.7]]);
    expect(boundsOf({ type: "FeatureCollection", features: [] })).toBeNull();
  });
});

describe("countOf", () => {
  it("spells one and many", () => {
    expect(countOf(1, "exec")).toBe("1 exec");
    expect(countOf(3, "exec")).toBe("3 execs");
    expect(countOf(2, "company", "companies")).toBe("2 companies");
  });
});
