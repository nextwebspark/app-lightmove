import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { CompetencyPanel } from "./CompetencyPanel";

describe("CompetencyPanel", () => {
  it("holds a competency's name and description to the server's 120 and 300 characters", () => {
    render(
      <CompetencyPanel
        title="Technical"
        accent="accent"
        rows={[{ id: "row-1", name: "Strategy", description: null, weight: 100 }]}
        locked={new Set()}
        onChange={vi.fn()}
        onToggleLock={vi.fn()}
        onReorder={vi.fn()}
      />,
    );

    expect(screen.getByRole("textbox", { name: "Technical competency 1 name" })).toHaveAttribute("maxLength", "120");
    expect(screen.getByRole("textbox", { name: "Strategy (row 1) description" })).toHaveAttribute("maxLength", "300");
  });
});
