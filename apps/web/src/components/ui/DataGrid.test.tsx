import {
  columnOrderingFeature,
  columnPinningFeature,
  columnVisibilityFeature,
  createColumnHelper,
  rowSortingFeature,
  tableFeatures,
  useTable,
  type ColumnOrderState,
  type Updater,
} from "@tanstack/react-table";
import { fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { DataGrid, type DataGridColumnLayout } from "./DataGrid";
import { EMPTY_GRID_LAYOUT, layoutColumnsOf, type GridLayout } from "../../lib/useGridLayout";

interface Row {
  name: string;
  sector: string;
  revenue: string;
}

const features = tableFeatures({
  columnOrderingFeature,
  columnPinningFeature,
  columnVisibilityFeature,
  rowSortingFeature,
  columnMeta: {} as DataGridColumnLayout,
});

const helper = createColumnHelper<typeof features, Row>();

const columns = helper.columns([
  helper.accessor("name", { id: "name", header: "Company", meta: { share: 22, min: 230 } }),
  helper.accessor("sector", { id: "sector", header: "Sector", meta: { share: 14, min: 140 } }),
  // Unsortable on purpose: 17 of the Strategy grid's 24 columns are, and they render no button.
  helper.accessor("revenue", {
    id: "revenue",
    header: "Revenue",
    enableSorting: false,
    meta: { share: 11, min: 120 },
  }),
]);

const DATA: Row[] = [{ name: "Aramco", sector: "Energy", revenue: "$1bn" }];

const onSort = vi.fn();

function Harness({ onLayout }: { onLayout?: (layout: GridLayout) => void }) {
  const [layout, setLayout] = useState<GridLayout>(EMPTY_GRID_LAYOUT);
  const table = useTable({
    features,
    columns,
    data: DATA,
    getRowId: (row) => row.name,
    initialState: { columnPinning: { start: ["name"], end: [] } },
    manualSorting: true,
    state: { columnOrder: layout.order },
    onSortingChange: onSort,
    onColumnOrderChange: (updater: Updater<ColumnOrderState>) => {
      const order = typeof updater === "function" ? updater(layout.order) : updater;
      setLayout({ ...layout, order });
    },
  });
  return (
    <DataGrid
      table={table}
      label="Companies"
      loading={false}
      error={false}
      errorMessage="failed"
      emptyMessage="nothing"
      layout={layout}
      onLayoutChange={(next) => {
        setLayout(next);
        onLayout?.(next);
      }}
    />
  );
}

const headers = () => screen.getAllByRole("columnheader").map((cell) => cell.textContent);

const cellFor = (id: string) =>
  screen.getAllByRole("columnheader").find((cell) => cell.dataset.columnId === id)!;

const rect = (left: number, width: number) =>
  ({
    width,
    height: 32,
    top: 0,
    left,
    right: left + width,
    bottom: 32,
    x: left,
    y: 0,
    toJSON: () => ({}),
  }) as DOMRect;

/**
 * jsdom lays nothing out, so every cell measures zero and both gestures have nothing to work from.
 * Each header is given the slot it would occupy on screen; anything else keeps a plain 140px box.
 */
const SLOTS: Record<string, DOMRect> = {
  name: rect(0, 230),
  sector: rect(242, 140),
  revenue: rect(394, 120),
};

const stubLayout = () =>
  vi.spyOn(HTMLElement.prototype, "getBoundingClientRect").mockImplementation(function (
    this: HTMLElement,
  ) {
    return SLOTS[this.dataset.columnId ?? ""] ?? rect(0, 140);
  });

describe("DataGrid columns", () => {
  beforeEach(() => vi.resetAllMocks());

  it("moves a column one place with Alt and an arrow", async () => {
    render(<Harness />);
    expect(headers()).toEqual(["Company", "Sector", "Revenue"]);

    await userEvent.click(screen.getByRole("button", { name: "Sector" }));
    await userEvent.keyboard("{Alt>}{ArrowRight}{/Alt}");

    expect(headers()).toEqual(["Company", "Revenue", "Sector"]);
  });

  it("keeps the pinned column first, because a row must not scroll away from its own name", async () => {
    render(<Harness />);

    await userEvent.click(screen.getByRole("button", { name: "Company" }));
    await userEvent.keyboard("{Alt>}{ArrowRight}{/Alt}");

    expect(headers()[0]).toBe("Company");
  });

  it("will not move a column onto the pinned one's place", async () => {
    render(<Harness />);

    await userEvent.click(screen.getByRole("button", { name: "Sector" }));
    await userEvent.keyboard("{Alt>}{ArrowLeft}{/Alt}");

    expect(headers()).toEqual(["Company", "Sector", "Revenue"]);
  });

  it("widens a column from the keyboard and remembers the width", async () => {
    stubLayout();
    const onLayout = vi.fn();
    render(<Harness onLayout={onLayout} />);

    // The handle refuses focus on pointerdown so a drag never steals it; a keyboard reaches it by tab.
    screen.getByRole("separator", { name: "Resize Sector column" }).focus();
    await userEvent.keyboard("{ArrowRight}");

    expect(onLayout).toHaveBeenCalledWith(expect.objectContaining({ widths: { sector: 156 } }));
  });

  it("will not let a drag take a column below its declared floor", async () => {
    stubLayout();
    const onLayout = vi.fn();
    render(<Harness onLayout={onLayout} />);
    const handle = screen.getByRole("separator", { name: "Resize Sector column" });

    await userEvent.pointer([
      { target: handle, keys: "[MouseLeft>]", coords: { x: 300, y: 10 } },
      { target: handle, coords: { x: 100, y: 10 } },
      { target: handle, keys: "[/MouseLeft]", coords: { x: 100, y: 10 } },
    ]);

    // 140 - 200 is well under the 140px floor, so the floor is what gets stored.
    expect(onLayout).toHaveBeenCalledWith(expect.objectContaining({ widths: { sector: 140 } }));
  });

  it("drops a column where the pointer let it go", async () => {
    stubLayout();
    render(<Harness />);
    const header = screen.getByRole("button", { name: "Sector" });

    await userEvent.pointer([
      { target: header, keys: "[MouseLeft>]", coords: { x: 312, y: 10 } },
      { target: header, coords: { x: 450, y: 10 } },
      { target: header, keys: "[/MouseLeft]", coords: { x: 450, y: 10 } },
    ]);

    expect(headers()).toEqual(["Company", "Revenue", "Sector"]);
  });

  it("does not sort the column a drag just moved", async () => {
    stubLayout();
    render(<Harness />);
    const header = screen.getByRole("button", { name: "Sector" });

    await userEvent.pointer([
      { target: header, keys: "[MouseLeft>]", coords: { x: 100, y: 10 } },
      { target: header, coords: { x: 300, y: 10 } },
      { target: header, keys: "[/MouseLeft]", coords: { x: 300, y: 10 } },
    ]);

    expect(onSort).not.toHaveBeenCalled();
  });

  it("takes the pointer only once a drag really starts, so a press still reaches the sort", async () => {
    // A captured pointer retargets the click that follows it away from the button, and capturing on
    // every pointerdown silently cost every sort on the grid. jsdom cannot retarget a click, so the
    // guard is the capture itself: pressing must not take the pointer, dragging must.
    stubLayout();
    const capture = vi.fn();
    Object.defineProperty(HTMLElement.prototype, "setPointerCapture", {
      configurable: true,
      writable: true,
      value: capture,
    });
    render(<Harness />);
    const header = screen.getByRole("button", { name: "Sector" });

    await userEvent.click(header);
    expect(capture).not.toHaveBeenCalled();

    await userEvent.pointer([
      { target: header, keys: "[MouseLeft>]", coords: { x: 312, y: 10 } },
      { target: header, coords: { x: 450, y: 10 } },
      { target: header, keys: "[/MouseLeft]", coords: { x: 450, y: 10 } },
    ]);
    expect(capture).toHaveBeenCalled();
  });

  it("does not eat the pinned column's sort after some other column was dragged", async () => {
    // The pinned column starts no reorder of its own, so a suppression flag only a movable header
    // could clear left Company needing two clicks to sort once anything had been moved.
    stubLayout();
    render(<Harness />);
    const sector = cellFor("sector");

    // Raw events, no click: a captured pointer retargets the drag's own click away from the button,
    // which is the case that stranded the flag. user-event always fires one, so it cannot show this.
    fireEvent.pointerDown(sector, { clientX: 312, pointerId: 1, buttons: 1 });
    fireEvent.pointerMove(sector, { clientX: 450, pointerId: 1, buttons: 1 });
    fireEvent.pointerUp(sector, { clientX: 450, pointerId: 1 });
    await userEvent.click(screen.getByRole("button", { name: "Company" }));

    expect(onSort).toHaveBeenCalled();
  });

  it("drops a press that ended before the threshold, rather than arming the next hover", async () => {
    // Until the threshold no pointer is captured, so the release lands wherever the cursor is —
    // over the pinned header it reported nothing, and the next hover became a button-less drag.
    stubLayout();
    render(<Harness />);
    const sector = cellFor("sector");

    fireEvent.pointerDown(sector, { clientX: 312, pointerId: 1, buttons: 1 });
    // Released over the pinned header, which starts no reorder and so reported nothing.
    fireEvent.pointerUp(cellFor("name"), { clientX: 310, pointerId: 1 });
    // A hover, no button held. The stale session used to pass the threshold and take the column.
    fireEvent.pointerMove(sector, { clientX: 450, pointerId: 1, buttons: 0 });

    expect(sector.style.opacity).toBe("");
    expect(headers()).toEqual(["Company", "Sector", "Revenue"]);
  });

  it("moves a column that has no sort button to hang the shortcut on", async () => {
    // 17 of the Strategy grid's 24 columns are unsortable; the shortcut lived on the sort button,
    // so those were mouse-only.
    render(<Harness />);
    const revenue = screen
      .getAllByRole("columnheader")
      .find((cell) => cell.dataset.columnId === "revenue");
    revenue?.focus();
    await userEvent.keyboard("{Alt>}{ArrowLeft}{/Alt}");

    expect(headers()).toEqual(["Company", "Revenue", "Sector"]);
  });

  it("still sorts on an ordinary click", async () => {
    render(<Harness />);

    await userEvent.click(screen.getByRole("button", { name: "Sector" }));

    expect(onSort).toHaveBeenCalled();
  });

  it("leaves the pinned header stuck, though a resize handle needs its cell positioned", () => {
    // `relative` and `sticky` are one twMerge group: declared the other way round, the pinned column
    // would quietly stop sticking and the row would scroll away from its own name.
    render(<Harness />);

    expect(screen.getAllByRole("columnheader")[0]).toHaveClass("sticky");
    expect(screen.getAllByRole("columnheader")[0]).not.toHaveClass("relative");
  });

  it("offers every column a width floor to remember it by", () => {
    expect(layoutColumnsOf(columns)).toEqual([
      { id: "name", min: 230 },
      { id: "sector", min: 140 },
      { id: "revenue", min: 120 },
    ]);
  });
});
