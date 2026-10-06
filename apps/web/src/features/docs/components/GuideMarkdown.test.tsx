import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { GuideMarkdown } from "./GuideMarkdown";

describe("GuideMarkdown", () => {
  it("opens another site in a new tab, and keeps this one's links and anchors in place", () => {
    render(<GuideMarkdown markdown="[spec](https://modelcontextprotocol.io) [keys](/settings/api-keys) [scopes](#scopes)" />);

    expect(screen.getByRole("link", { name: "spec" })).toHaveAttribute("target", "_blank");
    expect(screen.getByRole("link", { name: "keys" })).not.toHaveAttribute("target");
    expect(screen.getByRole("link", { name: "scopes" })).not.toHaveAttribute("target");
  });
});
