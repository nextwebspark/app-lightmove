import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { ICONS } from "../layout/Icon";
import { ActionMenu, MenuItem } from "./ActionMenu";

describe("ActionMenu", () => {
  it("opens a menu named like its trigger, and an item acts and closes it", async () => {
    const onRename = vi.fn();
    render(
      <ActionMenu label="Engaged options">
        {(close) => (
          <>
            <MenuItem
              icon={ICONS.pencil}
              onSelect={() => {
                close();
                onRename();
              }}
            >
              Rename
            </MenuItem>
            <MenuItem icon={ICONS.ban} note="The default stays in process" disabled onSelect={vi.fn()}>
              Mark out of process
            </MenuItem>
          </>
        )}
      </ActionMenu>,
    );

    const trigger = screen.getByRole("button", { name: "Engaged options" });
    expect(trigger).toHaveAttribute("aria-haspopup", "menu");
    await userEvent.click(trigger);
    const menu = screen.getByRole("menu", { name: "Engaged options" });
    expect(screen.getByRole("menuitem", { name: /^Mark out of process/ })).toBeDisabled();

    await userEvent.click(screen.getByRole("menuitem", { name: "Rename" }));
    expect(onRename).toHaveBeenCalledOnce();
    expect(menu).not.toBeInTheDocument();
  });
});
