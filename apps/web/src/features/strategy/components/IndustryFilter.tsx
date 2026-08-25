import { useEffect, useMemo, useRef, useState } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { cn } from "../../../lib/cn";
import type { FacetCount, SectorGroup } from "../api/types";
import { adjacentTo } from "../lib/sectorAdjacency";
import { SelectionPill } from "./SelectionPill";

const SPARKLE =
  "M9.9 2.6 11 5.9a2 2 0 0 0 1.3 1.3l3.3 1.1-3.3 1.1a2 2 0 0 0-1.3 1.3L9.9 14l-1.1-3.3a2 2 0 0 0-1.3-1.3L4.2 8.3l3.3-1.1a2 2 0 0 0 1.3-1.3ZM18 14l.6 1.8 1.8.6-1.8.6-.6 1.8-.6-1.8-1.8-.6 1.8-.6Z";

const ADJACENT_SHOWN = 6;
const SUGGESTIONS_SHOWN = 8;
const LIST_ID = "industry-suggestions";
/** Distinguishes a sector pill from an industry pill, which are both just strings. */
const SECTOR_TAG = "sector:";

/**
 * A sector is taken whole or not at all; anything else selected is an individual industry. Splitting
 * it this way means a stored filter reads back one way only — the partial-vs-refined ambiguity the
 * previous shape carried had no answer.
 */
interface Picks {
  sectors: string[];
  industries: string[];
}

function industriesOf(picks: Picks, byName: Map<string, SectorGroup>): string[] {
  const reached: string[] = [];
  for (const name of picks.sectors) {
    const group = byName.get(name);
    if (group) reached.push(...group.industries.map((industry) => industry.value));
  }
  return [...new Set([...reached, ...picks.industries])];
}

function picksFrom(selected: string[], groups: SectorGroup[]): Picks {
  const chosen = new Set(selected);
  const sectors: string[] = [];
  const covered = new Set<string>();
  for (const group of groups) {
    const leaves = group.industries.map((industry) => industry.value);
    if (leaves.length === 0 || !leaves.every((leaf) => chosen.has(leaf))) continue;
    sectors.push(group.name);
    leaves.forEach((leaf) => covered.add(leaf));
  }
  // Whatever a whole sector does not account for stands on its own — including a label the taxonomy
  // no longer groups, which would otherwise be dropped the first time the panel wrote the filter.
  return { sectors, industries: selected.filter((value) => !covered.has(value)) };
}

const sameSet = (a: string[], b: string[]) =>
  a.length === b.length && a.every((entry) => b.includes(entry));

/**
 * The Industry panel: the sector list, a type-ahead over every industry, and the sectors beside
 * whichever one is open.
 *
 * <p>Selecting a sector stores its industries, never its name — the grouping is editorial and will
 * be re-tuned, and a saved search must not widen because a label moved.
 */
