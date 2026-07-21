/** One selectable sector or tag on the strategy. */
export interface Chip {
  label: string;
  selected: boolean;
}

/** The three sector chip groups — the Chip[] axes of a Strategy (company-size axes are string[]). */
export type SectorGroup = "direct" | "adjacent" | "inferred";

/** The whole strategy scope. Sectors split by how each chip came to be there; the fixed-catalog
 *  sections carry only the selected values (the pills render from the static catalogs in lib/ —
 *  company-size bands, geography markets as ISO codes, ownership structures as stable tokens). */
export interface Strategy {
  direct: Chip[];
  adjacent: Chip[];
  inferred: Chip[];
  employee: string[];
  revenue: string[];
  markets: string[];
  structures: string[];
}

/** A sector (primary_industry) and how many companies carry it — feeds the typeahead ranking. */
export interface SectorCount {
  name: string;
  count: number;
}

/** An industry tag and how often it co-occurs with the queried sectors. */
export interface TagCount {
  tag: string;
  count: number;
}

/** What the suggestions endpoint returns for a set of chosen direct sectors. */
export interface Suggestions {
  adjacent: string[];
  inferredTags: TagCount[];
}

export interface Estimate {
  count: number;
}
