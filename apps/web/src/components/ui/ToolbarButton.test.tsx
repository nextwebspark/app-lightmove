import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { ToolbarButton } from "./ToolbarButton";

describe("ToolbarButton", () => {
  it("refuses a press while it is loading", async () => {
    const onClick = vi.fn();
    render(
      <ToolbarButton loading onClick={onClick}>
        Export
      </ToolbarButton>,
    );

    const button = screen.getByRole("button", { name: "Export" });
    expect(button).toBeDisabled();
    await userEvent.click(button);
    expect(onClick).not.toHaveBeenCalled();
  });
});
