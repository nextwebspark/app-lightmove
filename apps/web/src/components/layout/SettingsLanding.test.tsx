import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import * as billingApi from "../../features/billing/api/billingApi";
import { aBilling } from "../../features/billing/test/fixtures";
import * as dataProtectionApi from "../../features/candidates/api/dataProtectionApi";
import { aUser } from "../../test/fixtures/user";
import { SettingsLanding } from "./SettingsLayout";
import { rememberSettingsSection, settingsLandingOf } from "./settingsSections";

vi.mock("../../features/candidates/api/dataProtectionApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../features/candidates/api/dataProtectionApi")>()),
  getDataProtectionConfig: vi.fn(),
}));

vi.mock("../../features/billing/api/billingApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../features/billing/api/billingApi")>()),
  getBilling: vi.fn(),
}));

const admin = aUser();
vi.mock("../../features/auth/AuthProvider", () => ({ useAuth: () => ({ user: admin }) }));

function renderLanding() {
  return render(
    <MemoryRouter initialEntries={["/settings"]}>
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <Routes>
          <Route path="/settings" element={<SettingsLanding />} />
          <Route path="/settings/data" element={<p>Data page</p>} />
          <Route path="/settings/general" element={<p>General page</p>} />
          <Route path="/settings/billing" element={<p>Billing page</p>} />
        </Routes>
      </QueryClientProvider>
    </MemoryRouter>,
  );
}

describe("the /settings landing and Data & retention", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    localStorage.clear();
    vi.mocked(billingApi.getBilling).mockResolvedValue(aBilling());
    rememberSettingsSection(admin, "/settings/data", { dataProtection: true });
  });

  it("lands on a remembered Data & retention only while the deployment offers it", () => {
    expect(settingsLandingOf(admin, { dataProtection: true })).toBe("/settings/data");
    expect(settingsLandingOf(admin, { dataProtection: false })).toBe("/settings/general");
    expect(settingsLandingOf(admin)).toBe("/settings/general");
  });

  it("waits for the config, then lands on the remembered section", async () => {
    vi.mocked(dataProtectionApi.getDataProtectionConfig).mockResolvedValue({ enabled: true });
    renderLanding();

    expect(await screen.findByText("Data page")).toBeInTheDocument();
  });

  it("falls back to General where the deployment holds the controls back", async () => {
    vi.mocked(dataProtectionApi.getDataProtectionConfig).mockResolvedValue({ enabled: false });
    renderLanding();

    expect(await screen.findByText("General page")).toBeInTheDocument();
  });
});

describe("the /settings landing and Billing", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    localStorage.clear();
    vi.mocked(dataProtectionApi.getDataProtectionConfig).mockResolvedValue({ enabled: false });
    rememberSettingsSection(admin, "/settings/billing", { billing: true });
  });

  it("lands on a remembered Billing while the deployment bills", async () => {
    vi.mocked(billingApi.getBilling).mockResolvedValue(aBilling());
    renderLanding();

    expect(await screen.findByText("Billing page")).toBeInTheDocument();
  });

  it("falls back to General where billing is switched off", async () => {
    vi.mocked(billingApi.getBilling).mockResolvedValue(aBilling({ enforced: false }));
    renderLanding();

    expect(await screen.findByText("General page")).toBeInTheDocument();
  });
});
