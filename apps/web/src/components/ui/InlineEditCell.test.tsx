import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { InlineEditCell } from "./InlineEditCell";

describe("InlineEditCell", () => {
  it("reads as plain text until clicked", () => {
    render(<InlineEditCell value="Adjacency only" editable onSave={vi.fn()} />);

    expect(screen.getByText("Adjacency only")).toBeInTheDocument();
    expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
  });

  it("is not clickable when not editable, and never opens an input", async () => {
    const user = userEvent.setup();
    render(<InlineEditCell value="Adjacency only" editable={false} onSave={vi.fn()} />);

    await user.click(screen.getByText("Adjacency only"));

    expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
  });

  it("saves the edited value on blur, and reverts to text", async () => {
    const user = userEvent.setup();
    const onSave = vi.fn().mockResolvedValue(undefined);
    render(<InlineEditCell value="Old note" editable onSave={onSave} />);

    await user.click(screen.getByText("Old note"));
    const input = screen.getByRole("textbox");
    await user.clear(input);
    await user.type(input, "New note");
    await user.tab();

    expect(onSave).toHaveBeenCalledWith("New note");
  });

  it("saves on Ctrl+Enter without waiting for blur", async () => {
    const user = userEvent.setup();
    const onSave = vi.fn().mockResolvedValue(undefined);
    render(<InlineEditCell value="Old note" editable onSave={onSave} />);

    await user.click(screen.getByText("Old note"));
    const input = screen.getByRole("textbox");
    await user.clear(input);
    await user.type(input, "New note{Control>}{Enter}{/Control}");

    expect(onSave).toHaveBeenCalledWith("New note");
  });

  it("Escape cancels without saving", async () => {
    const user = userEvent.setup();
    const onSave = vi.fn();
    render(<InlineEditCell value="Old note" editable onSave={onSave} />);

    await user.click(screen.getByText("Old note"));
    const input = screen.getByRole("textbox");
    await user.clear(input);
    await user.type(input, "Abandoned edit{Escape}");

    expect(onSave).not.toHaveBeenCalled();
    expect(screen.getByText("Old note")).toBeInTheDocument();
  });

  it("does not save when the text is unchanged", async () => {
    const user = userEvent.setup();
    const onSave = vi.fn();
    render(<InlineEditCell value="Same" editable onSave={onSave} />);

    await user.click(screen.getByText("Same"));
    await user.tab();

    expect(onSave).not.toHaveBeenCalled();
  });

  it("stays open on the unsaved draft when the save rejects", async () => {
    const user = userEvent.setup();
    const onSave = vi.fn().mockRejectedValue(new Error("network error"));
    render(<InlineEditCell value="Old note" editable onSave={onSave} />);

    await user.click(screen.getByText("Old note"));
    const input = screen.getByRole("textbox");
    await user.clear(input);
    await user.type(input, "New note");
    await user.tab();

    expect(onSave).toHaveBeenCalledWith("New note");
    expect(await screen.findByDisplayValue("New note")).toBeInTheDocument();
  });

  it("clears an existing value to empty via the Save button", async () => {
    const user = userEvent.setup();
    const onSave = vi.fn().mockResolvedValue(undefined);
    render(<InlineEditCell value="Old note" editable onSave={onSave} />);

    await user.click(screen.getByText("Old note"));
    await user.clear(screen.getByRole("textbox"));
    await user.click(screen.getByRole("button", { name: "Save" }));

    expect(onSave).toHaveBeenCalledWith("");
  });

  it("disables Save until the draft actually differs, and Cancel discards it", async () => {
    const user = userEvent.setup();
    const onSave = vi.fn().mockResolvedValue(undefined);
    render(<InlineEditCell value="Old note" editable onSave={onSave} />);

    await user.click(screen.getByText("Old note"));
    expect(screen.getByRole("button", { name: "Save" })).toBeDisabled();

    const input = screen.getByRole("textbox");
    await user.type(input, " more");
    expect(screen.getByRole("button", { name: "Save" })).toBeEnabled();

    await user.click(screen.getByRole("button", { name: "Cancel" }));

    expect(onSave).not.toHaveBeenCalled();
    expect(screen.getByText("Old note")).toBeInTheDocument();
  });
});
