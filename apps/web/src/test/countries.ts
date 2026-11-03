/**
 * A drop-in for `lib/countries` — every screen with a country control reads it, so three page suites
 * were stubbing it three times and each copy drifted the moment the hook grew a field.
 *
 * Used as `vi.mock("…/lib/countries", () => import("…/test/countries"))`, which is why this exports
 * the hook itself rather than a helper: `vi.mock` is hoisted above every import in the calling file.
 */
export const STUB_MARKETS = [
  "United Arab Emirates",
  "Saudi Arabia",
  "Qatar",
  "Kuwait",
  "Oman",
  "Bahrain",
  "Türkiye",
  "Egypt",
];

const STUB_COUNTRIES = [
  { code: "AE", name: "United Arab Emirates", spellings: ["UAE"] },
  { code: "SA", name: "Saudi Arabia", spellings: ["KSA"] },
  { code: "QA", name: "Qatar", spellings: [] },
  { code: "KW", name: "Kuwait", spellings: [] },
  { code: "OM", name: "Oman", spellings: [] },
  { code: "BH", name: "Bahrain", spellings: [] },
  { code: "TR", name: "Türkiye", spellings: ["Turkey"] },
  { code: "EG", name: "Egypt", spellings: [] },
];

export function useCountries() {
  return {
    options: STUB_MARKETS.map((name) => ({ value: name, label: name, aliases: [] })),
    countries: STUB_COUNTRIES,
    markets: STUB_MARKETS,
    isPending: false,
    isError: false,
  };
}
