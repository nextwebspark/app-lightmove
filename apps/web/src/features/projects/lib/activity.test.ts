import { describe, expect, it } from "vitest";
import type { ProjectActivityEntry } from "../api/types";
import { activityLines, formatActivityTime } from "./activity";

const entry = (
  id: number,
  type: string,
  details: Record<string, string> = {},
  actorUserId = "u1",
): ProjectActivityEntry => ({
  id,
  type,
  occurredAt: "2026-09-24T10:00:00Z",
  actorUserId,
  actorName: actorUserId === "u1" ? "Yasmin Haddad" : "Omar Khalil",
  actorAvatarUrl: null,
  details,
});

const texts = (entries: ProjectActivityEntry[]) => activityLines(entries).map((line) => line.text);

describe("activityLines", () => {
  it("merges one person's run of one act and keeps other people apart", () => {
    expect(
      texts([
        entry(5, "TRIAGE_COMPANY_CAPTURED"),
        entry(4, "TRIAGE_COMPANY_ADDED"),
        entry(3, "TRIAGE_BULK_ADDED", { added: "5", status: "IN_UNIVERSE" }),
        entry(2, "TRIAGE_COMPANY_ADDED", {}, "u2"),
        entry(1, "TRIAGE_COMPANY_ADDED"),
      ]),
    ).toEqual(["added 7 companies", "added a company", "added a company"]);
  });

  it("reads stage moves in both spellings the server records", () => {
    expect(
      texts([
        entry(3, "TRIAGE_COMPANY_MOVED", { status: "shortlisted" }),
        entry(2, "TRIAGE_BULK_ADDED", { added: "3", status: "SHORTLISTED" }),
        entry(1, "TRIAGE_COMPANY_MOVED", { status: "inUniverse" }),
      ]),
    ).toEqual(["shortlisted 4 companies", "moved a company back to the universe"]);
  });

  it("merges a status run only when the status matches", () => {
    expect(
      texts([
        entry(3, "CANDIDATE_UPDATED", { status: "contacted" }),
        entry(2, "CANDIDATE_UPDATED", { status: "contacted" }),
        entry(1, "CANDIDATE_UPDATED", { status: "notInterested" }),
      ]),
    ).toEqual(["marked 2 executives Contacted", "marked an executive Not interested"]);
  });

  it("collapses a brief's autosaves into one edit", () => {
    expect(texts([entry(3, "POSITION_UPDATED"), entry(2, "POSITION_UPDATED"), entry(1, "POSITION_UPDATED")])).toEqual([
      "updated the brief",
    ]);
  });

  it("names what a removal took, and counts a run of them", () => {
    expect(texts([entry(1, "CANDIDATE_REMOVED", { fullName: "Hana Aziz" })])).toEqual(["removed Hana Aziz"]);
    expect(
      texts([entry(2, "CANDIDATE_REMOVED", { fullName: "Hana Aziz" }), entry(1, "CANDIDATE_REMOVED", { fullName: "Omar" })]),
    ).toEqual(["removed 2 executives"]);
  });

  it("states what an import brought in, one line per file", () => {
    expect(
      texts([
        entry(2, "SPREADSHEET_IMPORTED", { fileName: "gcc.xlsx", companiesCreated: "40", candidatesCreated: "1" }),
        entry(1, "SPREADSHEET_IMPORTED", { fileName: "ksa.csv" }),
      ]),
    ).toEqual(["imported gcc.xlsx — 40 companies, 1 executive", "imported ksa.csv"]);
  });

  it("drops what it has no words for rather than printing a code", () => {
    expect(texts([entry(2, "PROJECT_TEAM_CHANGED"), entry(1, "CANDIDATE_UPDATED")])).toEqual([]);
  });
});

describe("formatActivityTime", () => {
  const now = new Date("2026-09-24T12:00:00Z");

  it("steps from minutes to days to a date", () => {
    expect(formatActivityTime("2026-09-24T11:59:40Z", now)).toBe("just now");
    expect(formatActivityTime("2026-09-24T11:48:00Z", now)).toBe("12 minutes ago");
    expect(formatActivityTime("2026-09-24T09:00:00Z", now)).toBe("3 hours ago");
    expect(formatActivityTime("2026-09-23T09:00:00Z", now)).toBe("Yesterday");
    expect(formatActivityTime("2026-09-20T09:00:00Z", now)).toBe("4 days ago");
    expect(formatActivityTime("2026-09-01T09:00:00Z", now)).toBe("01 Sept 2026");
  });
});
