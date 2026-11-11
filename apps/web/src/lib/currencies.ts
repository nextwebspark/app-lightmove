import { useQuery } from "@tanstack/react-query";
import { request } from "./apiClient";

/**
 * The currencies an executive package in this market is most often quoted in: the GCC six, then the three
 * reference currencies a multinational's grade table is written in. Every picker lists them first, as
 * Suggested, ahead of every other currency the server offers.
 */
export const CURRENCIES = ["AED", "SAR", "QAR", "KWD", "BHD", "OMR", "USD", "GBP", "EUR"] as const;

export type Currency = (typeof CURRENCIES)[number];

/** What the picker spells beside each suggested code before the served names arrive — "AED - UAE Dirham". */
export const CURRENCY_NAMES: Record<Currency, string> = {
  AED: "UAE Dirham",
  SAR: "Saudi Riyal",
  QAR: "Qatari Riyal",
  KWD: "Kuwaiti Dinar",
  BHD: "Bahraini Dinar",
  OMR: "Omani Rial",
  USD: "US Dollar",
  GBP: "British Pound",
  EUR: "Euro",
};

/** A code as the picker lists it; one no list carries is shown as the bare code it is. */
export function currencyOptionLabel(code: string, servedName?: string): string {
  const name = (CURRENCY_NAMES as Record<string, string | undefined>)[code] ?? servedName;
  return name ? `${code} - ${name}` : code;
}

/**
 * What anything starts in until the workspace's own currency is read; the server seeds that from the firm's
 * country and an admin changes it in Settings → General.
 */
export const DEFAULT_CURRENCY: Currency = "AED";

export interface OfferedCurrency {
  code: string;
  name: string;
}

interface CurrenciesResponse {
  currencies: OfferedCurrency[];
}

const CURRENCIES_KEY = ["currencies"] as const;
const NO_CURRENCIES: OfferedCurrency[] = [];

/**
 * Every currency some country uses today, as the server validates them — served rather than mirrored so a
 * picker never offers a code the API refuses. Until it lands, or where it is refused, the Suggested nine stand.
 */
export function useCurrencies(): OfferedCurrency[] {
  const currencies = useQuery({
    queryKey: CURRENCIES_KEY,
    queryFn: () => request<CurrenciesResponse>("/currencies"),
    staleTime: Infinity,
  });
  return currencies.data?.currencies ?? NO_CURRENCIES;
}

export interface CurrencyGroups {
  suggested: string[];
  others: OfferedCurrency[];
}

/**
 * The picker's two groups: the codes already in play (a stored one, the brief's, the workspace's) then the
 * suggested nine, and below them every other served currency by code — each code listed once.
 */
export function currencyGroupsOf(
  held: ReadonlyArray<string | null | undefined>,
  served: ReadonlyArray<OfferedCurrency>,
): CurrencyGroups {
  const suggested = [...new Set([...held.filter((code): code is string => Boolean(code)), ...CURRENCIES])];
  const listed = new Set(suggested);
  return { suggested, others: served.filter((currency) => !listed.has(currency.code)) };
}
