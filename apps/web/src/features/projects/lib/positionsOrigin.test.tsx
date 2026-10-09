import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Link, Route, Routes, useLocation } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { NO_POSITIONS_ORIGIN, usePositionsOrigin, usePositionsOriginState } from "./positionsOrigin";

vi.mock("../../workspace/lib/vocabulary", () => ({ useWorkspaceVocabulary: () => ({ units: "Clients" }) }));

function OpenLink() {
  const state = usePositionsOriginState();
  return (
    <Link to="/projects/p1" state={state}>
      Open
    </Link>
  );
}

function Position() {
  const origin = usePositionsOrigin("p1");
  const { pathname } = useLocation();
  return (
    <>
      <a href={origin.path}>Back to {origin.label}</a>
      <span data-testid="at">{pathname}</span>
      <Link to="/projects/p1/strategy">Strategy</Link>
    </>
  );
}

const renderFrom = (entry: string) =>
  render(
    <MemoryRouter initialEntries={[entry]}>
      <Routes>
        <Route path="/" element={<OpenLink />} />
        <Route path="/all" element={<OpenLink />} />
        <Route path="/clients" element={<OpenLink />} />
        <Route
          path="/candidates"
          element={
            <Link to="/projects/p1" state={NO_POSITIONS_ORIGIN}>
              Open
            </Link>
          }
        />
        <Route path="/projects/p1/*" element={<Position />} />
      </Routes>
    </MemoryRouter>,
  );

describe("the list a position was opened from", () => {
  beforeEach(() => sessionStorage.clear());

  it.each([
    ["/all?stage=allstages&q=cfo", "All positions"],
    ["/", "My positions"],
    ["/clients", "Clients"],
  ])("returns to %s, filters and all, and names it", async (from, label) => {
    renderFrom(from);
    await userEvent.click(screen.getByRole("link", { name: "Open" }));

    expect(screen.getByRole("link", { name: `Back to ${label}` })).toHaveAttribute("href", from);
  });

  it("survives moving between the position's tabs", async () => {
    renderFrom("/all?q=cfo");
    await userEvent.click(screen.getByRole("link", { name: "Open" }));
    await userEvent.click(screen.getByRole("link", { name: "Strategy" }));

    expect(screen.getByTestId("at")).toHaveTextContent("/projects/p1/strategy");
    expect(screen.getByRole("link", { name: "Back to All positions" })).toHaveAttribute("href", "/all?q=cfo");
  });

  it("falls back to My positions when nothing says where it came from", () => {
    renderFrom("/projects/p1");

    expect(screen.getByRole("link", { name: "Back to My positions" })).toHaveAttribute("href", "/");
  });

  it("forgets an earlier list once the position is opened from somewhere else", async () => {
    sessionStorage.setItem("lightmove.positionsOrigin.p1", "/all?q=cfo");
    renderFrom("/candidates");
    await userEvent.click(screen.getByRole("link", { name: "Open" }));

    expect(screen.getByRole("link", { name: "Back to My positions" })).toHaveAttribute("href", "/");
    expect(sessionStorage.getItem("lightmove.positionsOrigin.p1")).toBeNull();
  });

  it.each(["//evil.example", "/\\evil.example", "/projects/p2"])("never follows a kept origin to %s", (kept) => {
    sessionStorage.setItem("lightmove.positionsOrigin.p1", kept);
    renderFrom("/projects/p1");

    expect(screen.getByRole("link", { name: "Back to My positions" })).toHaveAttribute("href", "/");
  });
});
