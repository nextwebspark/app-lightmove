import { ApiRequestError } from "../../../lib/apiClient";
import { formatNumber } from "../../../lib/format";
import type { Billing, ContactCredits, FairUseKind } from "../api/types";

/** Where an invoiced workspace writes to change its plan or add credits, and where anyone asks for more fair use. */
export const BILLING_CONTACT_EMAIL = "billing@uncava.com";

export type CreditTone = "warn" | "out";

/** The topbar chip: nothing below 80% used, then what is left, then used up. */
export function creditChipOf(credits: ContactCredits): { tone: CreditTone; label: string } | null {
  switch (credits.level) {
    case "OK":
      return null;
    case "OUT":
      return { tone: "out", label: "Out of contact credits" };
    default:
      return { tone: "warn", label: `${formatNumber(credits.left)} contact credits left` };
  }
}

/**
 * How an admin gets more credits: Stripe's checkout once it is offered on a card account (disabled until then), or
 * a word to Uncava on an invoiced one. A member is offered neither — only an admin buys.
 */
export type BuyOption =
  | { kind: "buy"; label: string; disabled: boolean }
  | { kind: "contact"; label: string; href: string };

export function buyOptionOf(billing: Billing, isAdmin: boolean): BuyOption | null {
  if (!isAdmin || !billing.plan) return null;
  if (billing.paymentMethod.kind === "CARD") {
    return { kind: "buy", label: "Buy more credits", disabled: !billing.stripeOffered };
  }
  return { kind: "contact", label: "Contact Uncava", href: mailtoBilling("More contact credits") };
}

export type BannerTone = "red" | "warn" | "info";

export interface BillingBanner {
  tone: BannerTone;
  title: string;
  body: string;
  action: BuyOption | null;
}

/** The one banner the page draws, most pressing first: a failed payment, credits used up, 80/90%, then invoiced. */
export function billingBannerOf(billing: Billing, isAdmin: boolean): BillingBanner | null {
  const { credits } = billing;
  const resets = formatResetDate(credits.resetsAt);
  if (billing.status === "PAST_DUE") {
    return {
      tone: "red",
      title: "We couldn't take this month's payment",
      body: `The card will be tried again. Everything keeps working until then.${isAdmin ? "" : " An admin can update the card."}`,
      action: null,
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

/** A refusal one of the two billing sheets answers, read off the problem the server sent. */
export type BillingRefusal =
  | { kind: "credits"; required: number | null; available: number | null; resetsAt: string | null }
  | { kind: "fairUse"; use: FairUseKind | null; resetsAt: string | null };

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
