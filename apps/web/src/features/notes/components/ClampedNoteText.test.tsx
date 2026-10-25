import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ClampedNoteText } from "./ClampedNoteText";
import { NoteReaderModal } from "./NoteReaderModal";

const BODY = "Discussed the Group CFO seat. Open to a move in Q1 if the role carries the transformation agenda.";

/** jsdom lays nothing out, so the one height the component asks about is stubbed. */
function contentHeight(px: number) {
  Object.defineProperty(HTMLElement.prototype, "scrollHeight", { configurable: true, value: px });
}

afterEach(() => contentHeight(0));

describe("ClampedNoteText", () => {
  it("holds a long note to the card's height and opens it in place", async () => {
    contentHeight(600);
    const { container } = render(<ClampedNoteText body={BODY} format="plain" onOpen={() => {}} />);

    expect(container.querySelector("[data-clamped]")).not.toBeNull();
    await userEvent.click(screen.getByRole("button", { name: "Show more" }));

    expect(container.querySelector("[data-clamped]")).toBeNull();
    expect(screen.getByRole("button", { name: "Show less" })).toHaveAttribute("aria-expanded", "true");
  });

  it("shows a short note whole, with no controls", () => {
    contentHeight(80);
    const { container } = render(<ClampedNoteText body={BODY} format="plain" onOpen={() => {}} />);

    expect(container.querySelector("[data-clamped]")).toBeNull();
    expect(screen.queryByRole("button", { name: "Show more" })).toBeNull();
    expect(screen.queryByRole("button", { name: "Open" })).toBeNull();
  });

  it("shows a note only a line over the cap whole, since a Show more revealing one line is worse than none", () => {
    contentHeight(190);
    render(<ClampedNoteText body={BODY} format="plain" />);

    expect(screen.queryByRole("button", { name: "Show more" })).toBeNull();
  });

  it("opens a note brought into view already expanded", () => {
    contentHeight(600);
    const { container } = render(<ClampedNoteText body={BODY} format="plain" defaultExpanded />);

    expect(container.querySelector("[data-clamped]")).toBeNull();
    expect(screen.getByRole("button", { name: "Show less" })).toBeInTheDocument();
  });

  it("asks for the reader on Open", async () => {
    contentHeight(600);
    const onOpen = vi.fn();
    render(<ClampedNoteText body={BODY} format="plain" onOpen={onOpen} />);

    await userEvent.click(screen.getByRole("button", { name: "Open" }));
    expect(onOpen).toHaveBeenCalledOnce();
  });
});

describe("NoteReaderModal", () => {
  it("reads the whole note and offers Edit only where it may be changed", async () => {
    const onClose = vi.fn();
    const onEdit = vi.fn();
    const { rerender } = render(
      <NoteReaderModal open onClose={onClose} title="Meeting note" writtenAt="2026-10-22T09:00:00Z" body={BODY} format="plain" onEdit={onEdit} />,
    );

    expect(screen.getByRole("dialog")).toHaveTextContent(BODY);
    await userEvent.click(screen.getByRole("button", { name: "Edit" }));
    expect(onEdit).toHaveBeenCalledOnce();

    rerender(<NoteReaderModal open onClose={onClose} title="Meeting note" writtenAt="2026-10-22T09:00:00Z" body={BODY} format="plain" />);
    expect(screen.queryByRole("button", { name: "Edit" })).toBeNull();
    await userEvent.keyboard("{Escape}");
    expect(onClose).toHaveBeenCalled();
  });
});
