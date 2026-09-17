import type { CandidateStatus } from "../../candidates/api/types";

/** Where the mandate has got to with someone, in the four readings the report draws. */
export type StatusTone = "interested" | "inConversation" | "closed" | "untouched";

const TONE_OF: Record<CandidateStatus, StatusTone> = {
  interested: "interested",
  contacted: "inConversation",
  engaged: "inConversation",
  notInterested: "closed",
  offLimits: "closed",
  identified: "untouched",
  outOfScope: "untouched",
};

export const STATUS_TONES: Record<StatusTone, { label: string; fill: string; swatch: string; pill: string }> = {
  interested: { label: "Interested", fill: "fill-u-direct", swatch: "bg-u-direct", pill: "bg-u-direct-tint text-u-direct" },
  inConversation: {
    label: "In conversation",
    fill: "fill-u-adjacent",
    swatch: "bg-u-adjacent",
    pill: "bg-u-adjacent-tint text-u-adjacent",
  },
  closed: {
    label: "Said no or off-limits",
    fill: "fill-u-offlimits",
    swatch: "bg-u-offlimits",
    pill: "bg-u-offlimits-tint text-u-offlimits",
  },
  untouched: {
    label: "Identified or out of scope",
    fill: "fill-u-text3",
    swatch: "bg-u-text3",
    pill: "border border-u-border-strong bg-u-sunken text-u-text2",
  },
};

export function statusTone(status: CandidateStatus): StatusTone {
  return TONE_OF[status];
}
