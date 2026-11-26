import { useCallback, useEffect, useState } from "react";
import { useLocation, useSearchParams } from "react-router-dom";

/**
 * A search box kept in its address (`?q=`) so a return finds it as left. The address lags typing (a router write per
 * keystroke moves the caret and hits history limits); an address moved elsewhere is followed.
 */
export function useAddressedQuery() {
  const [searchParams, setSearchParams] = useSearchParams();
  const { state } = useLocation();
  const addressQuery = searchParams.get("q") ?? "";
  const [query, setQuery] = useState(addressQuery);
  const [seenAddressQuery, setSeenAddressQuery] = useState(addressQuery);
  // What this hook last wrote and the address has yet to show; anything else arriving there is someone else's.
  const [pendingQuery, setPendingQuery] = useState<string | null>(null);

  if (addressQuery !== seenAddressQuery) {
    setSeenAddressQuery(addressQuery);
    if (addressQuery === pendingQuery) setPendingQuery(null);
    else setQuery(addressQuery);
  }

  const writeQuery = useCallback(() => {
    if (query === addressQuery) return;
    setPendingQuery(query);
    setSearchParams(
      (current) => {
        const next = new URLSearchParams(current);
        if (query === "") next.delete("q");
        else next.set("q", query);
        return next;
      },
      { replace: true, state },
    );
  }, [query, addressQuery, setSearchParams, state]);

  useEffect(() => {
    const timer = window.setTimeout(writeQuery, 300);
    return () => window.clearTimeout(timer);
  }, [writeQuery]);

  const clear = (alsoClears: readonly string[] = []) => {
    setPendingQuery("");
    setQuery("");
    setSearchParams(
      (current) => {
        const next = new URLSearchParams(current);
        for (const key of ["q", ...alsoClears]) next.delete(key);
        return next;
      },
      { replace: true, state },
    );
  };

  return {
    query,
    setQuery,
    // Flushed on leaving the box, so a row opened straight after typing carries the search into its way back.
    flushQuery: writeQuery,
    // Trimmed, so a trailing space neither refetches under a new key nor reads as a search.
    addressedQuery: addressQuery.trim(),
    clear,
  };
}

/** {@link useAddressedQuery} with a chip beside the box, kept in the address under `chipKey`. */
export function useAddressedSearch<Chip extends string>(chipKey: string, chips: readonly Chip[], defaultChip: Chip) {
  const [searchParams, setSearchParams] = useSearchParams();
  const { state } = useLocation();
  const { query, setQuery, flushQuery, clear } = useAddressedQuery();
  const addressChip = searchParams.get(chipKey);
  const chip = chips.find((candidate) => candidate === addressChip) ?? defaultChip;

  const setChip = (next: Chip) =>
    setSearchParams(
      (current) => {
        const params = new URLSearchParams(current);
        if (next === defaultChip) params.delete(chipKey);
        else params.set(chipKey, next);
        return params;
      },
      { replace: true, state },
    );

  return { query, setQuery, flushQuery, chip, setChip, clear: () => clear([chipKey]) };
}
