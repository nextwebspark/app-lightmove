import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it } from "vitest";
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
});
