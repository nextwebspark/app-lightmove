import type { ApplicationVariable } from "../api/types";

export const VARIABLES_PATH = "/platform/variables";

export const NOTE_LIMIT = 500;

const SHORT_UNITS: Record<string, string> = { seconds: "s" };

/** A value as a column draws it: "8", or "180 s" where the number is not a plain count. */
export function valueLabel(value: number | string, unit: string | null): string {
  return unit ? `${value} ${SHORT_UNITS[unit] ?? unit}` : `${value}`;
}

/** A value inside a sentence: "180 seconds". */
export function valueInWords(value: number, unit: string | null): string {
  return unit ? `${value} ${unit}` : `${value}`;
}

export function rangeHint(variable: ApplicationVariable): string {
  const range = `Whole number, ${variable.min} to ${variable.max}${variable.unit ? ` ${variable.unit}` : ""}`;
  return `${range} · default ${variable.defaultValue}`;
}

export function outOfRangeMessage(variable: ApplicationVariable): string {
  return `Enter a whole number from ${variable.min} to ${variable.max}.`;
}

export interface VariableGroup {
  name: string;
  keyPattern: string;
  variables: ApplicationVariable[];
}

/** The variables under their headings, in the order the server lists them. */
export function groupsOf(variables: ApplicationVariable[]): VariableGroup[] {
  const groups: VariableGroup[] = [];
  for (const variable of variables) {
    const group = groups.find((existing) => existing.name === variable.group);
    if (group) group.variables.push(variable);
    else groups.push({ name: variable.group, keyPattern: `${variable.groupKeyPrefix}.*`, variables: [variable] });
  }
  return groups;
}
