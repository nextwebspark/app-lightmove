import { useState } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { cn } from "../../../lib/cn";
import { formatInstantDate } from "../../../lib/format";
import type { SavedSearch, SearchVisibility, StrategyFilter } from "../api/types";

const ROW_ACTION =
  "grid size-6 flex-none place-items-center rounded-[5px] text-text3 opacity-0 transition " +
  "group-hover:opacity-100 focus-visible:opacity-100 group-focus-within:opacity-100";

const ROW_INPUT =
  "w-full rounded-[7px] border border-line bg-panel2 px-2.5 py-[7px] font-sans text-[13px] text-text outline-none focus:border-text3";

/**
 * One saved search in the dropdown: load it, rename it, re-capture the current filter onto it, move
 * it between tiers, or delete it.
 *
 * <p>Renaming is state this row owns rather than the menu's, so closing the popover discards a
 * half-typed name with it — a menu that reopened into an editor for a row the reader had moved on
 * from was the alternative.
 */
export function SavedSearchRow({
  search,
  tab,
  isMine,
  isActive,
  onLoad,
  onRename,
  onSetVisibility,
  onOverwrite,
  onDelete,
}: {
  search: SavedSearch;
  tab: "mine" | "shared";
  /** Only the author moves a search between tiers; the server refuses anyone else. */
  isMine: boolean;
  /** Its filter is the one the sidebar is showing. */
  isActive: boolean;
  onLoad: (filter: StrategyFilter) => void;
  onRename: (searchId: string, name: string) => void;
  onSetVisibility: (searchId: string, visibility: SearchVisibility) => void;
  onOverwrite: (searchId: string) => void;
  onDelete: (searchId: string) => void;
}) {
  const [draft, setDraft] = useState<string | null>(null);

  if (draft !== null) {
    return (
      <div className="px-1 py-1">
        <input
          autoFocus
          value={draft}
          maxLength={120}
          aria-label={`Rename ${search.name}`}
          onChange={(event) => setDraft(event.target.value)}
          // Enter is the only thing that commits. Clicking away has to mean one thing, and it cannot
          // mean "save": a click outside the popover unmounts this input on mousedown, before any
          // blur fires, so committing on blur would save or discard depending on where the click
          // landed.
          onBlur={() => setDraft(null)}
          onKeyDown={(event) => {
            if (event.key === "Enter") {
              const trimmed = draft.trim();
              if (trimmed && trimmed !== search.name) onRename(search.id, trimmed);
              setDraft(null);
            }
            if (event.key === "Escape") {
              // Innermost first: Popover closes on Escape from the document, which would take the
              // whole menu down instead of just leaving this input.
              event.stopPropagation();
              setDraft(null);
            }
          }}
          className={ROW_INPUT}
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
          {isActive && <Icon d={ICONS.check} size={12} className="flex-none text-amber" />}
          <span className="truncate font-sans text-[13px] text-text2 group-hover:text-text">
            {search.name}
          </span>
        </span>
        {/* "Active" rides the provenance line rather than the name's. Four action slots are reserved
            on the first line whether or not they are visible, and a badge on top of them left the
            name — the only thing identifying the row — truncated to about a dozen characters. */}
        <span className="mt-[1px] block truncate font-sans text-[11px] text-text3">
          {isActive && <span className="font-semibold text-amber">Active · </span>}
          {provenance} · {formatInstantDate(search.updatedAt)}
        </span>
      </button>

      <button
        type="button"
        aria-label={`Update ${search.name} to the current filter`}
        disabled={isActive}
        title={isActive ? "Already the filter on screen" : undefined}
        onClick={() => onOverwrite(search.id)}
        // twMerge resolves the later group-hover against ROW_ACTION's, so the dimming does not
        // depend on which variant Tailwind happens to emit first.
        className={cn(
          ROW_ACTION,
          isActive ? "cursor-default group-hover:opacity-30" : "hover:text-text",
        )}
      >
        <Icon d={ICONS.recapture} size={13} />
      </button>
      {isMine && (
        <button
          type="button"
          aria-label={
            search.visibility === "PRIVATE"
              ? `Share ${search.name} with the team`
              : `Make ${search.name} private`
          }
          onClick={() =>
            onSetVisibility(search.id, search.visibility === "PRIVATE" ? "SHARED" : "PRIVATE")
          }
          className={cn(ROW_ACTION, "hover:text-text")}
        >
          <Icon d={search.visibility === "PRIVATE" ? ICONS.members : ICONS.lock} size={13} />
        </button>
      )}
      <button
        type="button"
        aria-label={`Rename ${search.name}`}
        onClick={() => setDraft(search.name)}
        className={cn(ROW_ACTION, "hover:text-text")}
      >
        <Icon d={ICONS.pencil} size={13} />
      </button>
      <button
        type="button"
        aria-label={`Delete ${search.name}`}
        onClick={() => onDelete(search.id)}
        className={cn(ROW_ACTION, "hover:text-red")}
      >
        <Icon d={ICONS.close} size={13} />
      </button>
    </div>
  );
}
