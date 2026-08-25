import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { useState } from "react";
import * as companiesApi from "../api/companiesApi";
import type { CompanySuggestion } from "../api/types";
import { CompanyLogo } from "../../../components/ui/CompanyLogo";
import { useComboboxList, useDebouncedValue } from "../lib/useComboboxList";

/**
 * The company picker over the universe — the off-limits list and the client registry both use it.
 *
 * <p>Server-backed and debounced: 71,822 companies cannot sit in memory. A blank box offers nothing
 * rather than browsing, because six arbitrary companies before a key is pressed suggests they were
 * chosen for a reason. No add-on-blur either — an entry must be a picked row, never a guessed one.
 */
export function CompanySearchCombobox({
  listId,
  excludedIds,
  onPick,
}: {
  listId: string;
  excludedIds: Set<string>;
  onPick: (company: CompanySuggestion) => void;
}) {
  const [draft, setDraft] = useState("");
  const settled = useDebouncedValue(draft.trim());

  const { data } = useQuery({
    queryKey: companiesApi.COMPANY_SEARCH_KEY(settled),
    queryFn: ({ signal }) => companiesApi.searchCompanies(settled, undefined, signal),
    enabled: settled.length > 0,
    placeholderData: keepPreviousData,
  });

  const matches = (data?.companies ?? []).filter(
    (company) => !excludedIds.has(company.apolloAccountId),
  );

  const list = useComboboxList({
    optionCount: matches.length,
    onCommit: (index) => {
      const choice = matches[index];
      if (choice) {
        onPick(choice);
        setDraft("");
      }
    },
  });

  // `settled` and not `matches` alone: keepPreviousData keeps serving the last query's rows after a
  // pick clears the box, so without this the list reopens itself over an empty input.
  const showList = list.open && settled.length > 0 && matches.length > 0;
  const showEmpty = list.open && settled.length > 0 && data !== undefined && matches.length === 0;

  return (
    <div className="relative">
      <input
        role="combobox"
        aria-expanded={showList}
        aria-controls={listId}
        aria-autocomplete="list"
        aria-activedescendant={showList ? `${listId}-${list.active}` : undefined}
        value={draft}
        placeholder="Search companies…"
        aria-label="Search companies"
        onChange={(event) => {
          setDraft(event.target.value);
          list.setActive(0);
          list.setOpen(true);
        }}
        {...list.inputHandlers}
        className="w-full rounded-lg border border-line bg-panel px-[11px] py-2 font-mono text-[13px] text-text outline-none focus:border-sky"
      />

      {showList && (
        <ul
          id={listId}
          role="listbox"
          className="absolute z-10 mt-1 max-h-72 w-full overflow-auto rounded-[10px] border border-line bg-panel py-1 shadow-panel"
        >
          {matches.map((company, index) => (
            <li
              key={company.apolloAccountId}
              id={`${listId}-${index}`}
              role="option"
              aria-selected={index === list.active}
              onMouseDown={(event) => list.commitFromPointer(event, index)}
              onMouseEnter={() => list.setActive(index)}
              className={`flex cursor-pointer items-center gap-2.5 px-3 py-[7px] ${
                index === list.active ? "bg-panel2 text-text" : "text-text2"
              }`}
            >
              <CompanyLogo name={company.companyName} logo={company.logoUrl} size={16} />
              <span className="truncate font-sans text-[13px] font-medium text-text">
                {company.companyName}
              </span>
              <span className="min-w-0 flex-1 truncate text-right font-mono text-[10.5px] text-text3">
                {metaOf(company)}
              </span>
            </li>
          ))}
        </ul>
      )}
      {showEmpty && (
        <div
          aria-live="polite"
          className="absolute z-10 mt-1 w-full rounded-[10px] border border-line bg-panel px-3 py-2 font-mono text-[12px] text-text3 shadow-panel"
        >
          No companies found.
        </div>
      )}
    </div>
  );
}

/** The muted context line: whichever of industry and location the row has. */
function metaOf(company: CompanySuggestion): string {
  const location = [company.companyCity, company.companyCountry].filter(Boolean).join(", ");
  return [company.industry, location].filter(Boolean).join(" · ");
}
