import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import * as clientsApi from "../api/clientsApi";
import type { ClientDetail } from "../api/types";
import { ClientDrawer } from "./ClientDrawer";

// Only the calls are mocked: the query keys are what the drawer invalidates, so they stay real.
vi.mock("../api/clientsApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/clientsApi")>()),
  client: vi.fn(),
  revokeRepresentative: vi.fn(),
  resendRepresentativeInvite: vi.fn(),
}));

/** The client drawer's representative rows — where a contact's access is withdrawn or re-sent. */
describe("ClientDrawer representatives", () => {
  const detail: ClientDetail = {
    id: "c1",
    name: "Almarai",
    sector: "FMCG",
    hqCountry: "KSA",
    domain: "almarai.com",
    offLimitsNote: null,
    activeMandates: 1,
    deliveredMandates: 0,
    representatives: [
      { id: "r1", fullName: "Khalid Al-Otaibi", position: "Group CHRO", email: "khalid@almarai.com", status: "INVITED" },
      { id: "r2", fullName: "Noura Saleh", position: "Chair", email: "noura@almarai.com", status: "ACTIVE" },
    ],
    mandates: [],
  };

  const renderDrawer = () =>
    render(
      <MemoryRouter>
        <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
          <ToastProvider>
            <ClientDrawer clientId="c1" onClose={vi.fn()} onNewMandate={vi.fn()} />
          </ToastProvider>
        </QueryClientProvider>
      </MemoryRouter>,
    );

  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(clientsApi.client).mockResolvedValue(detail);
  });

  /** Every action is one click deep, behind the row's kebab — the mockup's shape. */
  const openMenu = async (user: ReturnType<typeof userEvent.setup>, name: string) =>
    user.click(await screen.findByRole("button", { name: `Actions for ${name}` }));

  it("asks before cancelling an outstanding invite", async () => {
    const user = userEvent.setup();
    renderDrawer();

    await openMenu(user, "Khalid Al-Otaibi");
    await user.click(screen.getByRole("button", { name: "Cancel invite" }));
    expect(clientsApi.revokeRepresentative).not.toHaveBeenCalled();

    await user.click(screen.getByRole("button", { name: "Keep" }));
    expect(clientsApi.revokeRepresentative).not.toHaveBeenCalled();
    expect(screen.queryByRole("button", { name: "Cancel invite" })).not.toBeInTheDocument();
  });

  it("cancels the invite once confirmed, and refreshes the client", async () => {
    const user = userEvent.setup();
    vi.mocked(clientsApi.revokeRepresentative).mockResolvedValue(undefined);
    renderDrawer();

    await openMenu(user, "Khalid Al-Otaibi");
    // The confirmation replaces the menu, so the second click is the same wording deliberately.
    await user.click(screen.getByRole("button", { name: "Cancel invite" }));
    await user.click(screen.getByRole("button", { name: "Cancel invite" }));

    await waitFor(() => expect(clientsApi.revokeRepresentative).toHaveBeenCalledWith("c1", "r1"));
    // The invalidation refetches the open client — the drawer's counts are all server-derived.
    await waitFor(() => expect(vi.mocked(clientsApi.client).mock.calls.length).toBeGreaterThan(1));
    expect(await screen.findByText(/invite to khalid@almarai\.com cancelled/i)).toBeInTheDocument();
  });

  it("revokes a live representative's access with its own wording", async () => {
    const user = userEvent.setup();
    vi.mocked(clientsApi.revokeRepresentative).mockResolvedValue(undefined);
    renderDrawer();

    await openMenu(user, "Noura Saleh");
    await user.click(screen.getByRole("button", { name: "Revoke access" }));
    expect(screen.getByText(/revoke portal access for noura saleh/i)).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Revoke" }));
    await waitFor(() => expect(clientsApi.revokeRepresentative).toHaveBeenCalledWith("c1", "r2"));
  });

  it("re-sends an outstanding invite without a confirm step", async () => {
    const user = userEvent.setup();
    vi.mocked(clientsApi.resendRepresentativeInvite).mockResolvedValue(undefined);
    renderDrawer();

    await openMenu(user, "Khalid Al-Otaibi");
    await user.click(screen.getByRole("button", { name: "Resend invite" }));

    await waitFor(() => expect(clientsApi.resendRepresentativeInvite).toHaveBeenCalledWith("c1", "r1"));
  });

  it("offers no resend or cancel wording for a representative who is already active", async () => {
    const user = userEvent.setup();
    renderDrawer();

    await openMenu(user, "Noura Saleh");
    expect(screen.getByRole("button", { name: "Copy email" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Resend invite" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Cancel invite" })).not.toBeInTheDocument();
  });
});
