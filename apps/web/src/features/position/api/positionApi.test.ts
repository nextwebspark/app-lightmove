import { describe, expect, it, vi } from "vitest";
import { ApiRequestError, request } from "../../../lib/apiClient";
import { extractAll } from "./positionApi";
import type { PositionExtraction } from "./types";

vi.mock("../../../lib/apiClient", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../../lib/apiClient")>()),
  request: vi.fn(),
}));

/**
 * `extractAll` is the only thing this issue adds to `positionApi.ts` — the rest of the fan-out lives
 * in `PositionPage`. What matters here is `Promise.allSettled` itself: one section's endpoint
 * rejecting must not take the other four's results down with it, and every result must land under
 * its own section key.
 */
describe("extractAll", () => {
  const extraction = (fieldKey: string): PositionExtraction => ({
    extractionSource: "model",
    fields: [{ fieldKey, value: "value", confidence: "high", snippet: null }],
  });

  it("settles every section independently, keeping four fulfilled results beside one rejection", async () => {
    const failure = new ApiRequestError({
      code: "RATE_LIMITED",
      detail: "Too many requests",
      status: 429,
      correlationId: "abc",
    });
    vi.mocked(request).mockImplementation((path) => {
      if (typeof path === "string" && path.endsWith("/document/extract/compensation")) {
        return Promise.reject(failure);
      }
      const section = typeof path === "string" ? path.split("/").at(-1)! : "unknown";
      return Promise.resolve(extraction(section));
    });

    const result = await extractAll("p1");

    expect(result.details).toEqual({ status: "fulfilled", value: extraction("details") });
    expect(result.context).toEqual({ status: "fulfilled", value: extraction("context") });
    expect(result.reporting).toEqual({ status: "fulfilled", value: extraction("reporting") });
    expect(result.assessment).toEqual({ status: "fulfilled", value: extraction("assessment") });
    expect(result.compensation).toEqual({ status: "rejected", reason: failure });
  });

  it("never rejects itself, even when every section fails", async () => {
    vi.mocked(request).mockRejectedValue(new Error("network down"));

    const result = await extractAll("p1");

    for (const section of Object.values(result)) {
      expect(section.status).toBe("rejected");
    }
  });
});
