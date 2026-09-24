import { ICONS } from "../../../components/layout/Icon";
import type { TriageCompanySource, TriageCompanyStatus } from "../api/types";

/**
 * How a company's provenance and its available moves read on screen, in one place — the grid draws
 * them in a row and the panel draws them in a header and a footer, and the two must not drift into
 * saying different things about the same company.
 */

/** "Plugin" rather than "Extension" — that is what people call it. */
export const SOURCE_STYLES: Record<TriageCompanySource, { label: string; className: string }> = {
  strategy: { label: "Strategy", className: "text-u-accent bg-u-accent-tint" },
  manual: { label: "Manual", className: "text-u-accent bg-u-accent-tint" },
  extension: { label: "Plugin", className: "text-u-direct bg-u-direct-tint" },
  csv: { label: "Import", className: "text-u-text2 bg-u-border" },
};

export interface TriageMove {
  status: TriageCompanyStatus;
  label: string;
  icon: string;
  /** Read on hover. Only Decline carries one — it is the move easy to mistake for the trash bin. */
  tooltip?: string;
}

/** What clicking Decline means, versus the trash bin beside it — read by the grid and the drawer alike. */
const DECLINE_TOOLTIP = "Decline — kept as a record that this company was assessed and ruled out";

/**
 * The moves a stage offers, keyed by where the company currently is. A company is never offered the
 * stage it is already in, so every button on a row and in the panel's footer does something.
 */
export const MOVES: Record<TriageCompanyStatus, TriageMove[]> = {
  inUniverse: [
    { status: "shortlisted", label: "Shortlist", icon: ICONS.star },
    { status: "declined", label: "Decline", icon: ICONS.close, tooltip: DECLINE_TOOLTIP },
  ],
  shortlisted: [
    { status: "inUniverse", label: "Back to universe", icon: ICONS.globe },
    { status: "declined", label: "Decline", icon: ICONS.close, tooltip: DECLINE_TOOLTIP },
  ],
  declined: [
    { status: "inUniverse", label: "Back to universe", icon: ICONS.globe },
    { status: "shortlisted", label: "Shortlist", icon: ICONS.star },
  ],
};

/**
 * What the trash bin means, for the grid's row action and the drawer's footer button alike — read on
 * hover beside {@link MOVES}' own Decline tooltip, so the two explain the difference between them
 * rather than each explaining only itself.
 */
export function removeTooltip(companyName: string): string {
  return `Remove ${companyName} from this mandate — not remembered; use Decline to keep a record`;
}
