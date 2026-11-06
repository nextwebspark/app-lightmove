/** Gap between an anchor's edge and the panel it opens, and the margin kept from the viewport edge. */
export const PANEL_GAP = 6;
export const EDGE_GAP = 12;
/** Room an `auto` panel wants beneath its anchor before it opens above instead, until its own height is known. */
export const AUTO_PANEL_ROOM = 340;

/**
 * Where a `fixed` panel sits against its anchor: beneath it, or — `auto`, and with more room above than the
 * `wanted` height finds beneath — above it. Either way it is held to the room on its side, so a long list
 * scrolls inside the window rather than running past its foot.
 */
export function verticalPlacement(anchor: DOMRect, placement: "below" | "auto", wanted = AUTO_PANEL_ROOM) {
  const below = Math.max(0, window.innerHeight - anchor.bottom - PANEL_GAP - EDGE_GAP);
  const above = Math.max(0, anchor.top - PANEL_GAP - EDGE_GAP);
  if (placement === "auto" && below < wanted && above > below) {
    return { bottom: window.innerHeight - anchor.top + PANEL_GAP, maxHeight: above };
  }
  return { top: anchor.bottom + PANEL_GAP, maxHeight: below };
}
