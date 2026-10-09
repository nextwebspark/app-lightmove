import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import { ApiRequestError } from "../../../lib/apiClient";
import type { WorkspaceDetail } from "../../workspace/api/types";
import * as workspaceApi from "../../workspace/api/workspaceApi";
import * as integrationsApi from "../api/integrationsApi";
import type { WorkspaceIntegration, WorkspaceIntegrations } from "../api/types";
import { SettingsIntegrationsPage } from "./SettingsIntegrationsPage";

vi.mock("../api/integrationsApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/integrationsApi")>()),
  integrations: vi.fn(),
  updateIntegration: vi.fn(),
  returnToSharedApp: vi.fn(),
  recordMicrosoftAdminConsent: vi.fn(),
}));
vi.mock("../../workspace/api/workspaceApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../workspace/api/workspaceApi")>()),
  workspace: vi.fn(),
  changeCalendarSync: vi.fn(),
}));

const sharedIntegration = (provider: WorkspaceIntegration["provider"]): WorkspaceIntegration => ({
  provider,
  mode: "SHARED",
  clientId: null,
  tenantId: null,
  secretExpiresOn: null,
  secretSet: false,
  sharedOffered: provider !== "ZOOM",
  redirectUri:
    provider === "ZOOM"
      ? "https://beta.uncava.com/api/v1/outreach/zoom/callback"
      : "https://beta.uncava.com/api/v1/outreach/mailbox/callback",
  scopes: provider === "GOOGLE" ? ["https://www.googleapis.com/auth/gmail.send"] : ["Mail.Send"],
  adminConsentUrl:
    provider === "MICROSOFT"
      ? "https://login.microsoftonline.com/organizations/adminconsent?client_id=uncava-microsoft-client"
      : null,
  ownAppGuideUrl: null,
  sharedAppGuideUrl: null,
  updatedAt: null,
  adminConsentedAt: null,
  adminConsentTenantId: null,
});

const allShared: WorkspaceIntegrations = {
  providers: [sharedIntegration("GOOGLE"), sharedIntegration("MICROSOFT"), sharedIntegration("ZOOM")],
  ownAppsOffered: true,
  recallOffered: true,
};

const googleOwn: WorkspaceIntegrations = {
  ...allShared,
  providers: [
    {
      ...sharedIntegration("GOOGLE"),
      mode: "OWN",
      clientId: "acme-app",
      secretSet: true,
      secretExpiresOn: "2027-10-01",
      updatedAt: "2026-10-02T10:00:00Z",
    },
    sharedIntegration("MICROSOFT"),
    sharedIntegration("ZOOM"),
  ],
};

const workspace = { id: "ws-1", name: "Acme", mode: "COMPANY", calendarSync: "RECALL" } as WorkspaceDetail;

function renderPage(path = "/settings/integrations") {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <ToastProvider>
          <SettingsIntegrationsPage />
        </ToastProvider>
      </QueryClientProvider>
    </MemoryRouter>,
  );
}

const card = (name: string) => screen.findByRole("region", { name });

function isoDateIn(days: number): string {
  const day = new Date();
  day.setDate(day.getDate() + days);
  return `${day.getFullYear()}-${String(day.getMonth() + 1).padStart(2, "0")}-${String(day.getDate()).padStart(2, "0")}`;
}

