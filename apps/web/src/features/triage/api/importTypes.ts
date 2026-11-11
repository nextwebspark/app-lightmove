import type { CustomColumnTarget, CustomColumnType } from "../../customcolumns/api/types";

/**
 * What one uploaded column becomes — the server's proposal and the confirmed mapping, one shape both ways. First that
 * applies wins: `targetField`, then `customFieldKey`, then `customLabel` (a new column), else ignored.
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
  /** What worked the mapping out. The three are worth different amounts of scrutiny. */
  mappingSource: MappingSource;
}

/**
 * `exactHeaders` — every header was a known name, so no model call was made.
 * `model` — the assistant resolved what the header matcher was unsure of.
 * `headerMatcher` — the matcher answered alone because the assistant could not be reached.
 */
export type MappingSource = "exactHeaders" | "model" | "headerMatcher";

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
