import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it, vi } from "vitest";
import type { TeammateOption } from "../../tasks/lib/team";
import { typeInto } from "../../../test/noteEditor";
import { NoteEditor, type NoteEditorProps } from "./NoteEditor";

const team: TeammateOption[] = [
  { userId: "u1", fullName: "Yara Haddad", avatarUrl: null, isMe: true, role: "Lead" },
  { userId: "u3", fullName: "Sara Al-Mansour", avatarUrl: null, isMe: false, role: "Researcher" },
];

function Harness(props: Partial<NoteEditorProps> & { onBody?: (body: string) => void }) {
  const [body, setBody] = useState(props.value ?? "");
  return (
    <NoteEditor
      value={body}
      format={props.format ?? "markdown"}
      onChange={(next) => {
        setBody(next);
        props.onBody?.(next);
      }}
      teammates={props.teammates === undefined ? team : props.teammates}
      ariaLabel="New note"
      onSubmit={props.onSubmit}
      fillsDialog={props.fillsDialog}
      onExpandChange={props.onExpandChange}
      header={props.header}
      footer={
        props.footer === undefined ? undefined : (
          <>
            {props.footer}
            <button type="button" onClick={() => setBody("")}>
              Save note
            </button>
          </>
        )
      }
    />
  );
}

describe("the note editor", () => {
  it("writes what is typed as Markdown, bold included", async () => {
    const onBody = vi.fn();
    render(<Harness onBody={onBody} />);
    const box = await screen.findByRole("textbox", { name: "New note" });

    await userEvent.click(box);
    await userEvent.click(screen.getByRole("button", { name: "Bold" }));
    await typeInto(box, "Wants Dubai");

    await waitFor(() => expect(onBody).toHaveBeenLastCalledWith("**Wants Dubai**"));
    expect(screen.getByRole("button", { name: "Bold" })).toHaveAttribute("aria-pressed", "true");
  });

  it("turns a typed dash and space into a list", async () => {
    const onBody = vi.fn();
    render(<Harness onBody={onBody} />);
    const box = await screen.findByRole("textbox", { name: "New note" });
    await userEvent.click(box);

    await typeInto(box, "- Send the brief");

    await waitFor(() => expect(onBody).toHaveBeenLastCalledWith("- Send the brief"));
    expect(within(box).getByRole("list")).toBeInTheDocument();
  });

  it("saves on Ctrl+Enter", async () => {
    const onSubmit = vi.fn();
    render(<Harness value="Ready" onSubmit={onSubmit} />);
    const box = await screen.findByRole("textbox", { name: "New note" });
    await userEvent.click(box);

    await userEvent.keyboard("{Control>}{Enter}{/Control}");

    expect(onSubmit).toHaveBeenCalled();
  });

  it("opens a plain note as its own lines", async () => {
    const onBody = vi.fn();
    render(<Harness value={"1. not a list"} format="plain" onBody={onBody} />);
    const box = await screen.findByRole("textbox", { name: "New note" });

    expect(box).toHaveTextContent("1. not a list");
    expect(within(box).queryByRole("list")).not.toBeInTheDocument();
  });

  it("mentions a teammate picked from the @ list", async () => {
    const onBody = vi.fn();
    render(<Harness onBody={onBody} />);
    const box = await screen.findByRole("textbox", { name: "New note" });
    await userEvent.click(box);
    await typeInto(box, "Call ");

    await userEvent.click(screen.getByRole("button", { name: "Mention a teammate" }));
    await userEvent.click(await screen.findByRole("option", { name: /Sara Al-Mansour/ }));

    await waitFor(() => expect(onBody).toHaveBeenLastCalledWith("Call @[Sara Al-Mansour](user:u3) "));
    expect(screen.getByText("Notifies")).toBeInTheDocument();
  });

  it("opens the @ list outside whatever box the note sits in, so no scrolling body or footer clips it", async () => {
    const { container } = render(<Harness />);
    await screen.findByRole("textbox", { name: "New note" });

    await userEvent.click(screen.getByRole("button", { name: "Mention a teammate" }));

    const picker = await screen.findByRole("listbox", { name: "Mention someone on this position's team" });
    expect(container).not.toContainElement(picker);
    expect(document.body).toContainElement(picker);
  });

  it("in the larger view, Escape closes the @ list and leaves the view open", async () => {
    render(<Harness />);
    await screen.findByRole("textbox", { name: "New note" });
    await userEvent.click(screen.getByRole("button", { name: "Expand" }));
    const dialog = await screen.findByRole("dialog");

    await userEvent.click(within(dialog).getByRole("button", { name: "Mention a teammate" }));
    await screen.findByRole("listbox", { name: "Mention someone on this position's team" });
    // The editor takes focus on the next frame; Escape pressed before it lands would go to the dialog.
    await waitFor(() => expect(within(dialog).getByRole("textbox", { name: "New note" })).toHaveFocus());
    await userEvent.keyboard("{Escape}");

    await waitFor(() => expect(screen.queryByRole("listbox")).not.toBeInTheDocument());
    expect(screen.getByRole("dialog")).toBeInTheDocument();
  });

  it("keeps the words when it opens larger and closes again", async () => {
    render(<Harness value="Prefers mornings" />);
    await screen.findByRole("textbox", { name: "New note" });

    await userEvent.click(screen.getByRole("button", { name: "Expand" }));
    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByRole("textbox", { name: "New note" })).toHaveTextContent("Prefers mornings");

    await userEvent.click(within(dialog).getByRole("button", { name: "Collapse" }));
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(screen.getByRole("textbox", { name: "New note" })).toHaveTextContent("Prefers mornings");
  });

  it("takes the whole composer into the larger view — its kind, who reads it and its save — and a save closes it", async () => {
    render(<Harness value="Prefers mornings" header={<p>Kind of note</p>} footer={<p>Who reads this note</p>} />);
    await screen.findByRole("textbox", { name: "New note" });

    await userEvent.click(screen.getByRole("button", { name: "Expand" }));
    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText("Kind of note")).toBeInTheDocument();
    expect(within(dialog).getByText("Who reads this note")).toBeInTheDocument();
    expect(screen.getAllByText("Kind of note")).toHaveLength(1);
    await userEvent.click(within(dialog).getByRole("button", { name: "Save note" }));

    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    expect(screen.getByText("Kind of note")).toBeInTheDocument();
    expect(screen.getByRole("textbox", { name: "New note" })).toHaveTextContent("");
  });

  it("filling a dialog, grows where it is and tells the dialog, once per press", async () => {
    const onExpandChange = vi.fn();
    render(
      <Harness value="Prefers mornings" footer={<p>Who reads this note</p>} fillsDialog onExpandChange={onExpandChange} />,
    );
    await screen.findByRole("textbox", { name: "New note" });

    await userEvent.click(screen.getByRole("button", { name: "Expand" }));
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(onExpandChange).toHaveBeenCalledTimes(1);
    expect(onExpandChange).toHaveBeenLastCalledWith(true);

    await userEvent.click(screen.getByRole("button", { name: "Collapse" }));
    expect(onExpandChange).toHaveBeenLastCalledWith(false);
    expect(screen.getByRole("button", { name: "Expand" })).toBeInTheDocument();
  });

  it("takes the caret back on a press in the box's empty space, after focus went elsewhere", async () => {
    render(<Harness footer={<button type="button">Elsewhere</button>} fillsDialog />);
    const box = await screen.findByRole("textbox", { name: "New note" });

    await userEvent.click(screen.getByRole("button", { name: "Elsewhere" }));
    expect(box).not.toHaveFocus();

    fireEvent.mouseDown(box.parentElement!);
    await waitFor(() => expect(box).toHaveFocus());
  });

  it("offers no mention where the note mentions nobody", async () => {
    render(<Harness teammates={null} />);
    await screen.findByRole("textbox", { name: "New note" });

    expect(screen.queryByRole("button", { name: "Mention a teammate" })).not.toBeInTheDocument();
  });
});
