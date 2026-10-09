import { beforeEach, describe, expect, it, vi } from "vitest";
import { aUser, aWorkspace } from "../../../test/fixtures/user";
import * as projectsApi from "../api/projectsApi";
import type { Project } from "../api/types";
import { landingAfterJoining } from "./landingAfterJoining";

vi.mock("../api/projectsApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/projectsApi")>()),
  projects: vi.fn(),
}));

const position = (id: string, seatedUserIds: string[], stage: Project["stage"] = "MAPPING") =>
  ({
    id,
    stage,
    team: seatedUserIds.map((userId) => ({ memberId: `m-${userId}`, userId })),
  }) as unknown as Project;

describe("landingAfterJoining", () => {
  beforeEach(() => vi.resetAllMocks());

  it("opens the one open position a new colleague was seated on", async () => {
    const user = aUser();
    vi.mocked(projectsApi.projects).mockResolvedValue([
      position("p1", [user.id]),
      position("p2", ["someone-else"]),
      position("p3", [user.id], "CLOSED"),
    ]);

    expect(await landingAfterJoining(user)).toBe("/projects/p1");
  });

  it("lands on My positions with none, or more than one", async () => {
    const user = aUser();
    vi.mocked(projectsApi.projects).mockResolvedValueOnce([position("p1", ["someone-else"])]);
    expect(await landingAfterJoining(user)).toBe("/");

    vi.mocked(projectsApi.projects).mockResolvedValueOnce([position("p1", [user.id]), position("p2", [user.id])]);
    expect(await landingAfterJoining(user)).toBe("/");
  });

  it("takes a client contact's list as already theirs", async () => {
    const user = aUser({ workspace: aWorkspace({ roles: ["CLIENT"] }) });
    vi.mocked(projectsApi.projects).mockResolvedValue([position("p1", [])]);

    expect(await landingAfterJoining(user)).toBe("/projects/p1");
  });

  it("falls back to My positions when the list can't be read", async () => {
    vi.mocked(projectsApi.projects).mockRejectedValue(new Error("503"));

    expect(await landingAfterJoining(aUser())).toBe("/");
  });
});
