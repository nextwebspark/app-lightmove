import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { useState } from "react";
import * as companiesApi from "../api/companiesApi";
import { useDebouncedValue } from "../lib/useComboboxList";
import { FilterCheckRow } from "../../../components/ui/FilterCheckRow";
import { TagCombobox } from "./TagCombobox";

const LIST_ID = "keyword-suggestions";
/** Mirrors the server's `keywordMinQueryLength`. */
const MIN_QUERY_LENGTH = 2;

/**
 * The keyword half of the Industry panel. Unticked is no constraint at all — every company the
 * industries reach comes back — and that is why nothing but the keywords themselves is stored: an
 * empty list is the unticked state, so there is no flag that could disagree with the box beneath it.
 *
 * <p>Keywords are picked from the universe's own vocabulary, never typed. A keyword the pipeline does
 * not carry would narrow the search to nothing while looking like it narrowed it to something.
 */
export function KeywordFilter({
  selected,
  onChange,
}: {
  selected: string[];
  onChange: (keywords: string[]) => void;
}) {
  const [isIncludingKeywords, setIncludingKeywords] = useState(selected.length > 0);
  const [draft, setDraft] = useState("");
  const settled = useDebouncedValue(draft.trim());
  const isSearchable = settled.length >= MIN_QUERY_LENGTH;

  const { data } = useQuery({
    queryKey: companiesApi.KEYWORD_SEARCH_KEY(settled),
    queryFn: ({ signal }) => companiesApi.searchKeywords(settled, signal),
    enabled: isIncludingKeywords && isSearchable,
    placeholderData: keepPreviousData,
  });

  const taken = new Set(selected);
  // `settled` and not `data` alone: keepPreviousData keeps serving the last query's rows after the
  // box is cleared, so without this the list reopens itself over an empty input.
  const offered = isSearchable
    ? (data?.keywords ?? []).filter((keyword) => !taken.has(keyword.value))
    : [];

  const toggleIncludingKeywords = () => {
    setIncludingKeywords((on) => {
      if (on && selected.length > 0) onChange([]);
      return !on;
    });
  };

  return (
    <div className="flex flex-col gap-3">
      <FilterCheckRow
        label="Include keywords"
        checked={isIncludingKeywords}
        onToggle={toggleIncludingKeywords}
      />

      {isIncludingKeywords && (
        <>
          <TagCombobox
            listId={LIST_ID}
            noun="keywords"
            tags={selected.map((value) => ({ value, label: value }))}
            options={offered}
            emptyText={isSearchable && data !== undefined ? "No keyword matches that." : null}
            isStale={draft.trim() !== settled}
            onQueryChange={setDraft}
            onPick={(value) => onChange([...selected, value])}
            onRemove={(value) => onChange(selected.filter((entry) => entry !== value))}
          />
          <p className="font-sans text-[11px] leading-relaxed text-text3">
            {selected.length === 0
              ? "Type at least two letters to search the universe's keywords."
              : "A company carrying any one of these keywords matches."}
          </p>
        </>
      )}
    </div>
  );
}
