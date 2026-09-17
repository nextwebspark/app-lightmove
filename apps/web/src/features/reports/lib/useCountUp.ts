import { useEffect, useRef, useState } from "react";

const DURATION_MS = 500;

/**
 * Eases a displayed number from its previous value to a new one, so a filter change reads as
 * cause and effect rather than a snap. Skipped under `prefers-reduced-motion`, and wherever there is
 * no animation frame to draw on.
 */
export function useCountUp(target: number): number {
  const [displayed, setDisplayed] = useState(target);
  const fromRef = useRef(target);

  useEffect(() => {
    const from = fromRef.current;
    const shouldAnimate =
      from !== target &&
      typeof requestAnimationFrame === "function" &&
      !window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    if (!shouldAnimate) {
      fromRef.current = target;
      setDisplayed(target);
      return;
    }
    let startedAt: number | null = null;
    let frame = 0;
    const step = (now: number) => {
      if (startedAt === null) startedAt = now;
      const progress = Math.min((now - startedAt) / DURATION_MS, 1);
      const eased = 1 - Math.pow(1 - progress, 3);
      // Recorded every frame, not on completion: a second filter change inside the window must ease
      // from the number on screen, or the figure jumps back to where the interrupted run began.
      fromRef.current = from + (target - from) * eased;
      setDisplayed(fromRef.current);
      if (progress < 1) {
        frame = requestAnimationFrame(step);
      }
    };
    frame = requestAnimationFrame(step);
    return () => cancelAnimationFrame(frame);
  }, [target]);

  return displayed;
}