describe("SettingsIntegrationsPage", () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(integrationsApi.integrations).mockResolvedValue(allShared);
    vi.mocked(workspaceApi.workspace).mockResolvedValue(workspace);
  });

  it("says plainly what Recall receives, and offers Direct instead", async () => {
    vi.mocked(workspaceApi.changeCalendarSync).mockResolvedValue({ ...workspace, calendarSync: "DIRECT" });
    renderPage();

    const calendar = await card("Calendar sync");
    expect(
      within(calendar).getByText(/client ID and secret and each person's calendar refresh token are shared with Recall.ai/),
    ).toBeInTheDocument();

    await userEvent.click(within(calendar).getByRole("radio", { name: "Direct" }));

    await waitFor(() => expect(workspaceApi.changeCalendarSync).toHaveBeenCalledWith("DIRECT"));
    expect(await within(calendar).findByText(/Nothing is shared with Recall.ai/)).toBeInTheDocument();
  });

  it("gives Microsoft's shared app the admin-consent link to send IT, and says where no shared app exists", async () => {
    renderPage();

    const microsoft = await card("Microsoft 365");
    expect(within(microsoft).getByText(/adminconsent\?client_id=uncava-microsoft-client/)).toBeInTheDocument();
    expect(within(microsoft).getByRole("button", { name: "Copy admin consent link" })).toBeInTheDocument();
    expect(within(await card("Zoom")).getByText(/shared app isn't available for this provider/)).toBeInTheDocument();
  });

  it("saves an own app's keys, telling the admin what to register and that Recall will receive them", async () => {
    vi.mocked(integrationsApi.updateIntegration).mockResolvedValue(googleOwn);
    renderPage();
    const google = await card("Google Workspace");

    await userEvent.click(within(google).getByRole("radio", { name: "Your own app" }));

    expect(within(google).getByText("https://beta.uncava.com/api/v1/outreach/mailbox/callback")).toBeInTheDocument();
    expect(within(google).getByText("https://www.googleapis.com/auth/gmail.send")).toBeInTheDocument();
    expect(within(google).getByText("Shared with Recall.ai while calendar sync is on Recall")).toBeInTheDocument();
    expect(within(google).queryByLabelText("Tenant ID")).not.toBeInTheDocument();

    await userEvent.type(within(google).getByLabelText("Client ID"), "acme-app");
    await userEvent.type(within(google).getByLabelText(/^Client secret/), " pasted secret ");
    await userEvent.click(within(google).getByRole("button", { name: "Save" }));

    await waitFor(() =>
      expect(integrationsApi.updateIntegration).toHaveBeenCalledWith("GOOGLE", {
        mode: "OWN",
        clientId: "acme-app",
        clientSecret: " pasted secret ",
        tenantId: undefined,
        secretExpiresOn: null,
      }),
    );
  });

  it("keeps a saved secret when the field is left blank, and never shows it", async () => {
    vi.mocked(integrationsApi.integrations).mockResolvedValue(googleOwn);
    vi.mocked(integrationsApi.updateIntegration).mockResolvedValue(googleOwn);
    renderPage();
    const google = await card("Google Workspace");

    const secret = within(google).getByLabelText(/^Client secret/);
    expect(secret).toHaveValue("");
    expect(secret).toHaveAttribute("placeholder", "Saved — leave blank to keep");

    await userEvent.click(within(google).getByRole("button", { name: "Save" }));

    await waitFor(() =>
      expect(integrationsApi.updateIntegration).toHaveBeenCalledWith(
        "GOOGLE",
        expect.objectContaining({ clientId: "acme-app", clientSecret: undefined, secretExpiresOn: "2027-10-01" }),
      ),
    );
  });

  it("asks a Microsoft app for its tenant before anything is sent", async () => {
    renderPage();
    const microsoft = await card("Microsoft 365");

    await userEvent.click(within(microsoft).getByRole("radio", { name: "Your own app" }));
    await userEvent.type(within(microsoft).getByLabelText("Client ID"), "contoso-app");
    await userEvent.type(within(microsoft).getByLabelText(/^Client secret/), "s3cret");
    await userEvent.click(within(microsoft).getByRole("button", { name: "Save" }));

    expect(await within(microsoft).findByText("Enter your directory's tenant ID")).toBeInTheDocument();
    expect(integrationsApi.updateIntegration).not.toHaveBeenCalled();
  });

  it("puts a refusal the server attributes to a field on that field", async () => {
    vi.mocked(integrationsApi.updateIntegration).mockRejectedValue(
      new ApiRequestError({
        code: "VALIDATION_FAILED",
        detail: "Validation failed",
        status: 400,
        correlationId: "c1",
        fieldErrors: { clientSecret: "Enter your app's client secret" },
      }),
    );
    renderPage();
    const zoom = await card("Zoom");

    await userEvent.click(within(zoom).getByRole("radio", { name: "Your own app" }));
    await userEvent.type(within(zoom).getByLabelText("Client ID"), "zoom-app");
    await userEvent.type(within(zoom).getByLabelText(/^Client secret/), "s3cret");
    await userEvent.click(within(zoom).getByRole("button", { name: "Save" }));

    expect(await within(zoom).findByText("Enter your app's client secret")).toBeInTheDocument();
  });

  it("warns on the card from 30 days before an own app's secret expires, and says when it has", async () => {
    const withExpiry = (secretExpiresOn: string): WorkspaceIntegrations => ({
      ...googleOwn,
      providers: [{ ...googleOwn.providers[0], secretExpiresOn }, ...googleOwn.providers.slice(1)],
    });
    vi.mocked(integrationsApi.integrations).mockResolvedValue(withExpiry(isoDateIn(10)));
    const { unmount } = renderPage();

    expect(await within(await card("Google Workspace")).findByRole("status")).toHaveTextContent(
      /The client secret expires on .*, in 10 days\./,
    );
    unmount();

    vi.mocked(integrationsApi.integrations).mockResolvedValue(withExpiry(isoDateIn(-2)));
    renderPage();
    expect(await within(await card("Google Workspace")).findByRole("status")).toHaveTextContent(
      /The client secret expired on/,
    );
  });

  it("says nothing of an expiry still more than 30 days away", async () => {
    vi.mocked(integrationsApi.integrations).mockResolvedValue(googleOwn);
    vi.useFakeTimers({ toFake: ["Date"], now: new Date("2026-10-03T09:00:00") });
    renderPage();

    await card("Google Workspace");
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
  });

  it("confirms before returning a saved own app to the shared one, which discards its keys", async () => {
    vi.mocked(integrationsApi.integrations).mockResolvedValue(googleOwn);
    vi.mocked(integrationsApi.returnToSharedApp).mockResolvedValue(allShared);
    renderPage();
    const google = await card("Google Workspace");

    await userEvent.click(within(google).getByRole("radio", { name: "Shared app" }));
    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText(/client ID and secret are deleted from Uncava/)).toBeInTheDocument();
    expect(integrationsApi.returnToSharedApp).not.toHaveBeenCalled();

    await userEvent.click(within(dialog).getByRole("button", { name: "Use the shared app" }));

    await waitFor(() => expect(integrationsApi.returnToSharedApp).toHaveBeenCalledWith("GOOGLE"));
  });

  it("redraws a provider another admin returned to the shared app when the page reads again", async () => {
    vi.mocked(integrationsApi.integrations).mockResolvedValueOnce(googleOwn).mockResolvedValue(allShared);
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <MemoryRouter>
        <QueryClientProvider client={queryClient}>
          <ToastProvider>
            <SettingsIntegrationsPage />
          </ToastProvider>
        </QueryClientProvider>
      </MemoryRouter>,
    );
    const google = await card("Google Workspace");
    expect(within(google).getByRole("radio", { name: "Your own app" })).toHaveAttribute("aria-checked", "true");

    await queryClient.invalidateQueries({ queryKey: integrationsApi.INTEGRATIONS_KEY });

    await waitFor(() =>
      expect(within(google).getByRole("radio", { name: "Shared app" })).toHaveAttribute("aria-checked", "true"),
    );
    expect(within(google).queryByLabelText("Client ID")).not.toBeInTheDocument();
  });

  it("explains a deployment that cannot store an own app's keys rather than letting a save fail", async () => {
    vi.mocked(integrationsApi.integrations).mockResolvedValue({ ...allShared, ownAppsOffered: false });
    renderPage();
    const google = await card("Google Workspace");

    await userEvent.click(within(google).getByRole("radio", { name: "Your own app" }));

    expect(within(google).getByRole("alert")).toHaveTextContent("can't be stored for your workspace");
    expect(within(google).getByRole("button", { name: "Save" })).toBeDisabled();
  });

  it("records Microsoft's admin-consent return once and says the organisation approved Uncava", async () => {
    vi.mocked(integrationsApi.recordMicrosoftAdminConsent).mockResolvedValue({
      ...allShared,
      providers: [
        sharedIntegration("GOOGLE"),
        { ...sharedIntegration("MICROSOFT"), adminConsentedAt: "2026-10-03T07:00:00Z", adminConsentTenantId: "t-1" },
        sharedIntegration("ZOOM"),
      ],
    });
    renderPage("/settings/integrations?admin_consent=True&tenant=t-1&state=signed-state");

    const microsoft = await card("Microsoft 365");
    await waitFor(() =>
      expect(within(microsoft).getByText(/Approved for your organisation on/)).toBeInTheDocument(),
    );
    expect(integrationsApi.recordMicrosoftAdminConsent).toHaveBeenCalledTimes(1);
    expect(integrationsApi.recordMicrosoftAdminConsent).toHaveBeenCalledWith("t-1", "signed-state");
  });

  it("says so when Microsoft returns without an approval, and records nothing", async () => {
    renderPage("/settings/integrations?error=access_denied&error_description=declined");

    expect(await screen.findByText(/Microsoft did not approve Uncava/)).toBeInTheDocument();
    expect(integrationsApi.recordMicrosoftAdminConsent).not.toHaveBeenCalled();
  });
});
