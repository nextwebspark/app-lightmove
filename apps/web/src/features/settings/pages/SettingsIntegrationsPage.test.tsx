import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
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
  verifyIntegration: vi.fn(),
  returnToSharedApp: vi.fn(),
  recordMicrosoftAdminConsent: vi.fn(),
}));
vi.mock("../../workspace/api/workspaceApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../workspace/api/workspaceApi")>()),
  workspace: vi.fn(),
  changeCalendarSync: vi.fn(),
}));

const GOOGLE_APP = "acme-app.apps.googleusercontent.com";
const MICROSOFT_APP = "6a1c3e5f-7b9d-4f2a-8c4e-0d2b4f6a8c0e";
const TENANT = "meridiansearch.onmicrosoft.com";
const minutesAgo = (minutes: number) => new Date(Date.now() - minutes * 60_000).toISOString();

const sharedIntegration = (provider: WorkspaceIntegration["provider"]): WorkspaceIntegration => ({
  provider,
  mode: "SHARED",
  clientId: null,
  tenantId: null,
  secretExpiresOn: null,
  secretSet: false,
  secretHint: null,
  secretSavedAt: null,
  secretSavedBy: null,
  keysCheckedAt: null,
  keysRefusedWith: null,
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
  connectionsThroughApp: 0,
});

const allShared: WorkspaceIntegrations = {
  providers: [sharedIntegration("GOOGLE"), sharedIntegration("MICROSOFT"), sharedIntegration("ZOOM")],
  ownAppsOffered: true,
  recallOffered: true,
};

const googleOwnApp: WorkspaceIntegration = {
  ...sharedIntegration("GOOGLE"),
  mode: "OWN",
  clientId: GOOGLE_APP,
  secretSet: true,
  secretHint: "Xk9a",
  secretSavedAt: "2026-10-12T10:00:00Z",
  secretSavedBy: "Yara Haddad",
  keysCheckedAt: minutesAgo(2),
  updatedAt: "2026-09-12T10:00:00Z",
  connectionsThroughApp: 4,
};

const microsoftOwnApp = (overrides: Partial<WorkspaceIntegration> = {}): WorkspaceIntegration => ({
  ...sharedIntegration("MICROSOFT"),
  mode: "OWN",
  clientId: MICROSOFT_APP,
  tenantId: TENANT,
  secretSet: true,
  secretHint: "Jx4q",
  secretExpiresOn: isoDateIn(300),
  secretSavedAt: "2026-10-12T10:00:00Z",
  secretSavedBy: "Yara Haddad",
  keysCheckedAt: minutesAgo(2),
  updatedAt: "2026-09-12T10:00:00Z",
  connectionsThroughApp: 6,
  ...overrides,
});

const withProvider = (integration: WorkspaceIntegration): WorkspaceIntegrations => ({
  ...allShared,
  providers: allShared.providers.map((shared) => (shared.provider === integration.provider ? integration : shared)),
});

const googleOwn = withProvider(googleOwnApp);

const workspace = { id: "ws-1", name: "Acme", mode: "COMPANY", calendarSync: "RECALL" } as WorkspaceDetail;

function renderPage(path = "/settings/integrations", queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <QueryClientProvider client={queryClient}>
        <ToastProvider>
          <SettingsIntegrationsPage />
        </ToastProvider>
      </QueryClientProvider>
    </MemoryRouter>,
  );
}

const card = (name: string) => screen.findByRole("region", { name });

async function openCard(name: string) {
  const region = await card(name);
  await userEvent.click(within(region).getByRole("button", { name: `Manage ${name}` }));
  return region;
}

async function setUpOwnApp(name: string) {
  const region = await openCard(name);
  await userEvent.click(within(region).getByRole("radio", { name: "Your own app" }));
  return region;
}

function isoDateIn(days: number): string {
  const day = new Date();
  day.setDate(day.getDate() + days);
  return `${day.getFullYear()}-${String(day.getMonth() + 1).padStart(2, "0")}-${String(day.getDate()).padStart(2, "0")}`;
}

