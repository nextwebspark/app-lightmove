import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it } from "vitest";
import { ConfirmDialog } from "./ConfirmDialog";
import { Drawer } from "./Drawer";

function Grid() {
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [confirmOpen, setConfirmOpen] = useState(false);
  return (
    <>
      <button onClick={() => setDrawerOpen(true)}>Omar Farouk</button>
      <Drawer open={drawerOpen} onClose={() => setDrawerOpen(false)} label="Executive">
        <input aria-label="Notes" />
        <button onClick={() => setConfirmOpen(true)}>Remove</button>
      </Drawer>
      <ConfirmDialog
        open={confirmOpen}
        title="Remove Omar Farouk?"
        confirmLabel="Remove"
        onConfirm={() => setConfirmOpen(false)}
        onClose={() => setConfirmOpen(false)}
      >
        They leave this position.
      </ConfirmDialog>
    </>
  );
}

/** A record drawer is read before it is edited: it takes focus itself, never one of its edit boxes. */
describe("Drawer", () => {
  it("takes focus on open, keeps Tab inside and hands focus back on close", async () => {
    const user = userEvent.setup();
    render(<Grid />);

    await user.click(screen.getByRole("button", { name: "Omar Farouk" }));
    const drawer = screen.getByRole("dialog", { name: "Executive" });
    expect(drawer).toHaveFocus();

    for (let press = 0; press < 4; press++) await user.tab();
    expect(drawer.contains(document.activeElement)).toBe(true);

    await user.keyboard("{Escape}");
    expect(screen.getByRole("button", { name: "Omar Farouk" })).toHaveFocus();
  });

  it("lets a confirm opened over it take Escape, then returns focus into the drawer", async () => {
    const user = userEvent.setup();
    render(<Grid />);
    await user.click(screen.getByRole("button", { name: "Omar Farouk" }));
    await user.click(screen.getByRole("button", { name: "Remove" }));
    const confirm = screen.getByRole("dialog", { name: "Remove Omar Farouk?" });
    expect(confirm.contains(document.activeElement)).toBe(true);

    await user.keyboard("{Escape}");

    expect(screen.queryByRole("dialog", { name: "Remove Omar Farouk?" })).not.toBeInTheDocument();
    expect(screen.getByRole("dialog", { name: "Executive" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Remove" })).toHaveFocus();
  });
});
