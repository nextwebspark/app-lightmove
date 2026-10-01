import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import * as mailboxApi from "../api/mailboxApi";
import type { Mailbox } from "../api/mailboxApi";
import { OutreachPage } from "./OutreachPage";

const auth = vi.hoisted(() => ({ roles: ["MEMBER"] as string[] }));

vi.mock("../../auth/AuthProvider", () => ({
  useAuth: () => ({ user: { id: "u1", workspace: { id: "w1", roles: auth.roles } } }),
}));

vi.mock("../api/mailboxApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/mailboxApi")>()),
  getMailbox: vi.fn(),
  startMailboxConnect: vi.fn(),
  sendMailboxTest: vi.fn(),
  disconnectMailbox: vi.fn(),
}));

const nothingConnected: Mailbox = { offered: true, providers: ["google", "microsoft"], connection: null };
const connected: Mailbox = {
  ...nothingConnected,
  connection: {
    address: "yara@firm.example",
    provider: "google",
    status: "ACTIVE",
    dailyCap: 50,
    connectedAt: "2026-10-01T09:00:00Z",
  },
};

function renderPage() {
  return render(
    <MemoryRouter>
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <ToastProvider>
          <OutreachPage />
        </ToastProvider>
      </QueryClientProvider>
    </MemoryRouter>,
  );
}

describe("OutreachPage", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    auth.roles = ["MEMBER"];
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("offers each mailbox host the deployment lists, by the name people know it by", async () => {
    vi.mocked(mailboxApi.getMailbox).mockResolvedValue(nothingConnected);
    renderPage();

    expect(await screen.findByRole("button", { name: "Connect Gmail" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Connect Outlook" })).toBeInTheDocument();
  });

  it("opens the popup in the click, then points it at the consent screen the server names", async () => {
    vi.mocked(mailboxApi.getMailbox).mockResolvedValue(nothingConnected);
    vi.mocked(mailboxApi.startMailboxConnect).mockResolvedValue({ authorizationUrl: "https://mail.example/connect?state=s" });
    const popup = { location: { href: "about:blank" }, closed: false, close: vi.fn() };
    const open = vi.fn(() => popup);
    vi.stubGlobal("open", open);
    renderPage();

    await userEvent.click(await screen.findByRole("button", { name: "Connect Gmail" }));

    expect(open).toHaveBeenCalledWith("about:blank", "_blank", expect.any(String));
    expect(mailboxApi.startMailboxConnect).toHaveBeenCalledWith("google");
    await waitFor(() => expect(popup.location.href).toBe("https://mail.example/connect?state=s"));
  });

  it("shows the connected address and sends a test from it", async () => {
    vi.mocked(mailboxApi.getMailbox).mockResolvedValue(connected);
    vi.mocked(mailboxApi.sendMailboxTest).mockResolvedValue(undefined);
    renderPage();

    expect(await screen.findByText("yara@firm.example")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Connect Gmail" })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Send a test email" }));

    expect(await screen.findByText("Test email sent. Check your inbox.")).toBeInTheDocument();
  });

  it("asks for a reconnect when the provider withdrew access", async () => {
    vi.mocked(mailboxApi.getMailbox).mockResolvedValue({
      ...connected,
      connection: { ...connected.connection!, status: "ERROR" },
    });
    renderPage();

    expect(await screen.findByRole("alert")).toHaveTextContent("Gmail disconnected.");
    expect(screen.getByRole("button", { name: "Reconnect" })).toBeInTheDocument();
  });

  it("says outreach is not set up where the deployment has no mail service", async () => {
    vi.mocked(mailboxApi.getMailbox).mockResolvedValue({ offered: false, providers: [], connection: null });
    renderPage();

    expect(await screen.findByText("Outreach email is not set up on this deployment.")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Connect/ })).not.toBeInTheDocument();
  });

  it("never asks for a mailbox on a client representative's behalf", () => {
    auth.roles = ["CLIENT"];
    renderPage();

    expect(screen.getByText("No outreach on this position yet")).toBeInTheDocument();
    expect(mailboxApi.getMailbox).not.toHaveBeenCalled();
  });
});
