import { useState } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { cn } from "../../../lib/cn";
import type { CompanyRef, Facets, NumericRange, StrategyFilter } from "../api/types";
import { FacetsUnavailable } from "./FacetsUnavailable";
import { FilterAccordion, type SelectedTag } from "./FilterAccordion";
import { FilterCheckRow } from "../../../components/ui/FilterCheckRow";
import { FilterChip } from "./FilterChip";
import { IndustryFilter } from "./IndustryFilter";
import { KeywordFilter } from "./KeywordFilter";
import { OffLimitsFilter } from "./OffLimitsFilter";
import { RangeFilter } from "./RangeFilter";

/** Which accordion keys exist, in the order the rail renders them. */
type AccordionKey = "location" | "employees" | "revenue" | "industry" | "segments" | "offlimits";

/** Every axis the filter stores as a plain list of wire values. */
type ListAxis = "industries" | "countries" | "employeeBands" | "revenueBands" | "marketSegments";

/** Pill identities for the two summaries that are not a plain facet value. */
const RANGE_TAG = "__range__";
const KEYWORD_TAG = "keyword:";

/**
 * The filter rail: a share of the results row, floored at 300px and capped at 360px. `shrink-0`
 * because below the floor the accordion labels wrap.
 *
 * <p>Single-open, like the wireframe: opening one closes the last, and clicking an open header closes
 * it. That is a real constraint rather than a stylistic one — Industry alone is twenty groups over
 * 148 labels, so two open panels would put the second below the fold.
 *
 * <p><b>Each axis gets the control its values deserve, which is the wireframe's point.</b> Location
 * is six GCC countries and reads as pills, where the shape of the set is the information. Employees,
 * Revenue and Market Segments are ordered or long, so they are checkbox lists — wrapped pills lose
 * the order of an ordered axis and turn a long one into a wall. Industry and its keywords are
 * vocabularies too long to browse at all, so both are tag boxes — and they share one accordion,
 * because keywords narrow within the industries rather than standing beside them.
 *
 * <p><b>There is no Ownership accordion.</b> The wireframe has one; the universe carries no ownership
 * column, and nothing derivable from {@code latest_funding} (2,123 rows of 71,822) or
 * {@code parent_company} (1,811) is honest at that coverage. An accordion whose every row counts zero
 * is worse than one that is not there — it reads as broken rather than as absent.
 */
