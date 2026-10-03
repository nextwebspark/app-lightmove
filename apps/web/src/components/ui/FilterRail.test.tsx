import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useEffect, useState } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { FilterRail, FilterRailToggle } from "./FilterRail";

function narrowViewport() {
  const desktop = window.matchMedia;
  vi.spyOn(window, "matchMedia").mockImplementation((query) => ({ ...desktop(query), matches: false }));
}

function RailWithToggle({ onWindowEscape }: { onWindowEscape?: () => void }) {
  const [open, setOpen] = useState(false);
  useEffect(() => {
    if (!onWindowEscape) return;
    const listener = (event: KeyboardEvent) => event.key === "Escape" && onWindowEscape();
    window.addEventListener("keydown", listener);
    return () => window.removeEventListener("keydown", listener);
  }, [onWindowEscape]);
  return (
    <div>
      <FilterRailToggle open={open} onToggle={() => setOpen((shown) => !shown)} />
      <FilterRail label="Filters" open={open} onClose={() => setOpen(false)}>
        <input aria-label="Narrow tags" />
      </FilterRail>
    </div>
  );
}

describe("FilterRail", () => {
  afterEach(() => vi.restoreAllMocks());

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

  it("hides without unmounting, so what was typed into it survives a close", async () => {
    render(<RailWithToggle />);

    expect(screen.queryByRole("region", { name: "Filters" })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Show Filters" }));
    await userEvent.type(screen.getByRole("textbox", { name: "Narrow tags" }), "lead");
    await userEvent.click(screen.getByRole("button", { name: "Hide Filters" }));
    await userEvent.click(screen.getByRole("button", { name: "Show Filters" }));
    expect(screen.getByRole("textbox", { name: "Narrow tags" })).toHaveValue("lead");
  });

  it("as an overlay takes focus, closes on Escape without the Escape reaching the page, and hands focus back", async () => {
    narrowViewport();
    const onWindowEscape = vi.fn();
    render(<RailWithToggle onWindowEscape={onWindowEscape} />);

    await userEvent.click(screen.getByRole("button", { name: "Show Filters" }));
    expect(screen.getByRole("button", { name: "Hide filters" })).toHaveFocus();

    await userEvent.keyboard("{Escape}");
    expect(screen.queryByRole("region", { name: "Filters" })).not.toBeInTheDocument();
    expect(onWindowEscape).not.toHaveBeenCalled();
    expect(screen.getByRole("button", { name: "Show Filters" })).toHaveFocus();
  });

  it("beside the results leaves focus and Escape alone", async () => {
    render(<RailWithToggle />);

    await userEvent.click(screen.getByRole("button", { name: "Show Filters" }));
    expect(screen.getByRole("button", { name: "Hide Filters" })).toHaveFocus();
    await userEvent.click(screen.getByRole("textbox", { name: "Narrow tags" }));
    await userEvent.keyboard("{Escape}");
    expect(screen.getByRole("region", { name: "Filters" })).toBeInTheDocument();
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
