import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it } from "vitest";
import { TagListInput } from "./TagListInput";

function Harness({ initial }: { initial: string[] }) {
  const [values, setValues] = useState(initial);
  return (
    <>
      <TagListInput ariaLabel="Add a sector" values={values} onChange={setValues} />
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
});
