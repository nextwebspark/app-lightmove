import type { ReactNode } from "react";
import { Icon, ICONS } from "../layout/Icon";
import { cn } from "../../lib/cn";

/**
 * The filter row above a workspace list: search on the left, the stage/type chips beside it, and
 * whatever grid controls the screen offers pushed to the end.
 *
 * <p>Shared because the mandate list and the client registry render the same row over different
 * chips, and a Columns menu that sat a few pixels differently on each would read as two products.
 */
export function ListToolbar<TChip extends string>({
  query,
  onQueryChange,
  placeholder,
  chips,
  activeChip,
  onChipChange,
  trailing,
}: {
  query: string;
  onQueryChange: (query: string) => void;
  placeholder: string;
  chips: readonly { key: TChip; label: string }[];
  activeChip: TChip;
  onChipChange: (chip: TChip) => void;
  /** The grid's own controls — the Columns menu. Shown only where the grid is, from `md` up. */
  trailing?: ReactNode;
}) {
  return (
    <div className="mb-3.5 flex flex-wrap items-center gap-2.5">
      <div className="flex w-full items-center gap-2 rounded-lg border border-line bg-panel2 px-[11px] py-[7px] sm:w-[300px]">
        <Icon d={ICONS.search} size={14} className="text-text3" />
        <input
          value={query}
          onChange={(event) => onQueryChange(event.target.value)}
          placeholder={placeholder}
          // A placeholder is a hint, not a name: it is gone the moment a letter is typed.
          aria-label={placeholder}
          className="w-full bg-transparent font-mono text-[13px] text-text outline-none placeholder:text-text3"
        />
      </div>

      <div className="flex flex-wrap gap-1.5">
        {chips.map(({ key, label }) => (
          <button
            key={key}
            type="button"
            onClick={() => onChipChange(key)}
            className={cn(
              "rounded-full border px-[11px] py-[5px] font-mono text-xs font-medium transition hover:text-text",
              activeChip === key ? "border-amber bg-amber-dim text-amber" : "border-line text-text2",
            )}
          >
            {label}
          </button>
        ))}
      </div>

      {/* Below `md` the list is a card stack, and a Columns menu over cards has nothing to act on. */}
      {trailing && <div className="ms-auto hidden items-center gap-1.5 md:flex">{trailing}</div>}
    </div>
  );
}
