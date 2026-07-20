import { describe, expect, it } from "vitest";
import { EMPLOYEE_BANDS, REVENUE_BANDS } from "./companySizeBands";

/**
 * Drift guard: the frontend band catalog must stay in step with the backend EmployeeBand / RevenueBand
 * enums (and the distinct employee_range / revenue_range values in the company universe). These value
 * sets were verified against the live DB; if the backend gains or renames a band, this red test flags
 * the mirror before a PUT silently fails validation on an unknown value.
 */
describe("company-size band catalog", () => {
  it("carries exactly the backend employee band values, in ascending order", () => {
    expect(EMPLOYEE_BANDS.map((b) => b.value)).toEqual([
      "1-10",
      "11-50",
      "51-200",
      "201-500",
      "501-1000",
      "1001-5000",
      "5001-10000",
      "10000+",
    ]);
  });

  it("carries exactly the backend revenue band values, in ascending order", () => {
    expect(REVENUE_BANDS.map((b) => b.value)).toEqual([
      "<5M",
      "5M-25M",
      "25M-100M",
      "100M-500M",
      "500M-1B",
      "1B-5B",
      "5B+",
    ]);
  });

  it("gives every band a display label", () => {
    for (const band of [...EMPLOYEE_BANDS, ...REVENUE_BANDS]) {
      expect(band.label).not.toHaveLength(0);
    }
  });
});
