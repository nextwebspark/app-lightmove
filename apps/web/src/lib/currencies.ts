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

/** A code as it reads in a sentence or a label — "AED - UAE Dirham"; one no list carries is the bare code. */
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

/** The short name where the suggested nine carry one, else the served one; null for a code no list carries. */
export function currencyNameOf(code: string, served: ReadonlyArray<OfferedCurrency>): string | null {
  return (
    (CURRENCY_NAMES as Record<string, string | undefined>)[code] ??
    served.find((currency) => currency.code === code)?.name ??
    null
  );
}

export interface CurrencyChoiceGroup {
  label: "Suggested" | "All currencies";
  currencies: OfferedCurrency[];
}

/**
 * The picker's two groups: the codes already in play then the suggested nine, and below them every other served
 * currency — each code listed once. A search narrows both by code or name, a code match first.
 */
export function currencyChoicesOf(
  held: ReadonlyArray<string | null | undefined>,
  served: ReadonlyArray<OfferedCurrency>,
  search = "",
): CurrencyChoiceGroup[] {
  const suggestedCodes = [...new Set([...held.filter((code): code is string => Boolean(code)), ...CURRENCIES])];
  const listed = new Set(suggestedCodes);
  const suggested = suggestedCodes.map((code) => ({ code, name: currencyNameOf(code, served) ?? "" }));
  const others = served.filter((currency) => !listed.has(currency.code));
  const needle = search.trim().toLowerCase();
  return [
    { label: "Suggested" as const, currencies: rankedFor(needle, suggested, served) },
    { label: "All currencies" as const, currencies: rankedFor(needle, others, served) },
  ].filter((group) => group.currencies.length > 0);
}

function rankedFor(
  needle: string,
  currencies: OfferedCurrency[],
  served: ReadonlyArray<OfferedCurrency>,
): OfferedCurrency[] {
  if (!needle) return currencies;
  const servedName = new Map(served.map((currency) => [currency.code, currency.name.toLowerCase()]));
  return currencies
    .map((currency) => ({ currency, rank: rankOf(needle, currency, servedName.get(currency.code)) }))
    .filter((entry) => entry.rank >= 0)
    .sort((left, right) => left.rank - right.rank)
    .map((entry) => entry.currency);
}

function rankOf(needle: string, currency: OfferedCurrency, servedName?: string): number {
  const code = currency.code.toLowerCase();
  const names = [currency.name.toLowerCase(), servedName ?? ""].filter(Boolean);
  if (code === needle) return 0;
  if (code.startsWith(needle)) return 1;
  if (names.some((name) => name.startsWith(needle))) return 2;
  if (names.some((name) => name.split(/\s+/).some((word) => word.startsWith(needle)))) return 3;
  if (names.some((name) => name.includes(needle))) return 4;
  return -1;
}
