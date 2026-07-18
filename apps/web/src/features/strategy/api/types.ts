/** One selectable sector or tag on the strategy. */
export interface Chip {
  label: string;
  selected: boolean;
}

/** The sector scope, split by how each chip came to be there. */
export interface Strategy {
  direct: Chip[];
  adjacent: Chip[];
  inferred: Chip[];
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
