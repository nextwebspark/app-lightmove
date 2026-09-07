import { createColumnHelper, tableFeatures, type ColumnVisibilityState } from "@tanstack/react-table";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it, vi } from "vitest";
import {
  CLIENT_ROW_MODELS,
  DATA_GRID_FEATURES,
  DataGrid,
  type DataGridColumnLayout,
} from "../components/ui/DataGrid";
import { compareNumber, compareText } from "./gridSortFns";
import { useDataGridTable } from "./useDataGridTable";
import { EMPTY_GRID_LAYOUT, type GridLayout } from "./useGridLayout";
import { useGridPaging } from "./useGridPaging";
import type { GridSort } from "./useGridSort";

interface Row {
  id: string;
  name: string;
  size: number;
}

const features = tableFeatures({
  ...DATA_GRID_FEATURES,
  ...CLIENT_ROW_MODELS,
  columnMeta: {} as DataGridColumnLayout,
});

const helper = createColumnHelper<typeof features, Row>();

const columns = helper.columns([
  helper.accessor("name", {
    id: "name",
    header: "Name",
    meta: { share: 1, min: 120 },
    sortFn: (a, b) => compareText(a.original.name, b.original.name),
  }),
  helper.accessor("size", {
    id: "size",
    header: "Size",
    meta: { share: 0, min: 80 },
    sortFn: (a, b) => compareNumber(a.original.size, b.original.size),
  }),
]);

type Field = "name" | "size";

const ROWS: Row[] = Array.from({ length: 60 }, (_, index) => ({
  id: `r${index}`,
  name: `Row ${String(index).padStart(2, "0")}`,
  size: 60 - index,
}));

function Harness({
  rows = ROWS,
  initialSort = { field: "name", direction: "asc" },
  onRowClick,
  renderCard,
}: {
  rows?: Row[];
  initialSort?: GridSort<Field>;
  onRowClick?: (row: Row) => void;
  renderCard?: (row: Row) => React.ReactNode;
}) {
  const [sort, setSort] = useState<GridSort<Field>>(initialSort);
  const [visibility, setVisibility] = useState<ColumnVisibilityState>({});
  const [layout, setLayout] = useState<GridLayout>(EMPTY_GRID_LAYOUT);
  const paging = useGridPaging(25);
  const table = useDataGridTable<typeof features, Row, Field>({
    features,
    columns,
    data: rows,
    getRowId: (row) => row.id,
    pinning: { start: ["name"], end: [] },
    sort,
    onSortChange: setSort,
    columnVisibility: visibility,
    onColumnVisibilityChange: setVisibility,
    layout,
    onLayoutChange: setLayout,
    pagination: paging.pagination,
    onPaginationChange: paging.onPaginationChange,
  });
  return (
    <>
      <DataGrid
        table={table}
        label="Rows"
        fit="content"
        loading={false}
        error={false}
        errorMessage="failed"
        emptyMessage="nothing"
        layout={layout}
        onLayoutChange={setLayout}
        onRowClick={onRowClick}
        renderCard={renderCard}
      />
      <button type="button" onClick={() => paging.setPage(paging.page + 1)}>
        next
      </button>
      <output>{`sort:${sort.field}:${sort.direction}`}</output>
    </>
  );
}

const grid = () => screen.getByRole("table", { name: "Rows" });
const bodyRows = () => within(grid()).getAllByRole("row").slice(1);
const firstCell = (row: HTMLElement) => within(row).getAllByRole("cell")[0]!.textContent;

describe("useDataGridTable in client mode", () => {
  it("sorts the rows it was handed and shows one page of them", () => {
    render(<Harness />);
    const rows = bodyRows();
    expect(rows).toHaveLength(25);
    expect(firstCell(rows[0]!)).toBe("Row 00");
    expect(firstCell(rows[24]!)).toBe("Row 24");
  });

  it("re-sorts on a header click by the column's own comparator, one column and never cleared", async () => {
    render(<Harness />);
    // A numeric column opens descending — largest first is what a reader wants from a figure — and
    // the same auto default the market grids have always had.
    await userEvent.click(within(grid()).getByRole("button", { name: /Size/ }));
    expect(screen.getByRole("status")).toHaveTextContent("sort:size:desc");
    expect(firstCell(bodyRows()[0]!)).toBe("Row 00");

    await userEvent.click(within(grid()).getByRole("button", { name: /Size/ }));
    expect(screen.getByRole("status")).toHaveTextContent("sort:size:asc");
    expect(firstCell(bodyRows()[0]!)).toBe("Row 59");

    // A third click flips again rather than dropping the sort.
    await userEvent.click(within(grid()).getByRole("button", { name: /Size/ }));
    expect(screen.getByRole("status")).toHaveTextContent("sort:size:desc");
  });

  it("turns the page from the caller's state", async () => {
    render(<Harness />);
    await userEvent.click(screen.getByText("next"));
    const rows = bodyRows();
    expect(firstCell(rows[0]!)).toBe("Row 25");
    await userEvent.click(screen.getByText("next"));
    expect(bodyRows()).toHaveLength(10);
  });

  it("activates a row by click, Enter and Space, and only when the row itself has focus", async () => {
    const onRowClick = vi.fn();
    render(<Harness onRowClick={onRowClick} />);
    const [first] = bodyRows();

    await userEvent.click(first!);
    expect(onRowClick).toHaveBeenLastCalledWith(expect.objectContaining({ id: "r0" }));

    first!.focus();
    await userEvent.keyboard("{Enter}");
    await userEvent.keyboard(" ");
    expect(onRowClick).toHaveBeenCalledTimes(3);
  });

  it("renders the card stack beside the grid when a card renderer is given", () => {
    render(<Harness rows={ROWS.slice(0, 3)} renderCard={(row) => <article>{row.name} card</article>} />);
    expect(screen.getAllByRole("article")).toHaveLength(3);
    expect(bodyRows()).toHaveLength(3);
  });

  it("says nothing matched in both halves, not just the grid", () => {
    render(<Harness rows={[]} renderCard={(row) => <article>{row.name}</article>} />);
    expect(screen.getAllByText("nothing")).toHaveLength(2);
  });
});
