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
}

/**
 * Which page of a grid is on screen.
 *
 * <p>Local rather than remembered: a page number is a position in one sitting's reading, and coming
 * back to a screen at page 14 of a list that has since been refilled is not where anyone left off.
 *
 * <p>{@link reset} returns the same state object when it is already on page one, so the effect that
 * calls it whenever the filter changes cannot loop.
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

  return useMemo(
    () => ({
      pagination,
      page: pagination.pageIndex,
      size: pagination.pageSize,
      setPage,
      setSize,
      onPaginationChange: setPagination,
      reset,
    }),
    [pagination, setPage, setSize, reset],
  );
}
