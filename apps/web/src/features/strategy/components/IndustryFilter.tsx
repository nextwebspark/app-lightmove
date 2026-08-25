import { useEffect, useMemo, useRef, useState } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { cn } from "../../../lib/cn";
import type { SectorGroup } from "../api/types";
import { adjacentTo } from "../lib/sectorAdjacency";
import { CheckBox } from "./FilterCheckRow";
import { SelectionPill } from "./SelectionPill";

const SPARKLE =
  "M9.9 2.6 11 5.9a2 2 0 0 0 1.3 1.3l3.3 1.1-3.3 1.1a2 2 0 0 0-1.3 1.3L9.9 14l-1.1-3.3a2 2 0 0 0-1.3-1.3L4.2 8.3l3.3-1.1a2 2 0 0 0 1.3-1.3ZM18 14l.6 1.8 1.8.6-1.8.6-.6 1.8-.6-1.8-1.8-.6 1.8-.6Z";

const ADJACENT_SHOWN = 6;

interface Picks {
  sectors: string[];
  subIndustries: string[];
  includeSubIndustries: boolean;
}

/**
 * A selected sector with no leaf picked contributes *all* of its leaves. That is what makes Include
 * Sub-Industries safe to tick: refining never empties the results and never asks for anything to be
 * deselected first — a sector stays whole until a leaf under it is chosen, and then only that sector
 * narrows.
 */
function industriesOf(picks: Picks, byName: Map<string, SectorGroup>): string[] {
  const picked = new Set(picks.subIndustries);
  const reached: string[] = [];
  for (const name of picks.sectors) {
    const group = byName.get(name);
    if (!group) continue;
    const leaves = group.industries.map((industry) => industry.value);
    const narrowed = picks.includeSubIndustries ? leaves.filter((leaf) => picked.has(leaf)) : [];
    reached.push(...(narrowed.length > 0 ? narrowed : leaves));
  }
  return [...new Set(reached)];
}

/** A partly-taken sector can only have come from refining, so it is what reopens the panel ticked. */
function picksFrom(selected: string[], groups: SectorGroup[]): Picks {
  const chosen = new Set(selected);
  const sectors: string[] = [];
  let refined = false;
  for (const group of groups) {
    const taken = group.industries.filter((industry) => chosen.has(industry.value)).length;
    if (taken === 0) continue;
    sectors.push(group.name);
    if (taken < group.industries.length) refined = true;
  }
  return { sectors, subIndustries: refined ? [...selected] : [], includeSubIndustries: refined };
}

const sameSet = (a: string[], b: string[]) =>
  a.length === b.length && a.every((entry) => b.includes(entry));

