import type { Column } from "@tanstack/react-table";
import type { RefObject } from "react";
import { useEffect, useState } from "react";
import { Skeleton, Spinner } from "../../../components/ui";
import type { CompanyResult } from "../api/types";
import type { SourcingTableFeatures } from "./columns";
import type { SourcingTable } from "../lib/useSourcingTable";

const SKELETON_ROWS = 6;

// The fill is load-bearing: rows scroll under the sticky header, and a transparent one shows them through.
const TH =
  "sticky top-0 z-20 whitespace-nowrap border-b border-line bg-panel2 px-3 py-[9px] text-left " +
  "align-bottom font-mono text-[10.5px] font-semibold uppercase tracking-[0.12em] text-text2";
const TD = "h-[52px] border-b border-line-soft px-3 py-2 align-middle";

export function CompanyTable({
  table,
  isReloading,
  isFetchingNextPage,
  sentinelRef,
}: {
  table: SourcingTable;
  isReloading: boolean;
  isFetchingNextPage: boolean;
  sentinelRef: RefObject<HTMLDivElement | null>;
}) {
  const [wrapper, setWrapper] = useState<HTMLDivElement | null>(null);
  const availableWidth = useElementWidth(wrapper);

  // The single column order for the whole table. Pinning reorders columns start/center/end, and
  // `getVisibleLeafColumns()` does not — reading widths from one and cells from the other lines up only
  // while the pinned column also happens to be the first declared.
  const headers = table.getHeaderGroups()[0]?.headers ?? [];

  // While every visible column still fits at its own minimum the table divides the width between them
  // and never scrolls sideways; past that it falls back to literal widths and a pinned first column.
  const minTotal = headers.reduce((total, header) => total + header.column.columnDef.minSize!, 0);
  const isOverflowing = availableWidth > 0 && minTotal > availableWidth;
  const totalSize = table.getTotalSize();

  return (
    <>
      {/* The only region that scrolls: it takes the height the viewport has left, so everything above
          it stays put and a wide table's horizontal scrollbar stays in reach. */}
      <div
        ref={setWrapper}
        className="min-h-0 flex-1 overflow-auto rounded-[10px] border border-line-soft"
      >
        {/* table-fixed in both modes: under auto layout a <col> width is only a minimum, and the pinned
            column's offset is computed from the widths being real. */}
        <table
          className={`table-fixed border-collapse ${isOverflowing ? "" : "w-full"}`}
          style={isOverflowing ? { width: `${totalSize}px` } : undefined}
        >
          <colgroup>
            {headers.map((header) => (
              <col
                key={header.id}
                // Computed, so it cannot be a Tailwind class: a share of the screen when the columns
                // fit, the column's own width when they don't.
                style={{
                  width: isOverflowing
                    ? `${header.column.getSize()}px`
                    : `${(header.column.getSize() / totalSize) * 100}%`,
                }}
              />
            ))}
          </colgroup>

          <thead>
            <tr>
              {headers.map((header) => {
                const column = header.column;
                const sorted = column.getIsSorted();
                return (
                  <th
                    key={header.id}
                    aria-sort={sorted ? (sorted === "asc" ? "ascending" : "descending") : "none"}
                    // A pinned header cell has to out-rank both its own row and the pinned body cells.
                    className={`${TH} ${pinnedClass(column, isOverflowing)} ${pinnedHeaderClass(column, isOverflowing)}`}
                    style={pinnedStyle(column, isOverflowing)}
                  >
                    {column.getCanSort() ? (
                      <button
                        type="button"
                        onClick={column.getToggleSortingHandler()}
                        className="group flex items-center gap-1 uppercase tracking-[0.12em] hover:text-text"
                      >
                        {String(column.columnDef.header)}
                        <span className={sorted ? "text-amber" : "text-text3 opacity-0 group-hover:opacity-100"}>
                          {sorted === "asc" ? "↑" : sorted === "desc" ? "↓" : "↕"}
                        </span>
                      </button>
                    ) : (
                      String(column.columnDef.header)
                    )}
                  </th>
                );
              })}
            </tr>
          </thead>

          <tbody>
            {isReloading
              ? Array.from({ length: SKELETON_ROWS }, (_, rowIndex) => (
                  <tr key={rowIndex}>
                    {headers.map(({ id, column }) => (
                      <td
                        key={id}
                        className={`${TD} ${pinnedClass(column, isOverflowing)} ${pinnedBodyClass(column, isOverflowing)}`}
                        style={pinnedStyle(column, isOverflowing)}
                      >
                        <Skeleton className="h-3.5 w-full max-w-24" />
                      </td>
                    ))}
                  </tr>
                ))
              : table.getRowModel().rows.map((row) => (
                  <tr key={row.id} className="group hover:bg-panel2">
                    {row.getVisibleCells().map((cell) => (
                      <td
                        key={cell.id}
                        // The full value stays reachable even though the cell clamps to two lines.
                        title={textOf(cell.getValue())}
                        className={`${TD} ${pinnedClass(cell.column, isOverflowing)} ${pinnedBodyClass(cell.column, isOverflowing)}`}
                        style={pinnedStyle(cell.column, isOverflowing)}
                      >
                        <span className="line-clamp-2 break-words">
                          <table.FlexRender cell={cell} />
                        </span>
                      </td>
                    ))}
                  </tr>
                ))}
          </tbody>
        </table>

        {/* Inside the scrollport — the page itself no longer scrolls, so a sentinel outside it would
            never come into view again. */}
        <div ref={sentinelRef} className="flex h-10 items-center justify-center text-text3">
          {isFetchingNextPage && (
            <span role="status" aria-label="Loading more companies">
              <Spinner />
            </span>
          )}
        </div>
      </div>
    </>
  );
}

