import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import McpGuide from "./McpGuide";

describe("McpGuide", () => {
  it("draws docs/mcp.md's tables as tables, with the server on this page's own origin", () => {
    render(<McpGuide />);

    expect(
      screen.getByRole("heading", {
        level: 1,
        name: "Connect an AI app to Uncava",
      }),
    ).toBeInTheDocument();
    expect(screen.getByRole("cell", { name: "uncava_search_positions" })).toBeInTheDocument();
    expect(screen.getAllByText(`${window.location.origin}/api/v1/mcp`).length).toBeGreaterThan(0);
  });

  it("leaves the maintainers' notes to the repository", () => {
    render(<McpGuide />);

    expect(screen.queryByRole("heading", { name: "For Uncava maintainers" })).not.toBeInTheDocument();
    expect(screen.queryByText(/Secret Manager/)).not.toBeInTheDocument();
  });
});
