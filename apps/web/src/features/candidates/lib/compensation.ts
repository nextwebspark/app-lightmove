import { formatNumber } from "../../../lib/format";
import type { AllowanceLine, CandidateCompensation, LongTermIncentiveType } from "../api/types";

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

export const LONG_TERM_INCENTIVE_TYPES: readonly { value: LongTermIncentiveType; label: string }[] = [
  { value: "options", label: "Options" },
  { value: "rsus", label: "RSUs" },
  { value: "cash", label: "Cash" },
  { value: "none", label: "None" },
];

/** "Options, RSUs" — the instruments an LTIP is paid in, in the editor's order, or null when none is recorded. */
export function incentiveTypesLabel(types: readonly LongTermIncentiveType[]): string | null {
  const labels = LONG_TERM_INCENTIVE_TYPES.filter((type) => types.includes(type.value)).map((type) => type.label);
  return labels.length > 0 ? labels.join(", ") : null;
}

/** The allowance lines worth reading back: a line with neither a name nor a figure is an empty row. */
export function recordedAllowanceLines(lines: readonly AllowanceLine[] | undefined): AllowanceLine[] {
  return (lines ?? []).filter((line) => (line.label?.trim() ?? "") !== "" || line.amount !== null);
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

/** The bonus field as typed — "45%" as a share, "150,000" as an amount — back as the number it means. */
export function shareTyped(value: string | undefined): number | null {
  return amountTyped(value?.replace(/%/g, ""));
}

/**
 * How a stored bonus reopens: as a share of base, because that is how a GCC package is quoted — but
 * only where the shown share comes back to the stored amount exactly. Otherwise saving an untouched
 * section recomputed the bonus from a rounded share and silently rewrote it (83,333 on 420,000 came
 * back as 83,160), so such a bonus, and one with no base to share, reopens as the amount it is.
 */
export function bonusBasisOf(baseSalary: number | null, bonus: number | null): BonusBasis {
  if (bonus === null) return "percent";
  if (!baseSalary) return "fixed";
  return bonusAmountOf(baseSalary, bonusPercentOf(baseSalary, bonus), "percent") === bonus ? "percent" : "fixed";
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

/** An LTIP amount contradicts "None"; the server drops it the same way, so the request says what is stored. */
export function incentiveTypesFor(
  longTermIncentive: number | null,
  types: readonly LongTermIncentiveType[],
): LongTermIncentiveType[] {
  return longTermIncentive && longTermIncentive > 0 ? types.filter((type) => type !== "none") : [...types];
}
