import type { Billing, BillingPlanOffer, ContactCredits, CreditPackOffer } from "../api/types";

export function someCredits(overrides: Partial<ContactCredits> = {}): ContactCredits {
  return {
    monthly: 750,
    left: 412,
    bought: 0,
    given: 0,
    usedPercent: 45,
    level: "OK",
    resetsAt: "2026-11-01T00:00:00Z",
    ...overrides,
  };
}

export function aBilling(overrides: Partial<Billing> = {}): Billing {
  return {
    plan: { code: "PRO", name: "Pro" },
    interval: "MONTHLY",
    seats: 5,
    seatPriceFils: 49_900,
    status: "ACTIVE",
    renewsAt: "2026-11-01T00:00:00Z",
    credits: someCredits(),
    prices: { email: 1, phone: 5 },
    paymentMethod: { kind: "CARD" },
    stripeOffered: false,
    plans: [],
    packs: [],
    trialEndsAt: null,
    ...overrides,
  };
}

export const PLAN_OFFERS: BillingPlanOffer[] = [
  { code: "CORE", name: "Core", seatPriceMonthlyFils: 29_900, seatPriceAnnualFils: 23_900, contactCreditsPerSeat: 50, custom: false, checkoutIntervals: ["MONTHLY", "ANNUAL"] },
  { code: "PRO", name: "Pro", seatPriceMonthlyFils: 49_900, seatPriceAnnualFils: 39_900, contactCreditsPerSeat: 150, custom: false, checkoutIntervals: ["MONTHLY", "ANNUAL"] },
  { code: "ENTERPRISE", name: "Enterprise", seatPriceMonthlyFils: null, seatPriceAnnualFils: null, contactCreditsPerSeat: null, custom: true, checkoutIntervals: [] },
];

export const PACK_OFFERS: CreditPackOffer[] = [
  { code: "contact-100", credits: 100, priceFils: 15_000 },
  { code: "contact-500", credits: 500, priceFils: 65_000 },
];

/** A workspace Stripe bills by card, on a deployment that sells plans and packs online. */
export function aCardBilling(overrides: Partial<Billing> = {}): Billing {
  return aBilling({ stripeOffered: true, plans: PLAN_OFFERS, packs: PACK_OFFERS, ...overrides });
}

/** A workspace on the app's own Pro trial, ending `endsAt`, on a deployment that sells plans online. */
export function aTrialBilling(endsAt: string, overrides: Partial<Billing> = {}): Billing {
  return aCardBilling({
    status: "TRIALING",
    seats: 1,
    paymentMethod: { kind: "NONE" },
    renewsAt: endsAt,
    trialEndsAt: endsAt,
    credits: someCredits({ monthly: 50, left: 50, usedPercent: 0, resetsAt: endsAt }),
    ...overrides,
  });
}

/** A workspace Uncava invoices, on the same deployment. */
export function anInvoicedBilling(overrides: Partial<Billing> = {}): Billing {
  return aCardBilling({ status: "INVOICED", paymentMethod: { kind: "INVOICED" }, ...overrides });
}
