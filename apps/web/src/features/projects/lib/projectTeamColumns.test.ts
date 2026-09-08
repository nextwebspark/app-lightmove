import { describe, expect, it } from "vitest";
import { sortableFieldsOf } from "../../../lib/useGridSort";
import { projectTeamColumns, PROJECT_TEAM_SORT_FIELDS } from "./projectTeamColumns";

describe("PROJECT_TEAM_SORT_FIELDS", () => {
  it("names exactly the columns that sort, so a remembered sort is never thrown away", () => {
    expect([...PROJECT_TEAM_SORT_FIELDS].sort()).toEqual(sortableFieldsOf(projectTeamColumns).sort());
  });
});
