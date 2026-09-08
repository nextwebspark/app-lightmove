import { formatNumber } from "../../../lib/format";
import type { CandidateCompensation } from "../api/types";

/** One element of a package: a stored figure and its share of the total, for the composition bar. */
export interface PackagePart {
  key: "baseSalary" | "bonus" | "allowances" | "longTermIncentive";
  label: string;
  amount: number;
  share: number;
}

const ELEMENTS: { key: PackagePart["key"]; label: string }[] = [
  { key: "baseSalary", label: "Base" },
  { key: "bonus", label: "Bonus" },
  { key: "allowances", label: "Allowances" },
  { key: "longTermIncentive", label: "LTIP" },
];

/**
 * A package summed and split. Only the elements actually paid appear as parts — a package with no
 * bonus has three parts, not a fourth at nought — and the shares are of the total so a bar drawn
 * from them fills its width exactly.
 */
export function packageOf(
  compensation: Pick<CandidateCompensation, PackagePart["key"]>,
): { total: number; parts: PackagePart[] } {
  const paid = ELEMENTS.map(({ key, label }) => ({ key, label, amount: compensation[key] ?? 0 })).filter(
    (element) => element.amount > 0,
  );
  const total = paid.reduce((sum, element) => sum + element.amount, 0);
  return {
    total,
    parts: paid.map((element) => ({ ...element, share: total > 0 ? element.amount / total : 0 })),
  };
}

/** "420,000" as typed, or "" — what an amount field holds, back as the number it means. */
export function amountTyped(value: string | undefined): number | null {
  if (!value) return null;
  const figure = Number(value.replace(/[,\s]/g, ""));
  return Number.isFinite(figure) && figure >= 0 ? figure : null;
}

/**
 * A figure in the currency it was quoted in, or null when nobody established it. Zero is kept rather
 * than blanked: "no bonus" is a fact about the package and "bonus not established" is a fact about
 * the research, and the panel must not turn the second into the first.
 */
export function formatAmount(currency: string, amount: number | null): string | null {
  if (amount === null || amount === undefined) return null;
  return `${currency} ${formatNumber(amount)}`.trim();
}
