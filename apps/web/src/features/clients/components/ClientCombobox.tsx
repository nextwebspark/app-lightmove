import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { Input } from "../../../components/ui";
import { CompanyLogo } from "../../../components/ui/CompanyLogo";
import { useComboboxList, useDebouncedValue } from "../../../lib/useComboboxList";
import { COMPANY_SEARCH_KEY, searchCompanies } from "../../strategy/api/companiesApi";
import type { CompanySuggestion } from "../../strategy/api/types";
import { useWorkspaceVocabulary } from "../../workspace/lib/vocabulary";
import type { Client } from "../api/types";
import { companyLocation } from "../lib/companyPick";
import { ComboboxGroupLabel, ComboboxNote, ComboboxOption, NewNameOption } from "./ComboboxRows";

const LIST_ID = "client-options";
const MIN_QUERY_LENGTH = 2;
/** Enough to recognise a client; more would push the database group out of the list's height. */
const MAX_CLIENT_MATCHES = 6;

/** An agency's client field: its clients, then the company database, then the typed name as a new client. */
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
  const matches = (
    query ? clients.filter((client) => client.name.toLowerCase().includes(query)) : clients
  ).slice(0, MAX_CLIENT_MATCHES);
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
          {matches.length > 0 && <ComboboxGroupLabel>Your {vocabulary.unitsLower}</ComboboxGroupLabel>}
          {matches.map((client, index) => (
            <ComboboxOption key={client.id} listId={LIST_ID} index={index} list={list}>
              <span className="truncate font-medium text-u-text">{client.name}</span>
            </ComboboxOption>
          ))}

          {(hits.length > 0 || isSearching || isError) && (
            <ComboboxGroupLabel>From the company database</ComboboxGroupLabel>
          )}
          {isSearching && <ComboboxNote>Searching…</ComboboxNote>}
          {/* A refused read is not an empty universe: say so rather than leave only the new-client row. */}
          {isError && <ComboboxNote tone="error">Couldn&apos;t reach the company database.</ComboboxNote>}
          {hits.map((company, offset) => (
            <ComboboxOption
              key={company.apolloAccountId}
              listId={LIST_ID}
              index={matches.length + offset}
              list={list}
            >
              <CompanyLogo name={company.companyName} logo={company.logoUrl} size={22} />
              <span className="min-w-0 flex-1">
                <span className="block truncate font-medium text-u-text">{company.companyName}</span>
                <span className="block truncate font-mono text-[11px] text-u-text3">
                  {companyLocation(company) || "—"}
                </span>
              </span>
            </ComboboxOption>
          ))}

          {offerNew && (
            <NewNameOption listId={LIST_ID} index={matches.length + hits.length} list={list}>
              Add <span className="font-medium text-u-text">“{trimmed}”</span> as a new {vocabulary.unitLower}
            </NewNameOption>
          )}
        </ul>
      )}
    </div>
  );
}
