import { useMutation } from "@tanstack/react-query";
import { ApiRequestError } from "../../../lib/apiClient";
import { useState } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Button, FormError, Input, Select, Spinner } from "../../../components/ui";
import { FileDropzone } from "../../../components/ui/FileDropzone";
import { Modal } from "../../../components/ui/Modal";
import { codeOf, messageFor } from "../../../lib/errorCodes";
import type { CustomColumn, CustomColumnType } from "../../customcolumns/api/types";
import * as importApi from "../api/importApi";
import type {
  ImportPreview,
  ImportSummary,
  MappingSource,
  ProposedColumnMapping,
} from "../api/importTypes";

/** Which of the three steps the dialog is on. The file is the thread running through all of them. */
type Step = "choose" | "map" | "done";

const CUSTOM_TYPES: { value: CustomColumnType; label: string }[] = [
  { value: "text", label: "Text" },
  { value: "number", label: "Number" },
  { value: "date", label: "Date" },
  { value: "boolean", label: "Yes / no" },
];

const LABEL = "font-mono text-[10px] font-semibold uppercase tracking-[0.12em] text-text3";

const LINK = "rounded-[4px] text-sky underline transition hover:brightness-110 disabled:opacity-50";

const MAPPING_SOURCE_LABELS: Record<MappingSource, string> = {
  exactHeaders: "every column matched by name",
  model: "columns matched by the assistant",
  headerMatcher: "the assistant could not be reached — matched by header name, so check these",
};

/**
 * Failures the sample file is an answer to. Everything else — a refused seat, a name already taken —
 * is about this mandate rather than the shape of the file, and offering a download there is noise.
 */
const FILE_SHAPED_CODES = [
  "IMPORT_FILE_UNREADABLE",
  "IMPORT_TOO_MANY_ROWS",
  "UNSUPPORTED_FILE_TYPE",
  "FILE_TOO_LARGE",
];

/**
 * Failures that will fail again unchanged. A missing seat and a name clash are decided by state this
 * dialog cannot alter, so offering to retry them would be offering a button that cannot work.
 */
const UNRETRYABLE_CODES = ["FORBIDDEN", "NOT_FOUND", "CUSTOM_COLUMN_NAME_TAKEN", "CUSTOM_COLUMN_LIMIT_REACHED"];

/** What failed, so the error can offer to run that same request again. */
type FailedRequest = "preview" | "commit" | "template";

/**
 * Import a spreadsheet into this mandate's Companies grid.
 *
 * <p>Three steps in one dialog: choose the file, confirm what each of its columns means, read what
 * happened. The middle one is the whole point — a file's headers will not match ours, and a mapping
 * applied without being shown would write a consultant's data into the wrong fields silently.
 *
 * <p>The chosen `File` is held here across both requests. Preview and commit each carry it, so
 * nothing is held open server-side and there is no import that can expire between the two steps.
 */
