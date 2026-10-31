import { useRef, useState, type KeyboardEvent, type PointerEvent, type RefObject } from "react";

const STORAGE_KEY = "lightmove.pipeline.outOfProcessHeight";
/** What the columns keep above the band however far it is dragged up. */
const COLUMNS_KEEP = 200;
const KEY_STEP = 24;

/**
 * The Out of process band's height, dragged from its grip: null is as tall as its people need, a number holds
 * it there and scrolls each cell. Kept per viewer, since it is only how they like the board.
 */
export function useBandHeight(gridRef: RefObject<HTMLElement | null>) {
  const [height, setHeight] = useState<number | null>(readStored);
  const session = useRef<{ startY: number; startHeight: number; max: number; latest: number | null } | null>(null);

  const measure = () => {
    const grid = gridRef.current;
    if (!grid) return null;
    const cells = [...grid.querySelectorAll<HTMLElement>("[data-band-cell]")];
    return {
      current: Math.max(0, ...cells.map((cell) => cell.getBoundingClientRect().height)),
      max: Math.max(0, grid.clientHeight - COLUMNS_KEEP),
    };
  };

  const settle = (next: number) => {
    setHeight(next);
    store(next);
  };

  const onPointerDown = (event: PointerEvent<HTMLElement>) => {
    const box = measure();
    if (!box) return;
    event.preventDefault();
    event.currentTarget.setPointerCapture?.(event.pointerId);
    session.current = { startY: event.clientY, startHeight: box.current, max: box.max, latest: null };
  };

  const onPointerMove = (event: PointerEvent<HTMLElement>) => {
    const drag = session.current;
    if (!drag) return;
    drag.latest = Math.round(Math.min(drag.max, Math.max(0, drag.startHeight - (event.clientY - drag.startY))));
    setHeight(drag.latest);
  };

  const onPointerUp = () => {
    const latest = session.current?.latest ?? null;
    session.current = null;
    if (latest !== null) store(latest);
  };

  const onKeyDown = (event: KeyboardEvent<HTMLElement>) => {
    if (event.key !== "ArrowUp" && event.key !== "ArrowDown") return;
    const box = measure();
    if (!box) return;
    event.preventDefault();
    const from = height ?? box.current;
    const step = event.key === "ArrowUp" ? KEY_STEP : -KEY_STEP;
    settle(Math.round(Math.min(box.max, Math.max(0, from + step))));
  };

  const reset = () => {
    setHeight(null);
    try {
      localStorage.removeItem(STORAGE_KEY);
    } catch {
      // Nowhere to forget it: the board still opens at the band's own height this time.
    }
  };

  return {
    height,
    reset,
    gripProps: {
      onPointerDown,
      onPointerMove,
      onPointerUp,
      onPointerCancel: onPointerUp,
      onKeyDown,
      onDoubleClick: reset,
    },
  };
}

function readStored(): number | null {
  try {
    const stored = Number.parseInt(localStorage.getItem(STORAGE_KEY) ?? "", 10);
    return Number.isFinite(stored) && stored >= 0 ? stored : null;
  } catch {
    return null;
  }
}

function store(height: number) {
  try {
    localStorage.setItem(STORAGE_KEY, String(height));
  } catch {
    // A private window keeps no height; the band still resizes for this visit.
  }
}