/**
 * The Industry panel: a sector list, the sub-industries of whichever sector is open, and the sectors
 * beside it. Selecting a sector stores its industries, never its name — the grouping is editorial
 * and will be re-tuned, and a saved search must not widen because a label moved.
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
  const [groupQuery, setGroupQuery] = useState("");
  const [industryQuery, setIndustryQuery] = useState("");
  const [showAllAdjacent, setShowAllAdjacent] = useState(false);
  const [picks, setPicks] = useState<Picks>(() => picksFrom(selected, groups));

  const byName = useMemo(() => new Map(groups.map((group) => [group.name, group])), [groups]);

  // Only a filter replaced from outside — Reset, or a saved search applied — has to rebuild the
  // picks; our own writes come back as the value we just emitted.
  const emitted = useRef<string[]>(selected);
  useEffect(() => {
    if (sameSet(selected, emitted.current)) return;
    emitted.current = selected;
    setPicks(picksFrom(selected, groups));
  }, [selected, groups]);

  const apply = (next: Picks) => {
    setPicks(next);
    const industries = industriesOf(next, byName);
    emitted.current = industries;
    onChange(industries);
  };

  const takenSectors = useMemo(() => new Set(picks.sectors), [picks.sectors]);
  const pickedSubs = useMemo(() => new Set(picks.subIndustries), [picks.subIndustries]);

  const visibleGroups = useMemo(() => {
    const needle = groupQuery.trim().toLowerCase();
    if (!needle) return groups;
    // Matching on the industries inside a sector too, so "aws" finds Technology without the
    // consultant knowing where we filed it.
    return groups.filter(
      (group) =>
        group.name.toLowerCase().includes(needle) ||
        group.industries.some((industry) => industry.label.toLowerCase().includes(needle)),
    );
  }, [groups, groupQuery]);

  const open = openGroup === null ? null : (byName.get(openGroup) ?? null);

  const visibleIndustries = useMemo(() => {
    if (!open) return [];
    const needle = industryQuery.trim().toLowerCase();
    if (!needle) return open.industries;
    return open.industries.filter((industry) => industry.label.toLowerCase().includes(needle));
  }, [open, industryQuery]);

  /**
   * Anchored to the open sector so the list does not move: deriving it from everything selected made
   * taking one suggestion drop that chip and fold in new ones, shifting whatever was about to be
   * clicked next. A taken neighbour stays, rendered as taken.
   */
  const adjacent = useMemo(() => (open ? adjacentTo(open, byName) : []), [open, byName]);

  const dropSector = (name: string) => {
    const leaves = new Set(byName.get(name)?.industries.map((industry) => industry.value) ?? []);
    apply({
      ...picks,
      sectors: picks.sectors.filter((entry) => entry !== name),
      subIndustries: picks.subIndustries.filter((entry) => !leaves.has(entry)),
    });
    if (openGroup === name) setOpenGroup(null);
  };

  const takeSector = (name: string) => apply({ ...picks, sectors: [...picks.sectors, name] });

  const toggleSector = (group: SectorGroup) => {
    if (takenSectors.has(group.name)) {
      dropSector(group.name);
      return;
    }
    takeSector(group.name);
    setOpenGroup(group.name);
    setIndustryQuery("");
  };

  const toggleSubIndustry = (value: string) => {
    apply({
      ...picks,
      subIndustries: pickedSubs.has(value)
        ? picks.subIndustries.filter((entry) => entry !== value)
        : [...picks.subIndustries, value],
    });
  };

  const toggleIncludeSubIndustries = () => {
    const including = !picks.includeSubIndustries;
    apply({
      ...picks,
      includeSubIndustries: including,
      subIndustries: including ? picks.subIndustries : [],
    });
  };

  // Deliberately does not open the sector: swapping the sub-industry list out mid-click is the
  // other half of the churn this panel had.
  const toggleAdjacent = (name: string) => {
    if (!byName.has(name)) return;
    if (takenSectors.has(name)) dropSector(name);
    else takeSector(name);
  };

  return (
    <div className="flex flex-col gap-3">
      <PillBox
        pills={picks.sectors.map((name) => ({ value: name, label: name }))}
        onRemove={dropSector}
        query={groupQuery}
        onQueryChange={setGroupQuery}
        placeholder="Search industries…"
        label="Search industries"
      />

      <ScrollList empty={visibleGroups.length === 0 ? "No sector matches that" : null}>
        {visibleGroups.map((group) => {
          const taken = takenSectors.has(group.name);
          const picked = group.industries.filter((industry) =>
            pickedSubs.has(industry.value),
          ).length;
          const narrowed =
            taken && picks.includeSubIndustries && picked > 0 && picked < group.industries.length;
          return (
            <Row
              key={group.name}
              label={group.name}
              active={openGroup === group.name}
              checked={taken && !narrowed}
              partial={narrowed}
              onClick={() => toggleSector(group)}
            />
          );
        })}
      </ScrollList>

      <div
        className={cn(
          "flex flex-col gap-3 rounded-md border p-2 transition",
          picks.includeSubIndustries ? "border-line bg-transparent" : "border-transparent bg-panel2",
        )}
      >
        <button
          type="button"
          role="checkbox"
          aria-checked={picks.includeSubIndustries}
          onClick={toggleIncludeSubIndustries}
          className="flex items-center gap-3 px-1 py-1 text-left"
        >
          <CheckBox checked={picks.includeSubIndustries} />
          <span className="font-sans text-[14px] font-medium text-text">
            Include Sub-Industries
          </span>
        </button>

        {picks.includeSubIndustries &&
          (open ? (
            <>
              <PillBox
                pills={open.industries
                  .filter((industry) => pickedSubs.has(industry.value))
                  .map((industry) => ({ value: industry.value, label: industry.label }))}
                onRemove={toggleSubIndustry}
                query={industryQuery}
                onQueryChange={setIndustryQuery}
                placeholder={`Search within ${open.name}…`}
                label={`Search within ${open.name}`}
              />

              <ScrollList
                empty={visibleIndustries.length === 0 ? "No industry matches that" : null}
              >
                {visibleIndustries.map((industry) => (
                  <Row
                    key={industry.value}
                    label={industry.label}
                    hint={industry.count.toLocaleString()}
                    checked={pickedSubs.has(industry.value)}
                    onClick={() => toggleSubIndustry(industry.value)}
                  />
                ))}
              </ScrollList>
            </>
          ) : (
            <p className="px-1 pb-1 font-sans text-[12px] text-text3">
              Pick an industry above to narrow it to sub-industries.
            </p>
          ))}
      </div>

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

/** The search field with what it has already taken sitting inside it, each pill removable. */
function PillBox({
  pills,
  onRemove,
  query,
  onQueryChange,
  placeholder,
  label,
}: {
  pills: { value: string; label: string }[];
  onRemove: (value: string) => void;
  query: string;
  onQueryChange: (value: string) => void;
  placeholder: string;
  label: string;
}) {
  return (
    <div className="rounded-md border border-line bg-panel2">
      {pills.length > 0 && (
        <div className="flex flex-wrap gap-[5px] px-2 pt-2">
          {pills.map((pill) => (
            <SelectionPill
              key={pill.value}
              label={pill.label}
              tone="amber"
              onRemove={() => onRemove(pill.value)}
            />
          ))}
        </div>
      )}
      <label className="flex h-9 items-center gap-2 px-[10px]">
        <Icon d={ICONS.search} size={13} className="flex-none text-text3" />
        <input
          value={query}
          onChange={(event) => onQueryChange(event.target.value)}
          placeholder={placeholder}
          aria-label={label}
          className="w-full bg-transparent font-sans text-[12px] font-medium text-text outline-none placeholder:text-text3"
        />
      </label>
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
 * One row of either list. The label never changes weight or colour, so the eye follows one column of
 * marks; `active` is the open sector, a different fact from being selected.
 */
function Row({
  label,
  hint,
  checked,
  partial,
  active,
  onClick,
}: {
  label: string;
  hint?: string;
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
        {hint && <span className="font-sans text-[11px] text-text3">{hint}</span>}
        {checked ? (
          <Icon d={ICONS.check} size={13} className="text-amber" />
        ) : partial ? (
          <span className="size-[7px] rounded-full bg-amber" />
        ) : null}
      </span>
    </button>
  );
}
