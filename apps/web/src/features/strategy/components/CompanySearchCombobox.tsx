import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { useEffect, useRef, useState } from "react";
import * as companiesApi from "../api/companiesApi";
import type { CompanySearchResult } from "../api/types";
import { companyKeyOf } from "../lib/companyKey";
import { CompanyLogo } from "./CompanyLogo";

/** How long a pause in typing must last before the query fires. */
const DEBOUNCE_MS = 250;

/**
 * The company-list picker over the shared universe. Focusing the empty box browses it — companies
 * in the strategy's direct sectors (all, when none are picked), ordered by revenue per list — and
 * any typed text switches to a name match instead. Server-backed and debounced (~54k companies
 * can't sit in memory); no add-on-blur, because a list entry must be a picked row, never a guessed
 * one.
 */
export function CompanySearchCombobox({
  listId,
  excludedKeys,
  browseSectors,
  browseOrder,
  onPick,
}: {
  listId: string;
  excludedKeys: Set<string>;
  browseSectors: string[];
  browseOrder: companiesApi.CompanySearchOrder;
  onPick: (company: CompanySearchResult) => void;
}) {
  const [draft, setDraft] = useState("");
  const [settled, setSettled] = useState("");
  const [active, setActive] = useState(0);
  const [open, setOpen] = useState(false);
  const blurTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    const timer = setTimeout(() => setSettled(draft.trim()), DEBOUNCE_MS);
    return () => clearTimeout(timer);
  }, [draft]);

  useEffect(
    () => () => {
      if (blurTimer.current) clearTimeout(blurTimer.current);
    },
    [],
  );

  const { data } = useQuery({
    queryKey: companiesApi.SEARCH_KEY(settled, browseSectors, browseOrder),
    queryFn: () => companiesApi.searchCompanies(settled, browseSectors, browseOrder),
    enabled: open,
    placeholderData: keepPreviousData,
  });

  const matches = (data?.companies ?? []).filter(
    (company) => !excludedKeys.has(companyKeyOf(company)),
  );
  const showList = open && matches.length > 0;
  const showEmpty = open && data !== undefined && matches.length === 0;

  const pick = (company: CompanySearchResult) => {
    onPick(company);
    setDraft("");
    setSettled("");
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
      const choice = matches[active];
      if (choice) pick(choice);
    } else if (event.key === "Escape") {
      setOpen(false);
    }
  };

  return (
    <div className="relative">
      <input
        role="combobox"
        aria-expanded={showList}
        aria-controls={listId}
        aria-autocomplete="list"
        aria-activedescendant={showList ? `${listId}-${active}` : undefined}
        value={draft}
        placeholder="Search companies…"
        aria-label="Search companies"
        onChange={(event) => {
          setDraft(event.target.value);
          setActive(0);
          setOpen(true);
        }}
        onFocus={() => setOpen(true)}
        onBlur={() => {
          blurTimer.current = setTimeout(() => setOpen(false), 120);
        }}
        onKeyDown={onKeyDown}
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
              key={companyKeyOf(company)}
              id={`${listId}-${index}`}
              role="option"
              aria-selected={index === active}
              // Commit before the input's blur fires and closes the list.
              onMouseDown={(event) => {
                event.preventDefault();
                if (blurTimer.current) clearTimeout(blurTimer.current);
                pick(company);
              }}
              onMouseEnter={() => setActive(index)}
              className={`flex cursor-pointer items-center gap-2.5 px-3 py-[7px] ${
                index === active ? "bg-panel2 text-text" : "text-text2"
              }`}
            >
              <CompanyLogo name={company.name} logo={company.logo} size={16} />
              <span className="truncate font-sans text-[13px] font-medium text-text">
                {company.name}
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

/** The muted context line: whichever of slogan, industry and location the row has. */
function metaOf(company: CompanySearchResult): string {
  const location = [company.hqCity, company.hqCountry].filter(Boolean).join(", ");
  return [company.slogan, company.primaryIndustry, location].filter(Boolean).join(" · ");
}
