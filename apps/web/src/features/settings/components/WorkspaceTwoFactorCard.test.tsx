import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import { ApiRequestError } from "../../../lib/apiClient";
import * as twoFactorApi from "../api/workspaceTwoFactorApi";
import type { WorkspaceTwoFactor } from "../api/types";
import { WorkspaceTwoFactorCard } from "./WorkspaceTwoFactorCard";

vi.mock("../api/workspaceTwoFactorApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/workspaceTwoFactorApi")>()),
  workspaceTwoFactor: vi.fn(),
  changeRequireMfa: vi.fn(),
  resetMemberTwoFactor: vi.fn(),
}));

const OVERVIEW: WorkspaceTwoFactor = {
  offered: true,
  required: false,
  members: [
    {
      memberId: "m1",
      userId: "u1",
      fullName: "Sara G",
      email: "sara@firm.example",
      avatarUrl: null,
      roles: ["ADMIN"],
      enabled: true,
      enabledAt: "2026-10-01T09:00:00Z",
      resettable: false,
    },
    {
      memberId: "m2",
      userId: "u2",
      fullName: "Omar K",
      email: "omar@firm.example",
      avatarUrl: null,
      roles: ["MEMBER"],
      enabled: true,
      enabledAt: "2026-10-02T09:00:00Z",
      resettable: true,
    },
    {
      memberId: "m3",
      userId: "u3",
      fullName: "Rana Client",
      email: "rana@client.example",
      avatarUrl: null,
      roles: ["CLIENT"],
      enabled: false,
      enabledAt: null,
      resettable: false,
    },
  ],
};

function renderCard() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <WorkspaceTwoFactorCard />
      </ToastProvider>
    </QueryClientProvider>,
  );
}

describe("WorkspaceTwoFactorCard", () => {
  beforeEach(() => {
    vi.mocked(twoFactorApi.workspaceTwoFactor).mockResolvedValue(OVERVIEW);
  });

  it("lists who has two-factor on, clients included, and offers a reset only where one is allowed", async () => {
    renderCard();

    expect(await screen.findByText("2 of 3 members have it on")).toBeInTheDocument();
    expect(screen.getByText("Rana Client")).toBeInTheDocument();
    expect(screen.getAllByRole("button", { name: "Reset" })).toHaveLength(1);
  });

  it("turns the requirement on", async () => {
    vi.mocked(twoFactorApi.changeRequireMfa).mockResolvedValue({} as never);
    const user = userEvent.setup();
    renderCard();

    const toggle = await screen.findByRole("switch", { name: "Require two-factor authentication" });
    await waitFor(() => expect(toggle).toBeEnabled());
    await user.click(toggle);

    expect(twoFactorApi.changeRequireMfa).toHaveBeenCalledWith(true);
    expect(await screen.findByText("Two-factor authentication is now required")).toBeInTheDocument();
  });

  it("says why the requirement could not be turned on", async () => {
    vi.mocked(twoFactorApi.changeRequireMfa).mockRejectedValue(
      new ApiRequestError({ code: "MFA_ENABLE_YOURS_FIRST", detail: "x", status: 409, correlationId: "c1" }),
    );
    const user = userEvent.setup();
    renderCard();

    const toggle = await screen.findByRole("switch", { name: "Require two-factor authentication" });
    await waitFor(() => expect(toggle).toBeEnabled());
    await user.click(toggle);

    expect(
      await screen.findByText("Turn on two-factor authentication for your own account first."),
    ).toBeInTheDocument();
  });

  it("resets a member's two-factor after asking", async () => {
    vi.mocked(twoFactorApi.resetMemberTwoFactor).mockResolvedValue(undefined);
    const user = userEvent.setup();
    renderCard();

    await user.click(await screen.findByRole("button", { name: "Reset" }));
    const dialog = await screen.findByRole("dialog", { name: /reset two-factor/i });
    expect(within(dialog).getByText(/Omar K will be signed out everywhere/)).toBeInTheDocument();
    await user.click(within(dialog).getByRole("button", { name: "Reset" }));

    expect(twoFactorApi.resetMemberTwoFactor).toHaveBeenCalledWith("m2");
    expect(await screen.findByText("Two-factor authentication reset for Omar K")).toBeInTheDocument();
  });

  it("cannot require it where the deployment does not offer it", async () => {
    vi.mocked(twoFactorApi.workspaceTwoFactor).mockResolvedValue({ ...OVERVIEW, offered: false });
    renderCard();

    expect(await screen.findByText("Not available yet")).toBeInTheDocument();
    expect(screen.getByRole("switch", { name: "Require two-factor authentication" })).toBeDisabled();
  });
});
