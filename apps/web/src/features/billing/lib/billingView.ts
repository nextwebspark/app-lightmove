import { ApiRequestError } from "../../../lib/apiClient";
import { formatNumber } from "../../../lib/format";
import type { Billing, BillingInterval, BillingPlanOffer, FairUseKind } from "../api/types";

/** Where an invoiced workspace writes to change its plan or add credits, and where anyone asks for more fair use. */
export const BILLING_CONTACT_EMAIL = "billing@uncava.com";

export type CreditTone = "warn" | "out";

const DAY_MS = 86_400_000;

/** The app's own trial, still unpaid: how many days are left, rounded up, and whether it has ended. */
export interface Trial {
  endsAt: string;
  daysLeft: number;
  ended: boolean;
}

export function trialOf(billing: Billing, now: number = Date.now()): Trial | null {
  if (!billing.trialEndsAt) return null;
  const left = new Date(billing.trialEndsAt).getTime() - now;
  return { endsAt: billing.trialEndsAt, daysLeft: Math.max(0, Math.ceil(left / DAY_MS)), ended: left <= 0 };
}

export function daysLeftLabel(days: number): string {
  return days === 1 ? "1 day left" : `${days} days left`;
}

/**
 * The topbar chip: a trial's days left, or its end; otherwise nothing below 80% used, then what is left, then used
 * up. A trial's last three days are a warning.
 */
export function creditChipOf(billing: Billing): CreditChip | null {
  const trial = trialOf(billing);
  if (trial?.ended) return { tone: "out", label: "Trial ended", about: "Trial" };
  const { credits } = billing;
  if (trial && credits.level === "OK") {
    return {
      tone: trial.daysLeft <= 3 ? "warn" : "trial",
      label: `Trial · ${daysLeftLabel(trial.daysLeft)}`,
      about: "Trial",
    };
  }
  switch (credits.level) {
    case "OK":
      return null;
    case "OUT":
      return { tone: "out", label: "Out of contact credits", about: "Contact credits" };
    default:
      return { tone: "warn", label: `${formatNumber(credits.left)} contact credits left`, about: "Contact credits" };
  }
}

export interface CreditChip {
  tone: CreditTone | "trial";
  label: string;
  about: "Trial" | "Contact credits";
}

/**
 * How an admin gets more credits: the Buy more credits dialog on a workspace paying Stripe by card, the plans on a
 * trial, or a word to Uncava wherever Stripe is not offered or the workspace is invoiced. A member is offered neither —
 * only an admin buys.
 */
export type BuyOption =
  | { kind: "buy"; label: string }
  | { kind: "plans"; label: string }
  | { kind: "contact"; label: string; href: string };

export function buyOptionOf(billing: Billing, isAdmin: boolean): BuyOption | null {
  if (!isAdmin || !billing.plan) return null;
  if (billing.trialEndsAt) {
    return billing.stripeOffered && billing.plans.length > 0
      ? { kind: "plans", label: "Choose a plan" }
      : { kind: "contact", label: "Contact Uncava", href: mailtoBilling("Choose a plan") };
  }
  if (paysByCard(billing) && billing.packs.length > 0) {
    return { kind: "buy", label: "Buy more credits" };
  }
  return { kind: "contact", label: "Contact Uncava", href: mailtoBilling("More contact credits") };
}

/** How an admin changes the plan: the plans dialog where Stripe takes payment, or a word to Uncava where it does not. */
export type PlanOption = { kind: "plans"; label: string } | { kind: "contact"; label: string; href: string };

export function planOptionOf(billing: Billing, isAdmin: boolean): PlanOption | null {
  if (!isAdmin) return null;
  if (!billing.stripeOffered || billing.status === "INVOICED" || billing.plans.length === 0) {
    return { kind: "contact", label: "Contact Uncava", href: mailtoBilling(billing.plan ? "Change plan" : "Choose a plan") };
  }
  if (billing.trialEndsAt) return { kind: "plans", label: "Choose a plan" };
  return { kind: "plans", label: hasLivePlan(billing) ? "Change plan" : "See plans" };
}

/** Whether Stripe bills this workspace by card now: its plan is then changed in the Customer Portal. */
export function paysByCard(billing: Billing): boolean {
  return billing.stripeOffered && billing.paymentMethod.kind === "CARD";
}

function hasLivePlan(billing: Billing): boolean {
  return billing.plan !== null && billing.status !== "CANCELLED";
}

/** What a plan card offers: the plan in force, Enterprise's conversation, or a move Stripe takes payment for. */
export type PlanChoice =
  | { kind: "current"; label: string }
  | { kind: "talk"; label: string; href: string }
  | { kind: "checkout"; label: string; available: boolean }
  | { kind: "portal"; label: string };

