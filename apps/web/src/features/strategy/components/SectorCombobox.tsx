import { useMemo, useRef, useState } from "react";
import type { SectorCount } from "../api/types";
import { rankSectors, type RankedSector } from "../lib/fuzzy";

/**
 * The Direct-sector typeahead — a search-as-you-suggest field the locked mockup has no equivalent
 * for. It filters the in-memory sector list (fetched once), so there is no debounce: ranking ~500
 * strings per keystroke is imperceptible. Matches are ranked by similarity, not plain prefix, and the
 * matched characters are emphasised. Already-added sectors drop out of the list.
 */
export function SectorCombobox({
  sectors,
  existing,
  onAdd,
}: {
  sectors: SectorCount[];
  existing: string[];
  onAdd: (label: string) => void;
}) {
  const [draft, setDraft] = useState("");
  const [active, setActive] = useState(0);
  const [open, setOpen] = useState(false);
  const listId = "sector-typeahead";
  const blurTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const available = useMemo(() => {
    const taken = new Set(existing.map((label) => label.toLowerCase()));
    return sectors.filter((sector) => !taken.has(sector.name.toLowerCase()));
  }, [sectors, existing]);

  const matches = useMemo(() => rankSectors(draft, available), [draft, available]);
  const showList = open && draft.trim().length > 0 && matches.length > 0;

  const add = (label: string) => {
    onAdd(label);
    setDraft("");
    setActive(0);
  };

  const onKeyDown = (event: React.KeyboardEvent<HTMLInputElement>) => {
    if (event.key === "ArrowDown") {
      event.preventDefault();
      setActive((i) => Math.min(i + 1, matches.length - 1));
    } else if (event.key === "ArrowUp") {
      event.preventDefault();
      setActive((i) => Math.max(i - 1, 0));
    } else if (event.key === "Enter") {
      event.preventDefault();
      const pick = matches[active];
      if (pick) add(pick.name);
    } else if (event.key === "Escape") {
      setOpen(false);
    }
  };

  return (
    <div className="relative inline-block">
      <span className="inline-flex items-center rounded-full border border-dashed border-line px-[13px] py-[5px]">
        <input
          role="combobox"
          aria-expanded={showList}
          aria-controls={listId}
          aria-autocomplete="list"
          aria-activedescendant={showList ? `${listId}-${active}` : undefined}
          value={draft}
          placeholder="+ add sector"
          aria-label="Add a sector"
          onChange={(event) => {
            setDraft(event.target.value);
            setActive(0);
            setOpen(true);
          }}
          onFocus={() => setOpen(true)}
          onBlur={() => {
            // Clicking outside after typing a full sector name should add it — no dropdown pick
            // needed — but only on an exact match, so a half-typed fragment is never guessed at.
            const typed = draft.trim().toLowerCase();
            const exact = typed && available.find((sector) => sector.name.toLowerCase() === typed);
            if (exact) add(exact.name);
            blurTimer.current = setTimeout(() => setOpen(false), 120);
          }}
          onKeyDown={onKeyDown}
          className="w-40 bg-transparent py-[2px] font-sans text-[13px] font-medium text-text outline-none"
        />
      </span>

      {showList && (
        <ul
          id={listId}
          role="listbox"
          className="absolute z-10 mt-1 max-h-72 w-72 overflow-auto rounded-[10px] border border-line bg-panel py-1 shadow-panel"
        >
          {matches.map((match, index) => (
            <li
              key={match.name}
              id={`${listId}-${index}`}
              role="option"
              aria-selected={index === active}
              // Commit before the input's blur fires and closes the list.
              onMouseDown={(event) => {
                event.preventDefault();
                if (blurTimer.current) clearTimeout(blurTimer.current);
                add(match.name);
              }}
              onMouseEnter={() => setActive(index)}
              className={`flex cursor-pointer items-center justify-between gap-3 px-3 py-[7px] text-[13px] ${
                index === active ? "bg-panel2 text-text" : "text-text2"
              }`}
            >
              <span className="truncate">{highlight(match)}</span>
              <span className="flex-none font-mono text-[10.5px] text-text3">
                {match.count.toLocaleString("en-US")}
              </span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

/** Bolds the characters of the name that matched the query. */
function highlight(match: RankedSector) {
  const matched = new Set(match.matched);
  return (
    <>
      {[...match.name].map((char, index) =>
        matched.has(index) ? (
          <strong key={index} className="font-semibold text-text">
            {char}
          </strong>
        ) : (
          <span key={index}>{char}</span>
        ),
      )}
    </>
  );
}
