import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import * as talentMapApi from "../../talentmap/api/talentMapApi";
import { resolveCompaniesView, useCompaniesViews } from "./useCompaniesViews";

vi.mock("../../talentmap/api/talentMapApi");

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

describe("the Companies views while the map is switched off", () => {
  beforeEach(() => {
    vi.mocked(talentMapApi.getTalentMapConfig).mockResolvedValue({ enabled: true, publicToken: "pk.test" });
  });

  it("offers the table and the pipeline on the universe even where a Mapbox account is configured", async () => {
    const { result } = renderHook(() => useCompaniesViews("inUniverse", true), { wrapper });
    await waitFor(() => expect(talentMapApi.getTalentMapConfig).toHaveBeenCalled());
    await waitFor(() => expect(result.current.mapConfig.isSuccess).toBe(true));
    expect(result.current.views).toEqual(["table", "pipeline"]);
  });

  it("lands a link or a remembered choice of the map on the table", () => {
    expect(resolveCompaniesView(["table", "pipeline"], "map", "table")).toBe("table");
    expect(resolveCompaniesView(["table", "pipeline"], null, "map")).toBe("table");
  });
});
