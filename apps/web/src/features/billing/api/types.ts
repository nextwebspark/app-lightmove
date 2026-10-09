/** Mirrors the API's `ContactCreditLevel`: how far into the month's plan credits the workspace is. */
export type ContactCreditLevel = "OK" | "EIGHTY" | "NINETY" | "OUT";

export type PlanCode = "CORE" | "PRO" | "ENTERPRISE";
export type BillingInterval = "MONTHLY" | "ANNUAL";
export type SubscriptionStatus = "TRIALING" | "ACTIVE" | "PAST_DUE" | "CANCELLED" | "INVOICED";
export type PaymentMethodKind = "INVOICED" | "CARD" | "NONE";

/** Mirrors the API's `UsageKind`: the uses held to fair use rather than priced. */
export type FairUseKind = "PEOPLE_SEARCH_PAGE" | "SOURCING_RUN" | "AI_ENRICH" | "OUTREACH_OPENER" | "ASSISTANT_ASK";

/**
 * `monthly` is this billing month's plan grant; `left` is everything spendable now, of which `bought` was purchased
 * and `given` granted free by Uncava.
 */
export interface ContactCredits {
  monthly: number;
  left: number;
  bought: number;
  given: number;
  usedPercent: number;
  level: ContactCreditLevel;
  resetsAt: string;
}

/** Contact credits per find. */
export interface CreditPrices {
  email: number;
  phone: number;
}

/**
 * A plan the dialog offers: prices per staff seat a month before VAT, null with the credits on a plan agreed per
 * workspace; `checkoutIntervals` are the ones Stripe sells it in.
 */
export interface BillingPlanOffer {
  code: PlanCode;
  name: string;
  seatPriceMonthlyFils: number | null;
  seatPriceAnnualFils: number | null;
  contactCreditsPerSeat: number | null;
  custom: boolean;
  checkoutIntervals: BillingInterval[];
}

/** A pack of contact credits Checkout sells, priced before VAT. */
export interface CreditPackOffer {
  code: string;
  credits: number;
  priceFils: number;
}

/**
 * `GET /billing`. The plan's fields are null for a workspace with no subscription; `plans` and `packs` are empty where
 * Stripe is not offered, and for anyone but an admin. `trialEndsAt` is set only on the app's own trial, still unpaid, and passed once it has ended.
 */
export interface Billing {
  plan: { code: PlanCode; name: string } | null;
  interval: BillingInterval | null;
  seats: number;
  /** Per staff seat per month, in fils; null on a plan priced per workspace. */
  seatPriceFils: number | null;
  status: SubscriptionStatus | null;
  renewsAt: string | null;
  credits: ContactCredits;
  prices: CreditPrices;
  paymentMethod: { kind: PaymentMethodKind };
  stripeOffered: boolean;
  plans: BillingPlanOffer[];
  packs: CreditPackOffer[];
  trialEndsAt: string | null;
}

/** `GET /billing/card`: the card Stripe charges, asked of Stripe on each read; both null where it cannot say. */
export interface PaymentCard {
  /** Stripe's display brand: `visa`, `mastercard`, `american_express`, … */
  brand: string | null;
  last4: string | null;
}

/** One member's captured finds this billing month; a spend no member made has a null `userId`. */
export interface MemberCreditSpend {
  userId: string | null;
  name: string | null;
  emailsFound: number;
  phonesFound: number;
  creditsSpent: number;
}

/** A Stripe page to send the admin to: Checkout or the Customer Portal. */
export interface BillingRedirect {
  url: string;
}

/** `GET /billing/usage`. */
export interface BillingUsage {
  periodStart: string;
  periodEnd: string;
  members: MemberCreditSpend[];
}
