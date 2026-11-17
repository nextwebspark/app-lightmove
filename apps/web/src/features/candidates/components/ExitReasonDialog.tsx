import { useState } from "react";
import { Button, Field, Modal, Select, TextArea } from "../../../components/ui";
import type { ExitReason } from "../../workspace/api/types";
import type { ExitReasonChoice } from "../api/types";

export const EXIT_NOTE_LIMIT = 500;

/**
 * Why someone left (`PositionExitReasonDialog.dc.html`): asked on a move to a way out that offers reasons, and again
 * to change the reason in place. A reason that asks for a note (Other) is saved only with one. Staff-only.
 */
export function ExitReasonDialog({
  name,
  wayOut,
  reasons,
  editing = false,
  initial = null,
  saving = false,
  onCancel,
  onConfirm,
}: {
  name: string;
  /** The out-of-process status they are moved to, or hold. */
  wayOut: string;
  reasons: ExitReason[];
  /** Changes the reason of someone already out of process: their status stays. */
  editing?: boolean;
  initial?: ExitReasonChoice | null;
  saving?: boolean;
  onCancel: () => void;
  onConfirm: (choice: ExitReasonChoice) => void;
}) {
  const [reasonId, setReasonId] = useState(initial?.reasonId ?? "");
  const [note, setNote] = useState(initial?.note ?? "");
  const reason = reasons.find((candidate) => candidate.id === reasonId) ?? null;
  const needsNote = reason?.requiresNote === true;
  const ready = reason !== null && (!needsNote || note.trim() !== "");

  const handleSubmit = () => {
    if (!ready || saving) return;
    onConfirm({ reasonId, note: note.trim() || null });
  };

  return (
    <Modal
      open
      onClose={onCancel}
      onSubmit={handleSubmit}
      title={editing ? `Change why ${name} left` : `Why did ${name} leave?`}
      subtitle={
        editing
          ? `${wayOut}. Their status stays; only the reason changes.`
          : `Moving them to ${wayOut}.`
      }
      footer={
        <>
          <span className="me-auto font-mono text-meta text-u-text3">
            Seen by your team only
          </span>
          <Button
            type="button"
            variant="secondary"
            size="md"
            onClick={onCancel}
          >
            Cancel
          </Button>
          <Button type="submit" size="md" disabled={!ready} loading={saving}>
            {editing ? "Save reason" : `Move to ${wayOut}`}
          </Button>
        </>
      }
    >
      <Field label="Reason">
        <Select
          aria-label="Reason"
          value={reasonId}
          onChange={(event) => setReasonId(event.target.value)}
          autoFocus
        >
          <option value="">Choose a reason</option>
          {reasons.map((option) => (
            <option key={option.id} value={option.id}>
              {option.label}
            </option>
          ))}
        </Select>
      </Field>
      {(needsNote || note !== "") && (
        <Field
          label="Note"
          action={
            <span className="font-mono text-meta text-u-text3">
              {note.length}/{EXIT_NOTE_LIMIT}
            </span>
          }
        >
          <TextArea
            aria-label="Note"
            value={note}
            rows={3}
            maxLength={EXIT_NOTE_LIMIT}
            placeholder="Say why, in a line or two"
            onChange={(event) => setNote(event.target.value)}
          />
        </Field>
      )}
    </Modal>
  );
}
