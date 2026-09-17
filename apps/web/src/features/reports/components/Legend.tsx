import { cn } from "../../../lib/cn";

export type SwatchShape = "square" | "dot" | "line" | "dashed";

export interface LegendEntry {
  label: string;
  /** Token background class for the swatch. */
  swatchClass: string;
  shape?: SwatchShape;
  /** The series' own figure, for a legend that doubles as the chart's readout. */
  count?: number;
}

const SHAPE_CLASS: Record<SwatchShape, string> = {
  square: "size-[9px] rounded-[2px]",
  dot: "size-2 rounded-full",
  line: "h-[2.5px] w-[15px] rounded-[2px]",
  dashed: "h-[2.5px] w-[15px] rounded-[2px] [mask-image:repeating-linear-gradient(90deg,#000_0_4px,transparent_4px_7px)]",
};

/** Always present for two or more series, so identity never rests on colour alone. */
export function Legend({ items, className }: { items: LegendEntry[]; className?: string }) {
  return (
    <div className={cn("flex flex-wrap gap-x-4 gap-y-1.5", className)}>
      {items.map((item) => (
        <span key={item.label} className="inline-flex items-center gap-1.5 text-[11px] text-u-text2">
          <i aria-hidden className={cn("inline-block flex-none", SHAPE_CLASS[item.shape ?? "square"], item.swatchClass)} />
          {item.label}
          {item.count !== undefined && <b className="ms-0.5 font-u-num font-bold text-u-text">{item.count}</b>}
        </span>
      ))}
    </div>
  );
}
