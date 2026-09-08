/**
 * The currencies an executive package in this market is quoted in: the GCC six, then the three
 * reference currencies a multinational's grade table is written in. Three-letter ISO codes, which is
 * also the only shape the candidate API stores.
 */
export const CURRENCIES = ["AED", "SAR", "QAR", "KWD", "BHD", "OMR", "USD", "GBP", "EUR"] as const;

export type Currency = (typeof CURRENCIES)[number];
