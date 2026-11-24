import type { DocumentReading, FieldSource } from "../api/types";
import type { PositionSnapshot } from "./documentFill";
import { CONTEXT_FIELD_KEYS, DETAILS_FIELD_KEYS, REPORTING_FIELD_KEYS } from "./provenance";

/** The autosave channels a document reading fills; each sends its reading once, with the write the fill schedules. */
export type ReadingChannel = "details" | "context" | "reporting" | "criteria" | "competencies";

/** The prompt version each extraction answered with; null or absent when the heuristic did. */
export interface ReadingVersions {
  details?: number | null;
  context?: number | null;
  reporting?: number | null;
  assessment?: number | null;
}

/**
 * What a fill left as the document's on each channel (#1452), read off the fill's own payload rather than the write
 * that sends it, so an edit made before that write goes out still counts as a change.
 */
export function documentReadingsOf(
  next: PositionSnapshot,
  versions: ReadingVersions,
): Record<ReadingChannel, DocumentReading> {
  return {
    details: readingOf(versions.details, documentKeys(next.details.fieldSources, DETAILS_FIELD_KEYS), {
      responsibilities: fromDocument(next.details.responsibilities),
    }),
    context: readingOf(versions.context, documentKeys(next.context.fieldSources, CONTEXT_FIELD_KEYS), {
      strategicPriorities: fromDocument(next.context.strategicPriorities),
    }),
    reporting: readingOf(versions.reporting, documentKeys(next.reporting.fieldSources, REPORTING_FIELD_KEYS), {
      orgChart: fromDocument(next.reporting.orgChart),
    }),
    criteria: readingOf(versions.assessment, [], { criteria: fromDocument(next.criteria) }),
    competencies: readingOf(versions.assessment, [], {
      technicalCompetencies: fromDocument(next.technical),
      behaviouralCompetencies: fromDocument(next.behavioural),
    }),
  };
}

function readingOf(version: number | null | undefined, fields: string[], lists: Record<string, number>) {
  return {
    version: version ?? null,
    fields,
    listItems: Object.fromEntries(Object.entries(lists).filter(([, rows]) => rows > 0)),
  };
}

function documentKeys(fieldSources: Record<string, FieldSource>, keys: ReadonlySet<string>): string[] {
  return [...keys].filter((key) => fieldSources[key] === "DOCUMENT");
}

function fromDocument(rows: readonly { source?: FieldSource }[]): number {
  return rows.filter((row) => row.source === "DOCUMENT").length;
}