export function FilterSidebar({
  facets,
  facetsError,
  filter,
  offLimits,
  onChange,
  onOffLimitsChange,
  onClose,
}: {
  facets: Facets | undefined;
  /** The counts were refused. Absent counts and refused counts look identical without this. */
  facetsError: boolean;
  filter: StrategyFilter;
  offLimits: CompanyRef[];
  onChange: (filter: StrategyFilter) => void;
  onOffLimitsChange: (apolloAccountIds: string[]) => void;
  /** Dismisses the rail where it overlays the results, below `lg`. */
  onClose: () => void;
}) {
  const [open, setOpen] = useState<AccordionKey | null>("location");

  const toggleOpen = (key: AccordionKey) => setOpen((current) => (current === key ? null : key));

  const toggleValue = (axis: ListAxis, value: string) => {
    const current = filter[axis];
    const next = current.includes(value)
      ? current.filter((entry) => entry !== value)
      : [...current, value];
    onChange({ ...filter, [axis]: next });
  };

  /** Leaving Custom Range clears the typed bounds; entering it clears the ticked bands. */
  const setRange = (
    axis: "employee" | "revenue",
    range: NumericRange | null,
  ) => {
    const rangeKey = axis === "employee" ? "employeeRange" : "revenueRange";
    const bandKey = axis === "employee" ? "employeeBands" : "revenueBands";
    onChange({ ...filter, [rangeKey]: range, ...(range ? { [bandKey]: [] } : {}) });
  };

  const tagsOf = (axis: ListAxis) => {
    const options = {
      industries: facets?.sectorGroups.flatMap((group) => group.industries),
      countries: facets?.countries,
      employeeBands: facets?.employeeBands,
      revenueBands: facets?.revenueBands,
      marketSegments: facets?.marketSegments,
    }[axis];
    // Fall back to the stored value: a pill whose facet has not loaded still has to name itself.
    return filter[axis].map((value) => ({
      value,
      label: options?.find((option) => option.value === value)?.label ?? value,
    }));
  };

  /** A custom range summarises as one pill, since its two numbers are one decision. */
  const rangeTag = (range: NumericRange | null): SelectedTag[] => {
    const tag = (label: string) => [{ value: RANGE_TAG, label }];
    if (!range) return [];
    if (range.min !== null && range.max !== null) return tag(`${range.min}-${range.max}`);
    if (range.min !== null) return tag(`${range.min}+`);
    if (range.max !== null) return tag(`≤${range.max}`);
    return [];
  };

  // One panel, so one pill row. A keyword is prefixed because an industry and a keyword can be the
  // same word, and the two pills have to remove different things.
  const industryTags = (): SelectedTag[] => [
    ...tagsOf("industries"),
    ...filter.keywords.map((value) => ({ value: `${KEYWORD_TAG}${value}`, label: value })),
  ];

  const removeIndustryOrKeyword = (value: string) => {
    if (!value.startsWith(KEYWORD_TAG)) {
      toggleValue("industries", value);
      return;
    }
    const keyword = value.slice(KEYWORD_TAG.length);
    onChange({ ...filter, keywords: filter.keywords.filter((entry) => entry !== keyword) });
  };

  const removeBandOrRange = (axis: "employee" | "revenue", value: string) => {
    if (value === RANGE_TAG) setRange(axis, null);
    else toggleValue(axis === "employee" ? "employeeBands" : "revenueBands", value);
  };

  return (
    <div
      role="region"
      aria-label="Filters"
      className={cn(
        "overflow-y-auto border-line-soft bg-panel",
        // A 300px rail beside the table does not fit a phone, so below `lg` it overlays the results.
        "fixed inset-y-0 start-0 z-[95] w-[min(320px,88vw)] border-e shadow-panel",
        "lg:static lg:z-auto lg:w-[22%] lg:min-w-[300px] lg:max-w-[360px] lg:shrink-0 lg:shadow-none",
      )}
    >
      <div className="flex items-center justify-between border-b border-line-soft px-4 py-2.5 lg:hidden">
        <span className="font-mono text-[10px] font-semibold uppercase tracking-[0.14em] text-text3">
          Filters
        </span>
        <button
          type="button"
          onClick={onClose}
          aria-label="Hide filters"
          className="flex size-8 items-center justify-center rounded-[6px] text-text3 transition hover:bg-panel2 hover:text-text"
        >
          <Icon d={ICONS.close} size={16} />
        </button>
      </div>
      <FilterAccordion
        label="Location"
        selected={tagsOf("countries")}
        onRemove={(value) => toggleValue("countries", value)}
        open={open === "location"}
        onToggleOpen={() => toggleOpen("location")}
        onReset={() => onChange({ ...filter, countries: [] })}
      >
        {facetsError ? (
          <FacetsUnavailable />
        ) : facets ? (
          <div className="flex flex-wrap gap-2">
            {facets.countries.map((option) => (
              <FilterChip
                key={option.value}
                label={option.label}
                count={option.count}
                selected={filter.countries.includes(option.value)}
                onToggle={() => toggleValue("countries", option.value)}
              />
            ))}
          </div>
        ) : (
          <ChipSkeleton />
        )}
      </FilterAccordion>

      <FilterAccordion
        label="# Employees"
        selected={[...rangeTag(filter.employeeRange), ...tagsOf("employeeBands")]}
        onRemove={(value) => removeBandOrRange("employee", value)}
        open={open === "employees"}
        onToggleOpen={() => toggleOpen("employees")}
        onReset={() => onChange({ ...filter, employeeBands: [], employeeRange: null })}
      >
        <RangeFilter
          options={facets?.employeeBands}
          unavailable={facetsError}
          selectedBands={filter.employeeBands}
          range={filter.employeeRange}
          onToggleBand={(value) => toggleValue("employeeBands", value)}
          onRangeChange={(range) => setRange("employee", range)}
        />
      </FilterAccordion>

      <FilterAccordion
        label="Revenue"
        selected={[...rangeTag(filter.revenueRange), ...tagsOf("revenueBands")]}
        onRemove={(value) => removeBandOrRange("revenue", value)}
        open={open === "revenue"}
        onToggleOpen={() => toggleOpen("revenue")}
        onReset={() => onChange({ ...filter, revenueBands: [], revenueRange: null })}
      >
        <RangeFilter
          options={facets?.revenueBands}
          unavailable={facetsError}
          selectedBands={filter.revenueBands}
          range={filter.revenueRange}
          onToggleBand={(value) => toggleValue("revenueBands", value)}
          onRangeChange={(range) => setRange("revenue", range)}
          minPlaceholder="Min $"
          maxPlaceholder="Max $"
          footnote={
            /* Nine companies in ten publish no revenue figure, so any band silently drops them.
               The Unknown row is the only way to reach those, and this line is why it exists. */
            <p className="font-sans text-[11px] leading-relaxed text-text3">
              Most companies publish no revenue figure. Tick{" "}
              <b className="text-text2">Unknown</b> to keep them in scope.
            </p>
          }
        />
      </FilterAccordion>

      <FilterAccordion
        label="Industry & Keywords"
        selected={industryTags()}
        onRemove={removeIndustryOrKeyword}
        open={open === "industry"}
        onToggleOpen={() => toggleOpen("industry")}
        onReset={() => onChange({ ...filter, industries: [], keywords: [] })}
      >
        {facetsError ? (
          <FacetsUnavailable />
        ) : facets ? (
          <IndustryFilter
            groups={facets.sectorGroups}
            adjacency={facets.adjacentIndustries}
            selected={filter.industries}
            onChange={(industries) => onChange({ ...filter, industries })}
          >
            <div className="border-t border-line-soft pt-3">
              <KeywordFilter
                selected={filter.keywords}
                onChange={(keywords) => onChange({ ...filter, keywords })}
              />
            </div>
          </IndustryFilter>
        ) : (
          <ChipSkeleton />
        )}
      </FilterAccordion>

      <FilterAccordion
        label="Market Segments"
        selected={tagsOf("marketSegments")}
        onRemove={(value) => toggleValue("marketSegments", value)}
        open={open === "segments"}
        onToggleOpen={() => toggleOpen("segments")}
        onReset={() => onChange({ ...filter, marketSegments: [] })}
      >
        <div className="flex flex-col gap-[2px]">
          {facetsError ? (
            <FacetsUnavailable />
          ) : facets ? (
            facets.marketSegments.map((option) => (
              <FilterCheckRow
                key={option.value}
                label={option.label}
                count={option.count}
                checked={filter.marketSegments.includes(option.value)}
                onToggle={() => toggleValue("marketSegments", option.value)}
              />
            ))
          ) : (
            <ChipSkeleton />
          )}
        </div>
      </FilterAccordion>

      <FilterAccordion
        label="Off-limits"
        selected={offLimits.map((company) => ({
          value: company.apolloAccountId,
          label: company.companyName,
        }))}
        onRemove={(value) =>
          onOffLimitsChange(
            offLimits
              .filter((company) => company.apolloAccountId !== value)
              .map((company) => company.apolloAccountId),
          )
        }
        tagTone="red"
        open={open === "offlimits"}
        onToggleOpen={() => toggleOpen("offlimits")}
        onReset={() => onOffLimitsChange([])}
      >
        <OffLimitsFilter companies={offLimits} onChange={onOffLimitsChange} />
      </FilterAccordion>
    </div>
  );
}

function ChipSkeleton() {
  return (
    <div className="flex flex-wrap gap-2">
      {[72, 96, 60, 84].map((width) => (
        <div
          key={width}
          style={{ width }}
          className="h-[34px] animate-pulse rounded-full border border-line-soft bg-panel"
        />
      ))}
    </div>
  );
}
