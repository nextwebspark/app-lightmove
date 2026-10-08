import { Link } from "react-router-dom";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { cn } from "../../../lib/cn";
import { creditChipOf } from "../lib/billingView";
import { useBilling } from "../lib/useBilling";

/**
 * The topbar's contact-credit chip: a trial's days left or its end, else absent below 80% used, then what is left; it
 * opens Settings → Billing.
 */
export function CreditChip() {
  const billing = useBilling();
  const chip = billing.data ? creditChipOf(billing.data) : null;
  if (!chip) return null;

  return (
    <Link
      to="/settings/billing"
      title={chip.about}
      aria-label={chip.label}
      className={cn(
        "inline-flex h-7 flex-none items-center gap-1.5 whitespace-nowrap rounded-[7px] border px-2.5 text-note font-medium transition hover:brightness-110",
        chip.tone === "out"
          ? "border-u-offlimits bg-u-offlimits-tint text-u-offlimits"
          : chip.tone === "trial"
            ? "border-u-accent/35 bg-u-accent-tint text-u-accent"
            : "border-u-signal bg-u-signal-tint text-u-signal",
      )}
    >
      <Icon d={ICONS.mail} size={13} />
      <span className="hidden sm:inline">{chip.label}</span>
    </Link>
  );
}
