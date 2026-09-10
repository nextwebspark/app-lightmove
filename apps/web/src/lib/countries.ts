import { useQuery } from "@tanstack/react-query";
import { useMemo } from "react";
import { request } from "./apiClient";
import type { ComboboxOption } from "../components/ui/FacetCombobox";

/** One country as the server spells it: the ISO code, and the one English name every screen shows. */
export interface Country {
  code: string;
  name: string;
  /** The spellings that find it — the same ones the server folds on write. */
  spellings: string[];
}

interface CountriesResponse {
  countries: Country[];
  /** The markets the universe actually holds companies in, largest first — the Location chips. */
  markets: string[];
}

const COUNTRIES_KEY = ["countries"] as const;

/**
 * The country vocabulary, read once and kept.
 *
 * <p>Served rather than mirrored: the same catalog canonicalises every country the API stores, so a
 * spelling added there reaches the pickers without a frontend release. It was a hardcoded array of
 * eight until it drifted from the data twice — the universe carries thirteen countries, and a
 * mandate maps executives in countries the universe has never carried at all.
 *
 * <p>It changes when the pipeline loads, which is to say not during a session.
 */
export function useCountries() {
  const countries = useQuery({
    queryKey: COUNTRIES_KEY,
    queryFn: () => request<CountriesResponse>("/countries"),
    staleTime: Infinity,
  });

  const data = countries.data;
  const options = useMemo(() => (data ? optionsOf(data.countries) : EMPTY_OPTIONS), [data]);

  return {
    options,
    /** The Strategy filter's Location chips, in the order the universe justifies. */
    markets: data?.markets ?? EMPTY_MARKETS,
    /**
     * Refused and still-arriving are different facts and an empty list tells them apart from neither.
     * A pure client representative is gated out of this read exactly as they are gated out of the
     * facet counts, so every caller has to be able to say which happened.
     */
    isPending: countries.isPending,
    isError: countries.isError,
  };
}

const EMPTY_OPTIONS: ComboboxOption[] = [];
const EMPTY_MARKETS: string[] = [];

function optionsOf(countries: Country[]): ComboboxOption[] {
  return countries.map((country) => ({
    value: country.name,
    label: country.name,
    aliases: country.spellings,
  }));
}
