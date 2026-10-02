import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it } from "vitest";
import { PillSelect } from "./PillSelect";

function Harness() {
  const [value, setValue] = useState("");
  return (
    <PillSelect label="Who" value={value} onChange={setValue}>
      <option value="">Everyone</option>
      <option value="u1">Alok Kumar</option>
    </PillSelect>
  );
}

describe("PillSelect", () => {
  it("keeps its caption as its name whatever is chosen", async () => {
    render(<Harness />);

    await userEvent.selectOptions(screen.getByRole("combobox", { name: "Who" }), "u1");

    expect(screen.getByRole("combobox", { name: "Who" })).toHaveValue("u1");
  });
});
