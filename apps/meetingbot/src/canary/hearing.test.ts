import { describe, expect, it, vi } from "vitest";
import { transcribePiece } from "./hearing.js";

describe("transcribePiece", () => {
  it("posts the piece with the job's ID token and answers what the API heard", async () => {
    const fetcher = vi.fn(async () => Response.json({ heard: "Purple giraffes read quiet library books" }));

    expect(await transcribePiece("https://beta.uncava.com", "id-token-1", new Uint8Array([1, 2]),
      fetcher as unknown as typeof fetch)).toBe("Purple giraffes read quiet library books");
    const [url, init] = fetcher.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe("https://beta.uncava.com/api/v1/internal/notetaker/canary/hearing");
    expect(new Headers(init.headers).get("X-Notetaker-Bot-Identity")).toBe("id-token-1");
    expect(init.body).toEqual(new Uint8Array([1, 2]));
  });

  it("throws when the API could not hear it", async () => {
    const fetcher = vi.fn(async () => new Response(null, { status: 502 }));

    await expect(transcribePiece("https://beta.uncava.com", "id-token-1", new Uint8Array([1]),
      fetcher as unknown as typeof fetch)).rejects.toThrow("502");
  });
});