export function planChoiceOf(billing: Billing, offer: BillingPlanOffer, interval: BillingInterval): PlanChoice {
  if (offer.custom) return { kind: "talk", label: "Talk to us", href: mailtoBilling(`${offer.name} plan`) };
  if (paysByCard(billing)) {
    if (billing.plan?.code !== offer.code) return { kind: "portal", label: `Switch to ${offer.name}` };
    if (billing.interval === interval) return { kind: "current", label: "Current plan" };
    return { kind: "portal", label: interval === "ANNUAL" ? "Switch to yearly" : "Switch to monthly" };
  }
  return { kind: "checkout", label: `Choose ${offer.name}`, available: offer.checkoutIntervals.includes(interval) };
}

/** A staff seat's price a month on the workspace's plan, or null where its price is agreed with Uncava. */
export function seatCostOf(billing: Billing): number | null {
  return billing.plan && billing.status !== "CANCELLED" ? billing.seatPriceFils : null;
}

const CARD_BRANDS: Record<string, string> = {
  american_express: "Amex",
  cartes_bancaires: "Cartes Bancaires",
  diners_club: "Diners Club",
  eftpos_australia: "eftpos",
  jcb: "JCB",
  union_pay: "UnionPay",
};

/** Stripe's display brand → what the card says: "visa" → "Visa", "american_express" → "Amex". */
export function cardBrandLabel(brand: string): string {
  return CARD_BRANDS[brand] ?? brand.charAt(0).toUpperCase() + brand.slice(1).replaceAll("_", " ");
}

/** What one more staff seat costs a workspace Stripe bills by card, said before an admin adds it. */
export interface SeatCharge {
  planName: string;
  /** A seat a month in fils; on a yearly plan, its monthly share of the year. */
  monthlyFils: number;
  annual: boolean;
  /** Stripe's proration were the seat added now, to the whole dirham; null without a period end or once it passed. */
  nowFils: number | null;
  until: string | null;
  seatsAfter: number;
  monthlyTotalFils: number;
}

export function seatChargeOf(billing: Billing, now: number = Date.now()): SeatCharge | null {
  const monthlyFils = paysByCard(billing) ? seatCostOf(billing) : null;
  if (monthlyFils === null || !billing.plan) return null;
  const annual = billing.interval === "ANNUAL";
  const seatsAfter = billing.seats + 1;
  return {
    planName: billing.plan.name,
    monthlyFils,
    annual,
    nowFils: billing.renewsAt ? proratedFils(monthlyFils * (annual ? 12 : 1), billing.renewsAt, annual, now) : null,
    until: billing.renewsAt,
    seatsAfter,
    monthlyTotalFils: monthlyFils * seatsAfter,
  };
}

function proratedFils(periodFils: number, endsAt: string, annual: boolean, now: number): number | null {
  const end = new Date(endsAt);
  const start = periodStartOf(end, annual);
  const left = end.getTime() - now;
  if (left <= 0) return null;
  const share = Math.min(left, end.getTime() - start.getTime()) / (end.getTime() - start.getTime());
  return Math.round((periodFils * share) / 100) * 100;
}

/** The anchor one period before `end`, held to the month's last day as Stripe holds it: Mar 31 → Feb 28. */
function periodStartOf(end: Date, annual: boolean): Date {
  const year = end.getUTCFullYear() - (annual ? 1 : 0);
  const month = end.getUTCMonth() - (annual ? 0 : 1);
  const lastDay = new Date(Date.UTC(year, month + 1, 0)).getUTCDate();
  const start = new Date(end);
  start.setUTCFullYear(year, month, Math.min(end.getUTCDate(), lastDay));
  return start;
}

/** UAE VAT, which Stripe Tax adds at checkout on top of every price shown. */
export const VAT_RATE = 0.05;

export function withVat(fils: number): number {
  return Math.round(fils * (1 + VAT_RATE));
}

export type BannerTone = "red" | "warn" | "info";

export interface BillingBanner {
  tone: BannerTone;
  title: string;
  body: string;
  action: BuyOption | null;
}

/**
 * The one banner the page draws, most pressing first: a failed payment, an ended trial, credits used up, 80/90%, a
 * trial's days left, then invoiced.
 */
