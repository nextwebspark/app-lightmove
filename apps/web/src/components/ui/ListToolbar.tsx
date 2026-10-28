import { Fragment, type ReactNode } from "react";
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
  /** `separated` sets a chip apart from the ones before it — Archived, which is not a stage. */
  chips: readonly { key: TChip; label: string; count?: number; separated?: boolean }[];
  activeChip: TChip;
  onChipChange: (chip: TChip) => void;
  /** The grid's own controls — the Columns menu. Shown only where the grid is, from `md` up. */
  trailing?: ReactNode;
}) {
  return (
    <div className="mb-3.5 flex flex-wrap items-center gap-2.5">
      <label className="flex w-full items-center gap-2 rounded-lg border border-u-border-strong bg-u-raised px-[11px] py-[7px] sm:w-[300px]">
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
      </label>

      <div className="flex flex-wrap gap-1.5">
        {chips.map(({ key, label, count, separated }) => (
          <Fragment key={key}>
            {separated && <span aria-hidden="true" className="mx-0.5 h-4 w-px self-center bg-u-border-strong" />}
            <Chip selected={activeChip === key} count={count} onClick={() => onChipChange(key)}>
              {label}
            </Chip>
          </Fragment>
        ))}
      </div>

      {/* Below `md` the list is a card stack, and a Columns menu over cards has nothing to act on. */}
      {trailing && <div className="ms-auto hidden items-center gap-1.5 md:flex">{trailing}</div>}
    </div>
  );
}
