/** Which half of a Companies-grid row a custom column describes. A row is a person at a company. */
export type CustomColumnTarget = "company" | "candidate";

/** What a custom column holds, and therefore which cell and input it renders. */
export type CustomColumnType = "text" | "number" | "date" | "boolean";

/**
 * One column a mandate has added to its own grid.
 *
 * `fieldKey` rather than `id` is what a row's `customFields` map is keyed by: the key is slugged once
 * from the label and never moves, so renaming a header cannot orphan the values already stored.
 */
export interface CustomColumn {
  id: string;
  target: CustomColumnTarget;
  fieldKey: string;
  label: string;
  dataType: CustomColumnType;
  displayOrder: number;
  hidden: boolean;
}

export interface CustomColumnsResponse {
  columns: CustomColumn[];
}

export interface DefineCustomColumnPayload {
  target: CustomColumnTarget;
  label: string;
  dataType: CustomColumnType;
}

/** Every field optional: a null leaves that half alone, so hiding need not restate the name. */
export interface UpdateCustomColumnPayload {
  label?: string;
  dataType?: CustomColumnType;
  hidden?: boolean;
}

/** The values one row holds for its project's custom columns, keyed by `fieldKey`. */
export type CustomFieldValues = Record<string, string>;