export function billingBannerOf(billing: Billing, isAdmin: boolean): BillingBanner | null {
  const { credits } = billing;
  const resets = formatResetDate(credits.resetsAt);
  const trial = trialOf(billing);
  if (billing.status === "PAST_DUE") {
    return {
      tone: "red",
      title: "We couldn't take this month's payment",
      body: `The card will be tried again. Everything keeps working until then.${isAdmin ? "" : " An admin can update the card."}`,
      action: null,
    };
  }
  if (trial?.ended) {
    return {
      tone: "red",
      title: "Your trial has ended",
      body:
        "Everything your team mapped is still here. Finding contacts, search and AI start again once " +
        `${isAdmin ? "you choose" : "an admin chooses"} a plan.`,
      action: buyOptionOf(billing, isAdmin),
    };
  }
  if (trial && credits.level === "OUT") {
    return {
      tone: "red",
      title: "Your trial's contact credits are used up",
      body: `Choose a plan to get a month of credits at once. Search and AI keep working until ${formatResetDate(trial.endsAt)}.`,
      action: buyOptionOf(billing, isAdmin),
    };
  }
  if (billing.plan && credits.level === "OUT") {
    return {
      tone: "red",
      title: "This month's contact credits are used up",
      body:
        `Finding emails and phones is paused until ${isAdmin ? "you add more" : "an admin adds more"} ` +
        `or the credits reset on ${resets}. Everything else keeps working.`,
      action: buyOptionOf(billing, isAdmin),
    };
  }
  if (billing.plan && (credits.level === "EIGHTY" || credits.level === "NINETY")) {
    return {
      tone: "warn",
      title: `${credits.usedPercent}% of this month's contact credits used`,
      body: `${formatNumber(credits.left)} left until they reset on ${resets}.`,
      action: buyOptionOf(billing, isAdmin),
    };
  }
  if (trial) {
    return {
      tone: trial.daysLeft <= 3 ? "warn" : "info",
      title: `${billing.plan?.name ?? "Pro"} trial · ${daysLeftLabel(trial.daysLeft)}`,
      body:
        `Your trial ends on ${formatResetDate(trial.endsAt)}. ` +
        (isAdmin
          ? "Choose a plan any time to keep finding contacts, searching and using AI after that."
          : "An admin can choose a plan to keep everything working after that."),
      action: buyOptionOf(billing, isAdmin),
    };
  }
  if (billing.status === "INVOICED") {
    return {
      tone: "info",
      title: "Paid by invoice",
      body: `Uncava invoices this workspace each month. Write to ${BILLING_CONTACT_EMAIL} to change the plan or add credits.`,
      action: null,
    };
  }
  return null;
}

/** Fils → "AED 499", or "AED 499.50" where there are fils to show. */
export function formatAed(fils: number): string {
  const dirhams = fils / 100;
  return `AED ${dirhams.toLocaleString("en-GB", {
    minimumFractionDigits: Number.isInteger(dirhams) ? 0 : 2,
    maximumFractionDigits: 2,
  })}`;
}

/** An instant → "1 Nov". In UTC, because the billing month is counted in UTC. */
export function formatResetDate(isoInstant: string): string {
  return new Date(isoInstant).toLocaleDateString("en-GB", { day: "numeric", month: "short", timeZone: "UTC" });
}

/** An instant → "1 Nov 2026", in UTC for the same reason. */
export function formatBillingDate(isoInstant: string): string {
  return new Date(isoInstant).toLocaleDateString("en-GB", {
    day: "numeric",
    month: "short",
    year: "numeric",
    timeZone: "UTC",
  });
}

export function mailtoBilling(subject: string): string {
  return `mailto:${BILLING_CONTACT_EMAIL}?subject=${encodeURIComponent(subject)}`;
}

/** What each fair-use kind is called where it is pressed. */
export const FAIR_USE_FEATURES: Record<FairUseKind, string> = {
  PEOPLE_SEARCH_PAGE: "People Search",
  SOURCING_RUN: "Find executives",
  AI_ENRICH: "AI deep enrich",
  OUTREACH_OPENER: "Writing outreach openers",
  ASSISTANT_ASK: "The assistant",
};

/** A refusal one of the billing sheets answers, read off the problem the server sent. */
export type BillingRefusal =
  | { kind: "credits"; required: number | null; available: number | null; resetsAt: string | null }
  | { kind: "fairUse"; use: FairUseKind | null; resetsAt: string | null }
  | { kind: "trialEnded"; endedAt: string | null };

export function billingRefusalOf(error: unknown): BillingRefusal | null {
  if (!(error instanceof ApiRequestError)) return null;
  const { problem } = error;
  if (problem.code === "INSUFFICIENT_CREDITS") {
    return {
      kind: "credits",
      required: problem.required ?? null,
      available: problem.available ?? null,
      resetsAt: problem.resetsAt ?? null,
    };
  }
  if (problem.code === "TRIAL_ENDED") {
    return { kind: "trialEnded", endedAt: problem.trialEndedAt ?? null };
  }
  if (problem.code === "FAIR_USE_REACHED") {
    const use = problem.kind && problem.kind in FAIR_USE_FEATURES ? (problem.kind as FairUseKind) : null;
    return { kind: "fairUse", use, resetsAt: problem.resetsAt ?? null };
  }
  return null;
}

/** Whether a failure is one the billing sheets answer, so its caller says nothing more. */
export function isBillingRefusal(error: unknown): boolean {
  return billingRefusalOf(error) !== null;
}

/** "1 credit" / "5 credits": the price a paid button carries under it. */
export function creditsLabel(credits: number): string {
  return `${formatNumber(credits)} ${credits === 1 ? "credit" : "credits"}`;
}
