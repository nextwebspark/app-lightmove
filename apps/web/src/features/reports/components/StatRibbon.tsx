export interface RibbonStat {
  label: string;
  value: string;
  /** Token class for the figure; the default is the body colour. */
  valueClass?: string;
}

/**
 * The report's headline figures. The 1px grid gap over a `line-soft` background is how the mockup
 * draws the hairlines between cells — cheaper than a border on every side that would double up.
 */
export function StatRibbon({ stats }: { stats: RibbonStat[] }) {
  return (
    <div className="grid gap-px overflow-hidden rounded-[10px] border border-line-soft bg-line-soft [grid-template-columns:repeat(auto-fit,minmax(112px,1fr))]">
      {stats.map((stat) => (
        <div key={stat.label} className="bg-panel px-3.5 pb-3 pt-3.5">
          <div
            className={`font-mono text-[21px] font-bold tracking-[-0.01em] ${stat.valueClass ?? ""}`}
          >
            {stat.value}
          </div>
          <div className="mt-1.5 font-mono text-[9px] font-semibold uppercase tracking-[0.08em] text-text3">
            {stat.label}
          </div>
        </div>
      ))}
    </div>
  );
}
