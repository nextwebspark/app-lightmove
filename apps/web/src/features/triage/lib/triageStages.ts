import { ICONS } from "../../../components/layout/Icon";
import type { TriageCompanyStatus } from "../api/types";

/**
 * The three Companies pages, in the order a mandate works through them.
 *
 * <p>One list, read by the router, the sidebar and each page's own copy — so a stage cannot exist
 * in the menu and not in the routes, or be called "Shortlisted" in one place and "Shortlist" in
 * another.
 *
 * <p>`slug` and `status` are deliberately different strings. The slug is a URL a consultant reads and
 * sends to a colleague; the status is the API's token. Collapsing them would mean either
 * `/companies/inUniverse` in the address bar or a camel-case rename rippling through the server, the
 * audit vocabulary and the database CHECK.
 */
export interface TriageStage {
  slug: string;
  status: TriageCompanyStatus;
  label: string;
  icon: string;
  /**
   * An empty stage is not one situation. "In universe" empty means nobody has searched yet and the
   * next move is Strategy; "Shortlisted" empty means the working set has not been promoted from; and
   * "Declined" empty is a stage a mandate can legitimately stay at forever. One shared sentence would
   * be wrong on two of the three.
   */
  emptyMessage: string;
}

export const TRIAGE_STAGES: TriageStage[] = [
  {
    slug: "universe",
    status: "inUniverse",
    label: "In universe",
    icon: ICONS.globe,
    emptyMessage:
      "No companies in the universe yet. Filter the market on Strategy and add the ones worth a closer look, or add a company here.",
  },
  {
    slug: "shortlisted",
    status: "shortlisted",
    label: "Shortlisted",
    icon: ICONS.star,
    emptyMessage:
      "Nothing shortlisted yet. Promote a company from In universe once it is worth mapping people at.",
  },
  {
    slug: "declined",
    status: "declined",
    label: "Declined",
    icon: ICONS.close,
    emptyMessage:
      "Nothing declined. Companies you rule out land here, and stay ruled out the next time someone adds in bulk.",
  },
];

export function stageBySlug(slug: string): TriageStage | undefined {
  return TRIAGE_STAGES.find((stage) => stage.slug === slug);
}

/**
 * The stage a stored status belongs to. Total rather than optional: every status the API can answer
 * with has a row above, and a panel that had to handle "no such stage" for a value the server just
 * sent would be guarding against its own type.
 */
export function stageByStatus(status: TriageCompanyStatus): TriageStage {
  return TRIAGE_STAGES.find((stage) => stage.status === status) ?? TRIAGE_STAGES[0];
}