export function ImportSpreadsheetDialog({
  open,
  projectId,
  customColumns,
  onClose,
  onImported,
}: {
  open: boolean;
  projectId: string;
  /** Offered in the dropdown, so a second import fills a column rather than proposing a twin. */
  customColumns: readonly CustomColumn[];
  onClose: () => void;
  /** Fired once the commit lands, so the grid and its counts refresh behind the dialog. */
  onImported: () => void;
}) {
  const [step, setStep] = useState<Step>("choose");
  const [file, setFile] = useState<File | null>(null);
  const [preview, setPreview] = useState<ImportPreview | null>(null);
  const [mappings, setMappings] = useState<ProposedColumnMapping[]>([]);
  const [summary, setSummary] = useState<ImportSummary | null>(null);
  const [failure, setFailure] = useState<Failure | null>(null);

  const reset = () => {
    setStep("choose");
    setFile(null);
    setPreview(null);
    setMappings([]);
    setSummary(null);
    setFailure(null);
  };

  const close = () => {
    reset();
    onClose();
  };

  const read = useMutation({
    mutationFn: (chosen: File) => importApi.previewImport(projectId, chosen),
    onMutate: () => setFailure(null),
    onSuccess: (result) => {
      setPreview(result);
      setMappings(result.columns.map((column) => column.mapping));
      setStep("map");
    },
    // The file is deliberately kept. Clearing it left nothing to retry, so a transient failure meant
    // finding the file on disk again to ask the same question.
    onError: (cause) => setFailure(failureOf(cause, "preview")),
  });

  const template = useMutation({
    mutationFn: () => importApi.saveTemplate(projectId),
    onMutate: () => setFailure(null),
    onError: (cause) => setFailure(failureOf(cause, "template")),
  });

  const commit = useMutation({
    mutationFn: () => importApi.commitImport(projectId, file!, mappings),
    onMutate: () => setFailure(null),
    onSuccess: (result) => {
      setSummary(result);
      setStep("done");
      onImported();
    },
    onError: (cause) => setFailure(failureOf(cause, "commit")),
  });

  const retry = () => {
    if (failure?.request === "preview" && file) return read.mutate(file);
    if (failure?.request === "commit") return commit.mutate();
    if (failure?.request === "template") return template.mutate();
  };

  const setMapping = (index: number, change: Partial<ProposedColumnMapping>) =>
    setMappings((current) =>
      current.map((mapping) => (mapping.index === index ? { ...mapping, ...change } : mapping)),
    );

  return (
    <Modal open={open} onClose={close} title="Import companies and people" className="md:w-[760px]">
      {failure && (
        // FormError carries its own bottom margin, which the action row tucks back under itself.
        <div>
          <FormError message={failure.message} />
          {(failure.retryable || failure.offerTemplate) && (
            <p className="-mt-2 mb-4 flex flex-wrap items-center gap-3 font-mono text-[11.5px] text-text3">
              {failure.retryable && (
                <button type="button" onClick={retry} className={LINK}>
                  Try again
                </button>
              )}
              {failure.offerTemplate && (
                <button type="button" onClick={() => template.mutate()} className={LINK}>
                  Download a sample file
                </button>
              )}
            </p>
          )}
        </div>
      )}

      {step === "choose" && (
        <div className="flex flex-col gap-3">
          <FileDropzone
            accept=".csv,.tsv,.xlsx,.xls"
            label="Spreadsheet to import"
            title="Choose a CSV or Excel file"
            hint="Companies, people, or both — one row per person at a company. Nothing is written until you have confirmed the columns."
            disabled={read.isPending}
            onFile={(chosen) => {
              setFile(chosen);
              read.mutate(chosen);
            }}
          />
          {read.isPending && (
            <span className="flex items-center gap-[7px] font-mono text-[11.5px] text-text3">
              <Spinner />
              Reading {file?.name}…
            </span>
          )}

          {/* Offered, never required — the import maps whatever headers a file arrives with. */}
          <TemplateLine
            lead="Not sure of the format?"
            onDownload={() => template.mutate()}
            pending={template.isPending}
          />
        </div>
      )}

      {step === "map" && preview && (
        <div className="flex flex-col gap-4">
          <p className="font-mono text-[11.5px] text-text3">
            {preview.fileName} · {preview.rowCount} {preview.rowCount === 1 ? "row" : "rows"} ·{" "}
            {/* Said plainly rather than hidden: the three differ in how far they are worth trusting,
                and a user who knows which one answered knows how hard to look at the rows below. */}
            {MAPPING_SOURCE_LABELS[preview.mappingSource]}
          </p>

          {/* Only where it is actually the fix: a file built from the template maps with no assistant
              call at all, so it is the way out of the degrade rather than a repeat of the offer. */}
          {preview.mappingSource === "headerMatcher" && (
            <TemplateLine
              lead="Rather start from a file we already understand?"
              onDownload={() => template.mutate()}
              pending={template.isPending}
            />
          )}

          <div className="max-h-[46dvh] overflow-y-auto rounded-lg border border-line-soft">
            <table className="w-full border-collapse">
              <thead className="sticky top-0 bg-panel2">
                <tr>
                  <th className={`p-2.5 text-start ${LABEL}`}>Column in your file</th>
                  <th className={`p-2.5 text-start ${LABEL}`}>Imports as</th>
                </tr>
              </thead>
              <tbody>
                {preview.columns.map((column) => {
                  const mapping = mappings.find((entry) => entry.index === column.index);
                  if (!mapping) return null;
                  return (
                    <tr key={column.index} className="border-t border-line-soft align-top">
                      <td className="w-[40%] p-2.5">
                        <span className="block truncate font-sans text-[13px] text-text">
                          {column.header}
                        </span>
                        <span className="mt-px block truncate font-mono text-[11px] text-text3">
                          {column.sampleValues.length > 0
                            ? column.sampleValues.join(" · ")
                            : "no values"}
                        </span>
                      </td>
                      <td className="p-2.5">
                        <Select
                          aria-label={`What "${column.header}" imports as`}
                          value={selectionOf(mapping)}
                          onChange={(event) =>
                            setMapping(column.index, changeFor(event.target.value, column.header))
                          }
                        >
                          <option value="ignore">Don't import</option>
                          <option value="new">New column…</option>
                          {customColumns.length > 0 && (
                            <optgroup label="This mandate's columns">
                              {customColumns.map((custom) => (
                                <option key={custom.id} value={`custom:${custom.fieldKey}`}>
                                  {custom.label}
                                </option>
                              ))}
                            </optgroup>
                          )}
                          <optgroup label="Company">
                            {preview.availableFields
                              .filter((field) => field.target === "company")
                              .map((field) => (
                                <option key={field.value} value={`field:${field.value}`}>
                                  {field.label}
                                </option>
                              ))}
                          </optgroup>
                          <optgroup label="Person">
                            {preview.availableFields
                              .filter((field) => field.target === "candidate")
                              .map((field) => (
                                <option key={field.value} value={`field:${field.value}`}>
                                  {field.label}
                                </option>
                              ))}
                          </optgroup>
                        </Select>

                        {failure?.fieldErrors[`columns[${column.index}]`] && (
                          <p className="mt-1.5 font-mono text-[11px] text-red">
                            {failure.fieldErrors[`columns[${column.index}]`]}
                          </p>
                        )}

                        {/* Only for a column being created. An existing one already has a name and a
                            type, and offering to change them here would edit every other row using it. */}
                        {mapping.targetField === null &&
                          mapping.customFieldKey === null &&
                          mapping.customLabel !== null && (
                            <div className="mt-2 flex flex-wrap gap-2">
                              <Input
                                aria-label={`Name of the new column for "${column.header}"`}
                                value={mapping.customLabel}
                                onChange={(event) =>
                                  setMapping(column.index, { customLabel: event.target.value })
                                }
                                className="min-w-[140px] flex-1"
                              />
                              <Select
                                aria-label={`What the new column for "${column.header}" describes`}
                                value={mapping.customTarget ?? "candidate"}
                                onChange={(event) =>
                                  setMapping(column.index, {
                                    customTarget: event.target.value as "company" | "candidate",
                                  })
                                }
                                className="w-auto flex-none"
                              >
                                <option value="candidate">About the person</option>
                                <option value="company">About the company</option>
                              </Select>
                              <Select
                                aria-label={`Kind of value in the new column for "${column.header}"`}
                                value={mapping.customType ?? "text"}
                                onChange={(event) =>
                                  setMapping(column.index, {
                                    customType: event.target.value as CustomColumnType,
                                  })
                                }
                                className="w-auto flex-none"
                              >
                                {CUSTOM_TYPES.map((type) => (
                                  <option key={type.value} value={type.value}>
                                    {type.label}
                                  </option>
                                ))}
                              </Select>
                            </div>
                          )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>

          <div className="flex justify-end gap-2">
            <Button variant="secondary" onClick={close} disabled={commit.isPending}>
              Cancel
            </Button>
            <Button onClick={() => commit.mutate()} loading={commit.isPending}>
              Import {preview.rowCount} {preview.rowCount === 1 ? "row" : "rows"}
            </Button>
          </div>
        </div>
      )}

      {step === "done" && summary && (
        <div className="flex flex-col gap-4">
          <ul className="flex flex-col gap-1.5 font-sans text-[13px] text-text2">
            <Tally count={summary.companiesCreated} noun="company" plural="companies" verb="added" />
            <Tally count={summary.companiesUpdated} noun="company" plural="companies" verb="updated" />
            <Tally
              count={summary.companiesSkipped}
              noun="company"
              plural="companies"
              verb="left as the market has them"
            />
            <Tally count={summary.candidatesCreated} noun="person" plural="people" verb="added" />
            <Tally count={summary.candidatesUpdated} noun="person" plural="people" verb="updated" />
          </ul>

          {summary.customColumnsCreated.length > 0 && (
            <p className="rounded-lg bg-sky-dim px-3 py-2 font-sans text-[12.5px] text-sky">
              New {summary.customColumnsCreated.length === 1 ? "column" : "columns"} on this mandate:{" "}
              {summary.customColumnsCreated.join(", ")}
            </p>
          )}

          {summary.rowErrors.length > 0 && (
            <div>
              <p className={`mb-1.5 ${LABEL}`}>
                {summary.rowErrors.length} {summary.rowErrors.length === 1 ? "row" : "rows"} skipped
              </p>
              <ul className="max-h-[24dvh] overflow-y-auto rounded-lg border border-line-soft">
                {summary.rowErrors.map((rowError) => (
                  <li
                    key={rowError.rowNumber}
                    className="flex gap-2 border-b border-line-soft px-2.5 py-1.5 font-mono text-[11.5px] text-text3 last:border-b-0"
                  >
                    <span className="flex-none text-red">Row {rowError.rowNumber}</span>
                    <span className="min-w-0 flex-1">{rowError.message}</span>
                  </li>
                ))}
              </ul>
            </div>
          )}

          <div className="flex justify-end gap-2">
            <Button variant="secondary" onClick={reset}>
              <Icon d={ICONS.uploadCloud} size={14} />
              Import another
            </Button>
            <Button onClick={close}>Done</Button>
          </div>
        </div>
      )}
    </Modal>
  );
}

/**
 * A failure the dialog can act on rather than only report: what to say, which request to run again,
 * and whether the sample file is an answer to it.
 */
interface Failure {
  message: string;
  request: FailedRequest;
  retryable: boolean;
  offerTemplate: boolean;
  /** Keyed `columns[i]` by the server, so a refused column marks the row it came from. */
  fieldErrors: Record<string, string>;
}

function failureOf(cause: unknown, request: FailedRequest): Failure {
  const code = codeOf(cause);
  return {
    message: messageFor(cause),
    request,
    retryable: code === null || !UNRETRYABLE_CODES.includes(code),
    offerTemplate: code !== null && FILE_SHAPED_CODES.includes(code),
    fieldErrors: cause instanceof ApiRequestError ? cause.fieldErrors : {},
  };
}

/**
 * The offer of a blank file to fill in. A button rather than a link because the bytes need the bearer
 * token, the same reason `importApi.saveTemplate` fetches them.
 */
function TemplateLine({
  lead,
  onDownload,
  pending,
}: {
  lead: string;
  onDownload: () => void;
  pending: boolean;
}) {
  return (
    <p className="font-mono text-[11.5px] text-text3">
      {lead}{" "}
      <button type="button" onClick={onDownload} disabled={pending} className={LINK}>
        Download a sample file
      </button>{" "}
      with this mandate's columns already in it.
    </p>
  );
}

/** A count line, or nothing at all — a summary of five zeros tells the reader less than three lines. */
function Tally({
  count,
  noun,
  plural,
  verb,
}: {
  count: number;
  noun: string;
  plural: string;
  verb: string;
}) {
  if (count === 0) return null;
  return (
    <li>
      <span className="font-semibold text-text">{count}</span> {count === 1 ? noun : plural} {verb}
    </li>
  );
}

/** The dropdown's value for a mapping — one string, so the `<select>` needs no parallel state. */
function selectionOf(mapping: ProposedColumnMapping): string {
  if (mapping.targetField) return `field:${mapping.targetField}`;
  if (mapping.customFieldKey) return `custom:${mapping.customFieldKey}`;
  if (mapping.customLabel !== null) return "new";
  return "ignore";
}

/**
 * The mapping a dropdown choice means. Every branch clears the fields the other branches own, so a
 * column switched from a new custom column to a built-in field cannot carry a stale label into the
 * commit — where the server would read it and define a column nobody asked for.
 */
function changeFor(selection: string, header: string): Partial<ProposedColumnMapping> {
  if (selection === "ignore") {
    return { targetField: null, customFieldKey: null, customLabel: null };
  }
  if (selection === "new") {
    return {
      targetField: null,
      customFieldKey: null,
      customLabel: header.trim(),
      customTarget: "candidate",
      customType: "text",
    };
  }
  if (selection.startsWith("field:")) {
    return {
      targetField: selection.slice("field:".length),
      customFieldKey: null,
      customLabel: null,
    };
  }
  return {
    targetField: null,
    customFieldKey: selection.slice("custom:".length),
    customLabel: null,
  };
}
