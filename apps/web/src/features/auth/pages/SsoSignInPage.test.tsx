import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "../../../lib/apiClient";
import * as authApi from "../api/authApi";
import { SsoSignInPage } from "./SsoSignInPage";

vi.mock("../api/authApi");

const NOT_SET_UP = /We couldn.t find an SSO account for that email/;

const notConfigured = () =>
  new ApiRequestError({ code: "SSO_NOT_CONFIGURED", detail: "detail", status: 404, correlationId: "ref-1" });

/** Until single sign-on ships, the screen's one promise is that every address reads the same. */
describe("SsoSignInPage", () => {
  const renderPage = (email?: string) =>
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter initialEntries={[{ pathname: "/login/sso", state: email ? { email } : undefined }]}>
          <SsoSignInPage />
        </MemoryRouter>
      </QueryClientProvider>,
    );

  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(authApi.providers).mockResolvedValue({ providers: [] });
    vi.mocked(authApi.discoverSso).mockRejectedValue(notConfigured());
  });

  it("gives every address the same answer", async () => {
    const user = userEvent.setup();
    renderPage();
    // Held once: an emptied field shows its error inside the label, which then no longer reads "Work email" alone.
    const email = screen.getByLabelText("Work email");

    await user.type(email, "yara@firm.example");
    await user.click(screen.getByRole("button", { name: "Continue" }));
    const firstAnswer = (await screen.findByRole("alert")).textContent;

    await user.clear(email);
    await user.type(email, "nobody@unknown.example");
    await user.click(screen.getByRole("button", { name: "Continue" }));

    expect(await screen.findByText(NOT_SET_UP)).toBeInTheDocument();
    expect(screen.getByRole("alert").textContent).toBe(firstAnswer);
    expect(authApi.discoverSso).toHaveBeenCalledWith("nobody@unknown.example");
  });

  it("asks nothing of the server until the address is valid", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.type(screen.getByLabelText("Work email"), "not-an-address");
    await user.click(screen.getByRole("button", { name: "Continue" }));

    expect(await screen.findByText("That doesn't look like a valid email")).toBeInTheDocument();
    expect(authApi.discoverSso).not.toHaveBeenCalled();
    expect(screen.queryByText(NOT_SET_UP)).not.toBeInTheDocument();
  });

  it("starts with the address carried over from sign-in, and carries it back", async () => {
    renderPage("yara@firm.example");

    expect(screen.getByLabelText("Work email")).toHaveValue("yara@firm.example");
    expect(screen.getByRole("link", { name: "Sign in with email" })).toHaveAttribute("href", "/login");
  });

  it("names the providers the server reports on the way back", async () => {
    vi.mocked(authApi.providers).mockResolvedValue({ providers: ["linkedin", "google"] });
    renderPage();

    expect(await screen.findByRole("link", { name: "Sign in with LinkedIn, Google or email" })).toBeInTheDocument();
  });
});
