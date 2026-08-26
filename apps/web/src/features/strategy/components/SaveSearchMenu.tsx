import { useState } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Popover } from "../../../components/ui/Popover";
import { cn } from "../../../lib/cn";
import { formatInstantDate } from "../../../lib/format";
import type { SavedSearch, SearchVisibility, StrategyFilter } from "../api/types";
import { sameFilter } from "../lib/filterIdentity";

/** Stroked to match the toolbar's other glyphs; there is no pencil in the shared ICONS set. */
const PENCIL = "M12 20h9M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4 12.5-12.5Z";
/** An arrow into a tray — re-capturing the current filter onto a search that already has a name. */
const OVERWRITE = "M12 3v10m0 0 4-4m-4 4-4-4M4 17v2a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-2";

type Tab = "mine" | "shared";

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
 * <p>The two tabs overlap deliberately. <b>Mine</b> is everything the viewer saved, whichever tier;
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
  onSetVisibility: (search: SavedSearch, visibility: SearchVisibility) => void;
  onOverwrite: (searchId: string) => void;
  onDelete: (searchId: string) => void;
  saving: boolean;
}) {
  const [name, setName] = useState("");
  const [visibility, setVisibility] = useState<SearchVisibility>("SHARED");
  const [tab, setTab] = useState<Tab>("shared");
  const [renaming, setRenaming] = useState<string | null>(null);

  const mine = searches.filter((search) => search.createdById === viewerId);
  const shared = searches.filter((search) => search.visibility === "SHARED");
  const listed = tab === "mine" ? mine : shared;

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
                  "inline-flex items-center gap-1 rounded-[6px] px-2 py-[5px] font-sans text-[11.5px] transition",
                  visibility === tier
                    ? "bg-panel2 text-text"
                    : "text-text3 hover:text-text2",
                )}
              >
                <Icon d={tier === "PRIVATE" ? ICONS.lock : ICONS.members} size={12} />
                {tier === "PRIVATE" ? "Only me" : "The team"}
              </button>
            ))}
          </div>

          <div className="mx-1 my-1.5 h-px bg-line-soft" />

          <div className="flex items-center gap-1 px-1 pb-1.5" role="tablist" aria-label="Saved searches">
            <Tab id="mine" active={tab} count={mine.length} onSelect={setTab}>
              Mine
            </Tab>
            <Tab id="shared" active={tab} count={shared.length} onSelect={setTab}>
              Shared
            </Tab>
          </div>

          {listed.length === 0 ? (
            <p className="px-2.5 py-2 font-sans text-[12.5px] text-text3">
              {tab === "mine"
                ? "You have not saved a search on this mandate yet."
                : "Nothing shared with the mandate yet."}
            </p>
          ) : (
            listed.map((search) => (
              <SearchRow
                key={search.id}
                search={search}
                tab={tab}
                mine={search.createdById === viewerId}
                active={sameFilter(currentFilter, search.filter)}
                renaming={renaming === search.id}
                onStartRename={() => setRenaming(search.id)}
                onEndRename={() => setRenaming(null)}
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

function Tab({
  id,
  active,
  count,
  onSelect,
  children,
}: {
  id: Tab;
  active: Tab;
  count: number;
  onSelect: (tab: Tab) => void;
  children: string;
}) {
  return (
    <button
      type="button"
      role="tab"
      aria-selected={active === id}
      onClick={() => onSelect(id)}
      className={cn(
        "rounded-[6px] px-2 py-[5px] font-sans text-[12px] transition",
        active === id ? "bg-panel2 font-semibold text-text" : "text-text3 hover:text-text2",
      )}
    >
      {children} ({count})
    </button>
  );
}

function SearchRow({
  search,
  tab,
  mine,
  active,
  renaming,
  onStartRename,
  onEndRename,
  onRename,
  onSetVisibility,
  onOverwrite,
  onDelete,
  onLoad,
}: {
  search: SavedSearch;
  tab: Tab;
  /** The viewer saved this one. Only the author moves a search between tiers — the server agrees. */
  mine: boolean;
  active: boolean;
  renaming: boolean;
  onStartRename: () => void;
  onEndRename: () => void;
  onRename: (searchId: string, name: string) => void;
  onSetVisibility: (search: SavedSearch, visibility: SearchVisibility) => void;
  onOverwrite: (searchId: string) => void;
  onDelete: (searchId: string) => void;
  onLoad: (filter: StrategyFilter) => void;
}) {
  const [draft, setDraft] = useState(search.name);

  const commit = () => {
    const trimmed = draft.trim();
    if (trimmed && trimmed !== search.name) onRename(search.id, trimmed);
    onEndRename();
  };

  if (renaming) {
    return (
      <div className="px-1 py-1">
        <input
          autoFocus
          value={draft}
          maxLength={120}
          aria-label={`Rename ${search.name}`}
          onChange={(event) => setDraft(event.target.value)}
          onBlur={commit}
          onKeyDown={(event) => {
            if (event.key === "Enter") commit();
            if (event.key === "Escape") {
              setDraft(search.name);
              onEndRename();
            }
          }}
          className="w-full rounded-[7px] border border-line bg-panel2 px-2.5 py-[7px] font-sans text-[13px] text-text outline-none focus:border-text3"
        />
      </div>
    );
  }

  // On the Mine tab the author is always the viewer, so the tier is the useful half; on Shared it is
  // whose search this is.
  const provenance =
    tab === "mine"
      ? search.visibility === "PRIVATE"
        ? "Only me"
        : "Shared"
      : (search.createdByName ?? "Someone on the team");

  return (
    <div className="group flex items-center gap-1 rounded-[7px] px-1 transition hover:bg-panel2">
      <button
        type="button"
        onClick={() => onLoad(search.filter)}
        className="min-w-0 flex-1 px-1.5 py-[7px] text-left transition"
      >
        <span className="flex items-center gap-1.5">
          {active && <Icon d={ICONS.check} size={12} className="flex-none text-amber" />}
          <span className="truncate font-sans text-[13px] text-text2 group-hover:text-text">
            {search.name}
          </span>
          {active && (
            <span className="flex-none rounded-[4px] bg-amber-dim px-[5px] py-[1px] font-sans text-[10px] font-bold text-amber">
              Active
            </span>
          )}
        </span>
        <span className="mt-[1px] block truncate font-sans text-[11px] text-text3">
          {provenance} · {formatInstantDate(search.updatedAt)}
        </span>
      </button>

      <button
        type="button"
        aria-label={`Update ${search.name} to the current filter`}
        disabled={active}
        onClick={() => onOverwrite(search.id)}
        className="grid size-6 flex-none place-items-center rounded-[5px] text-text3 opacity-0 transition group-hover:opacity-100 hover:text-text disabled:opacity-0"
      >
        <Icon d={OVERWRITE} size={13} />
      </button>
      {mine && (
        <button
          type="button"
          aria-label={
            search.visibility === "PRIVATE"
              ? `Share ${search.name} with the team`
              : `Make ${search.name} private`
          }
          onClick={() =>
            onSetVisibility(search, search.visibility === "PRIVATE" ? "SHARED" : "PRIVATE")
          }
          className="grid size-6 flex-none place-items-center rounded-[5px] text-text3 opacity-0 transition group-hover:opacity-100 hover:text-text"
        >
          <Icon d={search.visibility === "PRIVATE" ? ICONS.members : ICONS.lock} size={13} />
        </button>
      )}
      <button
        type="button"
        aria-label={`Rename ${search.name}`}
        onClick={() => {
          setDraft(search.name);
          onStartRename();
        }}
        className="grid size-6 flex-none place-items-center rounded-[5px] text-text3 opacity-0 transition group-hover:opacity-100 hover:text-text"
      >
        <Icon d={PENCIL} size={13} />
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
  );
}