export function IndustryFilter({
  groups,
  selected,
  onChange,
}: {
  groups: SectorGroup[];
  selected: string[];
  onChange: (industries: string[]) => void;
}) {
  const [openGroup, setOpenGroup] = useState<string | null>(null);
  const [query, setQuery] = useState("");
  const [active, setActive] = useState(0);
  const [listOpen, setListOpen] = useState(false);
  const [showAllAdjacent, setShowAllAdjacent] = useState(false);
  const [picks, setPicks] = useState<Picks>(() => picksFrom(selected, groups));
  const blurTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => () => { if (blurTimer.current) clearTimeout(blurTimer.current); }, []);

  const byName = useMemo(() => new Map(groups.map((group) => [group.name, group])), [groups]);

  const leaves = useMemo(
    () =>
      groups.flatMap((group) =>
        group.industries.map((industry) => ({ ...industry, group: group.name })),
      ),
    [groups],
  );
  const labelOf = useMemo(() => {
    const labels = new Map(leaves.map((leaf) => [leaf.value, leaf.label]));
    return (value: string) => labels.get(value) ?? value;
  }, [leaves]);

  // Only a filter replaced from outside — Reset, or a saved search applied — has to rebuild the
  // picks; our own writes come back as the value we just emitted.
  const emitted = useRef<string[]>(selected);
  useEffect(() => {
    if (sameSet(selected, emitted.current)) return;
    emitted.current = selected;
    setPicks(picksFrom(selected, groups));
    setOpenGroup(null);
  }, [selected, groups]);

  const apply = (next: Picks) => {
    setPicks(next);
    const industries = industriesOf(next, byName);
    if (sameSet(industries, emitted.current)) return;
    emitted.current = industries;
    onChange(industries);
  };

  const takenSectors = useMemo(() => new Set(picks.sectors), [picks.sectors]);
  const reached = useMemo(
    () => new Set(industriesOf(picks, byName)),
    [picks, byName],
  );

  const needle = query.trim().toLowerCase();

  const suggestions = useMemo(() => {
    if (!needle) return [];
    return leaves
      .filter((leaf) => !reached.has(leaf.value) && leaf.label.toLowerCase().includes(needle))
      .slice(0, SUGGESTIONS_SHOWN);
  }, [leaves, needle, reached]);

  const visibleGroups = useMemo(() => {
    if (!needle) return groups;
    // Matching on the industries inside a sector too, so "aws" finds Technology without the
    // consultant knowing where we filed it.
    return groups.filter(
      (group) =>
        group.name.toLowerCase().includes(needle) ||
        group.industries.some((industry) => industry.label.toLowerCase().includes(needle)),
    );
  }, [groups, needle]);

  const open = openGroup === null ? null : (byName.get(openGroup) ?? null);

  /**
   * Anchored to the open sector so the list does not move: deriving it from everything selected made
   * taking one suggestion drop that chip and fold in new ones, shifting whatever was about to be
   * clicked next. A taken neighbour stays, rendered as taken.
   */
  const adjacent = useMemo(() => (open ? adjacentTo(open, byName) : []), [open, byName]);

  const takeSector = (group: SectorGroup) => {
    const leafValues = new Set(group.industries.map((industry) => industry.value));
    apply({
      sectors: [...picks.sectors, group.name],
      // Its leaves are now covered by the sector, and a pill for each would double-count.
      industries: picks.industries.filter((entry) => !leafValues.has(entry)),
    });
  };

  const dropSector = (name: string) => {
    apply({ ...picks, sectors: picks.sectors.filter((entry) => entry !== name) });
    if (openGroup === name) setOpenGroup(null);
  };

  const toggleSector = (group: SectorGroup) => {
    if (takenSectors.has(group.name)) {
      dropSector(group.name);
      return;
    }
    takeSector(group);
    setOpenGroup(group.name);
  };

  const pickIndustry = (leaf: FacetCount) => {
    apply({ ...picks, industries: [...picks.industries, leaf.value] });
    setQuery("");
    setActive(0);
    // Left open, the list covers the very pills it just added to.
    setListOpen(false);
  };

  const removePill = (value: string) => {
    if (value.startsWith(SECTOR_TAG)) {
      dropSector(value.slice(SECTOR_TAG.length));
      return;
    }
    apply({ ...picks, industries: picks.industries.filter((entry) => entry !== value) });
  };

  const toggleAdjacent = (name: string) => {
    const group = byName.get(name);
    if (!group) return;
    if (takenSectors.has(name)) dropSector(name);
    else takeSector(group);
  };

  const onKeyDown = (event: React.KeyboardEvent<HTMLInputElement>) => {
    if (event.key === "ArrowDown") {
      event.preventDefault();
      setActive((index) => Math.min(index + 1, suggestions.length - 1));
    } else if (event.key === "ArrowUp") {
      event.preventDefault();
      setActive((index) => Math.max(index - 1, 0));
    } else if (event.key === "Enter") {
      event.preventDefault();
      const choice = suggestions[active];
      if (choice) pickIndustry(choice);
    } else if (event.key === "Escape") {
      setListOpen(false);
    }
  };

  const showList = listOpen && suggestions.length > 0;
  const showEmpty = listOpen && needle.length > 0 && suggestions.length === 0;

  const pills = [
    ...picks.sectors.map((name) => ({ value: `${SECTOR_TAG}${name}`, label: name })),
    ...picks.industries.map((value) => ({ value, label: labelOf(value) })),
  ];

  return (
    <div className="flex flex-col gap-3">
      <div className="relative">
        <div className="rounded-md border border-line bg-panel2">
          {pills.length > 0 && (
            <div className="flex flex-wrap gap-[5px] px-2 pt-2">
              {pills.map((pill) => (
                <SelectionPill
                  key={pill.value}
                  label={pill.label}
                  tone="amber"
                  onRemove={() => removePill(pill.value)}
                />
              ))}
            </div>
          )}
          <label className="flex h-9 items-center gap-2 px-[10px]">
            <Icon d={ICONS.search} size={13} className="flex-none text-text3" />
            <input
              role="combobox"
              aria-expanded={showList}
              aria-controls={LIST_ID}
              aria-autocomplete="list"
              aria-activedescendant={showList ? `${LIST_ID}-${active}` : undefined}
              value={query}
              placeholder="Search industries…"
              aria-label="Search industries"
              onChange={(event) => {
                setQuery(event.target.value);
                setActive(0);
                setListOpen(true);
              }}
              onFocus={() => setListOpen(true)}
              onBlur={() => {
                blurTimer.current = setTimeout(() => setListOpen(false), 120);
              }}
              onKeyDown={onKeyDown}
              className="w-full bg-transparent font-sans text-[12px] font-medium text-text outline-none placeholder:text-text3"
            />
          </label>
        </div>

        {showList && (
          <ul
            id={LIST_ID}
            role="listbox"
            className="absolute z-10 mt-1 max-h-64 w-full overflow-auto rounded-md border border-line bg-panel py-1 shadow-panel"
          >
            {suggestions.map((leaf, index) => (
              <li
                key={leaf.value}
                id={`${LIST_ID}-${index}`}
                role="option"
                aria-selected={index === active}
                // Commit before the input's blur fires and closes the list.
                onMouseDown={(event) => {
                  event.preventDefault();
                  if (blurTimer.current) clearTimeout(blurTimer.current);
                  pickIndustry(leaf);
                }}
                onMouseEnter={() => setActive(index)}
                className={cn(
                  "flex cursor-pointer items-center gap-2 px-3 py-[7px]",
                  index === active ? "bg-panel2" : "",
                )}
              >
                <span className="truncate font-sans text-[13px] font-medium text-text">
                  {leaf.label}
                </span>
                <span className="ms-auto flex flex-none items-center gap-2">
                  <span className="font-sans text-[10.5px] text-text3">{leaf.group}</span>
                  <span className="font-sans text-[11px] text-text3">
                    {leaf.count.toLocaleString()}
                  </span>
                </span>
              </li>
            ))}
          </ul>
        )}
        {showEmpty && (
          <div
            aria-live="polite"
            className="absolute z-10 mt-1 w-full rounded-md border border-line bg-panel px-3 py-2 font-sans text-[12px] text-text3 shadow-panel"
          >
            No industry matches that.
          </div>
        )}
      </div>

      <ScrollList empty={visibleGroups.length === 0 ? "No sector matches that" : null}>
        {visibleGroups.map((group) => {
          const taken = takenSectors.has(group.name);
          const partly =
            !taken && group.industries.some((industry) => reached.has(industry.value));
          return (
            <Row
              key={group.name}
              label={group.name}
              active={openGroup === group.name}
              checked={taken}
              partial={partly}
              onClick={() => toggleSector(group)}
            />
          );
        })}
      </ScrollList>

      {adjacent.length > 0 && (
        <div className="flex flex-col gap-2 border-t border-line-soft pt-3">
          <div className="flex items-center justify-between gap-2">
            <span className="flex items-center gap-1.5">
              <Icon d={SPARKLE} size={13} className="flex-none text-amber" />
              <span className="font-sans text-[13px] font-semibold text-text">
                Adjacent Industries
              </span>
            </span>
            <span className="font-sans text-[11px] text-text3">Based on your selection</span>
          </div>

          <div className="flex flex-wrap gap-[6px]">
            {(showAllAdjacent ? adjacent : adjacent.slice(0, ADJACENT_SHOWN)).map((name) => {
              const taken = takenSectors.has(name);
              return (
                <button
                  key={name}
                  type="button"
                  aria-pressed={taken}
                  onClick={() => toggleAdjacent(name)}
                  className={cn(
                    "inline-flex items-center gap-1 rounded-full border px-[9px] py-[5px] font-sans text-[12px] font-medium transition",
                    taken
                      ? "border-amber bg-amber-dim text-amber"
                      : "border-line text-text2 hover:border-amber hover:text-amber",
                  )}
                >
                  <Icon d={taken ? ICONS.check : ICONS.plus} size={10} className="flex-none" />
                  {name}
                </button>
              );
            })}
          </div>

          {adjacent.length > ADJACENT_SHOWN && (
            <button
              type="button"
              onClick={() => setShowAllAdjacent((shown) => !shown)}
              className="self-start font-sans text-[11px] text-text3 transition hover:text-text"
            >
              {showAllAdjacent
                ? "Show fewer"
                : `+ ${adjacent.length - ADJACENT_SHOWN} more sectors`}
            </button>
          )}
        </div>
      )}
    </div>
  );
}

