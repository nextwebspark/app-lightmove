import { cn } from "../../../lib/cn";

export interface StackSegment {
  label: string;
  count: number;
  /** Token background class. */
  fillClass: string;
}

/** A part-to-whole bar with a 2px surface gap between segments and its legend carrying the counts. */
export function StackedBar({
  segments,
  className,
  height = "h-[18px]",
}: {
  segments: StackSegment[];
  className?: string;
  height?: string;
}) {
  const total = segments.reduce((sum, s) => sum + s.count, 0);
  return (
    <div className={className}>
      <div className={cn("flex w-full gap-0.5 overflow-hidden rounded-[4px]", height)}>
        {segments
          .filter((s) => s.count > 0)
          .map((s) => (
            <div
              key={s.label}
              title={`${s.label} · ${s.count}`}
              className={s.fillClass}
              style={{ width: `${total === 0 ? 0 : (s.count / total) * 100}%` }}
            />
          ))}
      </div>
      <div className="mt-[9px] flex flex-wrap gap-4">
        {segments.map((s) => (
          <span key={s.label} className="inline-flex items-center gap-1.5 text-[11px] text-u-text2">
            <i aria-hidden className={cn("inline-block size-[9px] rounded-[2px]", s.fillClass)} />
            {s.label}
            <b className="font-u-num text-[11px] font-semibold text-u-text">{s.count}</b>
          </span>
        ))}
      </div>
    </div>
  );
}
