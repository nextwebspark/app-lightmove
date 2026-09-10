import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it } from "vitest";
import { SegmentedControl } from "./SegmentedControl";

function Harness() {
  const [value, setValue] = useState<"table" | "map">("table");
  return (
    <SegmentedControl
      label="View"
      value={value}
      onChange={setValue}
      options={[
        { value: "table", label: "Table" },
        { value: "map", label: "Map" },
      ]}
    />
  );
}

describe("SegmentedControl", () => {
  it("keeps one tab stop and moves the choice with the arrows, both ways and around the end", async () => {
    render(<Harness />);

    const table = screen.getByRole("radio", { name: "Table" });
    const map = screen.getByRole("radio", { name: "Map" });
    expect(table).toHaveAttribute("tabindex", "0");
    expect(map).toHaveAttribute("tabindex", "-1");

    table.focus();
    await userEvent.keyboard("{ArrowRight}");
    expect(map).toHaveAttribute("aria-checked", "true");
    expect(map).toHaveFocus();
    expect(table).toHaveAttribute("tabindex", "-1");

    // Past the last option is the first again, which is what a radio group does.
    await userEvent.keyboard("{ArrowRight}");
    expect(table).toHaveAttribute("aria-checked", "true");
    await userEvent.keyboard("{ArrowLeft}");
    expect(map).toHaveAttribute("aria-checked", "true");
  });
});
