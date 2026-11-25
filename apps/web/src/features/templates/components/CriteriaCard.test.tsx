import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { Criterion } from "../../position/api/types";
import { CriteriaCard } from "./CriteriaCard";

const criteria = (count: number): Criterion[] =>
  Array.from({ length: count }, (_, index) => ({ text: `Criterion ${index}`, mode: "REQUIRED", source: "MANUAL" }));

describe("CriteriaCard", () => {
  it("holds each criterion to the server's 300 characters", () => {
    render(<CriteriaCard criteria={criteria(1)} onChange={vi.fn()} />);

    expect(screen.getByRole("textbox", { name: "Criterion 1" })).toHaveAttribute("maxLength", "300");
    expect(screen.getByPlaceholderText("Add a criterion…")).toHaveAttribute("maxLength", "300");
  });

  it("offers no more rows once the template holds the server's 30", () => {
    const { rerender } = render(<CriteriaCard criteria={criteria(29)} onChange={vi.fn()} />);
    expect(screen.getByPlaceholderText("Add a criterion…")).toBeInTheDocument();

    rerender(<CriteriaCard criteria={criteria(30)} onChange={vi.fn()} />);
    expect(screen.queryByPlaceholderText("Add a criterion…")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Add" })).not.toBeInTheDocument();
  });
});
