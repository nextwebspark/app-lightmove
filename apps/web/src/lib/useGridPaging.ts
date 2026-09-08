import { useCallback, useMemo, useState } from "react";
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
 * Which page of a grid is on screen.
 *
 * <p>Local rather than remembered: a page number is a position in one sitting's reading, and coming
 * back to a screen at page 14 of a list that has since been refilled is not where anyone left off.
 *
 * <p>{@link reset} and {@link clampTo} return the same state object when there is nothing to change,
 * so the effects that call them on every filter edit and every row count cannot loop. Between them
 * they are why the table's own `autoResetPageIndex` is off: a refetch that brought back the same
 * rows would otherwise bounce a reader to page one for nothing.
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
