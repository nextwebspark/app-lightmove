import { describe, expect, it } from "vitest";
import type { ChartQueryDef } from "../api/types";
import { allowedCharts, defaultTitle, optionSummary } from "./chartRules";

const executivesBy: ChartQueryDef = {
  id: "executivesBy",
  category: "Pipeline",
  label: "Executives by …",
  description: "",
  shape: "CATEGORICAL",
  noSum: false,
  staffOnly: false,
  defaultChart: "bar",
  charts: ["bar", "column", "pie", "donut", "table"],
  options: [
    {
      key: "group",
      label: "Group by",
      choices: [
        { value: "stage", label: "Stage" },
        { value: "level", label: "Level" },
      ],
      defaultValue: "stage",
    },
    {
      key: "top",
      label: "Show",
      choices: [
        { value: "all", label: "All" },
        { value: "5", label: "Top 5" },
      ],
      defaultValue: "all",
    },
  ],
};

describe("chart rules", () => {
  it("offers a pie only for parts of one whole of eight slices at most", () => {
    expect(allowedCharts("CATEGORICAL", false, 8)).toContain("pie");
    expect(allowedCharts("CATEGORICAL", false, 9)).not.toContain("pie");
    expect(allowedCharts("CATEGORICAL", true, 3)).not.toContain("donut");
    expect(allowedCharts("TIME_SERIES", false, 3)).toEqual(["line", "area", "column", "table"]);
    expect(allowedCharts("KPI", false, 0)).toEqual(["kpi"]);
  });

  it("completes an open title with the chosen group, and summarises the options", () => {
    expect(defaultTitle(executivesBy, { group: "level" })).toBe("Executives by level");
    expect(defaultTitle(executivesBy, {})).toBe("Executives by stage");
    expect(optionSummary(executivesBy, { group: "level", top: "5" })).toBe("By level · top 5");
    expect(optionSummary(executivesBy, { group: "stage", top: "all" })).toBe("By stage");
  });
});
