import { formatNumber } from "../../../lib/format";
import type { CandidateCompensation, LongTermIncentiveType } from "../api/types";

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

/** "420,000" or "45%" as typed, or "" — what a figure field holds, back as the number it means. */
export function amountTyped(value: string | undefined): number | null {
  if (!value) return null;
  const figure = Number(value.replace(/[,\s%]/g, ""));
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

export type BaseCadence = "annual" | "monthly";
export type BonusBasis = "percent" | "fixed";

/** A base typed per month is stored per year — `base_salary` is always annual. */
export function annualBaseOf(base: number | null, cadence: BaseCadence): number | null {
  if (base === null) return null;
  return cadence === "monthly" ? base * 12 : base;
}

/** A bonus typed as a share of the (annual) base, as the amount it comes to. */
export function bonusAmountOf(annualBase: number | null, bonus: number | null, basis: BonusBasis): number | null {
  if (bonus === null) return null;
  if (basis === "fixed") return bonus;
  // A share of a base nobody established is not a bonus of nought.
  if (annualBase === null) return null;
  return Math.round((annualBase * bonus) / 100);
}

/**
 * How a stored bonus reopens: as a share of base, because that is how a GCC package is quoted — except
 * a bonus with no base to share it of, which can only be restated as the amount it is.
 */
export function bonusBasisOf(baseSalary: number | null, bonus: number | null): BonusBasis {
  return bonus !== null && !baseSalary ? "fixed" : "percent";
}

export function bonusPercentOf(baseSalary: number, bonus: number): number {
  return Math.round((bonus / baseSalary) * 1000) / 10;
}

/** The typed lines summed, or null when none holds a figure — "not established", not nought. */
export function allowanceTotalOf(amounts: readonly (number | null)[]): number | null {
  const figures = amounts.filter((amount): amount is number => amount !== null);
  return figures.length === 0 ? null : figures.reduce((sum, amount) => sum + amount, 0);
}

/**
 * Pressing one instrument chip, as the mockup's chips behave. None stands alone: picking it clears
 * the rest, picking an instrument clears it, and letting go of the last instrument lands on None.
 */
export function toggleIncentiveType(
  selected: readonly LongTermIncentiveType[],
  pressed: LongTermIncentiveType,
): LongTermIncentiveType[] {
  if (pressed === "none") return ["none"];
  if (selected.includes(pressed)) {
    const remaining = selected.filter((type) => type !== pressed);
    return remaining.length === 0 ? ["none"] : remaining;
  }
  return [...selected.filter((type) => type !== "none"), pressed];
}
