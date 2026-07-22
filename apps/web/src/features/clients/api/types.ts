import type { ProjectHealth, ProjectStage } from "../../projects/api/types";

/**
 * The client-registry API contract, hand-mirrored like the projects module's. A client is either
 * backed by a company from the universe (picked in the New-client modal) or typed in custom; the list
 * row's {@link ClientType} and every count are derived server-side, never stored.
 */

export type ClientType = "RETAINED" | "PROSPECT";

export type ClientRepStatus = "INVITED" | "ACTIVE" | "REVOKED";

/** A representative reduced to what the table's avatar stack renders. */
export interface RepAvatar {
  fullName: string;
  status: ClientRepStatus;
}

/** The "Viewers" column: how many representatives are active versus still invited. */
export interface ViewerSummary {
  active: number;
  invited: number;
}

export interface Client {
  id: string;
  name: string;
  type: ClientType;
  sector: string | null;
  hqCountry: string | null;
  activeMandates: number;
  deliveredMandates: number;
  contacts: RepAvatar[];
  viewers: ViewerSummary;
}

export interface ClientRepresentative {
  id: string;
  fullName: string;
  position: string | null;
  email: string;
  status: ClientRepStatus;
}

/** A mandate as the client drawer and the portal list it. */
export interface ClientMandate {
  id: string;
  positionTitle: string;
  stage: ProjectStage;
  health: ProjectHealth;
  leadName: string | null;
  targetDate: string | null;
}

export interface ClientDetail {
  id: string;
  name: string;
  sector: string | null;
  hqCountry: string | null;
  domain: string | null;
  offLimitsNote: string | null;
  activeMandates: number;
  deliveredMandates: number;
  representatives: ClientRepresentative[];
  mandates: ClientMandate[];
}

/** One universe company as the New-client search surfaces it (a subset of the company search result). */
export interface CompanyHit {
  source: string;
  sourceId: string;
  name: string;
  domain: string | null;
  primaryIndustry: string | null;
  hqCity: string | null;
  hqCountry: string | null;
}

/** Either a universe pick (`company`) or a custom record (`customName`), plus an optional primary contact. */
export interface CreateClientPayload {
  company?: { source: string; sourceId: string } | null;
  customName?: string;
  customDomain?: string;
  sector?: string;
  hqCountry?: string;
  primaryContact?: { fullName: string; position?: string; email: string } | null;
}

export interface UpdateClientPayload {
  name: string;
  sector?: string;
  hqCountry?: string;
  domain?: string;
  offLimitsNote?: string;
}

export interface InviteRepresentativePayload {
  fullName: string;
  position?: string;
  email: string;
}

/** The representative's own read: their client and its mandates, and nothing else. */
export interface PortalClient {
  id: string;
  name: string;
  sector: string | null;
  hqCountry: string | null;
  mandates: ClientMandate[];
}
