import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import * as clientsApi from "../api/clientsApi";
import { ClientsPage } from "./ClientsPage";

vi.mock("../api/clientsApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/clientsApi")>()),
  clients: vi.fn(),
}));

/**
 * A refused read is not an empty registry. The list falls back to [] on any failure, so rendering the
 * empty state over a 403 told a caller the firm has no clients — a count they were never allowed to
 * read — and handed them a create button whose every call the server refuses.
 */
describe("ClientsPage — a refused read", () => {
  const renderPage = () =>
    render(
      <MemoryRouter>
        <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
          <ToastProvider>
            <ClientsPage />
          </ToastProvider>
        </QueryClientProvider>
      </MemoryRouter>,
    );

  beforeEach(() => vi.resetAllMocks());

  it("says the registry could not be loaded, and offers nothing", async () => {
    vi.mocked(clientsApi.clients).mockRejectedValue(new Error("403"));

    renderPage();

    expect(await screen.findByText("Couldn't load the client registry")).toBeInTheDocument();
    expect(screen.queryByText("Add your first client")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /new client/i })).not.toBeInTheDocument();
    expect(screen.queryByText(/0 clients/)).not.toBeInTheDocument();
  });

  it("still offers the empty state when the registry is genuinely empty", async () => {
    vi.mocked(clientsApi.clients).mockResolvedValue([]);

    renderPage();

    expect(await screen.findByText("Add your first client")).toBeInTheDocument();
  });
});
