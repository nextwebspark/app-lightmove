import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Link, MemoryRouter, Route, Routes } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";
import { LeaveGuard } from "./LeaveGuard";

const renderGuarded = (hasUnsavedChanges: boolean, flush: () => Promise<void>) =>
  render(
    <MemoryRouter initialEntries={["/brief"]}>
      <Routes>
        <Route
          path="/brief"
          element={
            <>
              <LeaveGuard hasUnsavedChanges={hasUnsavedChanges} flush={flush} />
              <Link to="/projects">Positions</Link>
              <Link to="/brief?step=review">Review</Link>
            </>
          }
        />
        <Route path="/projects" element={<h1>Positions page</h1>} />
      </Routes>
    </MemoryRouter>,
  );

describe("LeaveGuard", () => {
  it("saves first and then follows the link", async () => {
    const flush = vi.fn().mockResolvedValue(undefined);
    renderGuarded(true, flush);

    await userEvent.click(screen.getByRole("link", { name: "Positions" }));

    expect(await screen.findByRole("heading", { name: "Positions page" })).toBeInTheDocument();
    expect(flush).toHaveBeenCalledTimes(1);
  });

  it("asks before leaving when the save is refused, and stays when told to", async () => {
    const flush = vi.fn().mockRejectedValue(new Error("503"));
    renderGuarded(true, flush);
    const person = userEvent.setup();

    await person.click(screen.getByRole("link", { name: "Positions" }));
    expect(await screen.findByRole("dialog", { name: "Leave without saving?" })).toBeInTheDocument();

    await person.click(screen.getByRole("button", { name: "Stay on this page" }));
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Positions page" })).not.toBeInTheDocument();

    await person.click(screen.getByRole("link", { name: "Positions" }));
    await person.click(await screen.findByRole("button", { name: "Leave anyway" }));
    expect(await screen.findByRole("heading", { name: "Positions page" })).toBeInTheDocument();
  });

  it("leaves a link within the same page alone", async () => {
    const flush = vi.fn().mockRejectedValue(new Error("503"));
    renderGuarded(true, flush);

    await userEvent.click(screen.getByRole("link", { name: "Review" }));

    expect(flush).not.toHaveBeenCalled();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("does nothing while every edit is saved", async () => {
    const flush = vi.fn();
    renderGuarded(false, flush);

    await userEvent.click(screen.getByRole("link", { name: "Positions" }));

    expect(await screen.findByRole("heading", { name: "Positions page" })).toBeInTheDocument();
    expect(flush).not.toHaveBeenCalled();
  });
});
