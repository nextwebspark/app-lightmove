import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { PageHeader } from "../../../components/layout/PageHeader";
import { Avatar, Button } from "../../../components/ui";
import { cn } from "../../../lib/cn";
import { messageFor } from "../../../lib/errorCodes";
import { formatNumber } from "../../../lib/format";
import { useAuth } from "../../auth/AuthProvider";
import * as billingApi from "../api/billingApi";
import type { Billing, MemberCreditSpend } from "../api/types";
import {
  BILLING_CONTACT_EMAIL,
  billingBannerOf,
  buyOptionOf,
  formatAed,
  formatBillingDate,
  formatResetDate,
  type BannerTone,
  type BuyOption,
} from "../lib/billingView";
import { useBilling, useIsWorkspaceAdmin } from "../lib/useBilling";

/**
 * Settings → Billing (`Billing.dc.html`): the plan, this month's contact credits, who spent them, and how the
 * workspace pays. Every staff member reads it; only an admin is offered seats or more credits. Quiet until 80% of
 * the month's credits are used.
 */
export function SettingsBillingPage() {
  const isAdmin = useIsWorkspaceAdmin();
  const billing = useBilling();
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
          <Banner billing={billing.data} isAdmin={isAdmin} />
          {billing.data.plan ? (
            <>
              <PlanCard billing={billing.data} isAdmin={isAdmin} />
              <CreditMeter billing={billing.data} isAdmin={isAdmin} />
              <UsedThisMonth members={usage.data?.members} failed={usage.isError} />
            </>
          ) : (
            <NoPlan isAdmin={isAdmin} />
          )}
          <PaymentRow billing={billing.data} />
        </>
      )}
    </>
  );
}

const BANNER_TONES: Record<BannerTone, { box: string; icon: string; glyph: string }> = {
  red: { box: "border-u-offlimits/35 bg-u-offlimits-tint", icon: "text-u-offlimits", glyph: ICONS.warning },
  warn: { box: "border-u-signal/35 bg-u-signal-tint", icon: "text-u-signal", glyph: ICONS.warning },
  info: { box: "border-u-accent/35 bg-u-accent-tint", icon: "text-u-accent", glyph: ICONS.info },
};

function Banner({ billing, isAdmin }: { billing: Billing; isAdmin: boolean }) {
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
      {banner.action && <BuyControl option={banner.action} small className="flex-none self-center" />}
    </div>
  );
}

function PlanCard({ billing, isAdmin }: { billing: Billing; isAdmin: boolean }) {
  const { plan, seats, seatPriceFils, interval, status, renewsAt, credits } = billing;
  const seatLine = [
    `${seats} staff ${seats === 1 ? "seat" : "seats"}`,
    seatPriceFils === null ? "Agreed price" : `${formatAed(seatPriceFils)} per seat`,
    ...(seatPriceFils !== null && interval ? [interval === "ANNUAL" ? "billed yearly" : "billed monthly"] : []),
  ].join(" · ");
  const renewLine =
    status === "PAST_DUE"
      ? "Payment due"
      : status === "INVOICED"
        ? "Invoiced monthly by Uncava"
        : renewsAt
          ? `Renews ${formatBillingDate(renewsAt)}`
          : null;
  const includes = [
    "Search and AI included",
    ...(credits.monthly > 0 ? [`${formatNumber(credits.monthly)} contact credits a month`] : []),
    "Client contacts free",
  ];

  return (
    <section aria-label="Plan" className="rounded-xl border border-u-border bg-u-raised p-5">
      <div className="flex flex-wrap items-start gap-4">
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-2">
            <span className="text-xl font-semibold">{plan?.name}</span>
            {status === "PAST_DUE" && (
              <span className="rounded-full bg-u-offlimits-tint px-2 py-0.5 font-mono text-[10px] font-semibold uppercase tracking-[0.06em] text-u-offlimits">
                Payment due
              </span>
            )}
          </div>
          <div className="mt-1 text-[13px] text-u-text2">{seatLine}</div>
          {renewLine && <div className="mt-0.5 font-mono text-xs text-u-text3">{renewLine}</div>}
        </div>
        {seatPriceFils !== null && (
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
          <Link
            to="/settings/members"
            className="ml-auto inline-flex items-center rounded-[6px] border border-u-border-strong bg-u-surface px-3 py-1.5 text-[12.5px] font-medium text-u-text2 transition hover:border-u-text3 hover:text-u-text"
          >
            Add seats
          </Link>
        )}
      </div>
    </section>
  );
}

function CreditMeter({ billing, isAdmin }: { billing: Billing; isAdmin: boolean }) {
  const { credits, prices } = billing;
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
        <span className="font-mono text-[13px] text-u-text3">of {formatNumber(total)} left this month</span>
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
          {credits.monthly > 0
            ? `Resets to ${formatNumber(credits.monthly)} on ${formatResetDate(credits.resetsAt)} · unused credits don't carry over`
            : `The month resets on ${formatResetDate(credits.resetsAt)}`}
        </span>
        {credits.bought > 0 && <span>Includes {formatNumber(credits.bought)} bought credits</span>}
        {credits.given > 0 && <span>Includes {formatNumber(credits.given)} credits from Uncava</span>}
      </div>
      {buy && <BuyControl option={buy} className="mt-3.5" />}
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

function NoPlan({ isAdmin }: { isAdmin: boolean }) {
  return (
    <section
      aria-label="No plan yet"
      className="rounded-xl border border-dashed border-u-border-strong bg-u-raised px-6 py-7 text-center"
    >
      <div className="text-[15px] font-semibold">Choose a plan</div>
      <div className="mx-auto mt-1.5 max-w-[440px] font-mono text-xs/[1.55] text-u-text3">
        {isAdmin
          ? `Every plan includes search and AI; the plan sets how many contact credits your team gets each month. Write to ${BILLING_CONTACT_EMAIL} to choose one.`
          : "An admin hasn't chosen a plan yet."}
      </div>
    </section>
  );
}

function PaymentRow({ billing }: { billing: Billing }) {
  const { kind, brand, last4 } = billing.paymentMethod;
  const pastDue = billing.status === "PAST_DUE";
  const { title, sub } =
    kind === "CARD"
      ? {
          title: brand && last4 ? `${brand} •••• ${last4}` : "Paid by card",
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
    </section>
  );
}

/** Buy more credits, disabled until checkout is offered, or a word to Uncava on an invoiced workspace. */
function BuyControl({ option, small = false, className }: { option: BuyOption; small?: boolean; className?: string }) {
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
    <Button
      type="button"
      disabled={option.disabled}
      title={option.disabled ? "Buying credits is coming soon" : undefined}
      className={cn("inline-flex", size, className)}
    >
      {option.label}
    </Button>
  );
}
