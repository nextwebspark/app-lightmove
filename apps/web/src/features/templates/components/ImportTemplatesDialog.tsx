import { useMutation } from "@tanstack/react-query";
import { useState, type ReactNode } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Button, FormError, useToast } from "../../../components/ui";
import { FileDropzone } from "../../../components/ui/FileDropzone";
import { Modal } from "../../../components/ui/Modal";
import { cn } from "../../../lib/cn";
import { messageFor } from "../../../lib/errorCodes";
import * as templateApi from "../api/templateAdminApi";
import type { TemplateImportAction, TemplateImportResult, TemplateScope } from "../api/types";
import { AI_TEMPLATE_PROMPT } from "../lib/aiPrompt";

const ACTION_BADGES: Record<TemplateImportAction, { label: string; className: string }> = {
  CREATE: { label: "Create", className: "bg-green-dim text-green" },
  UPDATE: { label: "Update", className: "bg-sky-dim text-sky" },
  CUSTOMISE: { label: "Customise", className: "bg-amber-dim text-amber" },
  UNCHANGED: { label: "Unchanged", className: "bg-panel2 text-text3" },
  INVALID: { label: "Invalid", className: "bg-red-dim text-red" },
};

const SUMMARY_ORDER: TemplateImportAction[] = ["CREATE", "UPDATE", "CUSTOMISE", "UNCHANGED", "INVALID"];

const SCOPE_LINES: Record<TemplateScope, string> = {
  library: "Into the LightMove library — every workspace without its own copy sees the result",
  workspace: "Into your firm's templates — library templates in the file become your own copies",
};

const LINK = "text-[12.5px] font-medium text-sky transition hover:underline disabled:opacity-50";

/**
 * Import a template file: choose it, read what each template in it would do, then write — all of it,
 * or nothing if any template is invalid. The `File` is held here and sent twice, so nothing is held
 * open server-side between the preview and the import.
 */
