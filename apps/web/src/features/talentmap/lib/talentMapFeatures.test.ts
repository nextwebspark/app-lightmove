import { describe, expect, it } from "vitest";
import type { TalentMapPage } from "../api/types";
import { buildTree } from "./talentMapTree";
import { boundsOf, countOf, profileCards, spreadPoints, toFeatureCollection } from "./talentMapFeatures";

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

describe("toFeatureCollection", () => {
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
    expect(boundsOf(companiesOnly.features)).toEqual([[46.7, 24.7], [46.7, 24.7]]);
    expect(boundsOf([])).toBeNull();
  });

  it("boxes a subset on its own, which is what flying into one country reads", () => {
    const all = toFeatureCollection(buildTree(page), true);
    const oman = all.features.filter((feature) => feature.id === "c4");
    expect(boundsOf(oman)).toEqual([[57, 21], [57, 21]]);
  });
});

describe("profileCards", () => {
  const all = toFeatureCollection(buildTree(page), true).features;
  const everywhere = () => true;

  // A stand-in for the map's own projection: 1000px per degree, y growing downwards.
  const at = ([longitude, latitude]: [number, number]) => ({ x: longitude * 1000, y: -latitude * 1000 });

  it("cards the people in view and nobody else", () => {
    // Companies are never carded, however close in the map is.
    expect(profileCards(all, everywhere, 10, at).cards.map((card) => card.id).sort()).toEqual(["c1", "c4"]);
    // Oman only: the executive mapped at no company of the mandate.
    const oman = profileCards(all, ([longitude]) => longitude > 56, 10, at);
    expect(oman).toEqual({ cards: [{ id: "c4", lift: 10 }], crowded: false });
  });

  it("lifts a card clear of one it would land on, and leaves a distant one where it is", () => {
    const crowd = {
      ...page,
      candidates: [
        page.candidates[0],
        { ...page.candidates[0], id: "c2", fullName: "Ahmed Bakr" },
        { ...page.candidates[0], id: "c3", fullName: "Noura Al-Qahtani" },
      ],
    } as typeof page;
    const together = toFeatureCollection(buildTree(crowd), true).features;

    // Three at one address, seen from close in and from far enough out to be one pixel: a stack
    // either way, and no two cards sharing a box.
    for (const scale of [1000, 1]) {
      const project = ([longitude, latitude]: [number, number]) => ({ x: longitude * scale, y: -latitude * scale });
      const { cards } = profileCards(together, everywhere, 10, project);
      expect(cards).toHaveLength(3);
      const boxes = cards.map((card, index) => {
        const point = project(together.filter((f) => f.properties.kind === "executive")[index].geometry.coordinates);
        return { x: point.x, bottom: point.y - card.lift };
      });
      for (const [i, box] of boxes.entries()) {
        for (const other of boxes.slice(i + 1)) {
          expect(Math.abs(box.x - other.x) >= 190 || Math.abs(box.bottom - other.bottom) >= 34).toBe(true);
        }
      }
    }

    // Riyadh and Oman never collide, so neither is lifted off its pin.
    expect(profileCards(all, everywhere, 10, at).cards.map((card) => card.lift)).toEqual([10, 10]);
  });

  it("keeps the dots rather than burying them once past the cap", () => {
    expect(profileCards(all, everywhere, 1, at)).toEqual({ cards: [], crowded: true });
    expect(profileCards(all, () => false, 1, at)).toEqual({ cards: [], crowded: false });
  });
});

describe("countOf", () => {
  it("spells one and many", () => {
    expect(countOf(1, "exec")).toBe("1 exec");
    expect(countOf(3, "exec")).toBe("3 execs");
    expect(countOf(2, "company", "companies")).toBe("2 companies");
  });
});
