import { Icon, ICONS } from "../../../../components/layout/Icon";
import { cn } from "../../../../lib/cn";
import { formatInstantDate } from "../../../../lib/datetime";
import type { ShareConsentRow } from "../../api/shareConsentApi";
import type { ShareConsentBadgeState } from "../../lib/useShareConsents";

const LABELS: Record<ShareConsentBadgeState, string> = {
  none: "No consent to share",
  asked: "Consent asked",
  given: "Consent given",
};

const TONES: Record<ShareConsentBadgeState, string> = {
  none: "border-u-signal text-u-signal",
  asked: "border-u-border-strong text-u-text2",
  given: "border-transparent bg-u-direct-tint text-u-direct",
};

/** A card's share-with-client consent (`Position.dc.html`, `shareConsent`): never asked, asked, or given. */
export function ShareConsentBadge({
  state,
  row,
  clientName,
}: {
  state: ShareConsentBadgeState;
  row: ShareConsentRow | undefined;
  clientName: string;
}) {
  const title =
    state === "given"
      ? `Agreed on ${formatInstantDate(row?.answeredAt ?? null) ?? ""} to be shared with ${clientName}`
      : state === "asked"
        ? `Asked on ${formatInstantDate(row?.requestedAt ?? null) ?? ""} · answer by ${formatInstantDate(row?.requestOpenUntil ?? null) ?? ""}`
        : `No consent to share with ${clientName}`;
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
