import type { TalentMapTree, TreeCompany, TreeExecutive } from "./talentMapTree";

/**
 * The tree as GeoJSON: what the globe draws.
 *
 * <p>Every place is a city or a country centroid, so every company in Dubai is the same point and
 * would draw as one pin. `spreadPoints` lays the ones that share a coordinate out on a sunflower
 * spiral — deterministic by id, so the same company sits in the same spot on every read — and seats
 * an executive without a place of their own on a small ring around their company's pin.
 */

export type PinKind = "company" | "executive";

export interface PinProperties {
  kind: PinKind;
  rowId: string;
  label: string;
  sublabel: string;
  /** Headcount, for the company pin's size; 0 for a person or an unknown figure. */
  employees: number;
  /** The company an executive is drawn beside, when drawn beside one. */
  parentId: string | null;
  /** How many people are at a company, for the hover label; 0 for a person. */
  executives: number;
  /** "ACWA Power · 2 execs" or "Yasmin El-Sayed — VP Finance": the pill above a hovered pin. */
  hoverLabel: string;
}

export interface PinFeature {
  type: "Feature";
  id: string;
  geometry: { type: "Point"; coordinates: [number, number] };
  properties: PinProperties;
}

export interface PinCollection {
  type: "FeatureCollection";
  features: PinFeature[];
}

/** Degrees between neighbours on the spiral — about 3 km, which separates pins at city zoom. */
const SPIRAL_STEP_DEGREES = 0.03;
/** The ring an executive sits on around their company's pin. */
const SATELLITE_RING_DEGREES = 0.012;
const GOLDEN_ANGLE = Math.PI * (3 - Math.sqrt(5));

interface Placed {
  kind: PinKind;
  id: string;
  longitude: number;
  latitude: number;
  parentId: string | null;
}

/**
 * Coordinates for every located row, with shared points spread apart. The first of a group keeps
 * the centroid; each later one steps out along the spiral, ordered by id so nothing moves between
 * reads. Executives seated at a company take the company's spread point, then their ring.
 */
export function spreadPoints(rows: Placed[]): Map<string, [number, number]> {
  const groups = new Map<string, Placed[]>();
  const seated: Placed[] = [];
  for (const row of rows) {
    if (row.parentId) {
      seated.push(row);
      continue;
    }
    const key = `${row.latitude},${row.longitude}`;
    const held = groups.get(key);
    if (held) held.push(row);
    else groups.set(key, [row]);
  }

  const placed = new Map<string, [number, number]>();
  for (const group of groups.values()) {
    group.sort((a, b) => a.id.localeCompare(b.id));
    group.forEach((row, index) => {
      if (index === 0) {
        placed.set(row.id, [row.longitude, row.latitude]);
        return;
      }
      const radius = SPIRAL_STEP_DEGREES * Math.sqrt(index);
      const angle = index * GOLDEN_ANGLE;
      placed.set(row.id, [
        row.longitude + radius * Math.cos(angle) / latitudeStretch(row.latitude),
        row.latitude + radius * Math.sin(angle),
      ]);
    });
  }

  const siblings = new Map<string, Placed[]>();
  for (const row of seated) {
    const held = siblings.get(row.parentId!);
    if (held) held.push(row);
    else siblings.set(row.parentId!, [row]);
  }
  for (const [parentId, ring] of siblings) {
    const centre = placed.get(parentId);
    if (!centre) continue;
    ring.sort((a, b) => a.id.localeCompare(b.id));
    ring.forEach((row, index) => {
      const angle = (index / ring.length) * 2 * Math.PI - Math.PI / 2;
      placed.set(row.id, [
        centre[0] + SATELLITE_RING_DEGREES * Math.cos(angle) / latitudeStretch(centre[1]),
        centre[1] + SATELLITE_RING_DEGREES * Math.sin(angle),
      ]);
    });
  }
  return placed;
}

/** A degree of longitude shrinks towards the poles; without this a ring is an ellipse at 25°N. */
function latitudeStretch(latitude: number): number {
  return Math.max(0.2, Math.cos((latitude * Math.PI) / 180));
}

export function toFeatureCollection(tree: TalentMapTree, showExecutives: boolean): PinCollection {
  const companies: TreeCompany[] = [];
  const executives: TreeExecutive[] = [];
  for (const country of tree.countries) {
    companies.push(...country.companies);
    executives.push(...country.unmapped);
    for (const company of country.companies) executives.push(...company.executives);
  }
  // A company with no place of its own still carries people who may have one.
  for (const company of tree.unlocated.companies) executives.push(...company.executives);

  const rows: Placed[] = [];
  for (const company of companies) {
    if (!company.location) continue;
    rows.push({
      kind: "company",
      id: company.id,
      longitude: company.location.longitude,
      latitude: company.location.latitude,
      parentId: null,
    });
  }
  if (showExecutives) {
    for (const executive of executives) {
      if (executive.location) {
        rows.push({
          kind: "executive",
          id: executive.id,
          longitude: executive.location.longitude,
          latitude: executive.location.latitude,
          parentId: null,
        });
      } else if (executive.seatedAt) {
        rows.push({ kind: "executive", id: executive.id, longitude: 0, latitude: 0, parentId: executive.seatedAt });
      }
    }
  }
  const placed = spreadPoints(rows);

  const features: PinFeature[] = [];
  for (const company of companies) {
    const coordinates = placed.get(company.id);
    if (!coordinates) continue;
    features.push({
      type: "Feature",
      id: company.id,
      geometry: { type: "Point", coordinates },
      properties: {
        kind: "company",
        rowId: company.id,
        label: company.company.companyName,
        sublabel: [company.company.industry, company.location?.placeLabel]
          .filter(Boolean)
          .join(" · "),
        employees: company.company.numEmployees ?? 0,
        parentId: null,
        executives: company.executives.length,
        hoverLabel: `${company.company.companyName} · ${countOf(company.executives.length, "exec")}`,
      },
    });
  }
  if (showExecutives) {
    for (const executive of executives) {
      const coordinates = placed.get(executive.id);
      if (!coordinates) continue;
      features.push({
        type: "Feature",
        id: executive.id,
        geometry: { type: "Point", coordinates },
        properties: {
          kind: "executive",
          rowId: executive.id,
          label: executive.candidate.fullName,
          sublabel: [executive.candidate.title, executive.candidate.companyName]
            .filter(Boolean)
            .join(" · "),
          employees: 0,
          parentId: executive.seatedAt,
          executives: 0,
          hoverLabel: executive.candidate.title
            ? `${executive.candidate.fullName} — ${executive.candidate.title}`
            : executive.candidate.fullName,
        },
      });
    }
  }
  return { type: "FeatureCollection", features };
}

/** "1 exec", "3 execs", "2 companies" — the panel's and the pill's counts, spelled once. */
export function countOf(count: number, noun: string, plural = `${noun}s`): string {
  return `${count} ${count === 1 ? noun : plural}`;
}

/** The box every pin fits in, for "Fit to mapping"; null when nothing is drawn. */
export function boundsOf(collection: PinCollection): [[number, number], [number, number]] | null {
  if (!collection.features.length) return null;
  let west = Infinity;
  let south = Infinity;
  let east = -Infinity;
  let north = -Infinity;
  for (const feature of collection.features) {
    const [longitude, latitude] = feature.geometry.coordinates;
    west = Math.min(west, longitude);
    east = Math.max(east, longitude);
    south = Math.min(south, latitude);
    north = Math.max(north, latitude);
  }
  return [
    [west, south],
    [east, north],
  ];
}
