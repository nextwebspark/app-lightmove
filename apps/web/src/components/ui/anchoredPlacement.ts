/** Gap between an anchor's edge and the panel it opens, and the margin kept from the viewport edge. */
export const PANEL_GAP = 6;
export const EDGE_GAP = 12;
/** Room an `auto` panel wants beneath its anchor before it opens above instead. */
export const AUTO_PANEL_ROOM = 340;

/** Where a `fixed` panel sits against its anchor: beneath it, or — `auto` and short of room there — above it. */
export function verticalPlacement(anchor: DOMRect, placement: "below" | "auto") {
  const below = window.innerHeight - anchor.bottom - PANEL_GAP - EDGE_GAP;
  const above = anchor.top - PANEL_GAP - EDGE_GAP;
  if (placement === "auto" && below < AUTO_PANEL_ROOM && above > below) {
    return { bottom: window.innerHeight - anchor.top + PANEL_GAP, maxHeight: above };
  }
  return { top: anchor.bottom + PANEL_GAP };
}
