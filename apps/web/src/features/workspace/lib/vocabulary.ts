import { useAuth } from "../../auth/AuthProvider";
import type { WorkspaceMode } from "../../auth/api/types";

/** What a workspace calls the organisations it hires for, and the people there who read its work. */
export interface WorkspaceVocabulary {
  unit: string;
  units: string;
  unitLower: string;
  unitsLower: string;
  contact: string;
  contacts: string;
  contactLower: string;
  contactsLower: string;
  /** Team & access's line under the unit heading. */
  unitReportingLine: string;
  /** The registry's empty state: what one of these is, for someone who has none yet. */
  unitExplainer: string;
  /** Whose profile the assistant's starters are drawn from — the firm in-house, the client at an agency. */
  hiringCompanyPossessive: string;
  sectorStarterTag: string;
  /** Where to record that profile when the starters had to assume a sector. */
  hiringProfileHint: string;
}

const COMPANY_VOCABULARY: WorkspaceVocabulary = Object.freeze({
  unit: "Business unit",
  units: "Business units",
  unitLower: "business unit",
  unitsLower: "business units",
  contact: "Hiring manager",
  contacts: "Hiring managers",
  contactLower: "hiring manager",
  contactsLower: "hiring managers",
  unitReportingLine: "The business unit and the hiring managers we report to there",
  unitExplainer:
    "A business unit groups the hiring managers and open positions for one part of the org — Engineering, Sales, and so on.",
  hiringCompanyPossessive: "your firm's",
  sectorStarterTag: "Your sector",
  hiringProfileHint: "add your company in Settings → General",
});

const AGENCY_VOCABULARY: WorkspaceVocabulary = Object.freeze({
  unit: "Client",
  units: "Clients",
  unitLower: "client",
  unitsLower: "clients",
  contact: "Client contact",
  contacts: "Client contacts",
  contactLower: "client contact",
  contactsLower: "client contacts",
  unitReportingLine: "The client organisation and the people we report to on their side",
  unitExplainer: "A client is a company you search for — its contacts and open positions live here.",
  hiringCompanyPossessive: "your client's",
  sectorStarterTag: "Client's sector",
  hiringProfileHint: "add sectors to this client's persona under Clients",
});

export function vocabularyFor(mode: WorkspaceMode): WorkspaceVocabulary {
  return mode === "AGENCY" ? AGENCY_VOCABULARY : COMPANY_VOCABULARY;
}

/** COMPANY when there is no workspace yet: every workspace was in-house before modes existed. */
export function useWorkspaceMode(): WorkspaceMode {
  return useAuth().user?.workspace?.mode ?? "COMPANY";
}

export function useWorkspaceVocabulary(): WorkspaceVocabulary {
  return vocabularyFor(useWorkspaceMode());
}
