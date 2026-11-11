import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "../../../lib/apiClient";
import * as authApi from "../../auth/api/authApi";
import type { AccountDeletionPreview } from "../../auth/api/types";
import { rememberAccountDeleted } from "../../auth/accountDeletedNotice";
import { DeleteAccountModal } from "./DeleteAccountModal";

vi.mock("../../auth/api/authApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../auth/api/authApi")>()),
  accountDeletionPreview: vi.fn(),
  deleteAccount: vi.fn(),
}));

const forgetDeletedAccount = vi.fn();
const switchWorkspace = vi.fn();
vi.mock("../../auth/AuthProvider", () => ({
  useAuth: () => ({
    user: { id: "u1", email: "omar@firm.example", workspace: { id: "w1" } },
    forgetDeletedAccount,
    switchWorkspace,
    adoptRestoredSession: vi.fn(),
    completeSecondFactor: vi.fn(),
  }),
}));

vi.mock("../../auth/accountDeletedNotice", () => ({ rememberAccountDeleted: vi.fn() }));

const startOAuthSignIn = vi.fn((_providerId: string, _handlers: unknown) => () => {});
vi.mock("../../auth/oauthPopup", () => ({
  startOAuthSignIn: (providerId: string, handlers: unknown) => startOAuthSignIn(providerId, handlers),
}));

const preview = (overrides: Partial<AccountDeletionPreview> = {}): AccountDeletionPreview => ({
  workspaces: [
    { id: "w1", name: "Alpha Partners" },
    { id: "w2", name: "Beta Search" },
  ],
  soleAdminOf: [],
  soleLeadOf: [],
  proof: "PASSWORD",
  codeRequired: false,
  signedInRecently: false,
  signInProviders: [],
  ...overrides,
});

function LoginLanding() {
  return <div>landed</div>;
}

const renderModal = () =>
  render(
    <MemoryRouter initialEntries={["/settings/profile"]}>
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <Routes>
          <Route path="/settings/profile" element={<DeleteAccountModal onClose={vi.fn()} />} />
          <Route path="/login" element={<LoginLanding />} />
          <Route path="/team" element={<div>the roster</div>} />
        </Routes>
      </QueryClientProvider>
    </MemoryRouter>,
  );

describe("DeleteAccountModal", () => {
  beforeEach(() => {
    vi.mocked(authApi.accountDeletionPreview).mockReset();
    vi.mocked(authApi.deleteAccount).mockReset();
    forgetDeletedAccount.mockReset();
    switchWorkspace.mockReset();
    startOAuthSignIn.mockClear();
  });

  it("names every workspace left, and deletes with the password, landing on sign-in", async () => {
    vi.mocked(authApi.accountDeletionPreview).mockResolvedValue(preview());
    vi.mocked(authApi.deleteAccount).mockResolvedValue();
    const user = userEvent.setup();
    renderModal();

    expect(await screen.findByText(/You leave Alpha Partners and Beta Search now/)).toBeInTheDocument();
    const confirm = screen.getByRole("button", { name: "Delete my account" });
    expect(confirm).toBeDisabled();

    await user.type(screen.getByPlaceholderText("Enter your password to confirm"), "secret123");
    await user.click(confirm);

    expect(authApi.deleteAccount).toHaveBeenCalledWith({ currentPassword: "secret123", code: undefined });
    expect(rememberAccountDeleted).toHaveBeenCalled();
    expect(forgetDeletedAccount).toHaveBeenCalled();
    expect(await screen.findByText("landed")).toBeInTheDocument();
  });

  it("puts a wrong password under its field and keeps the session", async () => {
    vi.mocked(authApi.accountDeletionPreview).mockResolvedValue(preview());
    vi.mocked(authApi.deleteAccount).mockRejectedValue(
      new ApiRequestError({
        code: "CURRENT_PASSWORD_INVALID",
        detail: "",
        status: 400,
        correlationId: "c1",
        fieldErrors: { currentPassword: "That is not your current password" },
      }),
    );
    const user = userEvent.setup();
    renderModal();

    await user.type(await screen.findByPlaceholderText("Enter your password to confirm"), "wrong1");
    await user.click(screen.getByRole("button", { name: "Delete my account" }));

    expect(await screen.findByText("That is not your current password")).toBeInTheDocument();
    expect(forgetDeletedAccount).not.toHaveBeenCalled();
  });

  it("asks for a code too where two-factor is on", async () => {
    vi.mocked(authApi.accountDeletionPreview).mockResolvedValue(preview({ codeRequired: true }));
    vi.mocked(authApi.deleteAccount).mockResolvedValue();
    const user = userEvent.setup();
    renderModal();

    await user.type(await screen.findByPlaceholderText("Enter your password to confirm"), "secret123");
    expect(screen.getByRole("button", { name: "Delete my account" })).toBeDisabled();
    await user.type(screen.getByPlaceholderText("123456 or ABCD-EFGH-IJKL-MNOP"), " 123456 ");
    await user.click(screen.getByRole("button", { name: "Delete my account" }));

    expect(authApi.deleteAccount).toHaveBeenCalledWith({ currentPassword: "secret123", code: "123456" });
  });

  it("stops the only admin, naming the workspace, and offers the roster", async () => {
    vi.mocked(authApi.accountDeletionPreview).mockResolvedValue(
      preview({ soleAdminOf: [{ id: "w1", name: "Alpha Partners" }] }),
    );
    const user = userEvent.setup();
    renderModal();

    expect(await screen.findByRole("dialog", { name: "Hand over first" })).toBeInTheDocument();
    expect(screen.getByText("Alpha Partners")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Delete my account" })).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Make an admin" }));
    expect(switchWorkspace).not.toHaveBeenCalled();
    expect(await screen.findByText("the roster")).toBeInTheDocument();
  });

  it("names the positions only this person leads", async () => {
    vi.mocked(authApi.accountDeletionPreview).mockResolvedValue(
      preview({
        soleLeadOf: [{ workspaceId: "w2", workspaceName: "Beta Search", projectId: "p1", title: "Group CFO" }],
      }),
    );
    renderModal();

    expect(await screen.findByText("Group CFO")).toBeInTheDocument();
    expect(screen.getByText("Beta Search")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Seat a lead" })).toBeInTheDocument();
  });

  it("asks an account with no password to sign in again with its provider", async () => {
    vi.mocked(authApi.accountDeletionPreview).mockResolvedValue(
      preview({ proof: "RECENT_SIGN_IN", signInProviders: ["google"] }),
    );
    const user = userEvent.setup();
    renderModal();

    await user.click(await screen.findByRole("button", { name: /Sign in with Google again/ }));
    expect(startOAuthSignIn).toHaveBeenCalledWith("google", expect.anything());
    expect(screen.getByRole("button", { name: "Delete my account" })).toBeDisabled();
  });

  it("lets an account that just signed in delete without a password", async () => {
    vi.mocked(authApi.accountDeletionPreview).mockResolvedValue(
      preview({ proof: "RECENT_SIGN_IN", signedInRecently: true, signInProviders: ["google"] }),
    );
    vi.mocked(authApi.deleteAccount).mockResolvedValue();
    const user = userEvent.setup();
    renderModal();

    expect(await screen.findByText(/Signed in again as omar@firm.example just now/)).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Delete my account" }));
    expect(authApi.deleteAccount).toHaveBeenCalledWith({});
  });
});
