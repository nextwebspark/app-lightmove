import {
  columnOrderingFeature,
  columnPinningFeature,
  columnVisibilityFeature,
  rowSortingFeature,
  type Column,
  type ReactTable,
  type RowData,
  type TableFeatures,
} from "@tanstack/react-table";
import {
  useRef,
  useState,
  type CSSProperties,
  type KeyboardEvent,
  type PointerEvent,
  type ReactNode,
} from "react";
import { Icon, ICONS } from "../layout/Icon";
import { cn } from "../../lib/cn";
import { TruncatedText } from "./TruncatedText";
import { DEFAULT_COLUMN_MIN, type GridLayout } from "../../lib/useGridLayout";

/**
 * How a column claims horizontal space. `share` is a slice of the table's flexible width and `min` the
 * width it will not shrink below; `share: 0` pins the column at `min`. Declared per column in
 * `columnDef.meta`, and read here to build one grid template for the header and every row.
 */
export interface DataGridColumnLayout {
  share: number;
  min: number;
}

/** `gap-3` in numbers, so the row's minimum width can be added up rather than guessed. */
const ROW_GAP = 12;

/** Below this a pointer is still a click, so a header drag cannot steal the sort. */
const DRAG_THRESHOLD = 4;

const KEYBOARD_RESIZE_STEP = 16;
const KEYBOARD_RESIZE_LEAP = 64;

// A shadow rather than a border: a border would join the grid track and shift every column by a
// pixel. The opaque background stops the scrolling columns showing through the pinned one.
const PINNED_START = "sticky start-0 ps-4 shadow-[1px_0_0_0_var(--color-line-soft)]";

// The row centres its cells, so without `self-stretch` an opaque cell is a band with daylight
// above and below it, and the scrolling columns slide through the gaps.
const PINNED_FILL = "flex items-center self-stretch bg-panel transition group-hover:bg-panel2";

/**
 * The company grid: TanStack Table v9 computing the models, this file owning every pixel.
 *
 * <p>Shared rather than per-screen, because Strategy and the three Companies stages are one table
 * rendering four sources. Each caller builds its own table — its own columns, its own row actions,
 * its own data — and hands the instance here; the layout, the pinning, the scroll behaviour and the
 * loading and failure states are this component's, once.
 *
 * <p>One scroll box for both axes with the header sticky inside it. A body with its own
 * `overflow-y` becomes a horizontal scroll container too — the browser will not honour
 * `overflow-x: visible` beside a scrolling y-axis — which would strand the header the moment the
 * rows scrolled sideways.
 *
 * <p>Sorting and paging belong to the caller and, in both current callers, to the server: a page
 * holds 25 rows out of tens of thousands, so a header click changes the query rather than the array.
 *
 * <p>Columns move and resize by drag. Neither gesture re-renders while it is in flight: the grid
 * template lives in a CSS variable the handlers write straight to the DOM, and React only sees the
 * result on pointerup. Dragging 24 columns across 25 rows through state would repaint 600 cells a
 * frame.
 */
/**
 * The features every grid using this component registers, and the column meta they all declare.
 *
 * <p>It exists as a *concrete* type because v9 derives a table's API from the feature keys present in
 * `TFeatures`, and that lookup cannot resolve through a generic parameter — written against
 * `TFeatures extends TableFeatures`, this file would find that `getIsPinned`, `getIsSorted` and
 * `FlexRender` do not exist on the table it was handed. So the prop stays precisely typed for
 * callers, and the body works against this shape instead.
 */
type GridFeatures = {
  columnOrderingFeature: typeof columnOrderingFeature;
  columnPinningFeature: typeof columnPinningFeature;
  columnVisibilityFeature: typeof columnVisibilityFeature;
  rowSortingFeature: typeof rowSortingFeature;
  columnMeta: DataGridColumnLayout;
};

type GridColumn<TData extends RowData> = Column<GridFeatures, TData, unknown>;

