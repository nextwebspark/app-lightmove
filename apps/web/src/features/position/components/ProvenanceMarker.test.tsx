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

  it("names the document, the confidence and the snippet on focus, and closes on Escape", async () => {
    render(
      <ProvenanceMarker
        source="DOCUMENT"
        confidence="high"
        snippet="Leads the finance function"
        fileName="cfo-brief.pdf"
      />,
    );
    const person = userEvent.setup();

    expect(screen.queryByRole("group", { name: "Document provenance" })).not.toBeInTheDocument();

    await person.tab();
    expect(screen.getByRole("button", { name: "Read from the document" })).toHaveFocus();
    const panel = screen.getByRole("group", { name: "Document provenance" });
    expect(panel).toHaveTextContent("Read from cfo-brief.pdf");
    expect(panel).toHaveTextContent("High confidence");
    expect(screen.getByText("Leads the finance function", { exact: false })).toBeInTheDocument();

    await person.keyboard("{Escape}");
    expect(screen.queryByRole("group", { name: "Document provenance" })).not.toBeInTheDocument();
  });

  // The panel is portalled to the end of the body so it escapes the scrolling tables and the
  // transformed org-chart canvas it opens inside — which puts it out of Tab's reach, so Enter steps
  // into it instead and Escape comes back out.
  it("steps into the popover on Enter and returns focus to the glyph on Escape", async () => {
    const onUndo = vi.fn();
    render(<ProvenanceMarker source="DOCUMENT" onUndo={onUndo} />);
    const person = userEvent.setup();

    await person.tab();
    const trigger = screen.getByRole("button", { name: "Read from the document" });
    expect(trigger).toHaveFocus();

    await person.keyboard("{Enter}");
    const undo = await screen.findByRole("button", { name: "Undo" });
    expect(undo).toHaveFocus();

    await person.keyboard("{Enter}");
    expect(onUndo).toHaveBeenCalledTimes(1);

    await person.keyboard("{Escape}");
    expect(screen.queryByRole("group", { name: "Document provenance" })).not.toBeInTheDocument();
    expect(trigger).toHaveFocus();
  });

  it("keeps the popover open while the pointer moves from the glyph onto it", async () => {
    const onUndo = vi.fn();
    render(<ProvenanceMarker source="DOCUMENT" snippet="Owns the P&L" onUndo={onUndo} />);
    const person = userEvent.setup();

    await person.hover(screen.getByRole("button", { name: "Read from the document" }));
    const panel = screen.getByRole("group", { name: "Document provenance" });

    await person.hover(panel);
    expect(screen.getByRole("group", { name: "Document provenance" })).toBeInTheDocument();

    await person.click(screen.getByRole("button", { name: "Undo" }));
    expect(onUndo).toHaveBeenCalledTimes(1);
  });

  it("lets the popover go once the pointer reaches anything else", async () => {
    render(
      <>
        <button type="button">Elsewhere</button>
        <ProvenanceMarker source="DOCUMENT" snippet="Owns the P&L" />
      </>,
    );
    const person = userEvent.setup();

    await person.hover(screen.getByRole("button", { name: "Read from the document" }));
    expect(screen.getByRole("group", { name: "Document provenance" })).toBeInTheDocument();

    await person.hover(screen.getByRole("button", { name: "Elsewhere" }));
    expect(screen.queryByRole("group", { name: "Document provenance" })).not.toBeInTheDocument();
  });

  it("says where the value came from even once the session has no receipt for the field", async () => {
    render(<ProvenanceMarker source="DOCUMENT" />);
    const person = userEvent.setup();

    await person.tab();
    const panel = screen.getByRole("group", { name: "Document provenance" });
    expect(panel).toHaveTextContent("Read from the document");
    expect(panel).not.toHaveTextContent("confidence");
    expect(screen.queryByRole("button", { name: "Undo" })).not.toBeInTheDocument();
  });

  it("wears the low-confidence colour when a reading was unsure", () => {
    render(<ProvenanceMarker source="DOCUMENT" confidence="low" />);
    expect(screen.getByRole("button", { name: "Read from the document" })).toHaveClass("text-u-signal");
  });
});
