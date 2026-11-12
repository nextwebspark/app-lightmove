import { describe, expect, it } from "vitest";
import type { ProjectSummary } from "../../api/types";
import { positionGroups } from "./positionGroups";

function position(id: string, positionTitle: string, clientName: string | null): ProjectSummary {
  return { id, positionTitle, clientName, clientLogoUrl: null, team: [], archivedAt: null };
}

const POSITIONS = [
  position("p1", "Group Chief Financial Officer", "Meridian Energy Group"),
  position("p2", "Head of Credit Risk", "Gulf Commercial Bank"),
  position("p3", "Chief Digital Officer", "Meridian Energy Group"),
];

describe("the position switcher", () => {
  it("lists the recent positions first, then every position by client, A–Z", () => {
    const groups = positionGroups(POSITIONS, ["p2"], "");
    expect(groups.map((group) => group.label)).toEqual(["Recent", "Gulf Commercial Bank", "Meridian Energy Group"]);
    expect(groups[2].positions.map((each) => each.id)).toEqual(["p3", "p1"]);
  });

  it("searches titles and clients, and drops Recent while searching", () => {
    expect(positionGroups(POSITIONS, ["p2"], "meridian").map((group) => group.label)).toEqual(["Meridian Energy Group"]);
    expect(positionGroups(POSITIONS, [], "credit").flatMap((group) => group.positions.map((each) => each.id))).toEqual(["p2"]);
  });
});
