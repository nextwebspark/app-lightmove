import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { Button, Input, Select } from "./index";

/**
 * A caller's className must beat the component's default.
 *
 * This shipped broken: the controls hardcoded `w-full` and appended the caller's classes, so the
 * invite row's `w-[130px]` role select rendered full-width and mangled the layout. Tailwind picks the
 * winner by stylesheet order, not by the order of names in the attribute — so the bug was invisible in
 * the JSX, which read exactly as intended.
 */
describe("ui primitives merge classes in the caller's favour", () => {
  it("lets a caller override the control's default width", () => {
    render(<Select aria-label="Role" className="w-[130px]" />);

    const select = screen.getByLabelText("Role");
    expect(select.className).toContain("w-[130px]");
    expect(select.className).not.toContain("w-full");
  });

  it("keeps the default width when the caller asks for none", () => {
    render(<Input aria-label="Email" />);

    expect(screen.getByLabelText("Email").className).toContain("w-full");
  });

  it("lets a caller override a button's padding without losing its variant", () => {
    render(
      <Button variant="secondary" className="px-8">
        Create a separate workspace instead
      </Button>,
    );

    const button = screen.getByRole("button");
    expect(button.className).toContain("px-8");
    expect(button.className).not.toContain("px-3.5");
    expect(button.className).toContain("border-line");
  });
});