export function DataGrid<TFeatures extends TableFeatures, TData extends RowData>({
  table,
  label,
  loading,
  error,
  errorMessage,
  emptyMessage,
  layout,
  onLayoutChange,
}: {
  table: ReactTable<TFeatures, TData>;
  /** Names the grid for screen readers — "Companies", "Shortlisted companies". */
  label: string;
  loading: boolean;
  error: boolean;
  errorMessage: ReactNode;
  emptyMessage: ReactNode;
  /** Where the user has dragged the columns. Order is the table's; only the widths are read here. */
  layout: GridLayout;
  onLayoutChange: (layout: GridLayout) => void;
}) {
  /*
   * The one cast, and the reason GridFeatures exists. Every caller registers exactly those four
   * features and differs only in its `tableMeta` — which this component never reads, and which is
   * what stops two concrete table types being assignable to one another directly.
   */
  const grid = table as unknown as ReactTable<GridFeatures, TData>;
  const visibleColumns = grid.getVisibleLeafColumns();

  const scrollRef = useRef<HTMLDivElement>(null);
  const resizeRef = useRef<ResizeSession<TData> | null>(null);
  const reorderRef = useRef<ReorderSession<TData> | null>(null);
  // The pointerup that ends a drag is followed by a click on the sort button, and the session is
  // gone by then — so the fact that a drag happened has to outlive it.
  const draggedRef = useRef(false);
  const [announcement, setAnnouncement] = useState("");

  const { cols, min } = templateOf(visibleColumns, layout.widths);

  const setWidth = (column: GridColumn<TData>, width: number) => {
    onLayoutChange({ ...layout, widths: { ...layout.widths, [column.id]: width } });
    setAnnouncement(`${titleOf(column)} width ${width} pixels`);
  };

  const clearWidth = (column: GridColumn<TData>) => {
    const widths = { ...layout.widths };
    delete widths[column.id];
    onLayoutChange({ ...layout, widths });
    setAnnouncement(`${titleOf(column)} width reset`);
  };

  const preview = (widths: Record<string, number>) => {
    const box = scrollRef.current;
    if (!box) return;
    const next = templateOf(visibleColumns, widths);
    box.style.setProperty("--dg-cols", next.cols);
    box.style.setProperty("--dg-min", `${next.min}px`);
  };

  const startResize = (event: PointerEvent<HTMLElement>, column: GridColumn<TData>) => {
    const cell = event.currentTarget.closest("[data-column-id]");
    if (!(cell instanceof HTMLElement)) return;
    // Without this the header's own pointerdown starts a reorder as well as a resize.
    event.stopPropagation();
    event.preventDefault();
    event.currentTarget.setPointerCapture?.(event.pointerId);
    resizeRef.current = {
      column,
      startX: event.clientX,
      startWidth: cell.getBoundingClientRect().width,
      // The pointer travels the other way when the reading direction does.
      sign: getComputedStyle(cell).direction === "rtl" ? -1 : 1,
      floor: floorOf(column),
      width: null,
    };
  };

  const moveResize = (event: PointerEvent<HTMLElement>) => {
    const session = resizeRef.current;
    if (!session) return;
    const travelled = session.sign * (event.clientX - session.startX);
    session.width = Math.max(session.floor, Math.round(session.startWidth + travelled));
    preview({ ...layout.widths, [session.column.id]: session.width });
  };

  const endResize = () => {
    const session = resizeRef.current;
    resizeRef.current = null;
    if (!session || session.width === null) return;
    setWidth(session.column, session.width);
  };

  const startReorder = (event: PointerEvent<HTMLElement>, column: GridColumn<TData>) => {
    // Touch would have to choose between moving a column and scrolling the grid, and scrolling wins.
    if (event.pointerType === "touch" || !isMovable(column)) return;
    draggedRef.current = false;
    reorderRef.current = { column, startX: event.clientX, dragging: false, target: null };
  };

  const moveReorder = (event: PointerEvent<HTMLElement>) => {
    const session = reorderRef.current;
    if (!session) return;
    if (!session.dragging) {
      if (Math.abs(event.clientX - session.startX) < DRAG_THRESHOLD) return;
      session.dragging = true;
      // Captured here rather than on pointerdown: a captured pointer retargets the click that
      // follows it away from the sort button, so capturing every press would cost every sort.
      event.currentTarget.setPointerCapture?.(event.pointerId);
      cellOf(scrollRef.current, session.column.id)?.style.setProperty("opacity", "0.4");
    }
    const over = headerUnder(scrollRef.current, event.clientX);
    const target = over && over !== session.column.id ? columnById(visibleColumns, over) : undefined;
    // Nothing may land before the pinned column: the row would scroll out from under its own name.
    const valid = target && !target.getIsPinned() ? target : null;
    if (valid?.id === session.target?.id) return;
    paintDropEdge(scrollRef.current, session.target?.id, null);
    session.target = valid ?? null;
    if (valid) paintDropEdge(scrollRef.current, valid.id, isAfter(session.column, valid));
  };

  const endReorder = () => {
    const session = reorderRef.current;
    reorderRef.current = null;
    if (!session) return;
    draggedRef.current = session.dragging;
    cellOf(scrollRef.current, session.column.id)?.style.removeProperty("opacity");
    paintDropEdge(scrollRef.current, session.target?.id, null);
    if (!session.dragging || !session.target) return;
    moveColumn(grid, session.column, session.target.id, setAnnouncement);
  };

  const onHeaderKeyDown = (event: KeyboardEvent, column: GridColumn<TData>) => {
    if (!event.altKey || !isMovable(column)) return;
    const step = event.key === "ArrowRight" ? 1 : event.key === "ArrowLeft" ? -1 : 0;
    if (step === 0) return;
    const neighbour = visibleColumns[column.getIndex() + step];
    if (!neighbour || neighbour.getIsPinned()) return;
    event.preventDefault();
    moveColumn(grid, column, neighbour.id, setAnnouncement);
  };

  const onHandleKeyDown = (event: KeyboardEvent, column: GridColumn<TData>) => {
    if (event.key === "Home") {
      event.preventDefault();
      clearWidth(column);
      return;
    }
    const step = event.key === "ArrowRight" ? 1 : event.key === "ArrowLeft" ? -1 : 0;
    if (step === 0) return;
    event.preventDefault();
    const cell = cellOf(scrollRef.current, column.id);
    const current = layout.widths[column.id] ?? cell?.getBoundingClientRect().width ?? floorOf(column);
    const leap = event.shiftKey ? KEYBOARD_RESIZE_LEAP : KEYBOARD_RESIZE_STEP;
    setWidth(column, Math.max(floorOf(column), Math.round(current + step * leap)));
  };

  const rows = grid.getRowModel().rows;

  const refreshing = loading && rows.length > 0;

  const track = { gridTemplateColumns: "var(--dg-cols)", minWidth: "var(--dg-min)" };

  return (
    <div className="flex min-h-0 flex-1 flex-col overflow-hidden rounded-[8px] border border-line bg-panel">
      <span aria-live="polite" className="sr-only">
        {announcement}
      </span>
      <div
        ref={scrollRef}
        role="table"
        aria-label={label}
        aria-busy={loading}
        style={{ "--dg-cols": cols, "--dg-min": `${min}px` } as CSSProperties}
        className="flex min-h-0 flex-1 flex-col overflow-auto"
      >
        {grid.getHeaderGroups().map((headerGroup) => (
          <div
            key={headerGroup.id}
            role="row"
            style={track}
            className="sticky top-0 z-20 grid flex-none items-center gap-3 border-b border-line bg-panel2 py-2.5"
          >
            {headerGroup.headers.map((header) => {
              const column = header.column;
              const sortable = column.getCanSort();
              const sorted = column.getIsSorted();
              const pinned = column.getIsPinned();
              const movable = isMovable(column);
              const label = (
                // `block`: an inline box ignores overflow, so "EMPLOYEES" would run over "FOUNDED", not clip.
                <span
                  className={cn(
                    "block truncate font-sans text-[11px] font-semibold uppercase tracking-[0.04em]",
                    sorted ? "text-text" : "text-text3",
                  )}
                >
                  <grid.FlexRender header={header} />
                  {sorted && (sorted === "asc" ? " ↑" : " ↓")}
                </span>
              );
              return (
                <div
                  key={header.id}
                  role="columnheader"
                  data-column-id={column.id}
                  aria-sort={sorted ? (sorted === "asc" ? "ascending" : "descending") : undefined}
                  onPointerDown={movable ? (event) => startReorder(event, column) : undefined}
                  onPointerMove={movable ? moveReorder : undefined}
                  onPointerUp={movable ? endReorder : undefined}
                  onPointerCancel={movable ? endReorder : undefined}
                  className={cn(
                    "relative min-w-0",
                    movable && "cursor-grab",
                    // The gutter travels with the pinned cell; padding on the row would scroll out from under it.
                    pinned === "start" && `${PINNED_START} z-10 self-stretch bg-panel2`,
                    !pinned && "first:ps-4 last:pe-4",
                  )}
                >
                  {sortable ? (
                    <button
                      type="button"
                      // A drag that crossed the threshold is a move, and must not also sort.
                      onClick={(event) => {
                        if (draggedRef.current) {
                          draggedRef.current = false;
                          return;
                        }
                        column.getToggleSortingHandler()?.(event);
                      }}
                      onKeyDown={(event) => onHeaderKeyDown(event, column)}
                      aria-keyshortcuts={movable ? "Alt+ArrowLeft Alt+ArrowRight" : undefined}
                      className="block w-full text-left transition hover:opacity-80"
                    >
                      {label}
                    </button>
                  ) : (
                    label
                  )}

                  <span
                    role="separator"
                    aria-orientation="vertical"
                    aria-label={`Resize ${titleOf(column)} column`}
                    tabIndex={0}
                    onPointerDown={(event) => startResize(event, column)}
                    onPointerMove={moveResize}
                    onPointerUp={endResize}
                    onPointerCancel={endResize}
                    onDoubleClick={() => clearWidth(column)}
                    onKeyDown={(event) => onHandleKeyDown(event, column)}
                    className="absolute inset-y-0 -end-2 z-10 w-3 cursor-col-resize touch-none opacity-0 transition-opacity hover:opacity-100 focus-visible:opacity-100"
                  >
                    <span className="mx-auto block h-full w-px bg-amber" />
                  </span>
                </div>
              );
            })}
          </div>
        ))}

        <div
          className={cn(
            "flex-1 transition-opacity",
            refreshing && "pointer-events-none opacity-40",
          )}
        >
          {/* A refused read is not an empty result: branch on the error before the count, or a 403
              renders "nothing here" as a fact about the data. */}
          {error ? (
            <GridMessage>{errorMessage}</GridMessage>
          ) : loading && rows.length === 0 ? (
            <RowSkeleton />
          ) : rows.length === 0 ? (
            <GridMessage>{emptyMessage}</GridMessage>
          ) : (
            rows.map((row) => (
              <div
                key={row.id}
                role="row"
                style={track}
                className="group grid h-[52px] items-center gap-3 border-b border-line-soft transition hover:bg-panel2"
              >
                {row.getVisibleCells().map((cell) => {
                  const pinned = cell.column.getIsPinned();
                  return (
                    <div
                      key={cell.id}
                      role="cell"
                      className={cn(
                        "min-w-0",
                        // The row paints the hover tint and the pinned cell covers it, so it has to repaint it.
                        pinned === "start" && `${PINNED_FILL} ${PINNED_START}`,
                        !pinned && "first:ps-4 last:pe-4",
                      )}
                    >
                      <grid.FlexRender cell={cell} />
                    </div>
                  );
                })}
              </div>
            ))
          )}
        </div>
      </div>
    </div>
  );
}

