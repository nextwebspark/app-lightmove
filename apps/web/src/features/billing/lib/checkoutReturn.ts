import type { Billing } from "../api/types";

/** Where Stripe sends the admin back to: `?checkout=` on Settings → Billing, as the API builds its return URLs. */
export type CheckoutReturn = "subscribed" | "credits" | "cancelled";

const STASH_KEY = "lm-billing-checkout";

/** How long the page waits for Stripe's webhook before it stops asking and says so. */
export const CHECKOUT_WAIT_MS = 60_000;
export const CHECKOUT_POLL_MS = 2_000;

export function checkoutReturnOf(param: string | null): CheckoutReturn | null {
  return param === "subscribed" || param === "credits" || param === "cancelled" ? param : null;
}

/** Sends the admin to a Stripe page, behind one seam a test can replace. */
export function goToStripe(url: string) {
  window.location.assign(url);
}

/** The bought credits before Checkout, so the return can tell the webhook's grant from what was already there. */
export function rememberBoughtBefore(bought: number) {
  try {
    sessionStorage.setItem(STASH_KEY, String(bought));
  } catch {
    // Private windows refuse storage; the return then waits on any bought credits at all.
  }
}

/** Read, never removed: React runs a mount effect twice in development, and the second read must agree. */
export function boughtBeforeCheckout(): number | null {
  try {
    const stored = sessionStorage.getItem(STASH_KEY);
    return stored === null ? null : Number(stored);
  } catch {
    return null;
  }
}

/** Whether what Stripe was paid for has reached the billing read: the webhook may land after the admin does. */
export function hasLanded(kind: Exclude<CheckoutReturn, "cancelled">, billing: Billing, boughtBefore: number | null) {
  if (kind === "subscribed") return billing.paymentMethod.kind === "CARD";
  return billing.credits.bought > (boughtBefore ?? 0);
}
