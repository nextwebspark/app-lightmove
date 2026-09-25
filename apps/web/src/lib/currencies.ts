/**
 * The currencies an executive package in this market is quoted in: the GCC six, then the three
 * reference currencies a multinational's grade table is written in. Three-letter ISO codes, which is
 * also the only shape the candidate API stores.
 */
export const CURRENCIES = ["AED", "SAR", "QAR", "KWD", "BHD", "OMR", "USD", "GBP", "EUR"] as const;

export type Currency = (typeof CURRENCIES)[number];

/** What the picker spells beside each code — "AED - UAE Dirham". */
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

/** A code as the picker lists it; one the list does not carry is shown as the bare code it is. */
export function currencyOptionLabel(code: string): string {
  const name = (CURRENCY_NAMES as Record<string, string | undefined>)[code];
  return name ? `${code} - ${name}` : code;
}

/** What a workspace, a brief, a template and an executive's package start in until somebody picks another. */
export const DEFAULT_CURRENCY: Currency = "AED";
