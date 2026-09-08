import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { PaginationBar } from "./PaginationBar";

describe("PaginationBar", () => {
  it("says where the reader is and pages forward", async () => {
    const onPage = vi.fn();
    render(<PaginationBar page={1} size={25} totalCount={80} onPage={onPage} onSize={() => {}} />);

    expect(screen.getByText("26 - 50 of 80")).toBeInTheDocument();
    await userEvent.click(screen.getByLabelText("Next page"));
    expect(onPage).toHaveBeenCalledWith(2);
  });

  it("keeps the row on screen when the page size changes", async () => {
    const onPage = vi.fn();
    const onSize = vi.fn();
    render(<PaginationBar page={4} size={25} totalCount={500} onPage={onPage} onSize={onSize} />);

    // Row 101 was at the top; at 50 a page that is page 3 (index 2).
    await userEvent.selectOptions(screen.getByRole("combobox"), "50");
    expect(onSize).toHaveBeenCalledWith(50);
    expect(onPage).toHaveBeenCalledWith(2);
  });

  it("shows no count while the total is still unknown, rather than a false zero", () => {
    render(
      <PaginationBar page={0} size={25} totalCount={undefined} onPage={() => {}} onSize={() => {}} />,
    );
    expect(screen.queryByText(/results|of/)).not.toBeInTheDocument();
  });

  it("drops out entirely under autoHide once the result fits the smallest page", () => {
    const { rerender } = render(
      <PaginationBar page={0} size={50} totalCount={12} onPage={() => {}} onSize={() => {}} autoHide />,
    );
    expect(screen.queryByLabelText("Next page")).not.toBeInTheDocument();

    rerender(
      <PaginationBar page={0} size={50} totalCount={26} onPage={() => {}} onSize={() => {}} autoHide />,
    );
    expect(screen.getByLabelText("Next page")).toBeInTheDocument();
    expect(screen.getByText("1 - 26 of 26")).toBeInTheDocument();
  });

  it("stays put without autoHide, which is what the market grids want", () => {
    render(<PaginationBar page={0} size={50} totalCount={3} onPage={() => {}} onSize={() => {}} />);
    expect(screen.getByText("1 - 3 of 3")).toBeInTheDocument();
  });
});
