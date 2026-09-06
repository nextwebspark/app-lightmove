import { useQuery } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { Button, Field, Input, Spinner } from "../../../components/ui";
import { CompanyLogo } from "../../../components/ui/CompanyLogo";
import { COMPANY_SEARCH_KEY, searchCompanies } from "../../strategy/api/companiesApi";
import type { CompanySuggestion } from "../../strategy/api/types";
import type { CreateClientPayload } from "../api/types";

/** How many characters the box waits for before it asks the universe anything. */
const MIN_QUERY_LENGTH = 2;

/** A company chosen for a new client: a row of the universe, or a record typed in because it has none. */
export type CompanyPick =
  | { source: "universe"; company: CompanySuggestion }
  | { source: "custom"; name: string; domain: string };

/**
 * The company step of creating a client, shared by both entrances — the registry's New-client modal and
 * the New-project modal's inline client.
 *
 * <p>It reads the Apollo universe through the same `/companies/search` call and the same query key
 * Strategy's own pickers use, so a keystroke typed here is answered from the cache they filled. Only an
 * `apolloAccountId` ever travels to the server on a pick: `ClientService` re-resolves the canonical name
 * and domain, so a client cannot be filed under a name of its own choosing.
 *
 * <p>A company the market does not carry is the escape hatch, not the default — it is offered under the
 * results, once the search has settled, so it never competes with the rows that are still arriving.
 */
export function CompanyPicker({
  pick,
  onPick,
  existingNames,
  onRejectExisting,
  autoFocus,
}: {
  pick: CompanyPick | null;
  onPick: (pick: CompanyPick | null) => void;
  /** Lower-cased names already on the books — those rows show a CLIENT badge and refuse the pick. */
  existingNames?: Set<string>;
  /** How the caller reports a refused pick; without one the row is simply inert. */
  onRejectExisting?: (name: string) => void;
  autoFocus?: boolean;
}) {
  const [query, setQuery] = useState("");
  const [debounced, setDebounced] = useState("");
  const [customOpen, setCustomOpen] = useState(false);
  const [customName, setCustomName] = useState("");
  const [customDomain, setCustomDomain] = useState("");
  const [customError, setCustomError] = useState<string | null>(null);

  useEffect(() => {
    const handle = setTimeout(() => setDebounced(query.trim()), 250);
    return () => clearTimeout(handle);
  }, [query]);

  // The same shared universe reader the Strategy pickers use — a non-empty query name-matches, so
  // sectors/order are inert here, and the result shares one cache entry with those pickers.
  const { data: hits = [], isFetching } = useQuery({
    queryKey: COMPANY_SEARCH_KEY(debounced),
    queryFn: () => searchCompanies(debounced).then((page) => page.companies),
    enabled: pick === null && debounced.length >= MIN_QUERY_LENGTH,
  });

  const isOnTheBooks = (name: string) => existingNames?.has(name.toLowerCase()) ?? false;

  const handlePickHit = (hit: CompanySuggestion) => {
    if (isOnTheBooks(hit.companyName)) {
      onRejectExisting?.(hit.companyName);
      return;
    }
    onPick({ source: "universe", company: hit });
  };

  const handleOpenCustom = () => {
    setCustomName(query.trim());
    setCustomDomain("");
    setCustomError(null);
    setCustomOpen(true);
  };

  const handleConfirmCustom = () => {
    if (!customName.trim()) {
      setCustomError("Enter the company name");
      return;
    }
    setCustomError(null);
    setCustomOpen(false);
    onPick({ source: "custom", name: customName.trim(), domain: customDomain.trim() });
  };

  if (pick) {
    return (
      <div className="mb-4 flex items-center gap-2.5 rounded-lg border border-line-soft bg-panel2 px-3 py-2.5">
        <CompanyLogo name={nameOf(pick)} logo={logoOf(pick)} size={28} />
        <span className="min-w-0 flex-1">
          <span className="block truncate text-[13px] font-semibold text-text">{nameOf(pick)}</span>
          <span className="block truncate font-mono text-[11px] text-text3">
            {pick.source === "universe" ? locationOf(pick.company) || "—" : "new company record"}
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
      <Field label="Company">
        <Input
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder="Search company database…"
          autoFocus={autoFocus}
        />
      </Field>

      {query.trim().length < MIN_QUERY_LENGTH ? (
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
          {!isFetching && hits.length === 0 && (
            <p className="px-3 py-3 font-mono text-[11.5px] text-text3">
              No company found for “{debounced}”.
            </p>
          )}
          {/* The add-a-company escape hatch waits for the search to settle, so it never sits under the
              "Searching…" spinner as a second, competing action. */}
          {!isFetching && (
            <button
              type="button"
              onClick={handleOpenCustom}
              className="flex w-full items-center gap-1.5 px-3 py-2.5 text-left font-mono text-[11.5px] text-amber hover:bg-panel2"
            >
              ＋ None of these — add “{query.trim()}” as a new company
            </button>
          )}
        </div>
      )}

      {customOpen && (
        <div className="mt-4 rounded-lg border border-line-soft bg-panel2 p-3.5">
          <div className="mb-3 font-mono text-[10px] font-semibold uppercase tracking-[0.12em] text-text3">
            New company record
          </div>
          <Field label="Company name" error={customError ?? undefined}>
            <Input
              value={customName}
              onChange={(event) => {
                setCustomName(event.target.value);
                setCustomError(null);
              }}
              invalid={!!customError}
              placeholder="e.g. Meridian Energy Group"
              autoFocus
            />
          </Field>
          <Field label="Domain · optional, helps us match the client">
            <Input
              value={customDomain}
              onChange={(event) => setCustomDomain(event.target.value)}
              placeholder="e.g. meridian.ae"
            />
          </Field>
          <div className="flex justify-end gap-2">
            <Button variant="secondary" onClick={() => setCustomOpen(false)}>
              Cancel
            </Button>
            <Button onClick={handleConfirmCustom}>Use this company</Button>
          </div>
        </div>
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
          {locationOf(company) || "—"}
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

/** The create-client request a pick becomes — one shape, so both entrances post the same thing. */
export function createPayloadFor(pick: CompanyPick): CreateClientPayload {
  return pick.source === "universe"
    ? {
        company: { apolloAccountId: pick.company.apolloAccountId },
        sector: pick.company.industry ?? undefined,
      }
    : { customName: pick.name, customDomain: pick.domain || undefined };
}

export function nameOf(pick: CompanyPick): string {
  return pick.source === "universe" ? pick.company.companyName : pick.name;
}

function logoOf(pick: CompanyPick): string | null {
  return pick.source === "universe" ? pick.company.logoUrl : null;
}

/** Where a company is, city first — the subtext under its name everywhere a suggestion is rendered. */
function locationOf(company: CompanySuggestion): string {
  return [company.companyCity, company.companyCountry].filter(Boolean).join(", ");
}
