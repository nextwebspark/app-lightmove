import type { ReactNode } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { ConfirmDialog } from "../../../components/ui/ConfirmDialog";
import type { PipelineCatalog, PipelineRetirePreview } from "../../workspace/api/types";
import { statusById, triggerCopyOf } from "../../workspace/lib/statusAutomation";

/**
 * The question asked before a change that touches anyone — a retire, or taking a status out of process: which
 * statuses it reaches and who holds them, what happens to those people, and every automation that stops until
 * another status is chosen.
 */
export function PipelineRetireDialog({
  open,
  title,
  confirmLabel,
  catalog,
  preview,
  pending,
  explanation = "Candidates keep the status they hold, but it won't be offered again. Nothing is deleted.",
  onConfirm,
  onClose,
}: {
  open: boolean;
  title: string;
  confirmLabel: string;
  catalog: PipelineCatalog;
  preview: PipelineRetirePreview | null;
  pending: boolean;
  /** What becomes of whoever holds the statuses. */
  explanation?: ReactNode;
  onConfirm: () => void;
  onClose: () => void;
}) {
  return (
    <ConfirmDialog
      open={open}
      title={title}
      confirmLabel={confirmLabel}
      pending={pending}
      confirmDisabled={!preview}
      onConfirm={onConfirm}
      onClose={onClose}
    >
      {preview && (
        <div className="flex flex-col gap-3">
          {preview.statuses.length > 0 && (
            <ul className="flex flex-col divide-y divide-u-border rounded-md border border-u-border">
              {preview.statuses.map((status) => (
                <li key={status.statusId} className="flex items-center justify-between gap-3 px-3 py-2 text-note">
                  <span className="text-u-text">{status.label}</span>
                  <span className="font-mono text-meta text-u-text3">{holdersLabel(status.holders)}</span>
                </li>
              ))}
            </ul>
          )}
          <p className="text-body text-u-text2">{explanation}</p>
          {preview.automations.length > 0 && (
            <div role="note" className="rounded-md border border-u-signal/40 bg-u-signal-tint px-3 py-2.5">
              <p className="flex items-center gap-1.5 text-note font-semibold text-u-text">
                <Icon d={ICONS.warning} size={13} className="text-u-signal" />
                {preview.automations.length === 1 ? "An automation stops" : "Automations stop"}
              </p>
              <ul className="mt-1 flex flex-col gap-0.5 text-note text-u-text2">
                {preview.automations.map((automation) => (
                  <li key={automation.trigger}>
                    {triggerCopyOf(automation.trigger).when} → {statusById(catalog, automation.statusId)?.label}
                  </li>
                ))}
              </ul>
              <p className="mt-1 text-meta text-u-text3">
                Nobody is moved by {preview.automations.length === 1 ? "it" : "them"} until you choose another
                status under Move candidates automatically.
              </p>
            </div>
          )}
        </div>
      )}
    </ConfirmDialog>
  );
}

function holdersLabel(holders: number): string {
  if (holders === 0) return "No candidates";
  return holders === 1 ? "1 candidate" : `${holders} candidates`;
}
