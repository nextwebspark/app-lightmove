import type { Breakdown } from "../api/types";

/** Tier colours; the ladder runs strongest (a direct sector match) to weakest (an inferred tag). */
const TIER_COLOUR: Record<string, string> = {
  DIRECT: "bg-green",
  ADJACENT: "bg-sky",
  INFERRED: "bg-amber",
};

const TIER_LABEL: Record<string, string> = {
  DIRECT: "Direct",
  ADJACENT: "Adjacent",
  INFERRED: "Inferred",
};

/**
 * The relevance mix: one 100% bar split by match tier, with a legend carrying each tier's count and
 * share. Rows arrive already ordered by tier, so the bar reads the same way on every mandate.
 */
export function StackedBar({ caption, rows }: { caption: string; rows: Breakdown[] }) {
  const total = rows.reduce((sum, row) => sum + row.count, 0);
  const share = (count: number) => (total === 0 ? 0 : Math.round((count / total) * 100));

  return (
    <div>
      <div className="mb-2 font-mono text-[10px] font-semibold uppercase tracking-[0.12em] text-text3">
        {caption}
      </div>
      <div className="flex h-[26px] overflow-hidden rounded-md bg-line-soft">
        {rows.map((row) => (
          <span
            key={row.label}
            className={TIER_COLOUR[row.label] ?? "bg-line"}
            style={{ width: `${share(row.count)}%` }}
          />
        ))}
      </div>
      <div className="mt-3 flex flex-wrap gap-x-[18px] gap-y-2">
        {rows.map((row) => (
          <span key={row.label} className="flex items-center gap-[7px] text-xs text-text2">
            <span
              className={`size-2 flex-none rounded-sm ${TIER_COLOUR[row.label] ?? "bg-line"}`}
              aria-hidden="true"
            />
            {TIER_LABEL[row.label] ?? row.label}
            <b className="font-mono text-[11.5px] font-semibold text-text">
              {row.count} · {share(row.count)}%
            </b>
          </span>
        ))}
      </div>
    </div>
  );
}
