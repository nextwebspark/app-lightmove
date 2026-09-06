import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import { ApiRequestError } from "../../../lib/apiClient";
import * as workspaceApi from "../api/workspaceApi";
import { InviteModal } from "./InviteModal";

vi.mock("../api/workspaceApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/workspaceApi")>()),
  invite: vi.fn(),
}));

const refusal = (code: string, detail: string, fieldErrors?: Record<string, string>) =>
  new ApiRequestError({ code, detail, status: 400, correlationId: "c1", fieldErrors });

const wrap = (children: ReactNode) => (
  <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
    <ToastProvider>{children}</ToastProvider>
  </QueryClientProvider>
);

const open = () => render(wrap(<InviteModal open onClose={vi.fn()} />));

beforeEach(() => {
  vi.mocked(workspaceApi.invite).mockReset();
});

/**
 * A rejected address belongs under the address, not in a banner reading "One or more fields are
 * invalid" with nothing to point at.
 */
describe("InviteModal — where a refusal is reported", () => {
  it("puts the empty-field message on the email field", async () => {
    const user = userEvent.setup();
    open();

    await user.click(screen.getByRole("button", { name: "Send invite" }));

    expect(screen.getByText("Enter an email address")).toBeInTheDocument();
    expect(workspaceApi.invite).not.toHaveBeenCalled();
  });

  // The endpoint takes a list, so the server attributes the failure to the row, not to `email`.
  it("routes the server's per-row validation message to the email field", async () => {
    vi.mocked(workspaceApi.invite).mockRejectedValue(
      refusal("VALIDATION_FAILED", "One or more fields are invalid", {
        "requests[0].email": "That doesn't look like a valid email",
      }),
    );
    const user = userEvent.setup();
    open();

    await user.type(screen.getByPlaceholderText("colleague@firm.com"), "not-an-email@");
    await user.click(screen.getByRole("button", { name: "Send invite" }));

    expect(await screen.findByText("That doesn't look like a valid email")).toBeInTheDocument();
    expect(screen.queryByText("One or more fields are invalid")).not.toBeInTheDocument();
  });

  it("puts a domain refusal on the email field too", async () => {
    vi.mocked(workspaceApi.invite).mockRejectedValue(
      refusal("EMAIL_NOT_WORK_ADDRESS", "Consumer email domain"),
    );
    const user = userEvent.setup();
    open();

    await user.type(screen.getByPlaceholderText("colleague@firm.com"), "someone@gmail.com");
    await user.click(screen.getByRole("button", { name: "Send invite" }));

    expect(
      await screen.findByText("Use your work email — the domain identifies your organization."),
    ).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  // The error used to outlive the edit that fixed it: it was cleared only by the next submit.
  it("clears the email error as soon as the address is edited", async () => {
    const user = userEvent.setup();
    open();

    const field = screen.getByPlaceholderText("colleague@firm.com");
    await user.click(screen.getByRole("button", { name: "Send invite" }));
    expect(screen.getByText("Enter an email address")).toBeInTheDocument();

    await user.type(field, "c");

    expect(screen.queryByText("Enter an email address")).not.toBeInTheDocument();
    expect(field).not.toHaveAttribute("aria-invalid", "true");
  });

  // FormError is still the right home for a failure no single input owns.
  it("keeps a form-level refusal in the banner", async () => {
    vi.mocked(workspaceApi.invite).mockRejectedValue(
      refusal("FORBIDDEN", "You don't have permission to do this."),
    );
    const user = userEvent.setup();
    open();

    await user.type(screen.getByPlaceholderText("colleague@firm.com"), "colleague@firm.com");
    await user.click(screen.getByRole("button", { name: "Send invite" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "You don't have permission to do this.",
    );
  });
});
