import { Link } from "react-router-dom";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { cn } from "../../../lib/cn";
import { creditChipOf, type CreditTone } from "../lib/billingView";
import { useBilling } from "../lib/useBilling";

const TONE_TEXT: Record<CreditTone, string> = { warn: "text-u-signal", out: "text-u-offlimits" };

/** The topbar's trial and contact-credit chip, as {@link creditChipOf} reads it; it opens Settings → Billing. */
export function CreditChip() {
  const billing = useBilling();
  const chip = billing.data ? creditChipOf(billing.data) : null;
  if (!chip) return null;
  const spoken = chip.credits ? `${chip.label} · ${chip.credits.label}` : chip.label;

  return (
    <Link
      to="/settings/billing"
      title={spoken}
      aria-label={spoken}
      className={cn(
        "inline-flex h-7 flex-none items-center gap-1.5 whitespace-nowrap rounded-[7px] border px-2.5 text-note font-medium transition hover:brightness-110",
        chip.tone === "out"
          ? "border-u-offlimits bg-u-offlimits-tint text-u-offlimits"
          : chip.tone === "trial"
            ? "border-u-accent/35 bg-u-accent-tint text-u-accent"
            : "border-u-signal bg-u-signal-tint text-u-signal",
      )}
    >
      <Icon d={chip.kind === "trial" ? ICONS.clock : ICONS.mail} size={13} />
      <span className="sm:hidden">{chip.shortLabel}</span>
      <span className="hidden sm:inline">{chip.label}</span>
      {chip.credits && (
        <>
          {/* Narrow screens carry the credit level as its own glyph, so it is never colour alone. */}
          <span className={cn("inline-flex xl:hidden", TONE_TEXT[chip.credits.tone])}>
            <Icon d={ICONS.mail} size={12} />
          </span>
          <span className={cn("hidden xl:inline", TONE_TEXT[chip.credits.tone])}>· {chip.credits.label}</span>
        </>
      )}
    </Link>
  );
}