/** Stuck in both directions at once, so it outranks the scrolling headers and the pinned body cells.
 *  Its background stays in `TH`: a second background utility here would be equally specific, and
 *  stylesheet order would decide the colour. */
function pinnedHeaderClass(
  column: Column<SourcingTableFeatures, CompanyResult, unknown>,
  isOverflowing: boolean,
) {
  return isOverflowing && column.getIsPinned() === "start" ? "z-30" : "";
}

/** Its own background, or the scrolling columns show through; its own hover, or hovering a row leaves
 *  a differently-coloured square behind the pin. */
function pinnedBodyClass(
  column: Column<SourcingTableFeatures, CompanyResult, unknown>,
  isOverflowing: boolean,
) {
  return pinnedClass(column, isOverflowing) === "" ? "" : "bg-panel group-hover:bg-panel2";
}

/** Shared by a pinned header and a pinned body cell; inert until the table scrolls sideways. */
function pinnedClass(column: Column<SourcingTableFeatures, CompanyResult, unknown>, isOverflowing: boolean) {
  if (!isOverflowing || column.getIsPinned() !== "start") {
    return "";
  }
  return "sticky left-0 z-10 shadow-[1px_0_0_0_var(--color-line)]";
}

function pinnedStyle(column: Column<SourcingTableFeatures, CompanyResult, unknown>, isOverflowing: boolean) {
  if (!isOverflowing || column.getIsPinned() !== "start") {
    return undefined;
  }
  return { left: `${column.getStart("start")}px` };
}

/** A list joins; anything absent gets no tooltip rather than an empty one. */
function textOf(value: unknown): string | undefined {
  if (Array.isArray(value)) {
    return value.length > 0 ? value.join(", ") : undefined;
  }
  if (value === null || value === undefined || value === "") {
    return undefined;
  }
  return String(value);
}

/** Watched rather than measured once: the sidebar collapses and windows resize. */
function useElementWidth(element: HTMLElement | null): number {
  const [width, setWidth] = useState(0);

  useEffect(() => {
    if (!element) {
      return;
    }
    // jsdom has no ResizeObserver; the tests exercise fit mode, which is what a zero width selects.
    if (typeof ResizeObserver === "undefined") {
      return;
    }
    const observer = new ResizeObserver((entries) => {
      setWidth(entries[0]?.contentRect.width ?? 0);
    });
    observer.observe(element);
    return () => observer.disconnect();
  }, [element]);

  return width;
}
