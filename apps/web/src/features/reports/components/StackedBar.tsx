import { Legend } from "./Legend";

export interface StackSegment {
  label: string;
  count: number;
  /** Token background class. */
  fillClass: string;
}

/** A part-to-whole bar, with its legend carrying the counts. */
export function StackedBar({ segments }: { segments: StackSegment[] }) {
  const total = segments.reduce((sum, s) => sum + s.count, 0);
  return (
    <div>
      <div className="flex h-[22px] w-full gap-[1.5px] overflow-hidden rounded-[5px]">
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
      <Legend className="mt-2.5" items={segments.map((s) => ({ label: s.label, swatchClass: s.fillClass, count: s.count }))} />
    </div>
  );
}
