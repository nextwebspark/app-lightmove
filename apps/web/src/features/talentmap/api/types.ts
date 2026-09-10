import type { Candidate } from "../../candidates/api/types";
import type { TriageCompany } from "../../triage/api/types";

/** Where one row sits, as far as the server could tell: the city itself, or only its country. */
export interface MapLocation {
  latitude: number;
  longitude: number;
  precision: "CITY" | "COUNTRY";
  /** "Riyadh, Saudi Arabia" — ready to read back, so the popup need not rebuild it. */
  placeLabel: string;
}

/**
 * One stage of a mandate as the globe draws it. `locations` is keyed by row id, a company's or a
 * candidate's; an id absent from it has no point — no city and no country, or a place the vendor
 * could not put anywhere. An executive with no location of their own is absent too: the screen seats
 * them at their company, and the server states only what it actually resolved.
 */
export interface TalentMapPage {
  companies: TriageCompany[];
  totalCompanies: number;
  candidates: Candidate[];
  totalCandidates: number;
  locations: Record<string, MapLocation>;
  /** Places this read could not yet ask the vendor for; above zero the screen reads again shortly. */
  geocodingPending: number;
}

/** The points of one stage without the rows they belong to — what the pending-geocode poll reads. */
export interface TalentMapLocations {
  locations: Record<string, MapLocation>;
  geocodingPending: number;
}

/** Whether this deployment offers the map, and the public token the browser draws tiles with. */
export interface TalentMapConfig {
  enabled: boolean;
  publicToken: string | null;
}
