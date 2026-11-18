import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { editorOf } from "../../../test/noteEditor";
import type { PersonNote } from "../api/types";
import { NoteCard, type NoteCardActions } from "./NoteParts";

const noAnswer: PersonNote = {
  id: "n1",
  kind: "call",
  body: "",
  format: "markdown",
  pinned: false,
  projectId: "p1",
  projectTitle: "Group CFO",
  authorUserId: "u1",
  authorName: "Yara Haddad",
  authorAvatarUrl: null,
  createdAt: "2026-11-16T09:30:00Z",
  editedAt: null,
  editedByName: null,
  editable: true,
  outcome: "no_answer",
};

function renderCard(note: PersonNote, takesOutcome = true) {
  const actions: NoteCardActions = { onPin: vi.fn(), onRevise: vi.fn(), onRemove: vi.fn(), busy: false };
  render(
    <ul>
      <NoteCard note={note} actions={actions} takesOutcome={takesOutcome} />
    </ul>,
  );
  return actions;
}

describe("a logged call's card", () => {
  it("shows how the call went, and draws no empty words under it", () => {
    renderCard(noAnswer);

    expect(screen.getByText("No answer")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Read the whole note/ })).not.toBeInTheDocument();
  });

  it("reads as one line on a timeline when only the outcome was logged, its actions behind ⋯", async () => {
    const actions: NoteCardActions = { onPin: vi.fn(), onRevise: vi.fn(), onRemove: vi.fn(), busy: false };
    render(
      <ul>
        <NoteCard note={noAnswer} actions={actions} takesOutcome namesPosition={false} lead={{ mark: null, heading: "Call" }} />
      </ul>,
    );

    expect(screen.getByText("· No answer")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Edit" })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Note options" }));
    await userEvent.click(screen.getByRole("menuitem", { name: "Pin" }));

    expect(actions.onPin).toHaveBeenCalled();
  });

  it("changes a mis-tapped outcome in an edit, sending only the outcome", async () => {
    const actions = renderCard(noAnswer);

    await userEvent.click(screen.getByRole("button", { name: "Edit" }));
    await userEvent.click(screen.getByRole("radio", { name: "Connected" }));
    await userEvent.click(screen.getByRole("button", { name: "Save" }));

    expect(actions.onRevise).toHaveBeenCalledWith({ outcome: "connected" });
  });

  it("lets an edit clear the words while an outcome stays, and clears the outcome only for words", async () => {
    const actions = renderCard({ ...noAnswer, body: "Rang twice." });

    await userEvent.click(screen.getByRole("button", { name: "Edit" }));
    const editor = await screen.findByRole("textbox", { name: "Edit note" });
    await act(async () => {
      editorOf(editor).commands.setContent("");
    });
    await userEvent.click(screen.getByRole("radio", { name: "No answer" }));
    expect(screen.getByRole("button", { name: "Save" })).toBeDisabled();
    await userEvent.click(screen.getByRole("radio", { name: "Left voicemail" }));
    await userEvent.click(screen.getByRole("button", { name: "Save" }));

    expect(actions.onRevise).toHaveBeenCalledWith({ body: "", outcome: "voicemail" });
  });

  it("offers no outcome to a company's call note", async () => {
    renderCard({ ...noAnswer, body: "Spoke to the switchboard.", outcome: null }, false);

    await userEvent.click(screen.getByRole("button", { name: "Edit" }));

    expect(screen.queryByRole("radiogroup", { name: "How did the call go?" })).not.toBeInTheDocument();
  });
});