interface ResizeSession<TData extends RowData> {
  column: GridColumn<TData>;
  startX: number;
  startWidth: number;
  sign: 1 | -1;
  floor: number;
  width: number | null;
}

interface ReorderSession<TData extends RowData> {
  column: GridColumn<TData>;
  startX: number;
  dragging: boolean;
  target: GridColumn<TData> | null;
}

/**
 * The one grid template the header and every row share.
 *
 * <p>`fr`, not a literal `%`: percentages resolve against the grid's content box and ignore the gaps,
 * so shares summing to 100 would overflow the row by the width of every gap between them. A width a
 * user has dragged wins over the declared share, which is what makes a resized column hold still
 * while its neighbours keep flexing.
 *
 * <p>A grid honours a `minmax` floor whatever the container's width, so without the `min` sum the row
 * overflows a container that clips rather than scrolls, and the last column stops existing.
 */
function templateOf<TData extends RowData>(
  columns: readonly GridColumn<TData>[],
  widths: Record<string, number>,
): { cols: string; min: number } {
  const cols: string[] = [];
  let min = (columns.length - 1) * ROW_GAP;
  for (const column of columns) {
    const dragged = widths[column.id];
    const layout = column.columnDef.meta;
    if (dragged !== undefined) {
      cols.push(`${dragged}px`);
      min += dragged;
      continue;
    }
    cols.push(
      !layout
        ? "1fr"
        : layout.share > 0
          ? `minmax(${layout.min}px, ${layout.share}fr)`
          : `${layout.min}px`,
    );
    min += layout?.min ?? DEFAULT_COLUMN_MIN;
  }
  return { cols: cols.join(" "), min };
}

