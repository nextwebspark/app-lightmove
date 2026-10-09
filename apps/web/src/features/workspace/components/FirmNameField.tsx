import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { Field, Input } from "../../../components/ui";
import { CompanyLogo } from "../../../components/ui/CompanyLogo";
import { useDebouncedValue } from "../../../lib/useComboboxList";
import type { CompanySearchSource } from "../../clients/components/CompanyPicker";
import { companyLocation } from "../../clients/lib/companyPick";
import type { CompanySuggestion } from "../../strategy/api/types";

const MIN_QUERY_LENGTH = 2;
const SUGGESTION_LIMIT = 3;

/**
 * The firm's name, typed — what the founder thinks they are being asked. Companies the market carries under a
 * similar name are offered beneath as an optional match, which files the workspace with that company's details;
 * declining every one of them is the ordinary path for a search firm, not an exception.
 */
export function FirmNameField({
  label,
  name,
  onNameChange,
  match,
  onMatch,
  source,
  error,
  autoFocus,
}: {
  label: string;
  name: string;
  onNameChange: (name: string) => void;
  match: CompanySuggestion | null;
  onMatch: (company: CompanySuggestion | null) => void;
  source: CompanySearchSource;
  error?: string;
  autoFocus?: boolean;
}) {
  const trimmed = name.trim();
  const debounced = useDebouncedValue(trimmed);
  const { data } = useQuery({
    queryKey: source.key(debounced),
    queryFn: ({ signal }): Promise<CompanySuggestion[]> => source.search(debounced, signal),
    enabled: match === null && debounced.length >= MIN_QUERY_LENGTH,
    placeholderData: keepPreviousData,
  });
  // A failed or slow search simply offers nothing: the typed name is enough to carry on.
  const suggestions = match || debounced.length < MIN_QUERY_LENGTH ? [] : (data ?? []).slice(0, SUGGESTION_LIMIT);

  return (
    <div className="mb-4">
      <Field label={label} error={error}>
        <Input
          value={name}
          onChange={(event) => {
            onNameChange(event.target.value);
            // Renaming after a match keeps the typed name, so the match no longer describes it.
            if (match) onMatch(null);
          }}
          invalid={!!error}
          placeholder="e.g. Meridian Search Partners"
          autoComplete="organization"
          autoFocus={autoFocus}
        />
      </Field>

      {match && (
        <div className="-mt-2 flex items-center gap-2.5 rounded-lg border border-u-border bg-u-raised px-3 py-2">
          <CompanyLogo name={match.companyName} logo={match.logoUrl} size={24} />
          <span className="min-w-0 flex-1 truncate text-note text-u-text2">
            Matched to <span className="font-medium text-u-text">{match.companyName}</span>
            {companyLocation(match) && <span className="text-u-text3"> · {companyLocation(match)}</span>}
          </span>
          <button
            type="button"
            onClick={() => onMatch(null)}
            className="rounded-[4px] py-1 text-note text-u-accent hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-u-accent"
          >
            Not us
          </button>
        </div>
      )}

      {suggestions.length > 0 && (
        <div className="-mt-2 rounded-lg border border-u-border" aria-live="polite">
          <p className="border-b border-u-border px-3 py-2 text-note text-u-text3">
            Is this your company? Optional — it fills in your industry and location.
          </p>
          <ul>
            {suggestions.map((company) => (
              <li key={company.apolloAccountId} className="flex items-center gap-2.5 border-b border-u-border px-3 py-2 last:border-0">
                <CompanyLogo name={company.companyName} logo={company.logoUrl} size={24} />
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-note font-medium text-u-text">{company.companyName}</span>
                  {companyLocation(company) && (
                    <span className="block truncate text-meta text-u-text3">{companyLocation(company)}</span>
                  )}
                </span>
                <button
                  type="button"
                  onClick={() => {
                    onMatch(company);
                    onNameChange(company.companyName);
                  }}
                  className="rounded-[4px] py-1 text-note font-medium text-u-accent hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-u-accent"
                  aria-label={`Use ${company.companyName}`}
                >
                  Use this
                </button>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}
