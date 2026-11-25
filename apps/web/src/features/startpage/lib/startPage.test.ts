import { describe, expect, it } from "vitest";
import type { StartPage } from "../api/types";
import { startPageLabel, startPagePath } from "./startPage";

const page = (over: Partial<StartPage>): StartPage => ({
  kind: "HOME",
  dashboardId: null,
  projectId: null,
  section: null,
  available: true,
  ...over,
});

describe("startPagePath", () => {
  it("opens Home when nothing is saved", () => {
    expect(startPagePath(null)).toBe("/");
    expect(startPagePath(page({}))).toBe("/");
  });

  it("opens each list, a dashboard and a position's tab", () => {
    expect(startPagePath(page({ kind: "POSITIONS" }))).toBe("/positions");
    expect(startPagePath(page({ kind: "CANDIDATES" }))).toBe("/candidates");
    expect(startPagePath(page({ kind: "TASKS" }))).toBe("/tasks");
    expect(startPagePath(page({ kind: "DASHBOARD", dashboardId: "d1" }))).toBe("/dashboards?dashboard=d1");
    expect(startPagePath(page({ kind: "POSITION", projectId: "p1", section: "UNIVERSE" }))).toBe(
      "/projects/p1/companies/universe",
    );
    expect(startPagePath(page({ kind: "POSITION", projectId: "p1", section: "BRIEF" }))).toBe("/projects/p1");
    expect(startPagePath(page({ kind: "POSITION", projectId: "p1", section: "DASHBOARD" }))).toBe("/projects/p1/reports");
  });

  it("opens Home for a page the member can no longer open", () => {
    expect(startPagePath(page({ kind: "DASHBOARD", dashboardId: "d1", available: false }))).toBe("/");
    expect(startPageLabel(page({ kind: "DASHBOARD", dashboardId: "d1", available: false }))).toBe("Home");
  });
});
