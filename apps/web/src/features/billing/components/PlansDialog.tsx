import { useState } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Button, Modal, SegmentedControl } from "../../../components/ui";
import { cn } from "../../../lib/cn";
import { formatNumber } from "../../../lib/format";
import * as billingApi from "../api/billingApi";
import type { Billing, BillingInterval, BillingPlanOffer, PlanCode } from "../api/types";
import { formatAed, planChoiceOf, type PlanChoice } from "../lib/billingView";
import { useStripeRedirect } from "../lib/useStripeRedirect";

const INTERVALS = [
  { value: "MONTHLY", label: "Monthly" },
  { value: "ANNUAL", label: "Yearly · save 20%" },
] as const;

/**
 * The plans (`Billing.dc.html`'s Plans dialog): Core, Pro and Enterprise per staff seat. A workspace with no card on
 * Stripe goes to Checkout; one Stripe already bills changes plan in the Customer Portal, which the API requires.
 */
export function PlansDialog({ billing, onClose }: { billing: Billing; onClose: () => void }) {
  const [interval, setPeriod] = useState<BillingInterval>(billing.interval ?? "MONTHLY");
  const checkout = useStripeRedirect((plan: PlanCode) => billingApi.startSubscriptionCheckout(plan, interval));
  const portal = useStripeRedirect((_plan: PlanCode) => billingApi.openPortal());
  const busy = checkout.isPending || portal.isPending;

  return (
    <Modal
      open
      onClose={onClose}
      title="Plans"
      closeButton
      className="md:w-[880px]"
      headerAside={
        <SegmentedControl label="Billing period" options={INTERVALS} value={interval} onChange={setPeriod} />
      }
    >
      <div className="grid gap-3 md:grid-cols-3">
        {billing.plans.map((offer) => (
          <PlanCard
            key={offer.code}
            offer={offer}
            interval={interval}
            choice={planChoiceOf(billing, offer, interval)}
            highlighted={offer.code === "PRO"}
            busy={busy}
            pending={[checkout, portal].some((redirect) => redirect.isPending && redirect.variables === offer.code)}
            onCheckout={() => checkout.mutate(offer.code)}
            onPortal={() => portal.mutate(offer.code)}
          />
        ))}
      </div>
      <p className="mx-0.5 mt-4 font-mono text-[11.5px]/[1.55] text-u-text3">
        Prices in AED per staff seat, before 5% VAT. Client contacts are free. You confirm a change on Stripe's page; a
        move down starts at the end of the month.
      </p>
    </Modal>
  );
}

function PlanCard({
  offer,
  interval,
  choice,
  highlighted,
  busy,
  pending,
  onCheckout,
  onPortal,
}: {
  offer: BillingPlanOffer;
  interval: BillingInterval;
  choice: PlanChoice;
  highlighted: boolean;
  busy: boolean;
  pending: boolean;
  onCheckout: () => void;
  onPortal: () => void;
}) {
  const current = choice.kind === "current";
  const seatPrice = interval === "ANNUAL" ? offer.seatPriceAnnualFils : offer.seatPriceMonthlyFils;
  const lines = offer.custom
    ? ["Contact credits sized to your team", "Search and AI included", "Pay by invoice", "A named contact at Uncava", "Client contacts free"]
    : [
        `${formatNumber(offer.contactCreditsPerSeat ?? 0)} contact credits per seat a month`,
        "Search and AI included",
        "Credits shared by the whole team",
        "Client contacts free",
      ];

  return (
    <section
      aria-label={offer.name}
      className={cn(
        "flex flex-col rounded-xl bg-u-raised p-[18px]",
        current ? "border-[1.5px] border-u-signal" : "border border-u-border",
      )}
    >
      <div className="flex items-center gap-2">
        <span className="text-base font-semibold">{offer.name}</span>
        {current && (
          <span className="rounded-full bg-u-signal-tint px-2 py-0.5 font-mono text-[10px] font-semibold uppercase tracking-[0.06em] text-u-signal">
            Current
          </span>
        )}
      </div>
      <div className="mt-3 flex items-baseline gap-1.5">
        <span className="font-u-num text-[26px] font-medium">
          {offer.custom || seatPrice === null ? "Custom" : formatAed(seatPrice)}
        </span>
        {!offer.custom && <span className="font-mono text-xs text-u-text3">per seat / month</span>}
      </div>
      <div className="mt-0.5 min-h-4 font-mono text-[11px] text-u-text3">
        {offer.custom ? "For larger firms" : interval === "ANNUAL" ? "Billed yearly" : "Billed monthly"}
      </div>
      <ul className="mt-3.5 flex flex-col gap-2">
        {lines.map((line) => (
          <li key={line} className="flex items-start gap-2 text-[12.5px]/[1.45]">
            <Icon d={ICONS.check} size={14} className="mt-0.5 flex-none text-u-direct" />
            {line}
          </li>
        ))}
      </ul>
      <div className="mt-auto pt-[18px]">
        <PlanAction
          choice={choice}
          primary={highlighted}
          busy={busy}
          pending={pending}
          onCheckout={onCheckout}
          onPortal={onPortal}
        />
      </div>
    </section>
  );
}

function PlanAction({
  choice,
  primary,
  busy,
  pending,
  onCheckout,
  onPortal,
}: {
  choice: PlanChoice;
  primary: boolean;
  busy: boolean;
  pending: boolean;
  onCheckout: () => void;
  onPortal: () => void;
}) {
  if (choice.kind === "talk") {
    return (
      <a
        href={choice.href}
        className="flex w-full items-center justify-center rounded-[6px] border border-u-border-strong bg-u-surface px-3.5 py-2.5 text-[13.5px] font-medium text-u-text2 transition hover:border-u-text3 hover:text-u-text"
      >
        {choice.label}
      </a>
    );
  }
  if (choice.kind === "current") {
    return (
      <Button type="button" variant="secondary" disabled className="w-full opacity-60">
        {choice.label}
      </Button>
    );
  }
  const unavailable = choice.kind === "checkout" && !choice.available;
  return (
    <Button
      type="button"
      variant={primary ? "primary" : "secondary"}
      className="w-full"
      loading={pending}
      disabled={busy || unavailable}
      title={unavailable ? "Not sold online in this billing period" : undefined}
      onClick={choice.kind === "portal" ? onPortal : onCheckout}
    >
      {choice.label}
    </Button>
  );
}
