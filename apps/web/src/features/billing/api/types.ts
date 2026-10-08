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

/** `GET /billing`. The plan's fields are null for a workspace with no subscription. */
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
  paymentMethod: { kind: PaymentMethodKind; brand: string | null; last4: string | null };
  stripeOffered: boolean;
}

/** One member's captured finds this billing month; a spend no member made has a null `userId`. */
export interface MemberCreditSpend {
  userId: string | null;
  name: string | null;
  emailsFound: number;
  phonesFound: number;
  creditsSpent: number;
}

/** `GET /billing/usage`. */
export interface BillingUsage {
  periodStart: string;
  periodEnd: string;
  members: MemberCreditSpend[];
}
