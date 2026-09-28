import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import { aUser, aWorkspace } from "../../../test/fixtures/user";
import type { User } from "../../auth/api/types";
import * as clientsApi from "../api/clientsApi";
import type { ClientDetail } from "../api/types";
import { ClientDrawer } from "./ClientDrawer";

vi.mock("../api/clientsApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/clientsApi")>()),
  client: vi.fn(),
  updateClient: vi.fn(),
}));

let currentUser: User = aUser();

vi.mock("../../auth/AuthProvider", () => ({
  useAuth: () => ({ user: currentUser }),
}));

const renderDrawer = () =>
  render(
    <MemoryRouter>
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <ToastProvider>
          <ClientDrawer clientId="c1" onClose={() => {}} onNewMandate={() => {}} />
        </ToastProvider>
      </QueryClientProvider>
    </MemoryRouter>,
  );

const detail: ClientDetail = {
  id: "c1",
  name: "Automotive",
  sector: "Automotive",
  hqCountry: "United Arab Emirates",
  hqCity: "Dubai",
  logoUrl: null,
  domain: "example.ae",
  offLimitsNote: "Protected until 2027",
  notes: null,
  activeMandates: 2,
  deliveredMandates: 1,
  apolloAccountId: null,
  persona: { summary: null, sectors: [], competitors: [], geographies: [], notes: null },
  representatives: [],
  mandates: [],
};

/**
 * The PATCH is partial, so the drawer sends only what it edits. Sending the fields it no longer shows
 * would write back a snapshot taken when the drawer opened, over anything saved since.
 */
describe("ClientDrawer — saving details", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    currentUser = aUser();
  });

  it("sends only the name and the notes", async () => {
    vi.mocked(clientsApi.client).mockResolvedValue(detail);
    vi.mocked(clientsApi.updateClient).mockResolvedValue({ ...detail, notes: "Freeze lifted" });

    renderDrawer();

    const user = userEvent.setup();
    await user.type(await screen.findByPlaceholderText(/hiring freeze lifted Q1/), "Freeze lifted");
    await user.click(screen.getByRole("button", { name: "Save changes" }));

    expect(clientsApi.updateClient).toHaveBeenCalledWith("c1", { name: "Automotive", notes: "Freeze lifted" });
  });
});

describe("ClientDrawer — an in-house workspace's business unit", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(clientsApi.client).mockResolvedValue({ ...detail, logoUrl: "https://logos.example/automotive.png" });
  });

  it("files an in-house record as a business unit with hiring managers, under the team glyph", async () => {
    currentUser = aUser({ workspace: aWorkspace({ mode: "COMPANY" }) });

    const { container } = renderDrawer();

    expect(await screen.findByText("Business unit record")).toBeInTheDocument();
    expect(screen.getByText("Hiring managers")).toBeInTheDocument();
    expect(container.ownerDocument.querySelector("img")).toBeNull();
    expect(screen.getByRole("dialog", { name: "Automotive" })).toHaveClass("sm:w-[420px]");
  });
});
