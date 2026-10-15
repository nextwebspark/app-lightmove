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
  archiveClient: vi.fn(),
  restoreClient: vi.fn(),
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

describe("ClientDrawer — archiving the record", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    currentUser = aUser({ workspace: aWorkspace({ mode: "COMPANY", roles: ["ADMIN"] }) });
  });

  it("names the live positions that hold it, and goes no further while they are open", async () => {
    vi.mocked(clientsApi.client).mockResolvedValue({
      ...detail,
      mandates: [
        { id: "p1", positionTitle: "Head of Retail", stage: "MAPPING", health: "OK", leadName: "Sara", targetDate: null },
        {
          id: "p2",
          positionTitle: "Old Search",
          stage: "MAPPING",
          health: "OK",
          leadName: "Sara",
          targetDate: null,
          archivedAt: "2026-10-01T09:00:00Z",
        },
      ],
    });
    renderDrawer();
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: "Archive" }));

    expect(await screen.findByRole("link", { name: "Head of Retail" })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Old Search" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Archive business unit" })).toBeDisabled();
  });

  it("archives a record with no open position once its name is typed back", async () => {
    vi.mocked(clientsApi.client).mockResolvedValue(detail);
    vi.mocked(clientsApi.archiveClient).mockResolvedValue({ ...detail, archivedAt: "2026-10-14T09:00:00Z" });
    renderDrawer();
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: "Archive" }));
    const confirm = await screen.findByRole("button", { name: "Archive business unit" });
    expect(confirm).toBeDisabled();
    await user.type(screen.getByLabelText("Name to confirm"), "automotive");
    await user.click(confirm);

    expect(clientsApi.archiveClient).toHaveBeenCalledWith("c1", "automotive");
  });

  it("offers a member no way to archive", async () => {
    currentUser = aUser({ workspace: aWorkspace({ mode: "COMPANY", roles: ["MEMBER"] }) });
    vi.mocked(clientsApi.client).mockResolvedValue(detail);
    renderDrawer();

    expect(await screen.findByText("Business unit record")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Archive" })).not.toBeInTheDocument();
  });

  it("draws an archived record as archived, with Restore in place of New position", async () => {
    vi.mocked(clientsApi.client).mockResolvedValue({
      ...detail,
      archivedAt: "2026-10-14T09:00:00Z",
      archivedByName: "Alok Kumar",
    });
    vi.mocked(clientsApi.restoreClient).mockResolvedValue(detail);
    renderDrawer();
    const user = userEvent.setup();

    expect(await screen.findByText(/by Alok Kumar/)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /new position/i })).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Restore business unit" }));

    expect(clientsApi.restoreClient).toHaveBeenCalledWith("c1");
  });
});
