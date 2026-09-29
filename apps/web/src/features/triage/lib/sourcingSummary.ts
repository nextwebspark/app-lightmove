import type { SourcingRun } from "../api/sourcingApi";

const countOf = (count: number, one: string, many: string) => `${count} ${count === 1 ? one : many}`;

export const companiesOf = (count: number) => countOf(count, "company", "companies");

/** "Found 9 executives at 4 of 5 companies". */
export function summaryOf(run: SourcingRun): string {
  if (run.executivesFiled === 0) return `Found no executives to add at ${companiesOf(run.companiesTotal)}`;
  const filedAt = run.outcomes.filter((outcome) => outcome.outcome === "FILED").length;
  const executives = countOf(run.executivesFiled, "executive", "executives");
  return `Found ${executives} at ${filedAt} of ${companiesOf(run.companiesTotal)}`;
}
