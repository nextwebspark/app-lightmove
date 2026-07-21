/**
 * The geography market catalog — a client-side mirror of the backend GeographyMarket enum. `value` is
 * the ISO-3166 alpha-2 code (what a PUT sends and what the response echoes — also the company
 * universe's hq_country key); `label` is the display name the chips render. Only the GCC markets the
 * company universe actually holds are listed; Jordan and Egypt join when the pipeline expands.
 * geographyMarkets.test.ts guards the two catalogs against drift.
 */

import type { CatalogOption } from "./catalogOption";

export const GEOGRAPHY_MARKETS: CatalogOption[] = [
  { value: "AE", label: "UAE" },
  { value: "SA", label: "Saudi Arabia" },
  { value: "KW", label: "Kuwait" },
  { value: "QA", label: "Qatar" },
  { value: "BH", label: "Bahrain" },
  { value: "OM", label: "Oman" },
];
