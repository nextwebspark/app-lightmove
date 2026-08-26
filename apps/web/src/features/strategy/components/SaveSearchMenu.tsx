import { useState } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Popover } from "../../../components/ui/Popover";
import { cn } from "../../../lib/cn";
import type { SavedSearch, SearchVisibility, StrategyFilter } from "../api/types";
import { sameFilter } from "../lib/filterIdentity";
import { SavedSearchRow } from "./SavedSearchRow";

type TabId = "mine" | "shared";

const CHIP = "inline-flex items-center gap-1 rounded-[6px] px-2 py-[5px] font-sans transition";

/**
 * "Save Search" — the dropdown that names a filter, lists the ones already named, and loads one back.
 *
 * <p>The mockup gives the button and nothing else — no list, no naming — so this designs the rest to
 * the toolbar's own idiom, over the same {@link Popover} the Columns menu uses.
 *
 * <p>Loading a search is a client-side apply followed by the ordinary autosave, not its own endpoint:
 * that way loading a search and clicking a chip are one code path with one set of invalidations,
 * rather than two that can drift.
 *
 * <p>The two lists overlap deliberately. <b>Mine</b> is everything the viewer saved, whichever tier;
 * <b>Shared</b> is everything the mandate can see, whoever saved it. A viewer's own shared search
 * belongs in both — a "Mine" that hid the work you shared would be the confusing one.
 */
export function SaveSearchMenu({
  searches,
  currentFilter,
  viewerId,
  onSave,
  onLoad,
  onRename,
  onSetVisibility,
  onOverwrite,
  onDelete,
  saving,
}: {
  searches: SavedSearch[];
  currentFilter: StrategyFilter;
  viewerId: string | null;
  onSave: (name: string, visibility: SearchVisibility) => void;
  onLoad: (filter: StrategyFilter) => void;
  onRename: (searchId: string, name: string) => void;
  onSetVisibility: (searchId: string, visibility: SearchVisibility) => void;
  onOverwrite: (searchId: string) => void;
  onDelete: (searchId: string) => void;
  saving: boolean;
}) {
  const [name, setName] = useState("");
  const [visibility, setVisibility] = useState<SearchVisibility>("SHARED");
  const [tab, setTab] = useState<TabId | null>(null);

  const mine = searches.filter((search) => search.createdById === viewerId);
  const shared = searches.filter((search) => search.visibility === "SHARED");

  // Shared is the mandate's list and the one to open on — except for a viewer whose searches are all
  // private, who would otherwise be told there is nothing here under a trigger badge counting theirs.
  const selected = tab ?? (shared.length === 0 && mine.length > 0 ? "mine" : "shared");
  const listed = selected === "mine" ? mine : shared;

  const submit = () => {
    const trimmed = name.trim();
    if (!trimmed) return;
    onSave(trimmed, visibility);
    setName("");
    setTab(visibility === "PRIVATE" ? "mine" : "shared");
  };

  return (
    <Popover
      width={320}
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
              maxLength={120}
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

          <div className="flex items-center gap-1 px-1 pb-1" role="radiogroup" aria-label="Who can see it">
            {(["SHARED", "PRIVATE"] as const).map((tier) => (
              <button
                key={tier}
                type="button"
                role="radio"
                aria-checked={visibility === tier}
                onClick={() => setVisibility(tier)}
                className={cn(
                  CHIP,
                  "text-[11.5px]",
                  visibility === tier ? "bg-panel2 text-text" : "text-text3 hover:text-text2",
                )}
              >
                <Icon d={tier === "PRIVATE" ? ICONS.lock : ICONS.members} size={12} />
                {tier === "PRIVATE" ? "Only me" : "The team"}
              </button>
            ))}
          </div>

          <div className="mx-1 my-1.5 h-px bg-line-soft" />

          {/* A radiogroup, not a tablist: it is the same two-chip control as the tier picker above,
              and a tablist owes the reader aria-controls, a labelled panel and arrow-key movement
              that a pair of chips in a dropdown does not otherwise need. */}
          <div className="flex items-center gap-1 px-1 pb-1.5" role="radiogroup" aria-label="Which searches">
            {(["mine", "shared"] as const).map((id) => (
              <button
                key={id}
                type="button"
                role="radio"
                aria-checked={selected === id}
                onClick={() => setTab(id)}
                className={cn(
                  CHIP,
                  "text-[12px]",
                  selected === id ? "bg-panel2 font-semibold text-text" : "text-text3 hover:text-text2",
                )}
              >
                {id === "mine" ? "Mine" : "Shared"} ({id === "mine" ? mine.length : shared.length})
              </button>
            ))}
          </div>

          {listed.length === 0 ? (
            <p className="px-2.5 py-2 font-sans text-[12.5px] text-text3">
              {selected === "mine"
                ? "You have not saved a search on this mandate yet."
                : "Nothing shared with the mandate yet."}
            </p>
          ) : (
            listed.map((search) => (
              <SavedSearchRow
                key={search.id}
                search={search}
                tab={selected}
                isMine={search.createdById === viewerId}
                isActive={sameFilter(currentFilter, search.filter)}
                onRename={onRename}
                onSetVisibility={onSetVisibility}
                onOverwrite={onOverwrite}
                onDelete={onDelete}
                onLoad={(filter) => {
                  onLoad(filter);
                  close();
                }}
              />
            ))
          )}
        </>
      )}
    </Popover>
  );
}
