import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { Button, Field, Input, Spinner } from "../../../components/ui";
import { CompanyLogo } from "../../../components/ui/CompanyLogo";
import { COMPANY_SEARCH_KEY, searchCompanies } from "../../strategy/api/companiesApi";
import type { CompanySuggestion } from "../../strategy/api/types";
import { useDebouncedValue } from "../../strategy/lib/useComboboxList";
import {
  companyLocation,
  pickedCompanyLogo,
  pickedCompanyName,
  type CompanyPick,
} from "../lib/companyPick";

/** How many characters the box waits for before it asks the universe anything. */
const MIN_QUERY_LENGTH = 2;

/**
 * The company step of creating a client, shared by both entrances — the registry's New-client modal and
 * the New-project modal's inline client.
 *
 * <p>It reads the Apollo universe through the same `/companies/search` call and the same query key
 * Strategy's own pickers use, so a keystroke typed here is answered from the cache they filled.
 *
 * <p>A company the market does not carry is the escape hatch, not the default — it is offered under the
 * results, once the search has settled, so it never competes with the rows that are still arriving.
 */
export function CompanyPicker({
  pick,
  onPick,
  existingNames,
  onRejectExisting,
  error,
  autoFocus,
}: {
  pick: CompanyPick | null;
  onPick: (pick: CompanyPick | null) => void;
  /** Lower-cased names already on the books — those rows show a CLIENT badge and refuse the pick. */
  existingNames?: Set<string>;
  /** How the caller reports a refused pick; without one the row is simply inert. */
  onRejectExisting?: (name: string) => void;
  /** A refusal the caller owns — "nothing picked yet". Rendered by `Field`, like every other error. */
  error?: string;
  autoFocus?: boolean;
}) {
  const [query, setQuery] = useState("");
  const [customOpen, setCustomOpen] = useState(false);
  const trimmed = query.trim();
  const debounced = useDebouncedValue(trimmed);

  // The same shared universe reader the Strategy pickers use — a non-empty query name-matches, so
  // sectors/order are inert here, and the result shares one cache entry with those pickers.
  //
  // The signal is not optional politeness: typeahead is `company_name ILIKE '%…%'` over 71,822 rows,
  // which no index can serve, so an abandoned keystroke left running is a full scan nobody awaits.
  const { data, isFetching, isError } = useQuery({
    queryKey: COMPANY_SEARCH_KEY(debounced),
    queryFn: ({ signal }): Promise<CompanySuggestion[]> =>
      searchCompanies(debounced, undefined, signal).then((page) => page.companies),
    enabled: pick === null && debounced.length >= MIN_QUERY_LENGTH,
    placeholderData: keepPreviousData,
  });
  const hits = data ?? [];

  // "Answered" is its own condition, never inferred from `!isFetching`: inside the debounce window the
  // query has not started, so a query of its own would otherwise read as a search that found nothing.
  const isSettled = debounced === trimmed;
  const isAnswered = isSettled && !isFetching && !isError;

  const isOnTheBooks = (name: string) => existingNames?.has(name.toLowerCase()) ?? false;

  const handlePickHit = (hit: CompanySuggestion) => {
    if (isOnTheBooks(hit.companyName)) {
      onRejectExisting?.(hit.companyName);
      return;
    }
    onPick({ source: "universe", company: hit });
  };

  const handleConfirmCustom = (name: string, domain: string) => {
    setCustomOpen(false);
    onPick({ source: "custom", name, domain });
  };

  if (pick) {
    return (
      <div className="mb-4 flex items-center gap-2.5 rounded-lg border border-line-soft bg-panel2 px-3 py-2.5">
        <CompanyLogo name={pickedCompanyName(pick)} logo={pickedCompanyLogo(pick)} size={28} />
        <span className="min-w-0 flex-1">
          <span className="block truncate text-[13px] font-semibold text-text">
            {pickedCompanyName(pick)}
          </span>
          <span className="block truncate font-mono text-[11px] text-text3">
            {pick.source === "universe" ? companyLocation(pick.company) || "—" : "new company record"}
          </span>
          {pick.source === "universe" && pick.company.industry && (
            <span className="block truncate font-mono text-[10px] text-text3">
              {pick.company.industry}
            </span>
          )}
        </span>
        <button
          type="button"
          onClick={() => onPick(null)}
          className="font-mono text-[11px] text-sky hover:underline"
        >
          Change
        </button>
      </div>
    );
  }

  return (
    <>
      <Field label="Company" error={error}>
        <Input
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          invalid={!!error}
          placeholder="Search company database…"
          autoFocus={autoFocus}
        />
      </Field>

      {trimmed.length < MIN_QUERY_LENGTH ? (
        <p className="font-mono text-[11.5px] text-text3">
          Type at least {MIN_QUERY_LENGTH} characters to search the company database.
        </p>
      ) : (
        <div className="max-h-[280px] overflow-y-auto rounded-lg border border-line-soft">
          {isFetching && (
            <div className="flex items-center gap-2 px-3 py-3 font-mono text-[11.5px] text-text3">
              <Spinner /> Searching…
            </div>
          )}
          {!isFetching &&
            hits.map((hit) => (
              <SuggestionRow
                key={hit.apolloAccountId}
                company={hit}
                alreadyClient={isOnTheBooks(hit.companyName)}
                onSelect={() => handlePickHit(hit)}
              />
            ))}
          {/* A refused read is not an empty universe. Saying "no company found" over a failure sends
              the user straight to the escape hatch to file a duplicate of a company Apollo holds —
              which is the exact bug this picker exists to close. */}
          {isError && (
            <p role="alert" className="px-3 py-3 font-mono text-[11.5px] text-red">
              Couldn't reach the company database. Try again.
            </p>
          )}
          {isAnswered && hits.length === 0 && (
            <p className="px-3 py-3 font-mono text-[11.5px] text-text3">
              No company found for “{debounced}”.
            </p>
          )}
          {/* The add-a-company escape hatch waits for the search to settle, so it never sits under the
              "Searching…" spinner as a second, competing action. */}
          {!isFetching && (
            <button
              type="button"
              onClick={() => setCustomOpen(true)}
              className="flex w-full items-center gap-1.5 px-3 py-2.5 text-left font-mono text-[11.5px] text-amber hover:bg-panel2"
            >
              ＋ None of these — add “{trimmed}” as a new company
            </button>
          )}
        </div>
      )}

      {customOpen && (
        <NewCompanyForm
          initialName={trimmed}
          onCancel={() => setCustomOpen(false)}
          onConfirm={handleConfirmCustom}
        />
      )}
    </>
  );
}

