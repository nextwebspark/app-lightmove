import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import * as companiesApi from "../../strategy/api/companiesApi";
import type { CompanySuggestion } from "../../strategy/api/types";
import { CompanyPicker, createPayloadFor, type CompanyPick } from "./CompanyPicker";

vi.mock("../../strategy/api/companiesApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../strategy/api/companiesApi")>()),
  searchCompanies: vi.fn(),
}));

const MERIDIAN: CompanySuggestion = {
  apolloAccountId: "apollo-1",
  companyName: "Meridian Energy Group",
  industry: "oil & energy",
  companyCity: "Abu Dhabi",
  companyCountry: "United Arab Emirates",
  website: "https://meridian.ae",
  logoUrl: "https://logos.example/meridian.png",
  numEmployees: 4200,
};

/**
 * The one company step both entrances into client creation share. What matters here is what a row
 * carries — the mark, the name, where it is, what it does — and that only an id ever leaves on a pick.
 */
describe("CompanyPicker", () => {
  const renderPicker = (props: Partial<Parameters<typeof CompanyPicker>[0]> = {}) => {
    const onPick = vi.fn();
    render(
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <CompanyPicker pick={null} onPick={onPick} {...props} />
      </QueryClientProvider>,
    );
    return onPick;
  };

  beforeEach(() => {
    vi.mocked(companiesApi.searchCompanies).mockResolvedValue({ companies: [MERIDIAN] });
  });

  it("asks the universe nothing until the query is long enough", async () => {
    const user = userEvent.setup();
    renderPicker();

    await user.type(screen.getByPlaceholderText("Search company database…"), "M");

    expect(
      screen.getByText("Type at least 2 characters to search the company database."),
    ).toBeInTheDocument();
    expect(companiesApi.searchCompanies).not.toHaveBeenCalled();
  });

  it("shows the mark, the name, the location and the industry on a suggestion", async () => {
    const user = userEvent.setup();
    const onPick = renderPicker();

    await user.type(screen.getByPlaceholderText("Search company database…"), "Meridian");

    expect(await screen.findByText("Meridian Energy Group")).toBeInTheDocument();
    expect(screen.getByText("Abu Dhabi, United Arab Emirates")).toBeInTheDocument();
    expect(screen.getByText("oil & energy")).toBeInTheDocument();
    expect(screen.getByRole("presentation", { hidden: true })).toHaveAttribute(
      "src",
      MERIDIAN.logoUrl,
    );

    await user.click(screen.getByText("Meridian Energy Group"));

    expect(onPick).toHaveBeenCalledWith({ source: "universe", company: MERIDIAN });
  });

  it("refuses a company already on the books rather than picking it twice", async () => {
    const user = userEvent.setup();
    const onRejectExisting = vi.fn();
    const onPick = renderPicker({
      existingNames: new Set(["meridian energy group"]),
      onRejectExisting,
    });

    await user.type(screen.getByPlaceholderText("Search company database…"), "Meridian");
    await user.click(await screen.findByText("Meridian Energy Group"));

    expect(onRejectExisting).toHaveBeenCalledWith("Meridian Energy Group");
    expect(onPick).not.toHaveBeenCalled();
  });

  it("takes a company the market does not carry through the escape hatch", async () => {
    const user = userEvent.setup();
    const onPick = renderPicker();

    await user.type(screen.getByPlaceholderText("Search company database…"), "Northwind");
    await user.click(await screen.findByText(/None of these/));
    await user.type(screen.getByPlaceholderText("e.g. meridian.ae"), "northwind.ae");
    await user.click(screen.getByRole("button", { name: "Use this company" }));

    expect(onPick).toHaveBeenCalledWith({
      source: "custom",
      name: "Northwind",
      domain: "northwind.ae",
    });
  });
});

/**
 * A universe pick posts an id and nothing the client saw: the server re-resolves the canonical name and
 * domain, so a client cannot be filed under a name of its own choosing.
 */
describe("createPayloadFor", () => {
  it("sends only the account id for a universe pick", () => {
    const pick: CompanyPick = { source: "universe", company: MERIDIAN };

    expect(createPayloadFor(pick)).toEqual({
      company: { apolloAccountId: "apollo-1" },
      sector: "oil & energy",
    });
  });

  it("sends the typed record for a custom pick, and drops an empty domain", () => {
    const pick: CompanyPick = { source: "custom", name: "Northwind", domain: "" };

    expect(createPayloadFor(pick)).toEqual({ customName: "Northwind", customDomain: undefined });
  });
});
