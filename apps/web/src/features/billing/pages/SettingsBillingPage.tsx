import { useQuery } from "@tanstack/react-query";
import { useCallback, useEffect, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { PageHeader } from "../../../components/layout/PageHeader";
import { Avatar, Button, useToast } from "../../../components/ui";
import { cn } from "../../../lib/cn";
import { messageFor } from "../../../lib/errorCodes";
import { formatNumber } from "../../../lib/format";
import { useAuth } from "../../auth/AuthProvider";
import * as billingApi from "../api/billingApi";
import type { Billing, MemberCreditSpend } from "../api/types";
import { BuyCreditsDialog } from "../components/BuyCreditsDialog";
import { PlansDialog } from "../components/PlansDialog";
import {
  BILLING_CONTACT_EMAIL,
  billingBannerOf,
  buyOptionOf,
  cardBrandLabel,
  formatAed,
  formatBillingDate,
  formatResetDate,
  paysByCard,
  planOptionOf,
  trialOf,
  type BannerTone,
  type BuyOption,
  type PlanOption,
} from "../lib/billingView";
import {
  CHECKOUT_POLL_MS,
  CHECKOUT_WAIT_MS,
  checkoutReturnOf,
  hasLanded,
  boughtBeforeCheckout,
  type CheckoutReturn,
} from "../lib/checkoutReturn";
import { useBilling, useIsWorkspaceAdmin } from "../lib/useBilling";
import { useStripeRedirect } from "../lib/useStripeRedirect";

type Dialog = "plans" | "buy" | null;

/**
 * Settings → Billing (`Billing.dc.html`): the plan, this month's contact credits, who spent them, and how the
 * workspace pays. Every staff member reads it; only an admin is offered seats or more credits. Quiet until 80% of
 * the month's credits are used.
 */
export function SettingsBillingPage() {
  const isAdmin = useIsWorkspaceAdmin();
  const [dialog, setDialog] = useState<Dialog>(null);
  const { awaiting, settle } = useCheckoutReturn();
  const billing = useBilling(awaiting ? CHECKOUT_POLL_MS : false);
  useCheckoutLanding(awaiting, billing.data, settle);
  const usage = useQuery({
    queryKey: billingApi.BILLING_USAGE_KEY,
    queryFn: ({ signal }) => billingApi.getBillingUsage(signal),
  });

  return (
    <>
      <PageHeader
        title="Billing"
        subtitle={
          isAdmin
            ? "Your plan and this month's contact credits. Search and AI are part of the plan."
            : "Your team's plan and this month's contact credits. Only an admin can change the plan or add credits."
        }
      />

      {billing.isError ? (
        <p className="text-[13px] text-u-text3">{messageFor(billing.error)}</p>
      ) : !billing.data ? (
        <p className="text-[13px] text-u-text3">Loading…</p>
      ) : (
        <>
          {awaiting && <AwaitingStripe kind={awaiting.kind} />}
          <Banner
            billing={billing.data}
            isAdmin={isAdmin}
            onBuy={() => setDialog("buy")}
            onPlans={() => setDialog("plans")}
          />
          {billing.data.plan ? (
            <>
              <PlanCard billing={billing.data} isAdmin={isAdmin} onPlans={() => setDialog("plans")} />
              <CreditMeter
                billing={billing.data}
                isAdmin={isAdmin}
                onBuy={() => setDialog("buy")}
                onPlans={() => setDialog("plans")}
              />
              <UsedThisMonth members={usage.data?.members} failed={usage.isError} />
            </>
          ) : (
            <NoPlan billing={billing.data} isAdmin={isAdmin} onPlans={() => setDialog("plans")} />
          )}
          <PaymentRow billing={billing.data} isAdmin={isAdmin} />
          {dialog === "plans" && <PlansDialog billing={billing.data} onClose={() => setDialog(null)} />}
          {dialog === "buy" && <BuyCreditsDialog billing={billing.data} onClose={() => setDialog(null)} />}
        </>
      )}
    </>
  );
}

interface AwaitedCheckout {
  kind: Exclude<CheckoutReturn, "cancelled">;
  boughtBefore: number | null;
  since: number;
}

/**
 * Reads `?checkout=` once Stripe sends the admin back, and drops it so a reload does not replay it. A cancelled
 * Checkout is only a toast; a paid one is awaited until the webhook's change reaches the billing read.
 */
function useCheckoutReturn() {
  const toast = useToast();
  const [params, setParams] = useSearchParams();
  const [awaited, setAwaited] = useState<AwaitedCheckout | null>(null);
  const returned = checkoutReturnOf(params.get("checkout"));

  useEffect(() => {
    if (!returned) return;
    setParams(
      (current) => {
        const next = new URLSearchParams(current);
        next.delete("checkout");
        return next;
      },
      { replace: true },
    );
    if (returned === "cancelled") {
      toast("Checkout cancelled — nothing was charged");
      return;
    }
    setAwaited({ kind: returned, boughtBefore: returned === "credits" ? boughtBeforeCheckout() : null, since: Date.now() });
  }, [returned, setParams, toast]);

  useEffect(() => {
    if (!awaited) return;
    const timer = window.setTimeout(() => {
      setAwaited(null);
      toast("Stripe has not confirmed the payment yet. It will show here once it does.");
    }, CHECKOUT_WAIT_MS - (Date.now() - awaited.since));
    return () => window.clearTimeout(timer);
  }, [awaited, toast]);

  const settle = useCallback(() => setAwaited(null), []);
  return { awaiting: awaited, settle };
}

/** Ends the wait as soon as the billing read shows what was paid for. */
function useCheckoutLanding(awaited: AwaitedCheckout | null, billing: Billing | undefined, settle: () => void) {
  const toast = useToast();
  const landed = !!awaited && !!billing && hasLanded(awaited.kind, billing, awaited.boughtBefore);
  useEffect(() => {
    if (!landed || !awaited) return;
    toast(awaited.kind === "subscribed" ? "Your plan is active" : "Credits added");
    settle();
  }, [landed, awaited, toast, settle]);
}

function AwaitingStripe({ kind }: { kind: AwaitedCheckout["kind"] }) {
  return (
    <div role="status" className="mb-3 rounded-[10px] border border-u-accent/35 bg-u-accent-tint px-3.5 py-3 text-[13px]">
      {kind === "subscribed"
        ? "Payment received — waiting for Stripe to confirm the plan…"
        : "Payment received — waiting for Stripe to confirm the credits…"}
    </div>
  );
}

const BANNER_TONES: Record<BannerTone, { box: string; icon: string; glyph: string }> = {
  red: { box: "border-u-offlimits/35 bg-u-offlimits-tint", icon: "text-u-offlimits", glyph: ICONS.warning },
  warn: { box: "border-u-signal/35 bg-u-signal-tint", icon: "text-u-signal", glyph: ICONS.warning },
  info: { box: "border-u-accent/35 bg-u-accent-tint", icon: "text-u-accent", glyph: ICONS.info },
};

function Banner({
  billing,
  isAdmin,
  onBuy,
  onPlans,
}: {
  billing: Billing;
  isAdmin: boolean;
  onBuy: () => void;
  onPlans: () => void;
}) {
  const banner = billingBannerOf(billing, isAdmin);
  if (!banner) return null;
  const tone = BANNER_TONES[banner.tone];

  return (
    <div role="status" className={cn("mb-3 flex gap-2.5 rounded-[10px] border px-3.5 py-3", tone.box)}>
      <Icon d={tone.glyph} className={cn("mt-px flex-none", tone.icon)} />
      <div className="min-w-0 flex-1">
        <div className="text-[13px] font-semibold">{banner.title}</div>
        <div className="mt-0.5 font-mono text-xs/[1.5] text-u-text2">{banner.body}</div>
      </div>
      {banner.action && (
        <BuyControl option={banner.action} onBuy={onBuy} onPlans={onPlans} small className="flex-none self-center" />
      )}
    </div>
  );
}

function PlanCard({ billing, isAdmin, onPlans }: { billing: Billing; isAdmin: boolean; onPlans: () => void }) {
  const { plan, seats, seatPriceFils, interval, status, renewsAt, credits } = billing;
  const trial = trialOf(billing);
  const seatLine = trial
    ? `${seats} staff ${seats === 1 ? "seat" : "seats"}${trial.ended ? "" : " · free while the trial lasts"}`
    : [
        `${seats} staff ${seats === 1 ? "seat" : "seats"}`,
        seatPriceFils === null ? "Agreed price" : `${formatAed(seatPriceFils)} per seat`,
        ...(seatPriceFils !== null && interval ? [interval === "ANNUAL" ? "billed yearly" : "billed monthly"] : []),
      ].join(" · ");
  const renewLine = trial
    ? `${trial.ended ? "Trial ended" : "Trial ends"} ${formatBillingDate(trial.endsAt)}`
    : status === "PAST_DUE"
      ? "Payment due"
      : status === "INVOICED"
        ? "Invoiced monthly by Uncava"
        : renewsAt
          ? `Renews ${formatBillingDate(renewsAt)}`
          : null;
  const planOption = planOptionOf(billing, isAdmin);
  const includes = [
    "Search and AI included",
    ...(credits.monthly > 0
      ? [`${formatNumber(credits.monthly)} contact credits ${trial ? "for the trial" : "a month"}`]
      : []),
    "Client contacts free",
  ];

  return (
    <section aria-label="Plan" className="rounded-xl border border-u-border bg-u-raised p-5">
      <div className="flex flex-wrap items-start gap-4">
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-2">
            <span className="text-xl font-semibold">{plan?.name}</span>
            {trial && (
              <span className="rounded-full bg-u-accent-tint px-2 py-0.5 font-mono text-[10px] font-semibold uppercase tracking-[0.06em] text-u-accent">
                Trial
              </span>
            )}
            {status === "PAST_DUE" && (
              <span className="rounded-full bg-u-offlimits-tint px-2 py-0.5 font-mono text-[10px] font-semibold uppercase tracking-[0.06em] text-u-offlimits">
                Payment due
              </span>
            )}
          </div>
          <div className="mt-1 text-[13px] text-u-text2">{seatLine}</div>
          {renewLine && <div className="mt-0.5 font-mono text-xs text-u-text3">{renewLine}</div>}
        </div>
        {seatPriceFils !== null && !trial && (
          <div className="text-right">
            <div className="font-u-num text-xl font-medium">{formatAed(seatPriceFils * seats)}</div>
            <div className="font-mono text-[11.5px] text-u-text3">a month, before VAT</div>
          </div>
        )}
      </div>
      <div className="mt-3.5 flex flex-wrap items-center gap-x-[18px] gap-y-2 border-t border-u-border pt-3.5">
        {includes.map((line) => (
          <span key={line} className="inline-flex items-center gap-1.5 text-[12.5px] text-u-text2">
            <Icon d={ICONS.check} size={14} className="text-u-direct" />
            {line}
          </span>
        ))}
        {isAdmin && (
          <span className="ml-auto flex gap-2">
            {!trial?.ended && (
              <Link to="/settings/members" className={SECONDARY_SMALL}>
                Add seats
              </Link>
            )}
            {planOption && <PlanControl option={planOption} onPlans={onPlans} />}
          </span>
        )}
      </div>
    </section>
  );
}

function CreditMeter({
  billing,
  isAdmin,
  onBuy,
  onPlans,
}: {
  billing: Billing;
  isAdmin: boolean;
  onBuy: () => void;
  onPlans: () => void;
}) {
  const { credits, prices } = billing;
  const trial = trialOf(billing);
  const total = credits.monthly + credits.bought + credits.given;
  const out = credits.level === "OUT";
  const warn = credits.level === "EIGHTY" || credits.level === "NINETY";
  const share = total > 0 ? Math.min(100, (credits.left / total) * 100) : 0;
  const buy = out || warn ? buyOptionOf(billing, isAdmin) : null;

  return (
    <section
      aria-label="Contact credits"
      className={cn(
        "mt-3 rounded-xl border bg-u-raised p-5",
        out ? "border-u-offlimits/45" : warn ? "border-u-signal/45" : "border-u-border",
      )}
    >
      <div className="flex flex-wrap items-baseline gap-x-2.5">
        <span className="text-sm font-semibold">Contact credits</span>
        <span className="font-mono text-xs text-u-text3">
          Email found {prices.email} · Phone found {prices.phone}
        </span>
      </div>
      <div className="mt-2.5 flex items-baseline gap-2">
        <span className={cn("font-u-num text-[30px] font-medium", out ? "text-u-offlimits" : warn ? "text-u-signal" : "text-u-text")}>
          {formatNumber(credits.left)}
        </span>
        <span className="font-mono text-[13px] text-u-text3">
          of {formatNumber(total)} left {trial ? "in your trial" : "this month"}
        </span>
      </div>
      <div
        role="meter"
        aria-label="Contact credits left"
        aria-valuemin={0}
        aria-valuemax={total}
        aria-valuenow={credits.left}
        className="relative mt-2.5 h-2 overflow-hidden rounded bg-u-sunken"
      >
        <span
          className={cn("absolute inset-y-0 left-0 rounded", out ? "bg-u-offlimits" : warn ? "bg-u-signal" : "bg-u-direct")}
          style={{ width: `${share}%` }}
        />
      </div>
      <div className="mt-2 flex flex-wrap gap-x-4 gap-y-1 font-mono text-xs text-u-text3">
        <span>
          {trial
            ? trial.ended
              ? `Trial credits lapsed when the trial ended on ${formatResetDate(trial.endsAt)}`
              : `Trial credits lapse when the trial ends on ${formatResetDate(trial.endsAt)}`
            : credits.monthly > 0
              ? `Resets to ${formatNumber(credits.monthly)} on ${formatResetDate(credits.resetsAt)} · unused credits don't carry over`
              : `The month resets on ${formatResetDate(credits.resetsAt)}`}
        </span>
        {credits.bought > 0 && <span>Includes {formatNumber(credits.bought)} bought credits</span>}
        {credits.given > 0 && <span>Includes {formatNumber(credits.given)} credits from Uncava</span>}
      </div>
      {buy && <BuyControl option={buy} onBuy={onBuy} onPlans={onPlans} className="mt-3.5" />}
    </section>
  );
}

function UsedThisMonth({ members, failed }: { members: MemberCreditSpend[] | undefined; failed: boolean }) {
  const { user } = useAuth();

  return (
    <section aria-label="Used this month" className="mt-5">
      <h3 className="mb-2 font-mono text-[10px] font-semibold uppercase tracking-[0.14em] text-u-text3">
        Used this month
      </h3>
      <div className="overflow-hidden rounded-[10px] border border-u-border bg-u-raised">
        <div className="grid grid-cols-[1.8fr_1fr_1fr_1fr] gap-3 px-4 py-[9px] font-mono text-[10px] font-semibold uppercase tracking-[0.1em] text-u-text3">
          <span>Member</span>
          <span className="text-right">Emails</span>
          <span className="text-right">Phones</span>
          <span className="text-right">Credits</span>
        </div>
        {failed ? (
          <p className="border-t border-u-border px-4 py-3 text-[13px] text-u-text3">This month's use could not be loaded.</p>
        ) : !members ? (
          <p className="border-t border-u-border px-4 py-3 text-[13px] text-u-text3">Loading…</p>
        ) : members.length === 0 ? (
          <p className="border-t border-u-border px-4 py-3 text-[13px] text-u-text3">No contact credits used yet this month.</p>
        ) : (
          members.map((member) => {
            const name = member.name ?? "Former member";
            return (
              <div
                key={member.userId ?? "nobody"}
                className="grid grid-cols-[1.8fr_1fr_1fr_1fr] items-center gap-3 border-t border-u-border px-4 py-2.5"
              >
                <span className="flex min-w-0 items-center gap-2.5">
                  <Avatar id={member.userId ?? name} name={name} size="sm" />
                  <span className="truncate text-[13px] font-medium">
                    {name}
                    {member.userId === user?.id && " (you)"}
                  </span>
                </span>
                <span className="text-right font-u-num text-[13px] text-u-text2">{formatNumber(member.emailsFound)}</span>
                <span className="text-right font-u-num text-[13px] text-u-text2">{formatNumber(member.phonesFound)}</span>
                <span className="text-right font-u-num text-[13px] font-medium">{formatNumber(member.creditsSpent)}</span>
              </div>
            );
          })
        )}
      </div>
      <p className="mx-0.5 mt-2.5 font-mono text-[11.5px]/[1.55] text-u-text3">
        A credit is spent only when an email or phone is found. A lookup that finds nothing, or a contact already on
        file, is free.
      </p>
    </section>
  );
}

function NoPlan({ billing, isAdmin, onPlans }: { billing: Billing; isAdmin: boolean; onPlans: () => void }) {
  const planOption = planOptionOf(billing, isAdmin);
  return (
    <section
      aria-label="No plan yet"
      className="rounded-xl border border-dashed border-u-border-strong bg-u-raised px-6 py-7 text-center"
    >
      <div className="text-[15px] font-semibold">Choose a plan</div>
      <div className="mx-auto mt-1.5 max-w-[440px] font-mono text-xs/[1.55] text-u-text3">
        {!isAdmin
          ? "An admin hasn't chosen a plan yet."
          : planOption?.kind === "plans"
            ? "Pick Core, Pro or Enterprise. Every plan includes search and AI; the plan sets how many contact credits your team gets each month."
            : `Every plan includes search and AI; the plan sets how many contact credits your team gets each month. Write to ${BILLING_CONTACT_EMAIL} to choose one.`}
      </div>
      {planOption?.kind === "plans" && (
        <Button type="button" onClick={onPlans} className="mx-auto mt-4 inline-flex">
          See plans
        </Button>
      )}
    </section>
  );
}

function PaymentRow({ billing, isAdmin }: { billing: Billing; isAdmin: boolean }) {
  const portal = useStripeRedirect(() => billingApi.openPortal());
  const { kind } = billing.paymentMethod;
  const card = useQuery({
    queryKey: billingApi.BILLING_CARD_KEY,
    queryFn: ({ signal }) => billingApi.getBillingCard(signal),
    enabled: paysByCard(billing),
  }).data;
  const pastDue = billing.status === "PAST_DUE";
  const { title, sub } =
    kind === "CARD"
      ? {
          title: card?.brand && card.last4 ? `${cardBrandLabel(card.brand)} •••• ${card.last4}` : "Paid by card",
          sub: pastDue ? "The last payment failed — update the card" : null,
        }
      : kind === "INVOICED"
        ? { title: "Paid by bank transfer", sub: `Invoices come from ${BILLING_CONTACT_EMAIL}` }
        : { title: "No card yet", sub: "Added when you choose a plan" };

  return (
    <section
      aria-label="Payment and invoices"
      className="mt-5 flex flex-wrap items-center gap-3 rounded-xl border border-u-border bg-u-raised px-5 py-4"
    >
      <Icon d={ICONS.card} className="text-u-text2" />
      <div className="min-w-0 flex-1">
        <div className="text-[13px] font-medium">{title}</div>
        {sub && <div className={cn("mt-0.5 font-mono text-xs", pastDue ? "text-u-offlimits" : "text-u-text3")}>{sub}</div>}
      </div>
      {isAdmin && paysByCard(billing) && (
        <button
          type="button"
          disabled={portal.isPending}
          onClick={() => portal.mutate(undefined)}
          className={cn(SECONDARY_SMALL, "gap-1.5 disabled:opacity-60")}
        >
          Invoices &amp; card
          <Icon d={ICONS.externalLink} size={12} />
        </button>
      )}
    </section>
  );
}

const SECONDARY_SMALL =
  "inline-flex items-center rounded-[6px] border border-u-border-strong bg-u-surface px-3 py-1.5 text-[12.5px] font-medium text-u-text2 transition hover:border-u-text3 hover:text-u-text";

/** Change plan opens the plans dialog where Stripe takes payment, and writes to Uncava where it does not. */
function PlanControl({ option, onPlans }: { option: PlanOption; onPlans: () => void }) {
  if (option.kind === "contact") {
    return (
      <a href={option.href} className={SECONDARY_SMALL}>
        {option.label}
      </a>
    );
  }
  return (
    <button type="button" onClick={onPlans} className={SECONDARY_SMALL}>
      {option.label}
    </button>
  );
}

/** Buy more credits opens the packs dialog on a card account, a trial's the plans; an invoiced one writes to Uncava. */
function BuyControl({
  option,
  onBuy,
  onPlans,
  small = false,
  className,
}: {
  option: BuyOption;
  onBuy: () => void;
  onPlans: () => void;
  small?: boolean;
  className?: string;
}) {
  const size = small ? "px-3 py-1.5 text-[12.5px]" : "px-3.5 py-2 text-[13px]";
  if (option.kind === "contact") {
    return (
      <a
        href={option.href}
        className={cn(
          "inline-flex items-center justify-center rounded-[6px] border border-u-accent-solid bg-u-accent-solid font-medium text-white transition hover:border-u-accent-solid-hover hover:bg-u-accent-solid-hover",
          size,
          className,
        )}
      >
        {option.label}
      </a>
    );
  }
  return (
    <Button type="button" onClick={option.kind === "plans" ? onPlans : onBuy} className={cn("inline-flex", size, className)}>
      {option.label}
    </Button>
  );
}