/** A pinned column stays first, so the name never scrolls away from the row it names. */
function isMovable<TData extends RowData>(column: GridColumn<TData>): boolean {
  return !column.getIsPinned();
}

function floorOf<TData extends RowData>(column: GridColumn<TData>): number {
  return column.columnDef.meta?.min ?? DEFAULT_COLUMN_MIN;
}

function titleOf<TData extends RowData>(column: GridColumn<TData>): string {
  const header = column.columnDef.header;
  return typeof header === "string" && header.length > 0 ? header : column.id;
}

function columnById<TData extends RowData>(
  columns: readonly GridColumn<TData>[],
  id: string,
): GridColumn<TData> | undefined {
  return columns.find((column) => column.id === id);
}

function isAfter<TData extends RowData>(
  moving: GridColumn<TData>,
  target: GridColumn<TData>,
): boolean {
  return moving.getIndex() < target.getIndex();
}

/**
 * Reorders every leaf column, not just the visible ones: a hidden column left out of the order would
 * be appended when it came back rather than returning to where its author put it.
 */
function moveColumn<TData extends RowData>(
  grid: ReactTable<GridFeatures, TData>,
  moving: GridColumn<TData>,
  targetId: string,
  announce: (message: string) => void,
) {
  const ids = grid.getAllLeafColumns().map((column) => column.id);
  const from = ids.indexOf(moving.id);
  const to = ids.indexOf(targetId);
  if (from === -1 || to === -1 || from === to) return;
  const next = [...ids];
  next.splice(from, 1);
  next.splice(next.indexOf(targetId) + (from < to ? 1 : 0), 0, moving.id);
  grid.setColumnOrder(next);
  announce(`${titleOf(moving)} moved to column ${next.indexOf(moving.id) + 1} of ${next.length}`);
}

