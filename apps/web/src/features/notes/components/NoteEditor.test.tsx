import { render, screen, waitFor, within } from "@testing-library/react";
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

  it("offers no mention where the note mentions nobody", async () => {
    render(<Harness teammates={null} />);
    await screen.findByRole("textbox", { name: "New note" });

    expect(screen.queryByRole("button", { name: "Mention a teammate" })).not.toBeInTheDocument();
  });
});
