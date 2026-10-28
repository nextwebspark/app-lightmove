import { fireEvent, render, screen } from "@testing-library/react";
import { createPortal } from "react-dom";
import { describe, expect, it } from "vitest";
import { focusFieldWithin } from "./focusFieldWithin";

function Box() {
  return (
    <div data-testid="box" onMouseDown={focusFieldWithin}>
      <span>Energy</span>
      <button type="button">Remove Energy</button>
      <input aria-label="Add a sector" defaultValue="Ret" />
      {createPortal(<div>Retail suggestion</div>, document.body)}
    </div>
  );
}

describe("focusFieldWithin", () => {
  it("hands a left press on the box's padding or a chip's label to its field, caret at the end", () => {
    render(<Box />);
    const input = screen.getByLabelText<HTMLInputElement>("Add a sector");

    fireEvent.mouseDown(screen.getByText("Energy"));

    expect(input).toHaveFocus();
    expect(input.selectionStart).toBe(3);
  });

  it("leaves a press on a control in the box, another button, or a portalled list alone", () => {
    render(<Box />);
    const input = screen.getByLabelText("Add a sector");

    fireEvent.mouseDown(screen.getByRole("button", { name: "Remove Energy" }));
    fireEvent.mouseDown(screen.getByTestId("box"), { button: 2 });
    fireEvent.mouseDown(screen.getByText("Retail suggestion"));

    expect(input).not.toHaveFocus();
  });
});
