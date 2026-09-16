import type { MappingTree, TreeCompany, TreeExecutive } from "./talentMapTree";

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

/** South-west then north-east, the order Mapbox's `fitBounds` reads. */
export type MapBounds = [[number, number], [number, number]];

/** Degrees between neighbours on the spiral — about 3 km, which separates pins at city zoom. */
const SPIRAL_STEP_DEGREES = 0.03;
/**
 * The ring an executive sits on around their company's pin — about 2km, which is ~26px at the zoom
 * selecting a row flies to. Smaller and the ring is inside the company pin it is meant to orbit.
 */
const SATELLITE_RING_DEGREES = 0.018;
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

export function toFeatureCollection(tree: MappingTree, showExecutives: boolean): PinCollection {
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

/** A person's card and how far above their pin it sits, in pixels. */
export interface ProfileCard {
  id: string;
  lift: number;
}

/** Clear of the pin the card belongs to. */
const CARD_LIFT = 10;
/** A card at its widest and its tallest — the box two of them must not share. */
const CARD_WIDTH = 190;
const CARD_HEIGHT = 34;
/** What the search for a free slot climbs by — small, so a stack packs tightly rather than towers. */
const CARD_STACK_STEP = 8;

/**
 * The people a zoomed-in map draws a card for: the ones in view, and only while there are few
 * enough of them to read. Past the cap it reports `crowded` and no cards — a hundred overlapping
 * cards say less than the dots they would bury.
 *
 * <p>Takes the viewport as a predicate and the projection as a function rather than working in
 * degrees: the map's own bounds know about the antimeridian, and only the map knows how far apart
 * two places are on screen — which is the distance that decides whether two cards collide.
 *
 * <p>Whoever would land on a card already placed is lifted clear of it, which is what makes three
 * people at one address three lines rather than one smear. Deterministic in feature order, so the
 * same view stacks the same way twice.
 */
export function profileCards(
  features: readonly PinFeature[],
  inView: (coordinates: [number, number]) => boolean,
  cap: number,
  at: (coordinates: [number, number]) => { x: number; y: number },
): { cards: ProfileCard[]; crowded: boolean } {
  const cards: ProfileCard[] = [];
  const placed: { x: number; bottom: number }[] = [];
  for (const feature of features) {
    if (feature.properties.kind !== "executive" || !inView(feature.geometry.coordinates)) continue;
    if (cards.length === cap) return { cards: [], crowded: true };
    const { x, y } = at(feature.geometry.coordinates);
    let lift = CARD_LIFT;
    const collides = () =>
      placed.some(
        (box) => Math.abs(box.x - x) < CARD_WIDTH && Math.abs(box.bottom - (y - lift)) < CARD_HEIGHT,
      );
    while (collides()) lift += CARD_STACK_STEP;
    placed.push({ x, bottom: y - lift });
    cards.push({ id: feature.id, lift });
  }
  return { cards, crowded: false };
}

/** "1 exec", "3 execs", "2 companies" — the panel's and the pill's counts, spelled once. */
export function countOf(count: number, noun: string, plural = `${noun}s`): string {
  return `${count} ${count === 1 ? noun : plural}`;
}

/**
 * The box the given pins fit in — every one for "Fit to mapping", a country's for flying into it;
 * null when the set is empty. A fresh array on every call, which is what makes a repeated ask for
 * the same country a new camera move rather than a no-op.
 */
export function boundsOf(features: readonly PinFeature[]): MapBounds | null {
  if (!features.length) return null;
  let west = Infinity;
  let south = Infinity;
  let east = -Infinity;
  let north = -Infinity;
  for (const feature of features) {
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
