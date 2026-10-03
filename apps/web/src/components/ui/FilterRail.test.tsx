import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { FilterRail, FilterRailToggle } from "./FilterRail";

describe("FilterRail", () => {
  it("closes from its own close button and from the scrim behind it", async () => {
    const onClose = vi.fn();
    const { container } = render(
      <FilterRail label="Filters" onClose={onClose}>
        <p>Tags</p>
      </FilterRail>,
    );

    expect(screen.getByRole("region", { name: "Filters" })).toHaveTextContent("Tags");
    await userEvent.click(screen.getByRole("button", { name: "Hide filters" }));
    await userEvent.click(container.querySelector(".bg-u-scrim")!);
    expect(onClose).toHaveBeenCalledTimes(2);
  });

  it("says which way the toggle goes and carries the caller's badge", async () => {
    const onToggle = vi.fn();
    const { rerender } = render(<FilterRailToggle open={false} onToggle={onToggle} badge={<span>3</span>} />);

    await userEvent.click(screen.getByRole("button", { name: /Show Filters\s*3/, expanded: false }));
    expect(onToggle).toHaveBeenCalledOnce();
    rerender(<FilterRailToggle open onToggle={onToggle} />);
    expect(screen.getByRole("button", { name: "Hide Filters", expanded: true })).toBeInTheDocument();
  });
});
