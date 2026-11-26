/** One width for a list beside the record it opens: Outreach's conversations and a stage's Executives view. */
export const LIST_PANE_WIDTH = "w-[clamp(260px,24vw,336px)]";

/** Tailwind reads only literal classes, so `LIST_PANE_WIDTH`'s clamp is written out here too; keep the three in step. */
export const LIST_PANE_GRID =
  "lg:grid-cols-[clamp(260px,24vw,336px)_minmax(0,1fr)] xl:grid-cols-[clamp(260px,24vw,336px)_minmax(420px,1fr)_clamp(260px,22vw,320px)]";
