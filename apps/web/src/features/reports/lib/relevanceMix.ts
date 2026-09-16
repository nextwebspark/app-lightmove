/**
 * How directly each mapped company competes for this mandate — direct, adjacent, or reached by an
 * inferred adjacency.
 *
 * <p><b>Nothing records this yet, so nothing here is measured.</b> The split below is a fixed
 * illustration of the shape the card will take, and every surface that renders it says so. It is
 * here because the card is part of the report's argument and a silent gap would read as a market
 * with no adjacencies rather than as a question nobody has answered.
 *
 * <p>TODO: derive it. The fact existed once — `app_lm_strategy_sector.kind` was
 * `DIRECT | ADJACENT | INFERRED` until V30 dropped the table for the flat `filter.industries` list,
 * which carries no kind. `IndustryAdjacency` (the static `data/industry-adjacency.json` behind the
 * Strategy screen's suggestion chips) still knows which industries are adjacent to which; what is
 * missing is a column on `app_lm_project_triage_company` recording how each company was reached.
 */

export interface RelevanceBand {
  label: string;
  share: number;
  fillClass: string;
}

/** Shares, not counts: applied to a real universe size so the card at least scales with the mandate. */
const ILLUSTRATIVE_SHARES: RelevanceBand[] = [
  { label: "Direct", share: 0.43, fillClass: "bg-sky" },
  { label: "Adjacent", share: 0.38, fillClass: "bg-amber" },
  { label: "AI inferred", share: 0.19, fillClass: "bg-text3" },
];

export interface RelevanceMix {
  bands: { label: string; count: number; fillClass: string }[];
  direct: number;
  universeCount: number;
  /** Always true today. The card reads this rather than hard-coding its own disclaimer. */
  isIllustrative: boolean;
}

/**
 * The illustrative split over a mandate's real universe size, with the remainder pushed onto the
 * last band so the parts always sum to the whole — a stacked bar that does not add up would be a
 * second, worse lie on top of the first.
 */
export function relevanceMix(universeCount: number): RelevanceMix {
  const bands = ILLUSTRATIVE_SHARES.map((band, index) => ({
    label: band.label,
    fillClass: band.fillClass,
    count:
      index === ILLUSTRATIVE_SHARES.length - 1
        ? universeCount - ILLUSTRATIVE_SHARES.slice(0, -1).reduce((sum, b) => sum + Math.round(b.share * universeCount), 0)
        : Math.round(band.share * universeCount),
  }));
  return {
    bands,
    direct: bands[0]?.count ?? 0,
    universeCount,
    isIllustrative: true,
  };
}
