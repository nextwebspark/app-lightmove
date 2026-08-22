import { useState } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Popover } from "../../../components/ui/Popover";
import type { SavedSearch, StrategyFilter } from "../api/types";

/**
 * "Save Search" and the dropdown that loads one back.
 *
 * <p>The mockup gives the button and nothing else — no list, no naming — so this designs the rest to
 * the toolbar's own idiom, over the same {@link Popover} the Columns menu uses.
 *
 * <p>Loading a search is a client-side apply followed by the ordinary autosave, not its own endpoint:
 * that way loading a search and clicking a chip are one code path with one set of invalidations,
 * rather than two that can drift.
 */
export function SaveSearchMenu({
  searches,
  onSave,
  onLoad,
  onDelete,
  saving,
}: {
  searches: SavedSearch[];
  onSave: (name: string) => void;
  onLoad: (filter: StrategyFilter) => void;
  onDelete: (searchId: string) => void;
  saving: boolean;
}) {
  const [name, setName] = useState("");

  const submit = () => {
    const trimmed = name.trim();
    if (!trimmed) return;
    onSave(trimmed);
    setName("");
  };

  return (
    <Popover
      width={280}
      triggerClassName="inline-flex items-center gap-1.5 whitespace-nowrap py-1 font-sans text-[13px] text-text3 transition hover:text-text"
      trigger={() => (
        <>
          <Icon
            d="M12 17v5M9 10.76a2 2 0 0 1-1.11 1.79l-1.78.9A2 2 0 0 0 5 15.24V16a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-.76a2 2 0 0 0-1.11-1.79l-1.78-.9A2 2 0 0 1 15 10.76V7h1a2 2 0 0 0 0-4H8a2 2 0 0 0 0 4h1Z"
            size={14}
            className="flex-none text-amber"
          />
          Save Search
          {searches.length > 0 && (
            <span className="rounded-[4px] bg-sky-dim px-[5px] py-[2px] font-sans text-[10px] font-bold text-sky">
              {searches.length}
            </span>
          )}
        </>
      )}
    >
      {(close) => (
        <>
          <div className="flex items-center gap-1.5 p-1">
            <input
              value={name}
              onChange={(event) => setName(event.target.value)}
              onKeyDown={(event) => event.key === "Enter" && submit()}
              placeholder="Name this search…"
              aria-label="Name this search"
              className="w-full rounded-[7px] border border-line bg-panel2 px-2.5 py-[7px] font-sans text-[13px] text-text outline-none placeholder:text-text3 focus:border-text3"
            />
            <button
              type="button"
              onClick={submit}
              disabled={saving || name.trim().length === 0}
              className="flex-none rounded-[7px] border border-amber bg-amber-dim px-2.5 py-[7px] font-sans text-[12.5px] font-semibold text-amber transition hover:brightness-105 disabled:opacity-40"
            >
              Save
            </button>
          </div>

          {searches.length > 0 && <div className="mx-1 my-1.5 h-px bg-line-soft" />}

          {searches.map((search) => (
            <div
              key={search.id}
              className="group flex items-center gap-1 rounded-[7px] px-1 transition hover:bg-panel2"
            >
              <button
                type="button"
                onClick={() => {
                  onLoad(search.filter);
                  close();
                }}
                className="flex-1 truncate px-1.5 py-[7px] text-left font-sans text-[13px] text-text2 transition hover:text-text"
              >
                {search.name}
              </button>
              <button
                type="button"
                aria-label={`Delete ${search.name}`}
                onClick={() => onDelete(search.id)}
                className="grid size-6 flex-none place-items-center rounded-[5px] text-text3 opacity-0 transition group-hover:opacity-100 hover:text-red"
              >
                <Icon d={ICONS.close} size={13} />
              </button>
            </div>
          ))}
        </>
      )}
    </Popover>
  );
}
