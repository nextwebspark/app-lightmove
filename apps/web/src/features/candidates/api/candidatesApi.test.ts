import { beforeEach, describe, expect, it, vi } from "vitest";
import type { Candidate, CandidatesPage } from "./types";

vi.mock("../../../lib/apiClient", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../../lib/apiClient")>()),
  request: vi.fn(),
}));

const { request } = await import("../../../lib/apiClient");
const { getCandidates } = await import("./candidatesApi");

const person = (id: string) => ({ id, fullName: id }) as Candidate;

function pageOf(ids: string[], totalCount: number, page: number): CandidatesPage {
  return {
    candidates: ids.map(person),
    totalCount,
    page,
    size: 2,
    addedBy: Object.fromEntries(ids.map((id) => [id, { userId: id, name: id, avatarUrl: null }])),
  } as CandidatesPage;
}

describe("getCandidates", () => {
  beforeEach(() => vi.mocked(request).mockReset());

  it("reads every page of the people at a page of companies, not the first ceiling alone", async () => {
    vi.mocked(request).mockImplementation(async (path: string) => {
      const page = Number(new URL(path, "http://x").searchParams.get("page") ?? 0);
      return [pageOf(["a", "b"], 5, 0), pageOf(["c", "d"], 5, 1), pageOf(["e"], 5, 2)][page];
    });

    const all = await getCandidates("p1", { triageCompanyIds: ["mavi"], triageStatus: "inUniverse" });

    expect(all.candidates.map((candidate) => candidate.id)).toEqual(["a", "b", "c", "d", "e"]);
    expect(all.totalCount).toBe(5);
    expect(Object.keys(all.addedBy ?? {})).toEqual(["a", "b", "c", "d", "e"]);
    expect(request).toHaveBeenCalledTimes(3);
  });

  it("asks once for a plain list, which is paged by its own screen", async () => {
    vi.mocked(request).mockResolvedValue(pageOf(["a", "b"], 5, 0));

    const first = await getCandidates("p1", {});

    expect(first.candidates).toHaveLength(2);
    expect(request).toHaveBeenCalledTimes(1);
  });
});
