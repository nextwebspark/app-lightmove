import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";
import { ICONS } from "../Icon";
import { MobileTabBar, type MobileTab } from "./MobileTabBar";

const TABS: MobileTab[] = [
  {
    to: "/projects/p1/companies/universe",
    label: "Companies",
    icon: ICONS.globe,
    activeWhen: (path) => path.startsWith("/projects/p1/companies/"),
  },
  { to: "/projects/p1/pipeline", label: "Pipeline", icon: ICONS.pipeline },
  { to: "/projects/p1/outreach", label: "Outreach", icon: ICONS.outreach },
];

function renderBar(path: string, props: Partial<Parameters<typeof MobileTabBar>[0]> = {}) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <MobileTabBar tabs={TABS} pathname={path} onMore={() => {}} moreOpen={false} {...props} />
    </MemoryRouter>,
  );
}

describe("MobileTabBar", () => {
  it("lights the tab of the screen on show, every Companies stage lighting Companies", () => {
    renderBar("/projects/p1/companies/shortlisted");

    expect(screen.getByRole("link", { name: "Companies" })).toHaveAttribute("aria-current", "page");
    expect(screen.getByRole("link", { name: "Pipeline" })).not.toHaveAttribute("aria-current");
  });

  it("puts the + between the tabs only where there is something to add", async () => {
    const onQuickAction = vi.fn();
    const { unmount } = renderBar("/projects/p1/pipeline", { onQuickAction });

    const items = screen.getAllByRole("listitem").map((item) => item.textContent);
    expect(items).toEqual(["Companies", "Pipeline", "", "Outreach", "More"]);
    await userEvent.click(screen.getByRole("button", { name: "Quick actions" }));
    expect(onQuickAction).toHaveBeenCalledOnce();
    unmount();

    renderBar("/projects/p1/pipeline");
    expect(screen.queryByRole("button", { name: "Quick actions" })).toBeNull();
  });

  it("opens the full rail from More, so nothing the rail offers is out of reach", async () => {
    const onMore = vi.fn();
    renderBar("/projects/p1/pipeline", { onMore });

    await userEvent.click(screen.getByRole("button", { name: "More" }));

    expect(onMore).toHaveBeenCalledOnce();
  });
});
