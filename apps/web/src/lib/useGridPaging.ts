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
