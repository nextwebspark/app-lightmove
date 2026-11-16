import { useMemo, useState } from "react";
import type { DataGridCheckFilter } from "./gridFilters";

/** A closed-vocabulary column filter over rows the screen already holds in full. */
export interface LocalGridFilterSpec<TRow, TValue extends string = string> {
  /** The grid column it filters, so its header menu edits the same filter. */
  id: string;
  label: string;
  /** Every value a row holds for the column; a row is kept when any one of them is ticked. */
  valuesOf: (row: TRow) => readonly TValue[];
  labelOf?: (value: TValue) => string;
  /** The values in their natural order (stages, health); otherwise the options sort by label. */
  order?: readonly TValue[];
}

/** A spec with its value type erased, so specs over different columns sit in one list. Built by {@link filterSpec}. */
export interface LocalGridFilter<TRow> {
  id: string;
  label: string;
  valuesOf: (row: TRow) => readonly string[];
  labelOf: (value: string) => string;
  order?: readonly string[];
}

export function filterSpec<TRow, TValue extends string>(spec: LocalGridFilterSpec<TRow, TValue>): LocalGridFilter<TRow> {
  const { labelOf } = spec;
  return {
    id: spec.id,
    label: spec.label,
    valuesOf: spec.valuesOf,
    order: spec.order,
    // Every value it is handed came out of `valuesOf`, or was ticked from options built from it.
    labelOf: labelOf ? (value) => labelOf(value as TValue) : (value) => value,
  };
}

/**
 * Column filters for a list held whole in memory — the positions, team and templates grids. Options are the values the
 * rows actually hold, and a filter is offered only where there are two of them to choose between. Not TanStack's
 * filtered row model: the same option lists feed the toolbar's Filter panel, and the pager and empty states read the
 * filtered count before the table exists.
 */
export function useLocalGridFilters<TRow>(rows: readonly TRow[], specs: readonly LocalGridFilter<TRow>[]) {
  const [selected, setSelected] = useState<Record<string, string[]>>({});

  const filters = useMemo(() => {
    const offered: Record<string, DataGridCheckFilter> = {};
    for (const spec of specs) {
      const ticked = selected[spec.id] ?? [];
      const values = new Set<string>(ticked);
      for (const row of rows) for (const value of spec.valuesOf(row)) values.add(value);
      if (values.size < 2 && ticked.length === 0) continue;
      const options = [...values]
        .map((value) => ({ value, label: spec.labelOf(value) }))
        .sort((a, b) =>
          spec.order
            ? rank(spec.order, a.value) - rank(spec.order, b.value)
            : a.label.localeCompare(b.label, undefined, { sensitivity: "base" }),
        );
      offered[spec.id] = {
        kind: "check",
        label: spec.label,
        options,
        selected: ticked,
        onChange: (next) => setSelected((current) => ({ ...current, [spec.id]: next })),
        "aria-label": `Filter by ${spec.label.toLowerCase()}`,
      };
    }
    return offered;
  }, [rows, specs, selected]);

  const kept = useMemo(
    () =>
      rows.filter((row) =>
        specs.every((spec) => {
          const ticked = selected[spec.id];
          return !ticked?.length || spec.valuesOf(row).some((value) => ticked.includes(value));
        }),
      ),
    [rows, specs, selected],
  );

  const inForce = Object.values(selected).some((ticked) => ticked.length > 0);

  return {
    rows: kept,
    filters,
    inForce,
    /** Changes whenever a tick does, for a pager that returns to page one when the result narrows. */
    key: JSON.stringify(selected),
    clear: () => setSelected({}),
  };
}

function rank(order: readonly string[], value: string): number {
  const index = order.indexOf(value);
  return index === -1 ? order.length : index;
}
