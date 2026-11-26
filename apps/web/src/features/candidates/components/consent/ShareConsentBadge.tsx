import { Icon, ICONS } from "../../../../components/layout/Icon";
import { cn } from "../../../../lib/cn";
import { formatInstantDate } from "../../../../lib/datetime";
import type { ShareConsentRow } from "../../api/shareConsentApi";
import type { ShareConsentBadgeState } from "../../lib/useShareConsents";

const LABELS: Record<ShareConsentBadgeState, string> = {
  none: "No consent to share",
  asked: "Consent asked",
  given: "Consent given",
  declined: "Said no to sharing",
  withdrawn: "Withdrew sharing",
};

const TONES: Record<ShareConsentBadgeState, string> = {
  none: "border-u-signal text-u-signal",
  asked: "border-u-border-strong text-u-text2",
  given: "border-transparent bg-u-direct-tint text-u-direct",
  declined: "border-transparent bg-u-offlimits-tint text-u-offlimits",
  withdrawn: "border-transparent bg-u-offlimits-tint text-u-offlimits",
};

/**
 * A card's share-with-client consent (`Position.dc.html`, `shareConsent`): never asked, asked, given, or refused —
 * a no or a withdrawal hides them from the client until they say yes (#1488).
 */
export function ShareConsentBadge({
  state,
  row,
  clientName,
}: {
  state: ShareConsentBadgeState;
  row: ShareConsentRow | undefined;
  clientName: string;
}) {
  const title = titleOf(state, row, clientName);
  return (
    <span
      title={title}
      className={cn(
        "inline-flex min-w-0 items-center gap-1 truncate rounded-u-chip border px-1.5 py-0.5 font-mono text-meta",
        TONES[state],
      )}
    >
      <Icon d={ICONS.shield} size={11} className="flex-none" />
      <span className="truncate">{LABELS[state]}</span>
    </span>
  );
}

function titleOf(state: ShareConsentBadgeState, row: ShareConsentRow | undefined, clientName: string): string {
  switch (state) {
    case "given":
      return `Agreed on ${formatInstantDate(row?.answeredAt ?? null) ?? ""} to be shared with ${clientName}`;
    case "declined":
      return `Said no on ${formatInstantDate(row?.answeredAt ?? null) ?? ""} to being shared with ${clientName}`;
    case "withdrawn":
      return `Withdrew on ${formatInstantDate(row?.answeredAt ?? null) ?? ""} their consent to be shared with ${clientName}`;
    case "asked":
      return `Asked on ${formatInstantDate(row?.requestedAt ?? null) ?? ""} · answer by ${formatInstantDate(row?.requestOpenUntil ?? null) ?? ""}`;
    case "none":
      return `No consent to share with ${clientName}`;
  }
}