/** One row of the universe: the mark, the name, where it is, and what it does — in that order of weight. */
function SuggestionRow({
  company,
  alreadyClient,
  onSelect,
}: {
  company: CompanySuggestion;
  alreadyClient: boolean;
  onSelect: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onSelect}
      className="flex w-full items-center gap-2.5 border-b border-line-soft px-3 py-2.5 text-left last:border-0 hover:bg-panel2"
    >
      <CompanyLogo name={company.companyName} logo={company.logoUrl} size={28} />
      <span className="min-w-0 flex-1">
        <span className="block truncate text-[13px] font-medium text-text">
          {company.companyName}
        </span>
        <span className="block truncate font-mono text-[11px] text-text3">
          {companyLocation(company) || "—"}
        </span>
        {company.industry && (
          <span className="block truncate font-mono text-[10px] text-text3">{company.industry}</span>
        )}
      </span>
      {alreadyClient ? (
        <span className="rounded-md bg-green-dim px-1.5 py-0.5 font-mono text-[9.5px] font-semibold uppercase tracking-[0.08em] text-green">
          Client
        </span>
      ) : (
        <span className="font-mono text-[11px] text-sky">Select →</span>
      )}
    </button>
  );
}

/** The escape hatch's own form: a company the market does not carry, named and optionally domained. */
function NewCompanyForm({
  initialName,
  onCancel,
  onConfirm,
}: {
  initialName: string;
  onCancel: () => void;
  onConfirm: (name: string, domain: string) => void;
}) {
  const [name, setName] = useState(initialName);
  const [domain, setDomain] = useState("");
  const [error, setError] = useState<string | null>(null);

  const handleConfirm = () => {
    if (!name.trim()) {
      setError("Enter the company name");
      return;
    }
    onConfirm(name.trim(), domain.trim());
  };

  return (
    <div className="mt-4 rounded-lg border border-line-soft bg-panel2 p-3.5">
      <div className="mb-3 font-mono text-[10px] font-semibold uppercase tracking-[0.12em] text-text3">
        New company record
      </div>
      <Field label="Company name" error={error ?? undefined}>
        <Input
          value={name}
          onChange={(event) => {
            setName(event.target.value);
            setError(null);
          }}
          invalid={!!error}
          placeholder="e.g. Meridian Energy Group"
          autoFocus
        />
      </Field>
      <Field label="Domain · optional, helps us match the client">
        <Input
          value={domain}
          onChange={(event) => setDomain(event.target.value)}
          placeholder="e.g. meridian.ae"
        />
      </Field>
      <div className="flex justify-end gap-2">
        <Button variant="secondary" onClick={onCancel}>
          Cancel
        </Button>
        <Button onClick={handleConfirm}>Use this company</Button>
      </div>
    </div>
  );
}
