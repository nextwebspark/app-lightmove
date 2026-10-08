import { request } from "../../../lib/apiClient";
import type { Billing, BillingUsage } from "./types";

/** Under one prefix, so a find that spends credits refreshes the page, the chip and the sheets at once. */
export const BILLING_KEY = ["billing"] as const;
export const BILLING_USAGE_KEY = ["billing", "usage"] as const;

export function getBilling(signal?: AbortSignal): Promise<Billing> {
  return request<Billing>("/billing", { signal });
}

export function getBillingUsage(signal?: AbortSignal): Promise<BillingUsage> {
  return request<BillingUsage>("/billing/usage", { signal });
}
