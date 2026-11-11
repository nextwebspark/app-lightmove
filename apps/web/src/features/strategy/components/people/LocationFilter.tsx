import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { useDebouncedValue } from "../../../../lib/useComboboxList";
import * as peopleApi from "../../api/peopleApi";
import type { PlaceKind } from "../../api/types";
import { TagCombobox } from "../TagCombobox";

const MIN_QUERY_LENGTH = 2;
const RADII = [25, 50, 100, 300, 500] as const;

/**
 * The People rail's Location box: from two letters, countries, then on-file LinkedIn spellings, then Mapbox; a "Use …"
 * row takes free text. A radius is offered only around a city or area.
 */
export function LocationFilter({
  locations,
  radius,
  onChange,
}: {
  locations: string[];
  radius: number | null;
  onChange: (locations: string[], radius: number | null) => void;
}) {
  const [draft, setDraft] = useState("");
  const [kinds, setKinds] = useState<Record<string, PlaceKind>>({});
  const settled = useDebouncedValue(draft.trim());
  const isAsking = settled.length >= MIN_QUERY_LENGTH;

  const suggestions = useQuery({
    queryKey: peopleApi.LOCATION_SUGGESTIONS_KEY(settled.toLowerCase()),
    queryFn: ({ signal }) => peopleApi.suggestLocations(settled, signal),
    enabled: isAsking,
    placeholderData: keepPreviousData,
    staleTime: 5 * 60 * 1000,
  });

  const places = isAsking ? (suggestions.data ?? []) : [];
  const typed = draft.trim();
  const offersTyped =
    typed.length >= MIN_QUERY_LENGTH && !places.some((place) => place.value.toLowerCase() === typed.toLowerCase());
  const options = [
    ...places
      .filter((place) => !locations.includes(place.value))
      .map((place) => ({ value: place.value, label: place.label })),
    ...(offersTyped ? [{ value: typed, label: `Use “${typed}”` }] : []),
  ];

  const only = locations.length === 1 ? locations[0] : null;
  const canHaveRadius = only !== null && (kinds[only] ?? (only.includes(",") ? "CITY" : "COUNTRY")) !== "COUNTRY";

  const handlePick = (value: string) => {
    const picked = places.find((place) => place.value === value);
    if (picked) setKinds((known) => ({ ...known, [value]: picked.kind }));
    if (!locations.includes(value)) onChange([...locations, value], null);
    setDraft("");
  };

  return (
    <div className="flex flex-col gap-2">
      <TagCombobox
        listId="people-locations"
        noun="places"
        tags={locations.map((location) => ({ value: location, label: location }))}
        options={options}
        emptyText={isAsking && suggestions.isSuccess ? "No places match" : null}
        isStale={suggestions.isPlaceholderData}
        onQueryChange={setDraft}
        onPick={handlePick}
        onRemove={(value) => onChange(locations.filter((location) => location !== value), null)}
      />
      {canHaveRadius && (
        <label className="flex items-center justify-between gap-2 text-note text-u-text2">
          Within
          <select
            value={radius ?? ""}
            onChange={(event) => onChange(locations, event.target.value ? Number(event.target.value) : null)}
            aria-label="Radius in miles"
            className="h-8 rounded-md border border-u-border-strong bg-u-raised px-2 text-note text-u-text outline-none"
          >
            <option value="">The place only</option>
            {RADII.map((miles) => (
              <option key={miles} value={miles}>
                {miles} miles
              </option>
            ))}
          </select>
        </label>
      )}
    </div>
  );
}
