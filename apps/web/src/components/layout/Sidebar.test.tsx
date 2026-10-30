import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, useLocation } from "react-router-dom";
import { beforeEach, describe, expect, it } from "vitest";
import { Sidebar, type SidebarGroup } from "./Sidebar";

const GROUPS = [{ label: "Workspace", items: [{ to: "/settings", label: "Settings", icon: "" }] }];

describe("Sidebar", () => {
  it("names the release it is running, under the nav", () => {
    render(
      <MemoryRouter>
        <Sidebar groups={GROUPS} />
      </MemoryRouter>,
    );

    // `dev` is what vite's `define` falls back to whenever APP_VERSION is absent, which is every
    // build but a released one. A real release renders its tag here instead.
    expect(screen.getByRole("button", { name: "dev · What's new" })).toBeInTheDocument();
  });

  describe("beside an open assistant", () => {
    beforeEach(() => localStorage.clear());

    const rail = (assistantOpen: boolean) => (
      <MemoryRouter>
        <Sidebar groups={GROUPS} assistantOpen={assistantOpen} />
      </MemoryRouter>
    );

    it("collapses, and gives the user's own choice back when the assistant shuts", () => {
      const { rerender } = render(rail(true));
      expect(screen.getByTitle("Expand sidebar")).toBeInTheDocument();

      rerender(rail(false));

      expect(screen.getByTitle("Collapse sidebar")).toBeInTheDocument();
      expect(localStorage.getItem("lm-side-collapsed")).toBeNull();
    });

    it("can still be expanded by hand without changing the stored preference", async () => {
      render(rail(true));

      await userEvent.click(screen.getByTitle("Expand sidebar"));

      expect(screen.getByTitle("Collapse sidebar")).toBeInTheDocument();
      expect(localStorage.getItem("lm-side-collapsed")).toBeNull();
    });
  });

  describe("a section's views", () => {
    beforeEach(() => localStorage.clear());

    const groups = (path: string, search: string): SidebarGroup[] => [
      {
        label: "Position",
        items: [
          { to: "/p/brief", label: "Brief", icon: "", end: true },
          {
            to: "/p/strategy",
            label: "Strategy",
            icon: "",
            children: [
              { key: "companies", to: "/p/strategy", label: "Companies", icon: "", active: path === "/p/strategy" && search === "" },
              { key: "people", to: "/p/strategy?mode=people", label: "People", icon: "", active: path === "/p/strategy" && search === "?mode=people" },
            ],
          },
        ],
      },
    ];
    function RoutedRail() {
      const { pathname, search } = useLocation();
      return <Sidebar groups={groups(pathname, search)} />;
    }
    const rail = (path: string, search = "") => (
      <MemoryRouter initialEntries={[`${path}${search}`]}>
        <RoutedRail />
      </MemoryRouter>
    );

    it("opens under the section you are in, marking the view on screen rather than the section", () => {
      render(rail("/p/strategy", "?mode=people"));

      const views = screen.getByRole("group", { name: "Strategy" });
      expect(within(views).getByRole("link", { name: "People" })).toHaveAttribute("aria-current", "page");
      expect(within(views).getByRole("link", { name: "Companies" })).not.toHaveAttribute("aria-current");
      expect(screen.getByRole("link", { name: "Strategy" })).not.toHaveAttribute("aria-current", "page");
      expect(screen.getByRole("button", { name: "Strategy views" })).toHaveAttribute("aria-expanded", "true");
    });

    it("stays shut elsewhere until its chevron opens it", async () => {
      render(rail("/p/brief"));

      expect(screen.queryByRole("group", { name: "Strategy" })).not.toBeInTheDocument();
      const chevron = screen.getByRole("button", { name: "Strategy views" });
      expect(chevron).toHaveAttribute("aria-expanded", "false");
      // The views it controls are in the page, hidden, so aria-controls never points at nothing.
      expect(document.getElementById(chevron.getAttribute("aria-controls")!)).toHaveAttribute("hidden");

      await userEvent.click(chevron);

      expect(screen.getByRole("group", { name: "Strategy" })).toBeInTheDocument();
      expect(screen.getByRole("button", { name: "Strategy views" })).toHaveAttribute("aria-expanded", "true");
    });

    it("can be shut by hand inside its section, which then marks itself as where you are", async () => {
      render(rail("/p/strategy", "?mode=people"));

      await userEvent.click(screen.getByRole("button", { name: "Strategy views" }));

      expect(screen.queryByRole("group", { name: "Strategy" })).not.toBeInTheDocument();
      expect(screen.getByRole("link", { name: "Strategy" })).toHaveAttribute("aria-current", "page");
    });

    it("forgets a press once the section is left, opening again on the way back", async () => {
      render(rail("/p/strategy"));
      await userEvent.click(screen.getByRole("button", { name: "Strategy views" }));

      await userEvent.click(screen.getByRole("link", { name: "Brief" }));
      expect(screen.queryByRole("group", { name: "Strategy" })).not.toBeInTheDocument();
      await userEvent.click(screen.getByRole("link", { name: "Strategy" }));

      expect(screen.getByRole("group", { name: "Strategy" })).toBeInTheDocument();
    });

    it("forgets a press made elsewhere once the section has been visited and left", async () => {
      render(rail("/p/brief"));
      await userEvent.click(screen.getByRole("button", { name: "Strategy views" }));

      await userEvent.click(screen.getByRole("link", { name: "Strategy" }));
      await userEvent.click(screen.getByRole("link", { name: "Brief" }));

      expect(screen.queryByRole("group", { name: "Strategy" })).not.toBeInTheDocument();
    });

    it("keeps the section's views as icons when the rail is collapsed, even after a press shut them", async () => {
      render(rail("/p/strategy"));
      await userEvent.click(screen.getByRole("button", { name: "Strategy views" }));

      await userEvent.click(screen.getByTitle("Collapse sidebar"));

      expect(screen.queryByRole("button", { name: /Strategy views/ })).not.toBeInTheDocument();
      expect(within(screen.getByRole("group", { name: "Strategy" })).getByTitle("People")).toHaveAttribute(
        "href",
        "/p/strategy?mode=people",
      );
    });
  });
});
