import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it } from "vitest";
import { Sidebar } from "./Sidebar";

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
    expect(screen.getByText("dev")).toBeInTheDocument();
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
});
