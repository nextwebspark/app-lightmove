import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { ProvenanceMarker } from "./ProvenanceMarker";

describe("ProvenanceMarker", () => {
  it("renders nothing for a template-drafted or a typed value", () => {
    const { container: templateCase } = render(<ProvenanceMarker source="TEMPLATE" />);
    expect(templateCase).toBeEmptyDOMElement();

    const { container: manualCase } = render(<ProvenanceMarker source="MANUAL" />);
    expect(manualCase).toBeEmptyDOMElement();

    const { container: unsetCase } = render(<ProvenanceMarker source={undefined} />);
    expect(unsetCase).toBeEmptyDOMElement();
  });

  it("opens the snippet and Undo on focus, and closes on Escape", async () => {
    const onUndo = vi.fn();
    render(
      <ProvenanceMarker source="DOCUMENT" confidence="high" snippet="Leads the finance function" onUndo={onUndo} />,
    );
    const person = userEvent.setup();

    expect(screen.queryByRole("tooltip")).not.toBeInTheDocument();

    await person.tab();
    expect(screen.getByRole("button", { name: "Read from the document" })).toHaveFocus();
    expect(screen.getByRole("tooltip")).toHaveTextContent("From the document · High");
    expect(screen.getByText("Leads the finance function", { exact: false })).toBeInTheDocument();

    const undo = screen.getByRole("button", { name: "Undo" });
    await person.click(undo);
    expect(onUndo).toHaveBeenCalledTimes(1);

    await person.keyboard("{Escape}");
    expect(screen.queryByRole("tooltip")).not.toBeInTheDocument();
  });

  it("degrades to the glyph alone once the session has no receipt for the field", async () => {
    render(<ProvenanceMarker source="DOCUMENT" />);
    const person = userEvent.setup();

    await person.tab();
    const tooltip = screen.getByRole("tooltip");
    expect(tooltip).toHaveTextContent("From the document");
    expect(tooltip).not.toHaveTextContent("·");
    expect(screen.queryByRole("button", { name: "Undo" })).not.toBeInTheDocument();
  });

  it("wears the low-confidence colour when a reading was unsure", () => {
    render(<ProvenanceMarker source="DOCUMENT" confidence="low" />);
    expect(screen.getByRole("button", { name: "Read from the document" })).toHaveClass("text-u-signal");
  });
});
