import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it, vi } from "vitest";
import { GridFilterButton } from "./GridFilters";
import type { DataGridColumnFilter } from "../../lib/gridFilters";

const STATUS_OPTIONS = [
  { value: "contacted", label: "Contacted" },
  { value: "interested", label: "Interested" },
  { value: "declined", label: "Declined" },
];

function Harness({ initialName = "", initialStatus = [] as string[] }) {
  const [name, setName] = useState(initialName);
  const [status, setStatus] = useState<string[]>(initialStatus);
  const filters: Record<string, DataGridColumnFilter> = {
    name: { label: "Company", value: name, onChange: setName, "aria-label": "Filter by company name" },
    status: {
      kind: "check",
      label: "Status",
      options: STATUS_OPTIONS,
      selected: status,
      onChange: setStatus,
      "aria-label": "Filter by status",
    },
  };
  return <GridFilterButton filters={filters} resultLabel="3 of 9 companies" />;
}

describe("GridFilterButton", () => {
  it("draws nothing for a grid that offers no filters", () => {
    const { container } = render(<GridFilterButton filters={{}} />);
    expect(container).toBeEmptyDOMElement();
  });

  it("is a plain Filter button with no Clear all while nothing is in force", () => {
    render(<Harness />);

    expect(screen.getByRole("button", { name: "Filter" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Clear all filters" })).not.toBeInTheDocument();
  });

  it("counts the filters in force and clears every one of them from its ✕", async () => {
    render(<Harness initialName="acwa" initialStatus={["contacted", "interested"]} />);

    expect(screen.getByRole("button", { name: "Filter, 2 applied" })).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Clear all filters" }));

    expect(screen.getByRole("button", { name: "Filter" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Clear all filters" })).not.toBeInTheDocument();
  });

  it("lists only the filters in force, each summarised, with the result count", async () => {
    render(<Harness initialStatus={["contacted", "interested", "declined"]} />);

    await userEvent.click(screen.getByRole("button", { name: "Filter, 1 applied" }));

    expect(screen.getByText("3 of 9 companies")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Status values" })).toHaveTextContent("Contacted, Interested +1");
    expect(screen.queryByRole("textbox", { name: "Filter by company name" })).not.toBeInTheDocument();
  });

  it("adds a filter on a column not yet filtered, and stays open as its first tick puts it in force", async () => {
    render(<Harness />);

    await userEvent.click(screen.getByRole("button", { name: "Filter" }));
    expect(screen.getByText("No filters applied.")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Add filter" }));
    await userEvent.click(
      within(screen.getByRole("group", { name: "Choose a column to filter" })).getByRole("button", { name: "Status" }),
    );
    await userEvent.click(screen.getByRole("checkbox", { name: "Interested" }));

    expect(screen.getByRole("button", { name: "Filter, 1 applied" })).toBeInTheDocument();
    expect(screen.getByRole("checkbox", { name: "Interested" })).toBeChecked();
  });

  it("drops a value list over the panel that a click elsewhere in the panel closes, the panel staying open", async () => {
    render(<Harness initialStatus={["contacted"]} />);

    await userEvent.click(screen.getByRole("button", { name: "Filter, 1 applied" }));
    await userEvent.click(screen.getByRole("button", { name: "Status values" }));
    await userEvent.click(screen.getByRole("checkbox", { name: "Interested" }));
    expect(screen.getByRole("checkbox", { name: "Interested" })).toBeChecked();

    await userEvent.click(screen.getByText("Filters"));

    expect(screen.queryByRole("checkbox", { name: "Interested" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Add filter" })).toBeInTheDocument();
  });

  it("removes one filter from its line and leaves the others in force", async () => {
    render(<Harness initialName="acwa" initialStatus={["contacted"]} />);

    await userEvent.click(screen.getByRole("button", { name: "Filter, 2 applied" }));
    await userEvent.click(screen.getByRole("button", { name: "Remove Company filter" }));

    expect(screen.queryByRole("textbox", { name: "Filter by company name" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Status values" })).toHaveTextContent("Contacted");
    expect(screen.getByRole("button", { name: "Filter, 1 applied" })).toBeInTheDocument();
  });

  it("hands focus back to the Filter button, and says so, when its ✕ clears everything", async () => {
    render(<Harness initialName="acwa" />);

    await userEvent.click(screen.getByRole("button", { name: "Clear all filters" }));

    expect(screen.getByRole("button", { name: "Filter" })).toHaveFocus();
    expect(screen.getByRole("status")).toHaveTextContent("All filters cleared");
  });

  it("keeps focus in the panel when a line's ✕ takes the line away", async () => {
    render(<Harness initialName="acwa" initialStatus={["contacted"]} />);

    await userEvent.click(screen.getByRole("button", { name: "Filter, 2 applied" }));
    await userEvent.click(screen.getByRole("button", { name: "Remove Status filter" }));

    expect(screen.getByRole("button", { name: "Add filter" })).toHaveFocus();
    expect(screen.getByRole("status")).toHaveTextContent("Status filter removed");
  });

  it("offers no Add filter once every column is already listed", async () => {
    render(<Harness initialName="acwa" initialStatus={["contacted"]} />);

    await userEvent.click(screen.getByRole("button", { name: "Filter, 2 applied" }));

    expect(screen.getByRole("button", { name: "Add filter" })).toBeDisabled();
  });

  it("gives a long option list its own search", async () => {
    const options = Array.from({ length: 12 }, (_, index) => ({ value: `v${index}`, label: `Sector ${index}` }));
    const onChange = vi.fn();
    render(
      <GridFilterButton
        filters={{
          sector: {
            kind: "check",
            label: "Sector",
            options,
            selected: ["v1"],
            onChange,
            "aria-label": "Filter by sector",
          },
        }}
      />,
    );

    await userEvent.click(screen.getByRole("button", { name: "Filter, 1 applied" }));
    await userEvent.click(screen.getByRole("button", { name: "Sector values" }));
    await userEvent.type(screen.getByRole("searchbox", { name: "Search Sector options" }), "11");

    expect(screen.getAllByRole("checkbox")).toHaveLength(1);
    expect(screen.getByRole("checkbox", { name: "Sector 11" })).toBeInTheDocument();
  });
});
