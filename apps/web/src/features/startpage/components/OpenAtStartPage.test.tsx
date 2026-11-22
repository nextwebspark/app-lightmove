import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Link, MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { aUser } from "../../../test/fixtures/user";
import type { User } from "../../auth/api/types";
import { OpenAtStartPage } from "./OpenAtStartPage";

const auth: { user: User | null } = { user: null };
vi.mock("../../auth/AuthProvider", () => ({ useAuth: () => auth }));

function renderAt(entry: string) {
  return render(
    <MemoryRouter initialEntries={[entry]}>
      <Routes>
        <Route path="/" element={<OpenAtStartPage><p>Home</p></OpenAtStartPage>} />
        <Route path="/tasks" element={<Link to="/">Back home</Link>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe("OpenAtStartPage", () => {
  beforeEach(() => {
    auth.user = aUser({
      startPage: { kind: "TASKS", dashboardId: null, projectId: null, section: null, available: true },
    });
  });

  it("opens the start page when Uncava is opened at its bare root, and Home when reached from inside", async () => {
    const user = userEvent.setup();
    renderAt("/");

    await user.click(await screen.findByRole("link", { name: "Back home" }));
    expect(screen.getByText("Home")).toBeInTheDocument();
  });

  it("keeps Home for a root that asks for something", () => {
    renderAt("/?welcome=1");
    expect(screen.getByText("Home")).toBeInTheDocument();
  });

  it("keeps Home when the saved page is no longer the member's", () => {
    auth.user = aUser({
      startPage: { kind: "TASKS", dashboardId: null, projectId: null, section: null, available: false },
    });
    renderAt("/");
    expect(screen.getByText("Home")).toBeInTheDocument();
  });
});