/** Bounded: the panel must not grow past the rail whatever is inside it. */
function ScrollList({ children, empty }: { children: React.ReactNode; empty: string | null }) {
  return (
    <div className="max-h-[180px] overflow-y-auto rounded-md border border-line">
      {empty ? (
        <p className="px-3 py-4 text-center font-sans text-[12px] text-text3">{empty}</p>
      ) : (
        children
      )}
    </div>
  );
}

/**
 * One sector row. The label never changes weight or colour, so the eye follows one column of marks;
 * `active` is the open sector, a different fact from being selected.
 */
function Row({
  label,
  checked,
  partial,
  active,
  onClick,
}: {
  label: string;
  checked?: boolean;
  partial?: boolean;
  active?: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      role="checkbox"
      aria-checked={checked ? true : partial ? "mixed" : false}
      onClick={onClick}
      className={cn(
        "flex w-full items-center justify-between gap-2 px-3 py-[9px] text-left transition",
        active ? "bg-panel2" : "hover:bg-panel2",
      )}
    >
      <span className="truncate font-sans text-[13px] font-medium text-text">{label}</span>
      <span className="flex flex-none items-center gap-2">
        {checked ? (
          <Icon d={ICONS.check} size={13} className="text-amber" />
        ) : partial ? (
          <span className="size-[7px] rounded-full bg-amber" />
        ) : null}
      </span>
    </button>
  );
}
