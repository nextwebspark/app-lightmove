import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { Field, Input } from "../../../components/ui";
import { CompanyLogo } from "../../../components/ui/CompanyLogo";
import { useDebouncedValue } from "../../../lib/useComboboxList";
import type { CompanySearchSource } from "../../clients/components/CompanyPicker";
import { companyLocation } from "../../clients/lib/companyPick";
import type { CompanySuggestion } from "../../strategy/api/types";

const MIN_QUERY_LENGTH = 2;
const SUGGESTION_LIMIT = 3;

const TEXT_BUTTON =
  "rounded py-1 text-note text-u-accent hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-u-accent";

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
  // What the person typed before taking a match, so "Not us" hands it back rather than the company's name.
  const [typedBeforeMatch, setTypedBeforeMatch] = useState<string | null>(null);
  // A name whose matches were turned down, so the same suggestions don't come straight back.
  const [declinedFor, setDeclinedFor] = useState<string | null>(null);
  const trimmed = name.trim();
  const debounced = useDebouncedValue(trimmed);
  const asking = match === null && debounced.length >= MIN_QUERY_LENGTH && debounced !== declinedFor;
  const { data } = useQuery({
    queryKey: source.key(debounced),
    queryFn: ({ signal }): Promise<CompanySuggestion[]> => source.search(debounced, signal),
    enabled: asking,
  });
  // A failed or slow search simply offers nothing: the typed name is enough to carry on. Only an answer for what is
  // in the box now is shown, never the last one under newer text.
  const suggestions = asking && debounced === trimmed ? (data ?? []).slice(0, SUGGESTION_LIMIT) : [];

  const handleUse = (company: CompanySuggestion) => {
    setTypedBeforeMatch(name);
    onMatch(company);
    onNameChange(company.companyName);
  };

  const handleNotUs = () => {
    const restored = typedBeforeMatch ?? name;
    onMatch(null);
    onNameChange(restored);
    setDeclinedFor(restored.trim());
    setTypedBeforeMatch(null);
  };

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

      <p role="status" className="sr-only">
        {suggestions.length > 0
          ? `${suggestions.length} possible ${suggestions.length === 1 ? "match" : "matches"} below, optional`
          : ""}
      </p>

      {match && (
        <div className="-mt-2 flex items-center gap-2.5 rounded-lg border border-u-border bg-u-raised px-3 py-2">
          <CompanyLogo name={match.companyName} logo={match.logoUrl} size={24} />
          <span className="min-w-0 flex-1">
            <span className="block text-meta text-u-text3">Matched to</span>
            <span className="block truncate text-note font-medium text-u-text">{match.companyName}</span>
            {companyLocation(match) && (
              <span className="block truncate text-meta text-u-text3">{companyLocation(match)}</span>
            )}
          </span>
          <button type="button" onClick={handleNotUs} className={TEXT_BUTTON}>
            Not us
          </button>
        </div>
      )}

      {suggestions.length > 0 && (
        <div className="-mt-2 rounded-lg border border-u-border">
          <p className="border-b border-u-border px-3 py-2 text-note text-u-text3">
            Is this you? Optional — it fills in your industry and location.
          </p>
          <ul>
            {suggestions.map((company) => (
              <li
                key={company.apolloAccountId}
                className="flex items-center gap-2.5 border-b border-u-border px-3 py-2 last:border-0"
              >
                <CompanyLogo name={company.companyName} logo={company.logoUrl} size={24} />
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-note font-medium text-u-text">{company.companyName}</span>
                  {companyLocation(company) && (
                    <span className="block truncate text-meta text-u-text3">{companyLocation(company)}</span>
                  )}
                </span>
                <button type="button" onClick={() => handleUse(company)} className={`${TEXT_BUTTON} font-medium`}>
                  Use this<span className="sr-only">: {company.companyName}</span>
                </button>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}
