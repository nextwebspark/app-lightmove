import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import { createElement, type ReactNode } from "react";
import { expect, it, vi } from "vitest";
import * as shareConsentApi from "../api/shareConsentApi";
import type { ShareConsentRow } from "../api/shareConsentApi";
import { useShareConsents } from "./useShareConsents";

vi.mock("./useConsentMode", () => ({
  useConsentMode: () => ({ on: true, firm: { shareWithClient: true } }),
}));

vi.mock("../api/shareConsentApi", async (importOriginal) => ({
  ...(await importOriginal<typeof shareConsentApi>()),
  getShareConsents: vi.fn(),
}));

function aRow(candidateId: string, overrides: Partial<ShareConsentRow>): ShareConsentRow {
  return {
    candidateId,
    status: null,
    requestedAt: "2026-09-01T09:00:00Z",
    requestOpenUntil: null,
    answeredAt: null,
    clientNamed: false,
    ...overrides,
  };
}

it("says what each person answered, a no and a withdrawal included", async () => {
  vi.mocked(shareConsentApi.getShareConsents).mockResolvedValue({
    asksToShare: true,
    clientNamable: true,
    clientName: "Meridian Energy Group",
    rows: [
      aRow("declined", { status: "declined", answeredAt: "2026-09-24T09:00:00Z" }),
      aRow("withdrawn", { status: "withdrawn", answeredAt: "2026-09-29T09:00:00Z" }),
      aRow("given", { status: "given", answeredAt: "2026-09-20T09:00:00Z" }),
      aRow("asked", { status: "requested", requestOpenUntil: "2026-10-01T09:00:00Z" }),
      aRow("lapsed", { status: "expired" }),
    ],
  });
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const wrapper = ({ children }: { children: ReactNode }) => createElement(QueryClientProvider, { client }, children);
  const { result } = renderHook(() => useShareConsents("p1"), { wrapper });

  await waitFor(() => expect(result.current.on).toBe(true));
  expect(result.current.badgeOf("declined")).toBe("declined");
  expect(result.current.badgeOf("withdrawn")).toBe("withdrawn");
  expect(result.current.badgeOf("given")).toBe("given");
  expect(result.current.badgeOf("asked")).toBe("asked");
  expect(result.current.badgeOf("lapsed")).toBe("none");
  expect(result.current.badgeOf("never-asked")).toBe("none");
  expect(result.current.needsPrompt("declined")).toBe(true);
});
