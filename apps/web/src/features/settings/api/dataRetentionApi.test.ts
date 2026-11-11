import { beforeEach, describe, expect, it, vi } from "vitest";
import { request } from "../../../lib/apiClient";
import { ERASE_BATCH_SIZE, eraseDue } from "./dataRetentionApi";

vi.mock("../../../lib/apiClient", () => ({ request: vi.fn() }));

const ids = (count: number) => Array.from({ length: count }, (_, index) => `p${index}`);

describe("eraseDue", () => {
  beforeEach(() => vi.mocked(request).mockReset());

  it("sends a long selection in batches and adds up what each did", async () => {
    vi.mocked(request)
      .mockResolvedValueOnce({ erased: 24, noLongerDue: 1, sendInFlight: 0 })
      .mockResolvedValueOnce({ erased: 20, noLongerDue: 2, sendInFlight: 3 });

    const result = await eraseDue(ids(ERASE_BATCH_SIZE + 25));

    expect(request).toHaveBeenCalledTimes(2);
    const bodies = vi.mocked(request).mock.calls.map(([, init]) => (init?.body as { personIds: string[] }).personIds);
    expect(bodies[0]).toEqual(ids(ERASE_BATCH_SIZE));
    expect(bodies[1]).toHaveLength(25);
    expect(result).toEqual({ erased: 44, noLongerDue: 3, sendInFlight: 3 });
  });

  it("stops at the batch that fails, leaving the earlier ones sent", async () => {
    vi.mocked(request)
      .mockResolvedValueOnce({ erased: ERASE_BATCH_SIZE, noLongerDue: 0, sendInFlight: 0 })
      .mockRejectedValueOnce(new Error("gateway timeout"));

    await expect(eraseDue(ids(ERASE_BATCH_SIZE * 3))).rejects.toThrow("gateway timeout");
    expect(request).toHaveBeenCalledTimes(2);
  });
});
