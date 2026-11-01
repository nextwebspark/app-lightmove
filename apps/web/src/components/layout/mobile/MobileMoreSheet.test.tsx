import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ICONS } from "../Icon";
import { MobileMoreSheet } from "./MobileMoreSheet";

const signOut = vi.fn();
vi.mock("../../../features/auth/AuthProvider", () => ({
  useAuth: () => ({ user: { fullName: "Yara Haddad", email: "yara@firm.example" }, signOut }),
}));

const GROUPS = [
  {
    label: "Position",
    items: [
      { to: "/projects/p1", label: "Brief", icon: ICONS.position, end: true },
      { to: "/projects/p1/strategy", label: "Strategy", icon: ICONS.strategy },
    ],
  },
  { label: "Market mapping", items: [{ to: "/projects/p1/companies/universe", label: "In universe", icon: ICONS.globe, count: 128 }] },
];

function renderSheet(onClose = vi.fn(), path = "/projects/p1/strategy") {
  render(
    <MemoryRouter initialEntries={[path]}>
      <MobileMoreSheet
        open
        onClose={onClose}
        title="Group CFO"
        groups={GROUPS}
        backLink={{ to: "/", label: "My positions", icon: ICONS.back }}
      />
    </MemoryRouter>,
  );
  return onClose;
}

describe("MobileMoreSheet", () => {
  beforeEach(() => signOut.mockReset());

  it("lists every screen of the rail under its group, the one on show marked", () => {
    renderSheet();

    const panel = screen.getByRole("dialog", { name: "More" });
    expect(within(panel).getByRole("heading", { name: "Group CFO" })).toBeInTheDocument();
    const position = within(panel).getByRole("region", { name: "Position" });
    expect(within(position).getByRole("link", { name: "Strategy" })).toHaveAttribute("aria-current", "page");
    expect(within(position).getByRole("link", { name: "Brief" })).not.toHaveAttribute("aria-current");
    expect(within(panel).getByRole("link", { name: /In universe\s*128/ })).toBeInTheDocument();
    expect(within(panel).getByRole("link", { name: "My positions" })).toHaveAttribute("href", "/");
  });

  it("closes once a screen is chosen", async () => {
    const onClose = renderSheet();

    await userEvent.click(screen.getByRole("link", { name: "Brief" }));

    expect(onClose).toHaveBeenCalled();
  });

  it("closes on its × and on Escape", async () => {
    const onClose = renderSheet();

    await userEvent.click(screen.getByRole("button", { name: "Close" }));
    await userEvent.keyboard("{Escape}");

    expect(onClose).toHaveBeenCalledTimes(2);
  });

  it("carries the account underneath, sign out included", async () => {
    renderSheet();

    const account = screen.getByRole("region", { name: "Account" });
    expect(within(account).getByText("yara@firm.example")).toBeInTheDocument();
    await userEvent.click(within(account).getByRole("button", { name: "Sign out" }));

    expect(signOut).toHaveBeenCalledOnce();
  });
});
