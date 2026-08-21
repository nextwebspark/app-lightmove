import { useMemo, useState } from "react";
import { Icon } from "../../../components/layout/Icon";
import { cn } from "../../../lib/cn";
import type { SectorGroup } from "../api/types";
import { adjacentTo } from "../lib/sectorAdjacency";
import { CheckBox } from "./FilterCheckRow";

const SEARCH = "M11 3a8 8 0 1 0 0 16 8 8 0 0 0 0-16Zm10 18-4.3-4.3";
const CHECK = "m5 13 4 4L19 7";
const PLUS = "M12 5v14M5 12h14";
const SPARKLE =
  "M9.9 2.6 11 5.9a2 2 0 0 0 1.3 1.3l3.3 1.1-3.3 1.1a2 2 0 0 0-1.3 1.3L9.9 14l-1.1-3.3a2 2 0 0 0-1.3-1.3L4.2 8.3l3.3-1.1a2 2 0 0 0 1.3-1.3ZM18 14l.6 1.8 1.8.6-1.8.6-.6 1.8-.6-1.8-1.8-.6 1.8-.6Z";

const ADJACENT_SHOWN = 6;

/**
 * The Industry panel: a sector list, the sub-industries of whichever sector is open, and the
 * sectors beside it.
 *
 * <p>Selecting a sector stores its industries, never its name — the grouping is editorial and will
 * be re-tuned, and a saved search must not widen because a label moved.
 *
 * <p>Include Sub-Industries is what a sector click means: ticked it takes the whole sector,
 * unticked the sector is only a lens and nothing enters the filter until an industry is picked.
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
  const [includeSubIndustries, setIncludeSubIndustries] = useState(true);
  const [groupQuery, setGroupQuery] = useState("");
  const [industryQuery, setIndustryQuery] = useState("");
  const [showAllAdjacent, setShowAllAdjacent] = useState(false);

  const chosen = useMemo(() => new Set(selected), [selected]);
  const byName = useMemo(
    () => new Map(groups.map((group) => [group.name, group])),
    [groups],
  );

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

  const toggleIndustry = (value: string) => {
    onChange(
      chosen.has(value) ? selected.filter((entry) => entry !== value) : [...selected, value],
    );
  };

  const selectedCountIn = (group: SectorGroup) =>
    group.industries.filter((entry) => chosen.has(entry.value)).length;

  /** Take a whole sector, or give it back if it is already wholly taken. */
  const toggleWholeGroup = (group: SectorGroup) => {
    const values = group.industries.map((entry) => entry.value);
    const allTaken = values.every((value) => chosen.has(value));
    onChange(
      allTaken
        ? selected.filter((entry) => !values.includes(entry))
        : [...new Set([...selected, ...values])],
    );
  };

  const openAndMaybeTake = (group: SectorGroup) => {
    setOpenGroup(group.name);
    setIndustryQuery("");
    if (includeSubIndustries) toggleWholeGroup(group);
  };

  // Deliberately does not open the sector: swapping the sub-industry list out mid-click is the
  // other half of the churn this panel had.
  const toggleAdjacent = (name: string) => {
    const group = byName.get(name);
    if (group) toggleWholeGroup(group);
  };

  return (
    <div className="flex flex-col gap-3">
      <SearchBox
        value={groupQuery}
        onChange={setGroupQuery}
        placeholder="Search industries…"
        label="Search industries"
      />

      <ScrollList empty={visibleGroups.length === 0 ? "No sector matches that" : null}>
        {visibleGroups.map((group) => {
          const taken = selectedCountIn(group);
          return (
            <Row
              key={group.name}
              label={group.name}
              // What this sector contributes to the filter, not its size in the universe.
              hint={taken > 0 ? `${taken}/${group.industries.length}` : undefined}
              active={openGroup === group.name}
              checked={taken > 0 && taken === group.industries.length}
              partial={taken > 0 && taken < group.industries.length}
              onClick={() => openAndMaybeTake(group)}
            />
          );
        })}
      </ScrollList>

      <button
        type="button"
        role="checkbox"
        aria-checked={includeSubIndustries}
        onClick={() => setIncludeSubIndustries((on) => !on)}
        className="flex items-center gap-3 px-1 py-1 text-left"
      >
        <CheckBox checked={includeSubIndustries} />
        <span className="font-sans text-[14px] font-medium text-text">Include Sub-Industries</span>
      </button>

      {open && (
        <>
          <SearchBox
            value={industryQuery}
            onChange={setIndustryQuery}
            placeholder={`Search within ${open.name}…`}
            label={`Search within ${open.name}`}
          />

          <ScrollList empty={visibleIndustries.length === 0 ? "No industry matches that" : null}>
            {visibleIndustries.map((industry) => (
              <Row
                key={industry.value}
                label={industry.label}
                hint={industry.count.toLocaleString()}
                checked={chosen.has(industry.value)}
                onClick={() => toggleIndustry(industry.value)}
              />
            ))}
          </ScrollList>
        </>
      )}

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
              const group = byName.get(name)!;
              const taken = selectedCountIn(group) > 0;
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
                  <Icon d={taken ? CHECK : PLUS} size={10} className="flex-none" />
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

function SearchBox({
  value,
  onChange,
  placeholder,
  label,
}: {
  value: string;
  onChange: (value: string) => void;
  placeholder: string;
  label: string;
}) {
  return (
    <label className="flex h-9 items-center gap-2 rounded-md border border-line bg-panel2 px-[10px]">
      <Icon d={SEARCH} size={13} className="flex-none text-text3" />
      <input
        value={value}
        onChange={(event) => onChange(event.target.value)}
        placeholder={placeholder}
        aria-label={label}
        className="w-full bg-transparent font-sans text-[12px] font-medium text-text outline-none placeholder:text-text3"
      />
    </label>
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
 * One row of either list. The tick sits on the right and the label never changes weight or colour,
 * so the eye follows one column of marks. `active` is the open sector, a different fact from being
 * selected, so it gets a different signal.
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
          <Icon d={CHECK} size={13} className="text-amber" />
        ) : partial ? (
          <span className="size-[7px] rounded-full bg-amber" />
        ) : null}
      </span>
    </button>
  );
}
