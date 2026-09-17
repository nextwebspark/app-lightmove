import { cn } from "../../../lib/cn";

/** Two staggered rings swelling out of a point, so the reader finds "where we are" at a glance. */
export function PulseRings({ cx, cy, r, strokeClass }: { cx: number; cy: number; r: number; strokeClass: string }) {
  return (
    <>
      <circle cx={cx} cy={cy} r={r} fill="none" strokeWidth={2.5} className={cn("animate-pulse-ring", strokeClass)} />
      <circle
        cx={cx}
        cy={cy}
        r={r}
        fill="none"
        strokeWidth={2.5}
        className={cn("animate-pulse-ring [animation-delay:1.2s]", strokeClass)}
      />
    </>
  );
}