export function ImportTemplatesDialog({
  open,
  scope,
  onClose,
  onImported,
}: {
  open: boolean;
  scope: TemplateScope;
  onClose: () => void;
  onImported: () => void;
}) {
  const toast = useToast();
  const [file, setFile] = useState<File | null>(null);
  const [plan, setPlan] = useState<TemplateImportResult | null>(null);
  const [failure, setFailure] = useState<string | null>(null);

  const reset = () => {
    setFile(null);
    setPlan(null);
    setFailure(null);
  };

  const close = () => {
    reset();
    onClose();
  };

  const preview = useMutation({
    mutationFn: (chosen: File) => templateApi.previewImport(scope, chosen),
    onMutate: () => setFailure(null),
    onSuccess: setPlan,
    onError: (cause) => setFailure(messageFor(cause)),
  });

  const commit = useMutation({
    mutationFn: (chosen: File) => templateApi.commitImport(scope, chosen),
    onMutate: () => setFailure(null),
    onSuccess: (result) => {
      const written = writesIn(result);
      toast(`Imported ${written} template${written === 1 ? "" : "s"}`);
      onImported();
      close();
    },
    onError: (cause) => setFailure(messageFor(cause)),
  });

  const download = useMutation({
    mutationFn: (job: () => Promise<void>) => job(),
    onError: (cause) => setFailure(messageFor(cause)),
  });

  const handleChoose = (chosen: File) => {
    setFile(chosen);
    setPlan(null);
    preview.mutate(chosen);
  };

  const handleCopyPrompt = async () => {
    try {
      await navigator.clipboard.writeText(AI_TEMPLATE_PROMPT);
      toast("AI prompt copied — paste it with the schema into ChatGPT or Claude");
    } catch {
      setFailure("Your browser would not copy to the clipboard.");
    }
  };

  const invalid = plan?.rows.some((row) => row.action === "INVALID") ?? false;
  const toWrite = plan ? writesIn(plan) : 0;

  return (
    <Modal
      open={open}
      onClose={close}
      title="Import templates"
      className="md:w-[640px]"
      footer={
        <>
          <Button variant="secondary" onClick={close}>
            Cancel
          </Button>
          {plan && file && (
            <Button disabled={invalid || toWrite === 0} loading={commit.isPending} onClick={() => commit.mutate(file)}>
              {invalid || toWrite === 0
                ? "Import"
                : `Import ${toWrite} template${toWrite === 1 ? "" : "s"}`}
            </Button>
          )}
        </>
      }
    >
      <p className="-mt-2 mb-4 font-mono text-[11.5px] text-text3">{SCOPE_LINES[scope]}</p>
      <FormError message={failure} />

      {plan === null ? (
        <>
          <FileDropzone
            accept=".json,application/json"
            label="Choose a template file"
            title={preview.isPending ? "Reading the file…" : "Drop a .json file here, or choose one"}
            hint="lightmove.position-templates · up to 100 templates · 1 MB"
            disabled={preview.isPending}
            onFile={handleChoose}
          />
          <div className="mt-4 grid grid-cols-1 gap-3 md:grid-cols-2">
            <HelpCard
              title="Start from an export"
              body="Export the current templates, edit the file, import it back. Templates you didn't change are skipped."
            >
              <button
                type="button"
                className={LINK}
                disabled={download.isPending}
                onClick={() => download.mutate(() => templateApi.exportTemplates(scope))}
              >
                Export templates
              </button>
            </HelpCard>
            <HelpCard
              title="Write one with AI"
              body="Give ChatGPT or Claude the schema and the prompt, describe the role, and import what it writes."
            >
              <button
                type="button"
                className={LINK}
                disabled={download.isPending}
                onClick={() => download.mutate(() => templateApi.downloadSchema(scope))}
              >
                Download schema
              </button>
              <button type="button" className={LINK} onClick={handleCopyPrompt}>
                Copy AI prompt
              </button>
            </HelpCard>
          </div>
        </>
      ) : (
        <>
          <div className="mb-3 flex flex-wrap items-center gap-2.5">
            <Icon d={ICONS.file} size={16} className="flex-none text-text3" />
            <span className="text-[13px] font-medium">{file?.name}</span>
            <span className="font-mono text-[11.5px] text-text3">
              {plan.rows.length} template{plan.rows.length === 1 ? "" : "s"}
            </span>
            <button type="button" className={cn(LINK, "ms-auto")} onClick={reset}>
              Choose a different file
            </button>
          </div>
          <div className="overflow-hidden rounded-[10px] border border-line-soft">
            {plan.rows.map((row, index) => (
              <div
                key={`${row.code ?? row.title}-${index}`}
                className={cn(
                  "px-3.5 py-2.5",
                  index > 0 && "border-t border-line-soft",
                  row.action === "INVALID" && "bg-red-dim",
                )}
              >
                <div className="flex items-center gap-2.5">
                  <div className="min-w-0 flex-1">
                    <div className="text-[13px] font-medium">{row.title}</div>
                    {row.code && <div className="font-mono text-[11px] text-text3">{row.code}</div>}
                  </div>
                  <span
                    className={cn(
                      "flex-none rounded-full px-2.5 py-0.5 font-mono text-[9.5px] font-semibold uppercase tracking-[0.05em]",
                      ACTION_BADGES[row.action].className,
                    )}
                  >
                    {ACTION_BADGES[row.action].label}
                  </span>
                </div>
                {row.problems.length > 0 && (
                  <ul className="mt-1.5 flex flex-col gap-0.5">
                    {row.problems.map((problem) => (
                      <li key={`${problem.field}-${problem.message}`} className="font-mono text-[11.5px] text-red">
                        {problem.field} — {problem.message}
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            ))}
          </div>
          <p className="mt-3 font-mono text-xs font-medium text-text2">{summaryOf(plan)}</p>
          {invalid && (
            <p className="mt-2.5 rounded-lg border border-red bg-red-dim px-3 py-2.5 font-mono text-xs text-text2">
              Nothing is written until every template in the file passes. Fix the invalid one and choose the
              file again.
            </p>
          )}
        </>
      )}
    </Modal>
  );
}

function HelpCard({ title, body, children }: { title: string; body: string; children: ReactNode }) {
  return (
    <div className="rounded-[10px] border border-line-soft bg-panel2 p-3.5">
      <div className="text-[13px] font-semibold">{title}</div>
      <p className="mb-2.5 mt-1 font-mono text-[11.5px] leading-relaxed text-text3">{body}</p>
      <div className="flex flex-wrap gap-3.5">{children}</div>
    </div>
  );
}

function writesIn(result: TemplateImportResult): number {
  return result.rows.filter((row) => ["CREATE", "UPDATE", "CUSTOMISE"].includes(row.action)).length;
}

function summaryOf(plan: TemplateImportResult): string {
  return SUMMARY_ORDER.map((action) => [action, plan.rows.filter((row) => row.action === action).length] as const)
    .filter(([, count]) => count > 0)
    .map(([action, count]) => `${count} ${ACTION_BADGES[action].label.toLowerCase()}`)
    .join(" · ");
}
