import { Field, Input, Select } from "../../../components/ui";
import type { CustomColumn, CustomFieldValues } from "../api/types";

/**
 * The inputs for a mandate's own columns, for one half of a Companies-grid row.
 *
 * <p>Rendered inside the drawer forms that already edit every built-in field, rather than as inline
 * editing in the grid: nothing on these screens is edited in a cell, and a custom column that were
 * would be the one field on the page behaving differently from the twenty beside it.
 *
 * <p>Controlled from outside and holding no state. The form owns the values so they travel in the
 * same request as the fields above them — one save, one audit event, and no half-saved row.
 *
 * <p>The values are strings whatever the column's declared type. The type decides what the server
 * accepts, never how it is stored, so a column corrected from text to number later does not lose the
 * rows already filled in.
 */
export function CustomFieldsFieldset({
  columns,
  values,
  onChange,
}: {
  /** Already narrowed to one target by the caller — a company form shows no personal columns. */
  columns: readonly CustomColumn[];
  values: CustomFieldValues;
  onChange: (values: CustomFieldValues) => void;
}) {
  const visible = columns.filter((column) => !column.hidden);
  if (visible.length === 0) return null;

  const set = (fieldKey: string, value: string) => onChange({ ...values, [fieldKey]: value });

  return (
    <div className="mt-1 border-t border-line-soft pt-4">
      <p className="mb-3 font-mono text-[10px] font-semibold uppercase tracking-[0.12em] text-text3">
        This mandate's columns
      </p>
      <div className="grid grid-cols-1 gap-x-3 sm:grid-cols-2">
        {visible.map((column) => (
          <Field key={column.id} label={column.label}>
            {column.dataType === "boolean" ? (
              <Select
                value={values[column.fieldKey] ?? ""}
                onChange={(event) => set(column.fieldKey, event.target.value)}
              >
                {/* Blank is a real choice, not a placeholder: it is how a value is cleared. */}
                <option value="">—</option>
                <option value="true">Yes</option>
                <option value="false">No</option>
              </Select>
            ) : (
              <Input
                type={column.dataType === "date" ? "date" : "text"}
                inputMode={column.dataType === "number" ? "numeric" : undefined}
                value={values[column.fieldKey] ?? ""}
                onChange={(event) => set(column.fieldKey, event.target.value)}
              />
            )}
          </Field>
        ))}
      </div>
    </div>
  );
}
