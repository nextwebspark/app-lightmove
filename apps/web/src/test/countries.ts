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

export function useCountries() {
  return {
    options: STUB_MARKETS.map((name) => ({ value: name, label: name, aliases: [] })),
    markets: STUB_MARKETS,
    isPending: false,
    isError: false,
  };
}
