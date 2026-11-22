import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from "react";
import { useLocation, useSearchParams } from "react-router-dom";
import type { OnChangeFn, PaginationState } from "@tanstack/react-table";
import { DEFAULT_PAGE_SIZE } from "./paging";

/** A grid's page window, in the two shapes it is needed in: the table's, and {@link PaginationBar}'s. */
export interface GridPaging {
  pagination: PaginationState;
  page: number;
  size: number;
  setPage: (page: number) => void;
  setSize: (size: number) => void;
  onPaginationChange: OnChangeFn<PaginationState>;
  /** Back to the first page, for when what is being asked changed. Idempotent, so an effect may call it. */
  reset: () => void;
  /**
   * Pulls the page back onto the last one that still exists, for when the rows themselves shrank —
   * a seat removed, a client deleted by someone else and refetched. Idempotent like {@link reset}.
   */
  clampTo: (totalCount: number) => void;
}

/**
 * Which page of a grid is on screen, never remembered. {@link reset} and {@link clampTo} return the same state when
 * nothing changes so their effects cannot loop, which is why the table's `autoResetPageIndex` is off.
 */
export function useGridPaging(initialSize: number = DEFAULT_PAGE_SIZE): GridPaging {
  const [pagination, setPagination] = useState<PaginationState>({
    pageIndex: 0,
    pageSize: initialSize,
  });

  const setPage = useCallback(
    (pageIndex: number) => setPagination((current) => ({ ...current, pageIndex })),
    [],
  );
  const setSize = useCallback(
    (pageSize: number) => setPagination((current) => ({ ...current, pageSize })),
    [],
  );
  const reset = useCallback(
    () => setPagination((current) => (current.pageIndex === 0 ? current : { ...current, pageIndex: 0 })),
    [],
  );
  const clampTo = useCallback(
    (totalCount: number) =>
      setPagination((current) => {
        const last = Math.max(0, Math.ceil(totalCount / current.pageSize) - 1);
        return current.pageIndex <= last ? current : { ...current, pageIndex: last };
      }),
    [],
  );

  return useMemo(
    () => ({
      pagination,
      page: pagination.pageIndex,
      size: pagination.pageSize,
      setPage,
      setSize,
      onPaginationChange: setPagination,
      reset,
      clampTo,
    }),
    [pagination, setPage, setSize, reset, clampTo],
  );
}

/**
 * {@link useGridPaging} with the page held in the address (`?page=3`, written in place), so Back from a row opened off
 * page 3 lands on page 3. The size stays local.
 *
 * Traps: every list resets its page when what is asked changes, in an effect that also runs on mount, and would throw
 * away the page a return brought back — so {@link GridPaging.reset} does nothing until the first render has settled.
 * And {@link GridPaging.clampTo} must be given the count only once the rows have arrived: an empty first read clamps a
 * returning reader onto page 1.
 */
export function useAddressedGridPaging(param = "page", initialSize: number = DEFAULT_PAGE_SIZE): GridPaging {
  const [searchParams, setSearchParams] = useSearchParams();
  const location = useLocation();
  const [pageSize, setPageSize] = useState(initialSize);
  const pageIndex = pageIndexOf(searchParams.get(param));
  // react-router's setSearchParams changes with every address write; held here so these callbacks keep one identity,
  // or a list's reset effect, which depends on them, reruns — and resets — whenever a drawer opens.
  const latest = useRef({ pageIndex, state: location.state as unknown, setSearchParams });
  useLayoutEffect(() => {
    latest.current = { pageIndex, state: location.state as unknown, setSearchParams };
  });
  const settled = useRef(false);
  useEffect(() => {
    const timer = window.setTimeout(() => {
      settled.current = true;
    });
    return () => window.clearTimeout(timer);
  }, []);

  const setPage = useCallback(
    (next: number) => {
      if (next === latest.current.pageIndex) return;
      latest.current.setSearchParams(
        (current) => {
          const params = new URLSearchParams(current);
          if (next <= 0) params.delete(param);
          else params.set(param, String(next + 1));
          return params;
        },
        { replace: true, state: latest.current.state },
      );
    },
    [param],
  );
  const setSize = useCallback(
    (size: number) => {
      setPageSize(size);
      setPage(0);
    },
    [setPage],
  );
  const reset = useCallback(() => {
    if (settled.current) setPage(0);
  }, [setPage]);
  const clampTo = useCallback(
    (totalCount: number) => {
      const last = Math.max(0, Math.ceil(totalCount / pageSize) - 1);
      if (latest.current.pageIndex > last) setPage(last);
    },
    [pageSize, setPage],
  );
  const onPaginationChange = useCallback<OnChangeFn<PaginationState>>(
    (updater) => {
      const next =
        typeof updater === "function" ? updater({ pageIndex: latest.current.pageIndex, pageSize }) : updater;
      if (next.pageSize !== pageSize) setPageSize(next.pageSize);
      setPage(next.pageIndex);
    },
    [pageSize, setPage],
  );

  return useMemo(
    () => ({
      pagination: { pageIndex, pageSize },
      page: pageIndex,
      size: pageSize,
      setPage,
      setSize,
      onPaginationChange,
      reset,
      clampTo,
    }),
    [pageIndex, pageSize, setPage, setSize, onPaginationChange, reset, clampTo],
  );
}

function pageIndexOf(raw: string | null): number {
  const page = Number(raw);
  return Number.isInteger(page) && page > 1 ? page - 1 : 0;
}
