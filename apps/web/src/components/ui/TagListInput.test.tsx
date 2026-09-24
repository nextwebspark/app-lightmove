import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it } from "vitest";
import { TagListInput } from "./TagListInput";

const COUNTRIES = [
  { value: "Saudi Arabia", label: "Saudi Arabia", aliases: ["ksa"] },
  { value: "United Arab Emirates", label: "United Arab Emirates", aliases: ["uae"] },
];

function Harness({ initial, options }: { initial: string[]; options?: typeof COUNTRIES }) {
  const [values, setValues] = useState(initial);
  return (
    <>
      <TagListInput
        ariaLabel="Add a sector"
        values={values}
        onChange={setValues}
        options={options}
        listId="test-suggestions"
      />
      <output>{values.join("|")}</output>
    </>
  );
}

describe("TagListInput", () => {
  it("adds on Enter or comma, and keeps a repeat once whatever its case", async () => {
    const user = userEvent.setup();
    render(<Harness initial={["Energy"]} />);

    await user.type(screen.getByLabelText("Add a sector"), "Retail{Enter}energy,Banking,");

    expect(screen.getByRole("status")).toHaveTextContent("Energy|Retail|Banking");
  });

  it("removes a chip by its button, and the last one by Backspace in an empty box", async () => {
    const user = userEvent.setup();
    render(<Harness initial={["Energy", "Retail", "Banking"]} />);

    await user.click(screen.getByRole("button", { name: "Remove Energy" }));
    await user.type(screen.getByLabelText("Add a sector"), "{Backspace}");

    expect(screen.getByRole("status")).toHaveTextContent("Retail");
    expect(screen.getByRole("status")).not.toHaveTextContent("Banking");
  });

  it("adds a suggestion's label when picked, by click or by Enter on a match that starts the typing", async () => {
    const user = userEvent.setup();
    render(<Harness initial={[]} options={COUNTRIES} />);

    await user.type(screen.getByLabelText("Add a sector"), "uae");
    await user.click(screen.getByRole("option", { name: "United Arab Emirates" }));
    await user.type(screen.getByLabelText("Add a sector"), "saudi{Enter}");

    expect(screen.getByRole("status")).toHaveTextContent("United Arab Emirates|Saudi Arabia");
  });

  it("keeps free text that only happens to contain a suggestion, and hides what is already a chip", async () => {
    const user = userEvent.setup();
    render(<Harness initial={["Saudi Arabia"]} options={COUNTRIES} />);

    await user.type(screen.getByLabelText("Add a sector"), "Arab");
    expect(screen.queryByRole("option", { name: "Saudi Arabia" })).not.toBeInTheDocument();
    expect(screen.getByRole("option", { name: "United Arab Emirates" })).toBeInTheDocument();

    await user.keyboard("{Enter}");

    expect(screen.getByRole("status")).toHaveTextContent("Saudi Arabia|Arab");
    expect(screen.getByRole("status")).not.toHaveTextContent("Emirates");
  });
});
