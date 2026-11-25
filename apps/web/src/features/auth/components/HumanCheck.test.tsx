import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import * as authApi from "../api/authApi";
import { useHumanCheck } from "./HumanCheck";

vi.mock("../api/authApi");

function Form() {
  const humanCheck = useHumanCheck("login");
  const [taken, setTaken] = useState<string>("not yet");
  return (
    <>
      {humanCheck.widget}
      <button type="button" onClick={async () => setTaken(String(await humanCheck.takeToken()))}>
        Submit
      </button>
      <output>{taken}</output>
    </>
  );
}

const renderForm = () =>
  render(
    <QueryClientProvider client={new QueryClient()}>
      <Form />
    </QueryClientProvider>,
  );

const turnstileScript = () =>
  document.head.querySelector('script[src^="https://challenges.cloudflare.com/turnstile/"]');

/** The check is the server's to switch on: without a site key nothing loads and nothing is sent. */
describe("useHumanCheck", () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  afterEach(() => {
    delete window.turnstile;
  });

  it("draws nothing, loads nothing and hands no token while the check is off", async () => {
    vi.mocked(authApi.providers).mockResolvedValue({ providers: ["google"], humanCheckSiteKey: null });
    const user = userEvent.setup();
    renderForm();
    await waitFor(() => expect(authApi.providers).toHaveBeenCalled());

    await user.click(screen.getByRole("button", { name: "Submit" }));

    expect(await screen.findByText("undefined")).toBeInTheDocument();
    expect(screen.queryByTestId("human-check")).not.toBeInTheDocument();
    expect(turnstileScript()).toBeNull();
  });

  it("renders the widget for its form and hands each token out once", async () => {
    const renderWidget = vi.fn((_container: HTMLElement, options: { callback: (token: string) => void }) => {
      options.callback("token-1");
      return "widget-1";
    });
    const reset = vi.fn();
    window.turnstile = { render: renderWidget, reset, remove: vi.fn() };
    vi.mocked(authApi.providers).mockResolvedValue({ providers: [], humanCheckSiteKey: "site-key" });
    const user = userEvent.setup();
    renderForm();

    await waitFor(() => expect(renderWidget).toHaveBeenCalled());
    expect(renderWidget.mock.calls[0][1]).toMatchObject({
      sitekey: "site-key",
      action: "login",
      appearance: "interaction-only",
    });

    await user.click(screen.getByRole("button", { name: "Submit" }));

    expect(await screen.findByText("token-1")).toBeInTheDocument();
    expect(reset).toHaveBeenCalledWith("widget-1");
  });
});
