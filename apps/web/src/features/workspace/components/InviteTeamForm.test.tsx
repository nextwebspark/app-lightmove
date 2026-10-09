import { fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { addressesIn, InviteTeamForm } from "./InviteTeamForm";

const renderForm = (submit = vi.fn().mockResolvedValue(undefined), onDone = vi.fn()) => {
  render(<InviteTeamForm subtitle="Step 4 of 4" submit={submit} onDone={onDone} onSkip={vi.fn()} />);
  return { submit, onDone };
};

const paste = (input: HTMLElement, text: string) =>
  fireEvent.paste(input, { clipboardData: { getData: () => text } });

describe("InviteTeamForm", () => {
  it("says what each workspace role can do, and nothing about projects", () => {
    renderForm();

    expect(screen.getByText(/works on the ones they're added to/)).toBeInTheDocument();
    expect(screen.getByText(/Also opens any position/)).toBeInTheDocument();
    expect(screen.getByText("Whoever creates a position leads it and adds its team.")).toBeInTheDocument();
    expect(screen.queryByText(/project/i)).not.toBeInTheDocument();
    expect(screen.getAllByRole("combobox", { name: "Role" })[0]).toHaveAccessibleDescription(/Also opens any position/);
  });

  it("labels the button by how many invites it will send", async () => {
    const user = userEvent.setup();
    renderForm();

    expect(screen.getByRole("button", { name: "Finish" })).toBeInTheDocument();
    const [first, second] = screen.getAllByRole("textbox", { name: "Colleague's email" });
    await user.type(first, "a@firm.example");
    expect(screen.getByRole("button", { name: "Send 1 invite & finish" })).toBeInTheDocument();
    await user.type(second, "b@firm.example");
    expect(screen.getByRole("button", { name: "Send 2 invites & finish" })).toBeInTheDocument();
  });

  it("splits a pasted list into one row per address, keeping the row's role", async () => {
    const user = userEvent.setup();
    const { submit } = renderForm();
    const [first] = screen.getAllByRole("textbox", { name: "Colleague's email" });
    await user.selectOptions(screen.getAllByRole("combobox", { name: "Role" })[0], "ADMIN");

    paste(first, "a@x.com, b@x.com\nRania Haddad <c@x.com>");

    const rows = screen.getAllByRole("textbox", { name: "Colleague's email" });
    expect(rows.map((row) => (row as HTMLInputElement).value)).toEqual(["a@x.com", "b@x.com", "c@x.com"]);
    await user.click(screen.getByRole("button", { name: "Send 3 invites & finish" }));
    expect(submit).toHaveBeenCalledWith([
      { email: "a@x.com", role: "ADMIN" },
      { email: "b@x.com", role: "ADMIN" },
      { email: "c@x.com", role: "ADMIN" },
    ]);
  });

  it("keeps an address already typed into the row, and leaves out repeats", async () => {
    const user = userEvent.setup();
    renderForm();
    const [first, second] = screen.getAllByRole("textbox", { name: "Colleague's email" });
    await user.type(first, "a@x.com");
    await user.type(second, "keep@x.com");

    paste(first, "b@x.com, A@x.com, c@x.com, b@x.com");

    const rows = screen.getAllByRole("textbox", { name: "Colleague's email" });
    expect(rows.map((row) => (row as HTMLInputElement).value)).toEqual(["a@x.com", "b@x.com", "c@x.com", "keep@x.com"]);
    expect(screen.getByRole("status")).toHaveTextContent("2 addresses added");
  });

  it("leaves an ordinary single-address paste to the browser", () => {
    renderForm();
    const [first] = screen.getAllByRole("textbox", { name: "Colleague's email" });

    expect(paste(first, "a@x.com")).toBe(true);
    expect(screen.getAllByRole("textbox", { name: "Colleague's email" })).toHaveLength(2);
  });
});

describe("addressesIn", () => {
  it("reads commas, semicolons, new lines and mail-client names", () => {
    expect(addressesIn("a@x.com; b@x.com\n\"Lee, Kim\" <c@x.com>")).toEqual(["a@x.com", "b@x.com", "c@x.com"]);
    expect(addressesIn("no addresses here")).toEqual([]);
    expect(addressesIn("o'neill@firm.com, mailto:b@x.com and c@x.com.")).toEqual(["o'neill@firm.com", "b@x.com", "c@x.com"]);
  });
});
