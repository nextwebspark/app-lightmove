import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { HealthIcon, HealthInline, HealthPill } from "./Badge";

describe("health indicators", () => {
  it("draws a glyph beside the label, never a colour alone", () => {
    const { container } = render(
      <>
        <HealthPill health="OFF" />
        <HealthInline health="RISK" />
      </>,
    );

    expect(screen.getByText("Off track")).toBeInTheDocument();
    expect(screen.getByText("At risk")).toBeInTheDocument();
    expect(container.querySelectorAll("svg")).toHaveLength(2);
  });

  it("names the icon-only variant for assistive tech and the hover tooltip", () => {
    render(<HealthIcon health="OK" />);

    const indicator = screen.getByRole("img", { name: "On track" });
    expect(indicator).toHaveAttribute("title", "On track");
  });
});
