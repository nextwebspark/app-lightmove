import type { CustomColumnTarget, CustomColumnType } from "../../customcolumns/api/types";

/**
 * What one uploaded column becomes — the server's proposal, and what the mapping step sends back
 * once a person has confirmed or corrected it. One shape both ways, so the dialog has nothing to
 * translate.
 *
 * Exactly one outcome applies, read in this order: a `targetField` maps onto a built-in field;
 * otherwise a `customFieldKey` fills a custom column the mandate already has; otherwise a
 * `customLabel` defines a new one; otherwise the column is ignored.
 */
export interface ProposedColumnMapping {
  index: number;
  header: string;
  targetField: string | null;
  customFieldKey: string | null;
  customLabel: string | null;
  customTarget: CustomColumnTarget | null;
  customType: CustomColumnType | null;
}

/** One option in the mapping dropdown, sent with the preview so the two can never disagree. */
export interface ImportTargetField {
  value: string;
  label: string;
  target: CustomColumnTarget;
}

/** A column of the file as the mapping step shows it. The samples came from this browser. */
export interface ImportColumn {
  index: number;
  header: string;
  valueShape: string;
  sampleValues: string[];
  mapping: ProposedColumnMapping;
}

/**
 * The mapping step's whole input. No import id: nothing is held open server-side, because this
 * browser still has the file and confirming re-posts it.
 */
export interface ImportPreview {
  fileName: string;
  rowCount: number;
  columns: ImportColumn[];
  availableFields: ImportTargetField[];
  /** False when the model could not be reached and the header heuristic answered instead. */
  mappedByModel: boolean;
}

export interface ImportRowError {
  rowNumber: number;
  message: string;
}

export interface ImportSummary {
  rowsRead: number;
  companiesCreated: number;
  companiesUpdated: number;
  /** Companies left as the market published them — an import fills only their custom columns. */
  companiesSkipped: number;
  candidatesCreated: number;
  candidatesUpdated: number;
  customColumnsCreated: string[];
  rowErrors: ImportRowError[];
}
