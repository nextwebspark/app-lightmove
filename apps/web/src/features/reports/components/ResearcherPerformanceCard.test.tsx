import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { SAMPLE_REPORT } from "../../../test/sampleReport";
import { SAMPLE_TEAM_PERFORMANCE } from "../../../test/sampleTeamPerformance";
import * as reportApi from "../api/reportApi";
import { ResearcherPerformanceCard } from "./ResearcherPerformanceCard";

vi.mock("../api/reportApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/reportApi")>()),
  getTeamPerformance: vi.fn(),
}));

/** The researcher breakdown: the range it is read over, the table, and the two drill-in drawers. */
describe("ResearcherPerformanceCard", () => {
  const renderCard = () =>
    render(
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <ResearcherPerformanceCard projectId="p1" progress={SAMPLE_REPORT.progress} />
      </QueryClientProvider>,
    );

  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(reportApi.getTeamPerformance).mockResolvedValue(SAMPLE_TEAM_PERFORMANCE);
  });

  it("reads the last 30 days by default and each pill asks for its own range", async () => {
    renderCard();

    expect(await screen.findByRole("button", { name: /Omar Khoury/ })).toBeInTheDocument();
    expect(reportApi.getTeamPerformance).toHaveBeenCalledWith("p1", { from: "2026-08-10", to: "2026-09-08" }, expect.anything());

    await userEvent.click(screen.getByRole("radio", { name: "Last 7 days" }));
    await waitFor(() =>
      expect(reportApi.getTeamPerformance).toHaveBeenLastCalledWith("p1", { from: "2026-09-02", to: "2026-09-08" }, expect.anything()),
    );

    await userEvent.click(screen.getByRole("radio", { name: "All time" }));
    await waitFor(() => expect(reportApi.getTeamPerformance).toHaveBeenLastCalledWith("p1", {}, expect.anything()));

    await userEvent.click(screen.getByRole("button", { name: "Reset filters" }));
    expect(screen.getByRole("radio", { name: "Last 30 days" })).toHaveAttribute("aria-checked", "true");
  });

  it("never sends a custom range whose start is after its end, and says why", async () => {
    renderCard();
    await screen.findByRole("button", { name: /Omar Khoury/ });
    await userEvent.click(screen.getByRole("radio", { name: "Custom" }));
    await waitFor(() => expect(reportApi.getTeamPerformance).toHaveBeenLastCalledWith("p1", { from: "2026-07-21", to: "2026-09-08" }, expect.anything()));
    const calls = vi.mocked(reportApi.getTeamPerformance).mock.calls.length;

    fireEvent.change(screen.getByLabelText("From"), { target: { value: "2026-09-05" } });
    fireEvent.change(screen.getByLabelText("To"), { target: { value: "2026-08-01" } });

    expect(await screen.findByRole("alert")).toHaveTextContent("The start date must be on or before the end date.");
    expect(reportApi.getTeamPerformance).toHaveBeenCalledTimes(calls + 1);
    expect(screen.getByRole("button", { name: /Omar Khoury/ })).toBeInTheDocument();
    expect(screen.queryByText(/could not be loaded/)).not.toBeInTheDocument();
  });

  it("states every researcher's figures from the read, and the coverage it credits", async () => {
    renderCard();

    expect(await screen.findByText("31 / 42")).toBeInTheDocument();
    expect(screen.getByText("Yara Haddad filed last")).toBeInTheDocument();
    expect(screen.getByText("74%")).toBeInTheDocument();
    expect(screen.getByText("Good")).toBeInTheDocument();
    expect(screen.getByText("Attention")).toBeInTheDocument();
    expect(screen.getByText("0.9/day")).toBeInTheDocument();
    expect(screen.getByText("Not yet mapped")).toBeInTheDocument();
    expect(screen.getByText("The 1 most-mapped of 31 covered companies.")).toBeInTheDocument();
  });

  it("opens a researcher's drawer and a company's, each from its own row", async () => {
    renderCard();

    await userEvent.click(await screen.findByRole("button", { name: /Omar Khoury/ }));
    const researcher = screen.getByRole("dialog", { name: "Omar Khoury" });
    expect(within(researcher).getByText("Nadia Salloum")).toBeInTheDocument();
    expect(within(researcher).getByText("Active 6 of 30 days")).toBeInTheDocument();
    expect(within(researcher).queryByText(/confidence/i)).not.toBeInTheDocument();
    await userEvent.click(within(researcher).getByRole("button", { name: "Close" }));
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: /Al Naboodah Group/ }));
    const company = screen.getByRole("dialog", { name: "Al Naboodah Group" });
    expect(within(company).getByText("Conglomerates · Dubai · ~12k emps")).toBeInTheDocument();
    expect(within(company).getByText("Rami Khoury")).toBeInTheDocument();
  });
});