function cellOf(box: HTMLElement | null, id: string): HTMLElement | null {
  return box?.querySelector(`[role="columnheader"][data-column-id="${CSS.escape(id)}"]`) ?? null;
}

/** An inset shadow rather than a border, for the reason `PINNED_START` is one: a border shifts the track. */
function paintDropEdge(box: HTMLElement | null, id: string | undefined, after: boolean | null) {
  if (!id) return;
  const cell = cellOf(box, id);
  if (!cell) return;
  if (after === null) {
    cell.style.removeProperty("box-shadow");
    return;
  }
  cell.style.setProperty(
    "box-shadow",
    `inset ${after ? "-2px" : "2px"} 0 0 0 var(--color-amber)`,
  );
}

/**
 * The header the pointer is over, found by measuring rather than by `elementFromPoint`: the pinned
 * column floats over the scrolled ones, so hit-testing by z-order would answer "name" for whatever
 * has slid underneath it. Measuring left to right and keeping the last match gives the scrolled
 * column the overlap instead.
 */
function headerUnder(box: HTMLElement | null, x: number): string | undefined {
  const cells = box?.querySelectorAll('[role="columnheader"][data-column-id]');
  if (!cells) return undefined;
  let found: string | undefined;
  for (const cell of Array.from(cells)) {
    if (!(cell instanceof HTMLElement)) continue;
    const rect = cell.getBoundingClientRect();
    if (x >= rect.left && x <= rect.right) found = cell.dataset.columnId;
  }
  return found;
}

/** One ordinary text cell. Shared so a column reads the same in whichever grid it appears. */
export function DataGridCell({ value, muted }: { value: string | null; muted?: boolean }) {
  return (
    <TruncatedText
      value={value}
      className={muted ? "font-sans text-[13px] text-text3" : "font-sans text-[13px] text-text2"}
    />
  );
}

/** The icon button every grid's row actions and links are built from. */
export const GRID_ICON_BUTTON =
  "grid size-9 place-items-center rounded-[5px] text-text3 transition hover:bg-panel2 hover:text-text lg:size-6";

function GridMessage({ children }: { children: ReactNode }) {
  return (
    <div className="px-4 py-10 text-center font-mono text-[13px] text-text3">
      <Icon d={ICONS.search} size={18} className="mx-auto mb-2 text-text3" />
      {children}
    </div>
  );
}

function RowSkeleton() {
  return (
    <div>
      {Array.from({ length: 8 }, (_, index) => (
        <div key={index} className="flex h-[52px] items-center border-b border-line-soft px-4">
          <div className="h-3 w-1/3 animate-pulse rounded bg-panel2" />
        </div>
      ))}
    </div>
  );
}
