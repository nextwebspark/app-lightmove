import { describe, expect, it } from "vitest";
import { ApiRequestError } from "../../../lib/apiClient";
import { aBilling, aCardBilling, anInvoicedBilling, PLAN_OFFERS, someCredits } from "../test/fixtures";
import {
  billingBannerOf,
  billingRefusalOf,
  buyOptionOf,
  creditChipOf,
  formatAed,
  planChoiceOf,
  planOptionOf,
  withVat,
} from "./billingView";

describe("creditChipOf", () => {
  it("shows nothing below 80% used", () => {
    expect(creditChipOf(someCredits({ level: "OK" }))).toBeNull();
  });

  it("names what is left at 80% and 90%", () => {
    expect(creditChipOf(someCredits({ level: "EIGHTY", left: 1350 }))).toEqual({
      tone: "warn",
      label: "1,350 contact credits left",
    });
    expect(creditChipOf(someCredits({ level: "NINETY", left: 60 }))?.tone).toBe("warn");
  });

  it("says used up once nothing is left", () => {
    expect(creditChipOf(someCredits({ level: "OUT", left: 0 }))).toEqual({ tone: "out", label: "Out of contact credits" });
  });
});

describe("buyOptionOf", () => {
  it("offers a member nothing", () => {
    expect(buyOptionOf(aBilling(), false)).toBeNull();
  });

  it("offers an admin on a card Buy more credits once Stripe sells packs", () => {
    expect(buyOptionOf(aCardBilling(), true)).toEqual({ kind: "buy", label: "Buy more credits" });
  });

  it("sends an admin to Uncava where Stripe is not offered", () => {
    expect(buyOptionOf(aBilling(), true)).toMatchObject({ kind: "contact", label: "Contact Uncava" });
  });

  it("sends an invoiced admin to Uncava", () => {
    const option = buyOptionOf(aBilling({ status: "INVOICED", paymentMethod: { kind: "INVOICED", brand: null, last4: null } }), true);
    expect(option).toMatchObject({ kind: "contact", label: "Contact Uncava" });
    expect(option?.kind === "contact" && option.href).toMatch(/^mailto:billing@uncava\.com/);
  });
});

describe("planOptionOf", () => {
  it("offers a member nothing", () => {
    expect(planOptionOf(aCardBilling(), false)).toBeNull();
  });

  it("opens the plans dialog where Stripe takes payment", () => {
    expect(planOptionOf(aCardBilling(), true)).toEqual({ kind: "plans", label: "Change plan" });
    expect(planOptionOf(aCardBilling({ plan: null, status: null }), true)).toEqual({ kind: "plans", label: "See plans" });
  });

  it("writes to Uncava from an invoiced workspace or where Stripe is not offered", () => {
    expect(planOptionOf(anInvoicedBilling(), true)).toMatchObject({ kind: "contact", label: "Contact Uncava" });
    expect(planOptionOf(aBilling(), true)).toMatchObject({ kind: "contact", label: "Contact Uncava" });
  });
});

describe("planChoiceOf", () => {
  const [core, pro, enterprise] = PLAN_OFFERS;

  it("marks the plan and period in force, and sends any other move to the portal", () => {
    const billing = aCardBilling();
    expect(planChoiceOf(billing, pro, "MONTHLY")).toEqual({ kind: "current", label: "Current plan" });
    expect(planChoiceOf(billing, pro, "ANNUAL")).toEqual({ kind: "portal", label: "Switch to yearly" });
    expect(planChoiceOf(billing, core, "MONTHLY")).toEqual({ kind: "portal", label: "Switch to Core" });
  });

  it("sends a workspace with no card to Checkout, only in a period Stripe sells", () => {
    const billing = aCardBilling({ plan: null, status: null, paymentMethod: { kind: "NONE", brand: null, last4: null } });
    expect(planChoiceOf(billing, core, "ANNUAL")).toEqual({ kind: "checkout", label: "Choose Core", available: true });
    expect(planChoiceOf(billing, { ...core, checkoutIntervals: ["MONTHLY"] }, "ANNUAL")).toMatchObject({ available: false });
  });

  it("asks Enterprise to talk to us", () => {
    expect(planChoiceOf(aCardBilling(), enterprise, "MONTHLY")).toMatchObject({ kind: "talk", label: "Talk to us" });
  });
});

describe("withVat", () => {
  it("adds 5% to a price in fils", () => {
    expect(withVat(15_000)).toBe(15_750);
  });
});

describe("billingBannerOf", () => {
  it("draws no banner below 80% on a card", () => {
    expect(billingBannerOf(aBilling(), true)).toBeNull();
  });

  it("warns at 80% with what is left and when it resets", () => {
    const banner = billingBannerOf(aBilling({ credits: someCredits({ level: "EIGHTY", usedPercent: 82, left: 135 }) }), true);
    expect(banner).toMatchObject({ tone: "warn", title: "82% of this month's contact credits used" });
    expect(banner?.body).toBe("135 left until they reset on 1 Nov.");
  });

  it("says used up, and tells a member an admin adds more", () => {
    const banner = billingBannerOf(aBilling({ credits: someCredits({ level: "OUT", left: 0 }) }), false);
    expect(banner).toMatchObject({ tone: "red", action: null });
    expect(banner?.body).toContain("an admin adds more");
  });

  it("puts a failed payment before the credits", () => {
    const banner = billingBannerOf(aBilling({ status: "PAST_DUE", credits: someCredits({ level: "OUT" }) }), true);
    expect(banner?.title).toBe("We couldn't take this month's payment");
  });

  it("says an invoiced workspace is paid by invoice", () => {
    expect(billingBannerOf(aBilling({ status: "INVOICED" }), true)).toMatchObject({ tone: "info", title: "Paid by invoice" });
  });
});

describe("billingRefusalOf", () => {
  const refused = (problem: Record<string, unknown>) =>
    new ApiRequestError({ detail: "", status: 402, correlationId: "x", code: "", ...problem });

  it("reads an out-of-credits refusal", () => {
    expect(billingRefusalOf(refused({ code: "INSUFFICIENT_CREDITS", required: 5, available: 2 }))).toEqual({
      kind: "credits",
      required: 5,
      available: 2,
      resetsAt: null,
    });
  });

  it("reads a fair-use refusal and which use reached it", () => {
    expect(
      billingRefusalOf(refused({ code: "FAIR_USE_REACHED", kind: "PEOPLE_SEARCH_PAGE", resetsAt: "2026-11-01T00:00:00Z" })),
    ).toEqual({ kind: "fairUse", use: "PEOPLE_SEARCH_PAGE", resetsAt: "2026-11-01T00:00:00Z" });
  });

  it("leaves every other refusal to its caller", () => {
    expect(billingRefusalOf(refused({ code: "RATE_LIMITED", status: 429 }))).toBeNull();
    expect(billingRefusalOf(new Error("network"))).toBeNull();
  });
});

describe("formatAed", () => {
  it("drops fils only where there are none", () => {
    expect(formatAed(49_900)).toBe("AED 499");
    expect(formatAed(249_500 * 5)).toBe("AED 12,475");
    expect(formatAed(49_950)).toBe("AED 499.50");
  });
});
