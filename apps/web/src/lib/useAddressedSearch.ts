import { useCallback, useEffect, useRef, useState } from "react";
import { useSearchParams } from "react-router-dom";

/**
 * A list's search box and filter chip kept in its address (`?q=`, and a chip under its own key), so a return to the
 * list — the back link from a position — finds it as it was left.
 *
 * The box filters as typed and the address catches up a moment later: a router update per keystroke would move the
 * caret and run into the browser's limit on history writes. An address that moves on its own (a link to the bare
 * list, Back) is followed rather than overwritten.
 */
export function useAddressedSearch<Chip extends string>(chipKey: string, chips: readonly Chip[], defaultChip: Chip) {
  const [searchParams, setSearchParams] = useSearchParams();
  const addressQuery = searchParams.get("q") ?? "";
  const addressChip = searchParams.get(chipKey);
  const chip = chips.find((candidate) => candidate === addressChip) ?? defaultChip;
  const [query, setQuery] = useState(addressQuery);
  const [seenAddressQuery, setSeenAddressQuery] = useState(addressQuery);
  const writtenQuery = useRef(addressQuery);

  if (addressQuery !== seenAddressQuery) {
    setSeenAddressQuery(addressQuery);
    if (addressQuery !== writtenQuery.current) setQuery(addressQuery);
  }

  const setParam = useCallback(
    (key: string, value: string, isDefault: boolean) =>
      setSearchParams(
        (current) => {
          const next = new URLSearchParams(current);
          if (isDefault) next.delete(key);
          else next.set(key, value);
          return next;
        },
        { replace: true },
      ),
    [setSearchParams],
  );

  useEffect(() => {
    if (query === addressQuery) return;
    const timer = window.setTimeout(() => {
      writtenQuery.current = query;
      setParam("q", query, query === "");
    }, 300);
    return () => window.clearTimeout(timer);
  }, [query, addressQuery, setParam]);

  const setChip = (next: Chip) => setParam(chipKey, next, next === defaultChip);
  const clear = () => {
    writtenQuery.current = "";
    setQuery("");
    setSearchParams(
      (current) => {
        const next = new URLSearchParams(current);
        next.delete("q");
        next.delete(chipKey);
        return next;
      },
      { replace: true },
    );
  };

  return { query, setQuery, chip, setChip, clear };
}
