import type { Billing, ContactCredits } from "../api/types";

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
    paymentMethod: { kind: "CARD", brand: null, last4: null },
    stripeOffered: false,
    ...overrides,
  };
}
