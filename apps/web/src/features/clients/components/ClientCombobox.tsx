import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { Input } from "../../../components/ui";
import { CompanyLogo } from "../../../components/ui/CompanyLogo";
import { useComboboxList, useDebouncedValue } from "../../../lib/useComboboxList";
import { COMPANY_SEARCH_KEY, searchCompanies } from "../../strategy/api/companiesApi";
import type { CompanySuggestion } from "../../strategy/api/types";
import { useWorkspaceVocabulary } from "../../workspace/lib/vocabulary";
import type { Client } from "../api/types";
import { companyLocation } from "../lib/companyPick";

const LIST_ID = "client-options";
const MIN_QUERY_LENGTH = 2;

/**
 * An agency's client field: the clients already on the books, then the company database, then the
 * typed name as a new client. Like {@link BusinessUnitCombobox} the text is the value — a database
 * pick is reported separately so the caller can file the client under that company.
 */
export function ClientCombobox({
  value,
  clients,
  invalid,
  onChange,
  onPickCompany,
}: {
  value: string;
  clients: Client[];
  invalid?: boolean;
  onChange: (name: string) => void;
  onPickCompany: (company: CompanySuggestion) => void;
}) {
  const vocabulary = useWorkspaceVocabulary();
  const trimmed = value.trim();
  const query = trimmed.toLowerCase();
  const settled = useDebouncedValue(trimmed);

  const { data, isFetching, isError } = useQuery({
    queryKey: COMPANY_SEARCH_KEY(settled),
    queryFn: ({ signal }) => searchCompanies(settled, undefined, signal),
    enabled: settled.length >= MIN_QUERY_LENGTH,
    placeholderData: keepPreviousData,
  });

  const clientNames = new Set(clients.map((client) => client.name.toLowerCase()));
  const matches = query ? clients.filter((client) => client.name.toLowerCase().includes(query)) : clients;
  const hits =
    trimmed.length >= MIN_QUERY_LENGTH
      ? (data?.companies ?? []).filter((company) => !clientNames.has(company.companyName.toLowerCase()))
      : [];
  const offerNew = query !== "" && !clientNames.has(query);
  const optionCount = matches.length + hits.length + (offerNew ? 1 : 0);

  const list = useComboboxList({
    optionCount,
    autoHighlightFirst: false,
    onCommit: (index) => {
      if (index < matches.length) {
        onChange(matches[index].name);
        return;
      }
      const hit = hits[index - matches.length];
      if (hit) {
        onPickCompany(hit);
        return;
      }
      onChange(trimmed);
    },
  });

  const isSearching = trimmed.length >= MIN_QUERY_LENGTH && isFetching && hits.length === 0;
  const showList = list.open && (optionCount > 0 || isSearching || isError);
  const optionClass = (index: number) =>
    `flex cursor-pointer items-center gap-2 px-3 py-[7px] font-sans text-body ${
      index === list.active ? "bg-u-raised text-u-text" : "text-u-text2"
    }`;
  const groupClass =
    "border-t border-u-border px-3 pb-1 pt-2 font-mono text-[10px] font-semibold uppercase tracking-[0.12em] text-u-text3 first:border-t-0";
  const newIndex = matches.length + hits.length;

  return (
    <div className="relative">
      <Input
        role="combobox"
        invalid={invalid}
        aria-expanded={showList}
        aria-controls={LIST_ID}
        aria-autocomplete="list"
        aria-activedescendant={showList && list.active >= 0 ? `${LIST_ID}-${list.active}` : undefined}
        autoComplete="off"
        value={value}
        placeholder={`Search your ${vocabulary.unitsLower} or the company database`}
        onChange={(event) => {
          onChange(event.target.value);
          list.setActive(-1);
          list.setOpen(true);
        }}
        {...list.inputHandlers}
      />

      {showList && (
        <ul
          id={LIST_ID}
          role="listbox"
          aria-label={vocabulary.units}
          className="absolute z-10 mt-1 max-h-72 w-full overflow-auto rounded-[10px] border border-u-border-strong bg-u-surface py-1 shadow-u-e3"
        >
          {matches.length > 0 && (
            <li role="presentation" className={groupClass}>
              Your {vocabulary.unitsLower}
            </li>
          )}
          {matches.map((client, index) => (
            <li
              key={client.id}
              id={`${LIST_ID}-${index}`}
              role="option"
              aria-selected={index === list.active}
              onMouseDown={(event) => list.commitFromPointer(event, index)}
              onMouseEnter={() => list.setActive(index)}
              className={optionClass(index)}
            >
              <span className="truncate font-medium text-u-text">{client.name}</span>
            </li>
          ))}

          {(hits.length > 0 || isSearching || isError) && (
            <li role="presentation" className={groupClass}>
              From the company database
            </li>
          )}
          {isSearching && (
            <li role="presentation" className="px-3 py-2 font-mono text-[11.5px] text-u-text3">
              Searching…
            </li>
          )}
          {/* A refused read is not an empty universe: say so rather than leave only the new-client row. */}
          {isError && (
            <li role="presentation" className="px-3 py-2 font-mono text-[11.5px] text-u-offlimits">
              Couldn&apos;t reach the company database.
            </li>
          )}
          {hits.map((company, offset) => {
            const index = matches.length + offset;
            return (
              <li
                key={company.apolloAccountId}
                id={`${LIST_ID}-${index}`}
                role="option"
                aria-selected={index === list.active}
                onMouseDown={(event) => list.commitFromPointer(event, index)}
                onMouseEnter={() => list.setActive(index)}
                className={optionClass(index)}
              >
                <CompanyLogo name={company.companyName} logo={company.logoUrl} size={22} />
                <span className="min-w-0 flex-1">
                  <span className="block truncate font-medium text-u-text">{company.companyName}</span>
                  <span className="block truncate font-mono text-[11px] text-u-text3">
                    {companyLocation(company) || "—"}
                  </span>
                </span>
              </li>
            );
          })}

          {offerNew && (
            <li
              id={`${LIST_ID}-${newIndex}`}
              role="option"
              aria-selected={newIndex === list.active}
              onMouseDown={(event) => list.commitFromPointer(event, newIndex)}
              onMouseEnter={() => list.setActive(newIndex)}
              className={`${optionClass(newIndex)} ${newIndex > 0 ? "border-t border-u-border" : ""}`}
            >
              <span aria-hidden="true" className="text-u-accent">
                ＋
              </span>
              <span className="truncate">
                Add <span className="font-medium text-u-text">“{trimmed}”</span> as a new {vocabulary.unitLower}
              </span>
            </li>
          )}
        </ul>
      )}
    </div>
  );
}
