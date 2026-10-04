import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { Chip } from "./Chip";

describe("Chip", () => {
  it("draws a zero count rather than dropping it", () => {
    render(
      <Chip selected={false} aria-pressed={false} count={0}>
        Owned by me
      </Chip>,
    );

    expect(screen.getByRole("button", { name: /^Owned by me\s*0$/ })).toHaveAttribute("aria-pressed", "false");
  });

  it("carries the role its group gives it", () => {
    render(
      <Chip selected role="checkbox" aria-checked>
        Referral
      </Chip>,
    );

    expect(screen.getByRole("checkbox", { name: "Referral" })).toBeChecked();
  });
});
