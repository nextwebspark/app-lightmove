import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import { createElement, type ReactNode } from "react";
import { beforeEach, expect, it, vi } from "vitest";
import { ApiRequestError, requestBlob } from "../../../lib/apiClient";
import { useCandidatePhoto } from "./useCandidatePhoto";

vi.mock("../../../lib/apiClient", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../../lib/apiClient")>()),
  requestBlob: vi.fn(),
}));

const requestBlobMock = vi.mocked(requestBlob);

const ENRICHED = { id: "c1", enrichedAt: "2026-09-08T22:59:46Z" };

const notFound = () =>
  new ApiRequestError({ code: "NOT_FOUND", detail: "Nothing here", status: 404, correlationId: "test" });

/** One client across both renders, so the second mount reads what the first cached. */
const renderTwiceOn = (client: QueryClient) => {
  const wrapper = ({ children }: { children: ReactNode }) =>
    createElement(QueryClientProvider, { client }, children);
  return renderHook(() => useCandidatePhoto("p1", ENRICHED), { wrapper });
};

beforeEach(() => {
  requestBlobMock.mockReset();
  window.URL.createObjectURL = vi.fn(() => "blob:stub");
  window.URL.revokeObjectURL = vi.fn();
});

it("asks once for a candidate who has no photo, however often the avatar remounts", async () => {
  requestBlobMock.mockRejectedValue(notFound());
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });

  const first = renderTwiceOn(client);
  await waitFor(() => expect(requestBlobMock).toHaveBeenCalledTimes(1));
  expect(first.result.current).toBeNull();
  first.unmount();

  // A 404 left to throw parks the query in an error state, and an errored query ignores staleTime —
  // which is how the map's tree re-asked for every absent photo on every table/map toggle.
  const second = renderTwiceOn(client);
  await waitFor(() => expect(second.result.current).toBeNull());
  expect(requestBlobMock).toHaveBeenCalledTimes(1);
});

it("mints an object URL for a candidate who has one", async () => {
  requestBlobMock.mockResolvedValue(new Blob(["bytes"], { type: "image/jpeg" }));
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });

  const { result } = renderTwiceOn(client);

  await waitFor(() => expect(result.current).toBe("blob:stub"));
});

it("still fails loudly on anything that is not a missing photo", async () => {
  requestBlobMock.mockRejectedValue(
    new ApiRequestError({ code: "FORBIDDEN", detail: "No", status: 403, correlationId: "test" }),
  );
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });

  const { result } = renderTwiceOn(client);

  await waitFor(() => expect(requestBlobMock).toHaveBeenCalled());
  expect(result.current).toBeNull();
});
