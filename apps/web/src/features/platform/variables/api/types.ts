/** Mirrors the API's `PlatformSettingResponse`: one application variable as the console lists it. */
export interface ApplicationVariable {
  key: string;
  group: string;
  groupKeyPrefix: string;
  label: string;
  description: string;
  value: number;
  /** What the release shipped: the value while nothing overrides it. */
  defaultValue: number;
  min: number;
  max: number;
  /** What the number counts, where it is not a plain count. */
  unit: string | null;
  changed: boolean;
  /** Null once that account is erased, and for a variable nobody has changed. */
  lastChangedBy: string | null;
  /** Null for a variable nobody has changed. */
  lastChangedAt: string | null;
}

/** One line of a variable's history. The values are as they served, so a reset reads as the default of that day. */
export interface VariableChange {
  oldValue: string;
  newValue: string;
  note: string;
  changedBy: string | null;
  changedAt: string;
}