function keysRefused(providerError: string) {
  return new ApiRequestError({
    code: "INTEGRATION_KEYS_REFUSED",
    detail: "The provider refused the keys",
    status: 422,
    correlationId: "c1",
    providerError,
  });
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

  it("draws each provider closed, its status in a pill and summed up under its name", async () => {
    vi.mocked(integrationsApi.integrations).mockResolvedValue(googleOwn);
    renderPage();

    const google = await card("Google Workspace");
    expect(google).toHaveAccessibleDescription("Own app · Verified Your own app · acme-app….com · secret ••••Xk9a");
    expect(within(google).getByRole("button", { name: "Manage Google Workspace" })).toHaveAttribute(
      "aria-expanded",
      "false",
    );
    expect(within(google).queryByRole("radiogroup")).not.toBeInTheDocument();

    expect(await card("Microsoft 365")).toHaveAccessibleDescription("Shared app Uncava's shared app");
    expect(await card("Zoom")).toHaveAccessibleDescription(
      "Not set up Not connected yet. Uncava's shared app isn't available here, so use your own.",
    );
  });

  it("opens a card on Manage and closes it again", async () => {
    renderPage();

    const google = await openCard("Google Workspace");
    const close = within(google).getByRole("button", { name: "Close Google Workspace" });
    expect(close).toHaveAttribute("aria-expanded", "true");
    expect(within(google).getByRole("radiogroup", { name: "Google Workspace app" })).toBeInTheDocument();

    await userEvent.click(close);

    expect(within(google).queryByRole("radiogroup")).not.toBeInTheDocument();
  });

  it("gives Microsoft's shared app the admin-consent link to send IT, and says where no shared app exists", async () => {
    renderPage();

    const microsoft = await openCard("Microsoft 365");
    expect(within(microsoft).getByText(/adminconsent\?client_id=uncava-microsoft-client/)).toBeInTheDocument();
    expect(within(microsoft).getByRole("button", { name: "Copy admin consent link" })).toBeInTheDocument();

    const zoom = await openCard("Zoom");
    expect(
      within(zoom).getByText("Uncava's shared Zoom app isn't available yet. Use your own app to connect now."),
    ).toBeInTheDocument();
  });

  it("sets up an own app in the console's order, saving it once the provider accepts the keys", async () => {
    vi.mocked(integrationsApi.updateIntegration).mockResolvedValue(googleOwn);
    renderPage();
    const google = await setUpOwnApp("Google Workspace");

    expect(google).toHaveAccessibleDescription("Not saved yet Your own app, not saved yet");
    const steps = within(google).getAllByRole("listitem").map((step) => step.textContent ?? "");
    expect(steps[0]).toMatch(/^1Register an app in the Google Cloud console/);
    expect(within(google).getByText("Add this redirect URI")).toBeInTheDocument();
    expect(within(google).getByText("https://beta.uncava.com/api/v1/outreach/mailbox/callback")).toBeInTheDocument();
    expect(within(google).getByText("Grant these scopes")).toBeInTheDocument();
    expect(within(google).getByText("https://www.googleapis.com/auth/gmail.send")).toBeInTheDocument();
    expect(within(google).getByText("Paste its keys")).toBeInTheDocument();
    expect(within(google).getByText(/Recall.ai holds a copy of these keys for each connected calendar/)).toBeInTheDocument();
    expect(within(google).queryByLabelText(/^Directory \(tenant\) ID/)).not.toBeInTheDocument();
    expect(within(google).queryByLabelText("Secret expires")).not.toBeInTheDocument();
    expect(integrationsApi.updateIntegration).not.toHaveBeenCalled();

    await userEvent.type(within(google).getByLabelText(/^Client ID/), GOOGLE_APP);
    await userEvent.type(within(google).getByLabelText("Client secret"), "GOCSPX-pasted~secret.value");
    await userEvent.click(within(google).getByRole("button", { name: "Save and verify" }));

    await waitFor(() =>
      expect(integrationsApi.updateIntegration).toHaveBeenCalledWith("GOOGLE", {
        mode: "OWN",
        clientId: GOOGLE_APP,
        clientSecret: "GOCSPX-pasted~secret.value",
        tenantId: undefined,
        secretExpiresOn: null,
      }),
    );
    expect(await screen.findByText("Google Workspace uses your own app. Google accepted the keys")).toBeInTheDocument();
    await waitFor(() => expect(google).toHaveAccessibleDescription(/^Own app · Verified/));
  });

  it("says what the provider refused, and saves nothing", async () => {
    vi.mocked(integrationsApi.updateIntegration).mockRejectedValue(keysRefused("AADSTS7000215"));
    renderPage();
    const microsoft = await setUpOwnApp("Microsoft 365");

    await userEvent.type(within(microsoft).getByLabelText("Application (client) ID"), MICROSOFT_APP);
    await userEvent.type(within(microsoft).getByLabelText(/^Directory \(tenant\) ID/), TENANT);
    await userEvent.type(within(microsoft).getByLabelText("Client secret value"), "Q8~kT2v.Lm9wrong");
    fireEvent.change(within(microsoft).getByLabelText("Secret expires"), { target: { value: isoDateIn(200) } });
    await userEvent.click(within(microsoft).getByRole("button", { name: "Save and verify" }));

    expect(await within(microsoft).findByRole("alert")).toHaveTextContent(
      "Microsoft refused these keys (AADSTS7000215). Nothing was saved.",
    );
    expect(integrationsApi.updateIntegration).toHaveBeenCalledWith(
      "MICROSOFT",
      expect.objectContaining({ clientId: MICROSOFT_APP, tenantId: TENANT, secretExpiresOn: isoDateIn(200) }),
    );
    expect(microsoft).toHaveAccessibleDescription(/^Not saved yet/);
    expect(within(microsoft).getByLabelText("Client secret value")).toHaveValue("Q8~kT2v.Lm9wrong");
  });

  it("refuses a secret pasted with a space, and a Google client ID of another shape, before sending", async () => {
    renderPage();
    const google = await setUpOwnApp("Google Workspace");

    await userEvent.type(within(google).getByLabelText(/^Client ID/), "1234-abc");
    await userEvent.type(within(google).getByLabelText("Client secret"), " GOCSPX-secret ");
    await userEvent.click(within(google).getByRole("button", { name: "Save and verify" }));

    expect(await within(google).findByText("A secret has no spaces. Check you copied the value alone")).toBeInTheDocument();
    expect(within(google).getByText("A Google client ID ends in .apps.googleusercontent.com")).toBeInTheDocument();
    expect(integrationsApi.updateIntegration).not.toHaveBeenCalled();
  });

  it("names a Microsoft app's keys as Entra does, and refuses its Secret ID, a multi-tenant directory and no expiry", async () => {
    renderPage();
    const microsoft = await setUpOwnApp("Microsoft 365");

    await userEvent.type(within(microsoft).getByLabelText("Application (client) ID"), MICROSOFT_APP);
    await userEvent.type(within(microsoft).getByLabelText(/^Directory \(tenant\) ID/), "common");
    await userEvent.type(within(microsoft).getByLabelText("Client secret value"), "8c1d2e3f-0a1b-4c2d-9e8f-7a6b5c4d3e2f");
    await userEvent.click(within(microsoft).getByRole("button", { name: "Save and verify" }));

    expect(await within(microsoft).findByText("That's the Secret ID. Paste the Value column instead")).toBeInTheDocument();
    expect(within(microsoft).getByText("Use your own directory, not common")).toBeInTheDocument();
    expect(within(microsoft).getByText("Enter when the secret expires")).toBeInTheDocument();
    expect(integrationsApi.updateIntegration).not.toHaveBeenCalled();
  });

  it("asks a Microsoft app for its tenant before anything is sent", async () => {
    renderPage();
    const microsoft = await setUpOwnApp("Microsoft 365");

    await userEvent.type(within(microsoft).getByLabelText("Application (client) ID"), MICROSOFT_APP);
    await userEvent.type(within(microsoft).getByLabelText("Client secret value"), "s3cret~value");
    fireEvent.change(within(microsoft).getByLabelText("Secret expires"), { target: { value: isoDateIn(200) } });
    await userEvent.click(within(microsoft).getByRole("button", { name: "Save and verify" }));

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
    const zoom = await setUpOwnApp("Zoom");

    await userEvent.type(within(zoom).getByLabelText("Client ID"), "zoom-app");
    await userEvent.type(within(zoom).getByLabelText("Client secret"), "s3cret");
    await userEvent.click(within(zoom).getByRole("button", { name: "Save and verify" }));

    expect(await within(zoom).findByText("Enter your app's client secret")).toBeInTheDocument();
  });

  it("masks the secret with a Show toggle, and keeps password managers away from it", async () => {
    renderPage();
    const zoom = await setUpOwnApp("Zoom");
    const secret = within(zoom).getByLabelText("Client secret");

    expect(secret).toHaveAttribute("type", "password");
    expect(secret).toHaveAttribute("autocomplete", "off");
    expect(secret).toHaveAttribute("data-1p-ignore", "true");
    expect(secret).toHaveAttribute("data-lpignore", "true");
    expect(secret).toHaveAttribute("data-bwignore", "true");
    expect(secret).toHaveAttribute("spellcheck", "false");

    await userEvent.click(within(zoom).getByRole("button", { name: "Show the secret" }));

    expect(secret).toHaveAttribute("type", "text");
    expect(within(zoom).getByRole("button", { name: "Hide the secret" })).toHaveAttribute("aria-pressed", "true");
  });

  it("shows a saved app as rows, the secret by its last four and who saved it, never the secret itself", async () => {
    vi.mocked(integrationsApi.integrations).mockResolvedValue(googleOwn);
    renderPage();
    const google = await openCard("Google Workspace");

    expect(within(google).getByText(GOOGLE_APP)).toBeInTheDocument();
    expect(within(google).getByRole("button", { name: "Copy client ID" })).toBeInTheDocument();
    expect(within(google).getByText("Saved secret ending Xk9a")).toBeInTheDocument();
    expect(within(google).getByText("••••••••••••Xk9a")).toBeInTheDocument();
    expect(within(google).getByText(/^Ends Xk9a · saved 12 Oct 2026 by Yara Haddad$/)).toBeInTheDocument();
    expect(within(google).queryByLabelText("Client secret")).not.toBeInTheDocument();
    expect(within(google).getByText(/^Google accepted these keys · checked \d+ minutes? ago$/)).toBeInTheDocument();
    expect(within(google).getByText("4 mailboxes connected through this app.")).toBeInTheDocument();
  });

  it("replaces the saved app's secret under the same client ID", async () => {
    const saved = withProvider(microsoftOwnApp());
    vi.mocked(integrationsApi.integrations).mockResolvedValue(saved);
    vi.mocked(integrationsApi.updateIntegration).mockResolvedValue(saved);
    renderPage();
    const microsoft = await openCard("Microsoft 365");

    await userEvent.click(within(microsoft).getByRole("button", { name: "Replace" }));
    expect(within(microsoft).getByText("Replacing ••••••••••••Jx4q. It keeps working until you save.")).toBeInTheDocument();

    await userEvent.type(within(microsoft).getByLabelText("New client secret value"), "N3w~secret.value");
    fireEvent.change(within(microsoft).getByLabelText("New secret expires"), { target: { value: isoDateIn(365) } });
    await userEvent.click(within(microsoft).getByRole("button", { name: "Save and verify" }));

    await waitFor(() =>
      expect(integrationsApi.updateIntegration).toHaveBeenCalledWith("MICROSOFT", {
        mode: "OWN",
        clientId: MICROSOFT_APP,
        clientSecret: "N3w~secret.value",
        tenantId: TENANT,
        secretExpiresOn: isoDateIn(365),
      }),
    );
    expect(await screen.findByText("Secret replaced. Microsoft accepted it")).toBeInTheDocument();
    expect(within(microsoft).queryByLabelText("New client secret value")).not.toBeInTheDocument();
  });

  it("says the saved secret is unchanged when the provider refuses its replacement", async () => {
    vi.mocked(integrationsApi.integrations).mockResolvedValue(googleOwn);
    vi.mocked(integrationsApi.updateIntegration).mockRejectedValue(keysRefused("invalid_client"));
    renderPage();
    const google = await openCard("Google Workspace");

    await userEvent.click(within(google).getByRole("button", { name: "Replace" }));
    await userEvent.type(within(google).getByLabelText("New client secret"), "GOCSPX-wrong");
    await userEvent.click(within(google).getByRole("button", { name: "Save and verify" }));

    expect(await within(google).findByRole("alert")).toHaveTextContent(
      "Google refused these keys (invalid_client). The saved secret is unchanged.",
    );
  });

  it("asks the provider again on Verify again, and turns the card red when it refuses", async () => {
    vi.mocked(integrationsApi.integrations).mockResolvedValue(withProvider(microsoftOwnApp()));
    vi.mocked(integrationsApi.verifyIntegration).mockResolvedValue(
      withProvider(microsoftOwnApp({ keysCheckedAt: minutesAgo(0), keysRefusedWith: "AADSTS7000215" })),
    );
    renderPage();
    const microsoft = await openCard("Microsoft 365");
    expect(microsoft).toHaveAccessibleDescription(/^Own app · Verified/);

    await userEvent.click(within(microsoft).getByRole("button", { name: "Verify again" }));

    await waitFor(() => expect(microsoft).toHaveAccessibleDescription(/^Keys refused/));
    expect(integrationsApi.verifyIntegration).toHaveBeenCalledWith("MICROSOFT");
    expect(
      within(microsoft).getByText(
        "Microsoft refused these keys (AADSTS7000215: the secret is wrong, or was deleted in Entra). Replace it with a current one; until then mail and calendars through this app wait.",
      ),
    ).toBeInTheDocument();
    expect(await screen.findByText("Microsoft refused these keys")).toBeInTheDocument();
  });

  it("warns from 30 days before an own app's secret expires, and says when it has", async () => {
    vi.mocked(integrationsApi.integrations).mockResolvedValue(
      withProvider(microsoftOwnApp({ secretExpiresOn: isoDateIn(10) })),
    );
    const { unmount } = renderPage();

    let microsoft = await openCard("Microsoft 365");
    expect(microsoft).toHaveAccessibleDescription(/^Expires in 10 days/);
    expect(within(microsoft).getByText(/The client secret expires on .*, in 10 days\./)).toBeInTheDocument();
    expect(within(microsoft).getByText(/ · 10 days left$/)).toBeInTheDocument();
    unmount();

    vi.mocked(integrationsApi.integrations).mockResolvedValue(
      withProvider(microsoftOwnApp({ secretExpiresOn: isoDateIn(-2) })),
    );
    renderPage();
    microsoft = await openCard("Microsoft 365");
    expect(microsoft).toHaveAccessibleDescription(/^Secret expired/);
    expect(within(microsoft).getByText(/The client secret expired on/)).toBeInTheDocument();
  });

  it("says nothing of an expiry still more than 30 days away", async () => {
    vi.mocked(integrationsApi.integrations).mockResolvedValue(withProvider(microsoftOwnApp()));
    renderPage();

    const microsoft = await openCard("Microsoft 365");
    expect(microsoft).toHaveAccessibleDescription(/^Own app · Verified/);
    expect(within(microsoft).queryByText(/The client secret expires/)).not.toBeInTheDocument();
  });

  it("says how many mailboxes will connect again before a different app replaces the one in use", async () => {
    vi.mocked(integrationsApi.integrations).mockResolvedValue(googleOwn);
    renderPage();
    const google = await openCard("Google Workspace");

    await userEvent.click(within(google).getByRole("button", { name: "Use a different app" }));

    expect(within(google).getByRole("status")).toHaveTextContent(
      "4 mailboxes connected through this app. Saving a different app asks each of them to connect again, once.",
    );
    expect(within(google).getByLabelText(/^Client ID/)).toHaveValue("");

    await userEvent.click(within(google).getByRole("button", { name: "Cancel" }));

    expect(within(google).getByText("Saved secret ending Xk9a")).toBeInTheDocument();
  });

  it("confirms before returning a saved own app to the shared one, counting who must connect again", async () => {
    vi.mocked(integrationsApi.integrations).mockResolvedValue(googleOwn);
    vi.mocked(integrationsApi.returnToSharedApp).mockResolvedValue(allShared);
    renderPage();
    const google = await openCard("Google Workspace");

    await userEvent.click(within(google).getByRole("radio", { name: "Shared app" }));
    const dialog = await screen.findByRole("dialog", { name: "Return Google Workspace to the shared app?" });
    expect(
      within(dialog).getByText(/deleted from Uncava, and from the copies Recall.ai holds for your team's calendars\./),
    ).toBeInTheDocument();
    expect(within(dialog).getByText("4 mailboxes are connected through your app.")).toBeInTheDocument();
    expect(integrationsApi.returnToSharedApp).not.toHaveBeenCalled();

    await userEvent.click(within(dialog).getByRole("button", { name: "Use the shared app" }));

    await waitFor(() => expect(integrationsApi.returnToSharedApp).toHaveBeenCalledWith("GOOGLE"));
    expect(
      await screen.findByText("Google Workspace uses the shared app. 4 mailboxes were asked to connect again"),
    ).toBeInTheDocument();
    await waitFor(() => expect(google).toHaveAccessibleDescription("Shared app Uncava's shared app"));
  });

  it("offers the return from the saved app's own foot too", async () => {
    vi.mocked(integrationsApi.integrations).mockResolvedValue(googleOwn);
    renderPage();
    const google = await openCard("Google Workspace");

    await userEvent.click(within(google).getByRole("button", { name: "Return to the shared app" }));

    expect(await screen.findByRole("dialog", { name: "Return Google Workspace to the shared app?" })).toBeInTheDocument();
  });

  it("redraws a provider another admin returned to the shared app when the page reads again", async () => {
    vi.mocked(integrationsApi.integrations).mockResolvedValueOnce(googleOwn).mockResolvedValue(allShared);
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    renderPage("/settings/integrations", queryClient);
    const google = await openCard("Google Workspace");
    expect(within(google).getByRole("radio", { name: "Your own app" })).toHaveAttribute("aria-checked", "true");

    await queryClient.invalidateQueries({ queryKey: integrationsApi.INTEGRATIONS_KEY });

    await waitFor(() =>
      expect(within(google).getByRole("radio", { name: "Shared app" })).toHaveAttribute("aria-checked", "true"),
    );
    expect(google).toHaveAccessibleDescription("Shared app Uncava's shared app");
    expect(within(google).queryByText("Saved secret ending Xk9a")).not.toBeInTheDocument();
  });

  it("explains a workspace that cannot store an own app's keys rather than letting a save fail", async () => {
    vi.mocked(integrationsApi.integrations).mockResolvedValue({ ...allShared, ownAppsOffered: false });
    renderPage();
    const google = await setUpOwnApp("Google Workspace");

    expect(within(google).getByRole("alert")).toHaveTextContent("can't be stored for your workspace");
    expect(within(google).getByRole("button", { name: "Save and verify" })).toBeDisabled();
  });

  it("records Microsoft's admin-consent return once and opens its card on the approval", async () => {
    vi.mocked(integrationsApi.recordMicrosoftAdminConsent).mockResolvedValue(
      withProvider({
        ...sharedIntegration("MICROSOFT"),
        adminConsentedAt: "2026-10-03T07:00:00Z",
        adminConsentTenantId: "t-1",
      }),
    );
    renderPage("/settings/integrations?admin_consent=True&tenant=t-1&state=signed-state");

    const microsoft = await card("Microsoft 365");
    expect(within(microsoft).getByRole("button", { name: "Close Microsoft 365" })).toHaveAttribute("aria-expanded", "true");
    expect(await within(microsoft).findByText(/Approved for your organisation on/)).toBeVisible();
    expect(integrationsApi.recordMicrosoftAdminConsent).toHaveBeenCalledTimes(1);
    expect(integrationsApi.recordMicrosoftAdminConsent).toHaveBeenCalledWith("t-1", "signed-state");
  });

  it("says so when Microsoft returns without an approval, and records nothing", async () => {
    renderPage("/settings/integrations?error=access_denied&error_description=declined");

    expect(await screen.findByText(/Microsoft did not approve Uncava/)).toBeInTheDocument();
    expect(integrationsApi.recordMicrosoftAdminConsent).not.toHaveBeenCalled();
  });
});
