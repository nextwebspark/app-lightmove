import { ICONS } from "../../../components/layout/Icon";
import type { TriageCompanySource, TriageCompanyStatus } from "../api/types";

/**
 * How a company's provenance and its available moves read on screen, in one place — the grid draws
 * them in a row and the panel draws them in a header and a footer, and the two must not drift into
 * saying different things about the same company.
 */

/** "Plugin" rather than "Extension" — that is what people call it. */
export const SOURCE_STYLES: Record<TriageCompanySource, { label: string; className: string }> = {
  strategy: { label: "Strategy", className: "text-sky bg-sky-dim" },
  manual: { label: "Manual", className: "text-amber bg-amber-dim" },
  extension: { label: "Plugin", className: "text-green bg-green-dim" },
  csv: { label: "Import", className: "text-text2 bg-line-soft" },
};

export interface TriageMove {
  status: TriageCompanyStatus;
  label: string;
  icon: string;
}

/**
 * The moves a stage offers, keyed by where the company currently is. A company is never offered the
 * stage it is already in, so every button on a row and in the panel's footer does something.
 */
export const MOVES: Record<TriageCompanyStatus, TriageMove[]> = {
  inUniverse: [
    { status: "shortlisted", label: "Shortlist", icon: ICONS.star },
    { status: "declined", label: "Decline", icon: ICONS.close },
  ],
  shortlisted: [
    { status: "inUniverse", label: "Back to universe", icon: ICONS.globe },
    { status: "declined", label: "Decline", icon: ICONS.close },
  ],
  declined: [
    { status: "inUniverse", label: "Back to universe", icon: ICONS.globe },
    { status: "shortlisted", label: "Shortlist", icon: ICONS.star },
  ],
};
