/**
 * The width of a list beside the record it opens, one figure across the app: Outreach's conversations and a stage's
 * Executives view.
 */
export const LIST_PANE_WIDTH = "w-[clamp(260px,24vw,336px)]";

/**
 * Outreach's three panes: the list at `LIST_PANE_WIDTH`, the conversation, and from `xl` the details rail. Tailwind
 * reads only literal classes, so the clamp is written out here too; keep the three in step.
 */
export const LIST_PANE_GRID =
  "lg:grid-cols-[clamp(260px,24vw,336px)_minmax(0,1fr)] xl:grid-cols-[clamp(260px,24vw,336px)_minmax(420px,1fr)_clamp(260px,22vw,320px)]";
