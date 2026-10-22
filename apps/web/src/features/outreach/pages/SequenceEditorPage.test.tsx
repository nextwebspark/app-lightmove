import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Outlet, Route, Routes, useLocation } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import * as candidatesApi from "../../candidates/api/candidatesApi";
import * as mailboxApi from "../api/mailboxApi";
import { SequenceEditorPage } from "./SequenceEditorPage";

vi.mock("../../auth/AuthProvider", () => ({
  useAuth: () => ({ user: { id: "u1", fullName: "Yara Haddad", workspace: { id: "w1", mode: "COMPANY", roles: ["MEMBER"] } } }),
}));

vi.mock("../api/mailboxApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/mailboxApi")>()),
  getMailbox: vi.fn(),
}));

vi.mock("../../candidates/api/candidatesApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../candidates/api/candidatesApi")>()),
  getCandidates: vi.fn(),
}));

const PROJECT = { id: "p1", positionTitle: "Group CFO" };

function Where() {
  return <output aria-label="Location">{useLocation().pathname}</output>;
}

function renderEditor() {
  return render(
    <MemoryRouter initialEntries={["/projects/p1/outreach/sequences/new"]}>
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <ToastProvider>
          <Routes>
            <Route element={<Outlet context={{ project: PROJECT }} />}>
              <Route path="/projects/:projectId/outreach/sequences/:sequenceId" element={<SequenceEditorPage />} />
              <Route path="*" element={null} />
            </Route>
          </Routes>
          <Where />
        </ToastProvider>
      </QueryClientProvider>
    </MemoryRouter>,
  );
}

describe("SequenceEditorPage", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(candidatesApi.getCandidates).mockReturnValue(new Promise(() => {}));
    vi.mocked(mailboxApi.getMailbox).mockResolvedValue({
      offered: true,
      providers: ["google"],
      connection: null,
      bookingLinkOffered: false,
    });
  });

  it("writes a new sequence under the Outreach header, a click from the conversations", async () => {
    renderEditor();

    const views = await screen.findByRole("radiogroup", { name: "Outreach view" });
    expect(within(views).getByRole("radio", { name: "Sequences" })).toBeChecked();
    expect(screen.getByRole("textbox", { name: "Sequence name" })).toHaveValue("New sequence");
    expect(await screen.findByRole("button", { name: "Connect mailbox" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "change it in your profile" })).toHaveAttribute("href", "/settings/profile");

    await userEvent.click(within(views).getByRole("radio", { name: "Conversations" }));

    await waitFor(() => expect(screen.getByLabelText("Location")).toHaveTextContent(/^\/projects\/p1\/outreach$/));
  });

  it("goes back to the list from the header's Sequences", async () => {
    renderEditor();

    const views = await screen.findByRole("radiogroup", { name: "Outreach view" });
    await userEvent.click(within(views).getByRole("radio", { name: "Sequences" }));

    await waitFor(() =>
      expect(screen.getByLabelText("Location")).toHaveTextContent(/^\/projects\/p1\/outreach\/sequences$/),
    );
  });
});
