import { afterEach, describe, expect, it, vi } from "vitest";
import * as apiClient from "../../../lib/apiClient";
import { getPositionHistory, POSITION_HISTORY_LIMIT } from "./personCrmApi";

vi.mock("../../../lib/apiClient", async (importOriginal) => ({
  ...(await importOriginal<typeof apiClient>()),
  request: vi.fn(),
}));

describe("getPositionHistory", () => {
  afterEach(() => vi.clearAllMocks());

  it("reads the position's history in one request, at the route's ceiling", async () => {
    vi.mocked(apiClient.request).mockResolvedValue({ entries: [], nextCursor: null });
    const history = await getPositionHistory("p1", "c1");
    expect(apiClient.request).toHaveBeenCalledTimes(1);
    expect(apiClient.request).toHaveBeenCalledWith(
      `/projects/p1/candidates/c1/timeline?limit=${POSITION_HISTORY_LIMIT}`,
      expect.anything(),
    );
    expect(history).toEqual({ entries: [], truncated: false });
  });

  it("says the oldest were left unread when the server holds more", async () => {
    vi.mocked(apiClient.request).mockResolvedValue({ entries: [], nextCursor: 42 });
    expect((await getPositionHistory("p1", "c1")).truncated).toBe(true);
  });

  it("lets a failed read fail, for the tab to say so", async () => {
    vi.mocked(apiClient.request).mockRejectedValue(new Error("offline"));
    await expect(getPositionHistory("p1", "c1")).rejects.toThrow("offline");
  });
});
