import { useMutation } from "@tanstack/react-query";
import { useState } from "react";
import { Icon, ICONS } from "../../../../components/layout/Icon";
import { Button, FormError, useToast } from "../../../../components/ui";
import { FileDropzone } from "../../../../components/ui/FileDropzone";
import { Modal } from "../../../../components/ui/Modal";
import { messageFor } from "../../../../lib/errorCodes";
import * as evalApi from "../api/evalApi";
import type { EvalCaseImportResult, EvalDatasetOverview } from "../api/types";
import { countOf } from "../lib/evalLabels";

/**
 * Add synthetic cases to a dataset from a JSONL file: choose it, read what it would add, then add. The `File` is
 * held here and sent twice, so nothing is held open server-side between the two, and nothing is added while a
 * row is at fault.
 */
export function ImportCasesDialog({
  dataset,
  onClose,
  onImported,
}: {
  dataset: EvalDatasetOverview;
  onClose: () => void;
  onImported: () => void;
}) {
  const toast = useToast();
  const [file, setFile] = useState<File | null>(null);
  const [plan, setPlan] = useState<EvalCaseImportResult | null>(null);
  const [failure, setFailure] = useState<string | null>(null);

  const preview = useMutation({
    mutationFn: (chosen: File) => evalApi.previewCaseImport(dataset.id, chosen),
    onMutate: () => setFailure(null),
    onSuccess: setPlan,
    onError: (cause) => setFailure(messageFor(cause)),
  });

  const commit = useMutation({
    mutationFn: (chosen: File) => evalApi.commitCaseImport(dataset.id, chosen),
    onMutate: () => setFailure(null),
    onSuccess: (result) => {
      toast.success(`${countOf(result.added, "case")} added to ${dataset.name}`);
      onImported();
      onClose();
    },
    onError: (cause) => setFailure(messageFor(cause)),
  });

  const handleChoose = (chosen: File) => {
    setFile(chosen);
    setPlan(null);
    preview.mutate(chosen);
  };

  const chooseAnother = () => {
    setFile(null);
    setPlan(null);
    setFailure(null);
  };

  const rowsAtFault = plan ? new Set(plan.problems.map((problem) => problem.line)).size : 0;
  const canAdd = plan !== null && rowsAtFault === 0 && plan.added > 0;

  return (
    <Modal
      open
      onClose={onClose}
      title="Import cases"
      className="md:w-[520px]"
      footer={
        <>
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="button" disabled={!canAdd} loading={commit.isPending} onClick={() => file && commit.mutate(file)}>
            {canAdd ? `Add ${countOf(plan.added, "case")}` : "Add cases"}
          </Button>
        </>
      }
    >
      <p className="mb-4 text-body text-u-text2">
        Add synthetic cases to {dataset.name} from a JSONL file, one case per line. Only fictional people and
        companies: a case is checked by a person before it counts.
      </p>
      <FormError message={failure} />

      {file === null || (plan === null && !preview.isPending) ? (
        <FileDropzone
          accept=".jsonl,.json,application/jsonl,application/x-ndjson"
          label="Choose a file of cases"
          title="Drop a .jsonl file here, or choose one"
          hint="One case per line"
          onFile={handleChoose}
        />
      ) : (
        <button
          type="button"
          onClick={chooseAnother}
          disabled={preview.isPending || commit.isPending}
          className="flex w-full items-center gap-3 rounded-lg border border-dashed border-u-border-strong bg-u-sunken p-3.5 text-left text-u-text disabled:opacity-60"
        >
          <Icon d={ICONS.file} size={18} className="flex-none text-u-text3" />
          <span className="min-w-0 flex-1">
            <span className="block truncate text-body font-medium">{file.name}</span>
            <span className="font-mono text-xs text-u-text3">
              {plan ? `${countOf(plan.rows, "row")} · choose another file` : "Reading the file"}
            </span>
          </span>
        </button>
      )}

      {plan && rowsAtFault === 0 && (
        <p role="status" className="mt-3 text-xs text-u-text2">
          {countOf(plan.added, "new case")}, {plan.alreadyPresent} already in the dataset. {plan.dev} dev,{" "}
          {plan.holdout} holdout. Every row has the right shape.
        </p>
      )}
      {plan && rowsAtFault > 0 && (
        <div role="alert" className="mt-3 rounded-lg border border-u-border bg-u-offlimits-tint px-3.5 py-2.5">
          <div className="text-body font-semibold">
            {countOf(rowsAtFault, "row")} cannot be imported. Fix the file and choose it again.
          </div>
          {plan.problems.map((problem, index) => (
            <div key={`${problem.line}-${index}`} className="mt-1.5 flex gap-2.5 text-xs text-u-text2">
              <span className="w-[52px] flex-none font-u-num text-u-text3">Row {problem.line}</span>
              <span className="min-w-0 [overflow-wrap:anywhere]">{problem.message}</span>
            </div>
          ))}
        </div>
      )}
    </Modal>
  );
}
