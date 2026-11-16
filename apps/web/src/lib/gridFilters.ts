/** One column's free-text filter: "contains". */
export interface DataGridTextFilter {
  kind?: "text";
  /** The column's name, as the toolbar's Filter panel lists it. */
  label: string;
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  "aria-label": string;
}

/** One column's closed-vocabulary filter (Status, Role): values ticked from a list rather than typed. */
export interface DataGridCheckFilter {
  kind: "check";
  label: string;
  /** `indent` draws an option as a child of the one above it — a status under its stage. */
  options: { value: string; label: string; indent?: boolean }[];
  selected: string[];
  onChange: (selected: string[]) => void;
  "aria-label": string;
}

/** A column's filter, keyed by column id — edited from its header menu and listed by the toolbar's Filter button. */
export type DataGridColumnFilter = DataGridTextFilter | DataGridCheckFilter;

export function isFilterInForce(filter: DataGridColumnFilter): boolean {
  return filter.kind === "check" ? filter.selected.length > 0 : filter.value.length > 0;
}

export function clearFilter(filter: DataGridColumnFilter) {
  if (filter.kind === "check") filter.onChange([]);
  else filter.onChange("");
}

export function filterSummary(filter: DataGridCheckFilter): string {
  const labels = filter.selected.map(
    (value) => filter.options.find((option) => option.value === value)?.label ?? value,
  );
  return labels.length > 2 ? `${labels.slice(0, 2).join(", ")} +${labels.length - 2}` : labels.join(", ");
}
