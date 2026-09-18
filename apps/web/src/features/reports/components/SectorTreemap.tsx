import { cn } from "../../../lib/cn";
import type { Breakdown } from "../api/types";
import { RAMP_BG, RAMP_GROUND_LABEL_FROM, rampStop } from "../lib/ramp";
import { squarify } from "../lib/treemap";
import { ChartEmpty } from "./ChartEmpty";

/** The block's shape: wide enough that a row of sectors reads, short enough to sit in a chapter. */
const ASPECT = 16 / 9;

/**
 * Below this share of the block a tile has no room for its figures, so it keeps its label alone and
 * says the rest in its tooltip. Crowding them in is how a long tail turns into overlapping text.
 */
const FIGURES_FROM_AREA = 2.4;

/** And below this it has no room for even a label. */
const LABEL_FROM_AREA = 0.7;

/**
 * Companies by sector, as area. A bar list ranks sectors; this says what share of the universe each
 * one <i>is</i>, which is the question the chapter asks.
 *
 * <p>Shaded on UNCAVA's five-stop sequential ramp — the same scale the sector × seniority matrix
 * uses, so the two heat surfaces on this chapter cannot be read as two different scales, and the
 * block is right in both themes because the ramp itself is defined per theme.
 *
 * <p>It carries its own header rather than sitting in a {@code ReportCard}: the universe it counts
 * is named beside the title, where a card would bury it in a caption.
 */
export function SectorTreemap({ rows, universeCount }: { rows: Breakdown[]; universeCount: number }) {
  const tiles = squarify(
    rows.map((row) => ({ key: row.label, value: row.count })),
    ASPECT,
  );
  const fullest = Math.max(0, ...rows.map((row) => row.count));
  const counted = rows.reduce((sum, row) => sum + row.count, 0);

  return (
    <figure className="mt-4 rounded-[11px] bg-u-surface px-4 py-[18px] shadow-u-e1 sm:px-6 sm:py-[22px]">
      <figcaption className="mb-3.5">
        <div className="flex items-baseline justify-between gap-3 text-[9.5px] font-bold uppercase tracking-[0.1em] text-u-text3">
          <span>Intelligence universe</span>
          <span>Target universe</span>
        </div>
        <div className="mt-1 flex items-end justify-between gap-3">
          <div className="text-[19px] font-bold leading-tight tracking-[-0.01em]">Companies by sector</div>
          <div className="font-u-num text-[19px] font-bold leading-tight">n={universeCount}</div>
        </div>
      </figcaption>

      {tiles.length === 0 ? (
        <ChartEmpty>No company of the universe has a sector on file yet.</ChartEmpty>
      ) : (
        <div className="relative w-full" style={{ aspectRatio: String(ASPECT) }}>
          {tiles.map((tile) => {
            const stop = rampStop(tile.row.value, fullest);
            // The tile's percentage of the block is its percentage of the total, by construction.
            const share = tile.width * tile.height * 0.01;
            return (
              <div
                key={tile.row.key}
                title={`${tile.row.key} — ${tile.row.value} companies, ${share1(tile.row.value, counted)}% of the sectored universe`}
                className={cn(
                  "absolute overflow-hidden rounded-[5px] p-2.5 sm:p-3",
                  RAMP_BG[stop],
                  stop >= RAMP_GROUND_LABEL_FROM ? "text-u-bg" : "text-u-text",
                )}
                style={{
                  insetInlineStart: `${tile.x}%`,
                  top: `${tile.y}%`,
                  width: `${tile.width}%`,
                  height: `${tile.height}%`,
                }}
              >
                {share >= LABEL_FROM_AREA && (
                  <div className="truncate text-[11.5px] font-semibold leading-tight">{tile.row.key}</div>
                )}
                {share >= FIGURES_FROM_AREA && (
                  <div className="absolute inset-x-2.5 bottom-2 flex items-baseline justify-between gap-2 sm:inset-x-3">
                    <span className="font-u-num text-[21px] font-extrabold leading-none tracking-[-0.01em]">
                      {tile.row.value}
                    </span>
                    <span className="font-u-num text-[11px] font-semibold opacity-70">
                      {share1(tile.row.value, counted)}%
                    </span>
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}
    </figure>
  );
}

/**
 * A share to one decimal. The chapter's other figures are whole percents, but a treemap's tail
 * sectors are all "2%" to the nearest point and the tile is drawn at the difference.
 */
function share1(part: number, whole: number): string {
  return whole === 0 ? "0.0" : ((part / whole) * 100).toFixed(1);
}
