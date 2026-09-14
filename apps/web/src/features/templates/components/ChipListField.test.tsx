import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it } from "vitest";
import { ChipListField } from "./ChipListField";

function Keywords({ initial }: { initial: string[] }) {
  const [values, setValues] = useState(initial);
  return (
    <ChipListField
      label="Match keywords"
      values={values}
      placeholder="Add a keyword"
      max={20}
      maxLength={80}
      lowercase
      onChange={setValues}
    />
  );
}

describe("ChipListField", () => {
  it("says a keyword is already there instead of silently keeping it in the box", async () => {
    render(<Keywords initial={["cfo"]} />);
    const input = screen.getByRole("textbox", { name: "Add to match keywords" });

    await userEvent.type(input, "CFO{Enter}");

    expect(screen.getByRole("status")).toHaveTextContent("“cfo” is already in the list.");
    expect(input).toHaveValue("");
    expect(screen.getAllByRole("button", { name: "Remove cfo" })).toHaveLength(1);
  });

  it("adds a new keyword, lower-cased, and clears the box", async () => {
    render(<Keywords initial={["cfo"]} />);
    const input = screen.getByRole("textbox", { name: "Add to match keywords" });

    await userEvent.type(input, "Finance Director{Enter}");

    expect(screen.getByRole("button", { name: "Remove finance director" })).toBeInTheDocument();
    expect(input).toHaveValue("");
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
  });
});
