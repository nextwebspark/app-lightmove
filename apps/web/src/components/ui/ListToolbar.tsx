import type { ReactNode } from "react";
import { Icon, ICONS } from "../layout/Icon";
import { Chip } from "./Chip";

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
  onQueryBlur,
  placeholder,
  chips,
  activeChip,
  onChipChange,
  trailing,
}: {
  query: string;
  onQueryChange: (query: string) => void;
  /** Leaving the box, which a click on a row does first. */
  onQueryBlur?: () => void;
  placeholder: string;
  chips: readonly { key: TChip; label: string }[];
  activeChip: TChip;
  onChipChange: (chip: TChip) => void;
  /** The grid's own controls — the Columns menu. Shown only where the grid is, from `md` up. */
  trailing?: ReactNode;
}) {
  return (
    <div className="mb-3.5 flex flex-wrap items-center gap-2.5">
      <div className="flex w-full items-center gap-2 rounded-lg border border-u-border-strong bg-u-raised px-[11px] py-[7px] sm:w-[300px]">
        <Icon d={ICONS.search} size={14} className="text-u-text3" />
        <input
          value={query}
          onChange={(event) => onQueryChange(event.target.value)}
          onBlur={onQueryBlur}
          placeholder={placeholder}
          // A placeholder is a hint, not a name: it is gone the moment a letter is typed.
          aria-label={placeholder}
          className="w-full bg-transparent font-mono text-[13px] text-u-text outline-none placeholder:text-u-text3"
        />
      </div>

      <div className="flex flex-wrap gap-1.5">
        {chips.map(({ key, label }) => (
          <Chip key={key} selected={activeChip === key} onClick={() => onChipChange(key)}>
            {label}
          </Chip>
        ))}
      </div>

      {/* Below `md` the list is a card stack, and a Columns menu over cards has nothing to act on. */}
      {trailing && <div className="ms-auto hidden items-center gap-1.5 md:flex">{trailing}</div>}
    </div>
  );
}
