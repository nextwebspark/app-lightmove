import type { FacetOption } from "../api/types";

/**
 * The Location vocabulary, held here rather than counted out of the universe.
 *
 * <p>The six GCC markets are the whole list — the pipeline carries no seventh — so the panel needs
 * no query to learn them and the rail draws its chips on first paint instead of waiting on the
 * facets read. The values are Apollo's spelled-out country names, because `company_country` holds
 * those verbatim and a saved filter stores what the column matches.
 *
 * <p>Largest market first, which is the order the counted facet returned: the counts are gone from
 * the chips, but the two markets a GCC search starts from should still be the two chips it starts
 * from.
 */
export const GCC_COUNTRIES: readonly FacetOption[] = [
  { value: "United Arab Emirates", label: "United Arab Emirates" },
  { value: "Saudi Arabia", label: "Saudi Arabia" },
  { value: "Qatar", label: "Qatar" },
  { value: "Kuwait", label: "Kuwait" },
  { value: "Oman", label: "Oman" },
  { value: "Bahrain", label: "Bahrain" },
];
