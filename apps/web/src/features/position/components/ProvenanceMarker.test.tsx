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

    expect(screen.queryByRole("group", { name: "Document provenance" })).not.toBeInTheDocument();

    await person.tab();
    expect(screen.getByRole("button", { name: "Read from the document" })).toHaveFocus();
    const panel = screen.getByRole("group", { name: "Document provenance" });
    expect(panel).toHaveTextContent("From the document · High");
    expect(screen.getByText("Leads the finance function", { exact: false })).toBeInTheDocument();

    const undo = screen.getByRole("button", { name: "Undo" });
    await person.click(undo);
    expect(onUndo).toHaveBeenCalledTimes(1);

    await person.keyboard("{Escape}");
    expect(screen.queryByRole("group", { name: "Document provenance" })).not.toBeInTheDocument();
  });

  it("keeps the popover open when Tab moves focus from the trigger to Undo inside it", async () => {
    const onUndo = vi.fn();
    render(<ProvenanceMarker source="DOCUMENT" onUndo={onUndo} />);
    const person = userEvent.setup();

    await person.tab();
    expect(screen.getByRole("button", { name: "Read from the document" })).toHaveFocus();

    await person.tab();
    const undo = screen.getByRole("button", { name: "Undo" });
    expect(undo).toHaveFocus();
    expect(screen.getByRole("group", { name: "Document provenance" })).toBeInTheDocument();

    await person.keyboard("{Enter}");
    expect(onUndo).toHaveBeenCalledTimes(1);
  });

  it("degrades to the glyph alone once the session has no receipt for the field", async () => {
    render(<ProvenanceMarker source="DOCUMENT" />);
    const person = userEvent.setup();

    await person.tab();
    const panel = screen.getByRole("group", { name: "Document provenance" });
    expect(panel).toHaveTextContent("From the document");
    expect(panel).not.toHaveTextContent("·");
    expect(screen.queryByRole("button", { name: "Undo" })).not.toBeInTheDocument();
  });

  it("wears the low-confidence colour when a reading was unsure", () => {
    render(<ProvenanceMarker source="DOCUMENT" confidence="low" />);
    expect(screen.getByRole("button", { name: "Read from the document" })).toHaveClass("text-u-signal");
  });
});
