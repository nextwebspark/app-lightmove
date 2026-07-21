import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import * as companiesApi from "../api/companiesApi";
import type { CompanySearchResult } from "../api/types";
import { companyKeyOf } from "../lib/companyKey";
import { CompanySearchCombobox } from "./CompanySearchCombobox";

vi.mock("../api/companiesApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/companiesApi")>()),
  searchCompanies: vi.fn(),
}));

const acme: CompanySearchResult = {
  source: "test",
  sourceId: "acme",
  name: "Acme Retail",
  domain: "acme.example",
  slogan: "Everything store",
  logo: null,
  hqCity: "Dubai",
  hqCountry: "AE",
  primaryIndustry: "Retail",
  employeeCount: 500,
};

const globex: CompanySearchResult = {
  ...acme,
  sourceId: "globex",
  name: "Globex Energy",
};

const renderBox = (excludedKeys = new Set<string>(), onPick = vi.fn()) => {
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <CompanySearchCombobox
        listId="test-typeahead"
        excludedKeys={excludedKeys}
        browseSectors={["Retail"]}
        browseOrder="revenue_desc"
        onPick={onPick}
      />
    </QueryClientProvider>,
  );
  return onPick;
};

describe("CompanySearchCombobox — the server-backed company picker", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(companiesApi.searchCompanies).mockResolvedValue({ companies: [acme, globex] });
  });

  it("browses on focus: the empty box already fetches, scoped to the given sectors and order", async () => {
    renderBox();

    await userEvent.click(screen.getByLabelText("Search companies"));
    await screen.findAllByRole("option");

    expect(companiesApi.searchCompanies).toHaveBeenCalledWith("", ["Retail"], "revenue_desc");
  });

  it("debounces typing: the browse fires on focus, then one settled query", async () => {
    renderBox();

    await userEvent.type(screen.getByLabelText("Search companies"), "acme");
    await waitFor(() =>
      expect(
        vi.mocked(companiesApi.searchCompanies).mock.calls.some(([q]) => q === "acme"),
      ).toBe(true),
    );

    // No intermediate keystroke ("a", "ac", "acm") ever reached the server.
    const queries = vi.mocked(companiesApi.searchCompanies).mock.calls.map(([q]) => q);
    expect(queries.every((q) => q === "" || q === "acme")).toBe(true);
  });

  it("picks with the keyboard: arrow down to the second match, Enter commits it", async () => {
    const onPick = renderBox();
    const input = screen.getByLabelText("Search companies");

    await userEvent.type(input, "en");
    await screen.findAllByRole("option");
    await userEvent.type(input, "{ArrowDown}{Enter}");

    expect(onPick).toHaveBeenCalledWith(globex);
    // The input clears for the next search.
    expect(input).toHaveValue("");
  });

  it("filters out excluded keys — companies already on a list are not offered", async () => {
    renderBox(new Set([companyKeyOf(acme)]));

    await userEvent.type(screen.getByLabelText("Search companies"), "retail");
    const options = await screen.findAllByRole("option");

    expect(options).toHaveLength(1);
    expect(options[0]).toHaveTextContent("Globex Energy");
  });

  it("says so when nothing matches", async () => {
    vi.mocked(companiesApi.searchCompanies).mockResolvedValue({ companies: [] });
    renderBox();

    await userEvent.type(screen.getByLabelText("Search companies"), "zz");

    expect(await screen.findByText("No companies found.")).toBeInTheDocument();
    expect(screen.queryByRole("option")).not.toBeInTheDocument();
  });
});
