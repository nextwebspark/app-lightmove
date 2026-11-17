import { useState } from "react";
import { Button, Field, Modal, Select, TextArea } from "../../../components/ui";
import type { ExitReason, PipelineStatus } from "../../workspace/api/types";
import type { ExitReasonChoice } from "../api/types";

export const EXIT_NOTE_LIMIT = 500;

/**
 * What saving does: a move out of process lands on a way out, with a reason where it asks for one; a change keeps
 * the way out and always saves a reason.
 */
export type ExitDialogPurpose =
  | { kind: "move"; onConfirm: (way: PipelineStatus, choice: ExitReasonChoice | null) => void }
  | { kind: "change"; onSave: (choice: ExitReasonChoice) => void };

/**
 * Moving someone out of process (`PositionExitReasonDialog.dc.html`): one dialog wherever it starts — the status
 * picker, a drop on the board's Out of process band, or the drawer's Why line. Way out, then Reason, then a note for a
 * reason that asks for one. A stage with one way out states it rather than offering a choice of one. The Reason row is
 * always drawn, so choosing a way out never moves the dialog. Staff-only.
 */
export function ExitReasonDialog({
  name,
  stageLabel,
  ways,
  initialWayId = null,
  reasonsOf,
  purpose,
  initial = null,
  saving = false,
  onCancel,
}: {
  name: string;
  /** The stage whose ways out are offered: "Moving them out of process at Approach." */
  stageLabel: string | null;
  /** The ways out to choose between; a picker's own choice comes first-filled and stays changeable. */
  ways: PipelineStatus[];
  initialWayId?: string | null;
  /** The live reasons a way out offers, in the list's order. */
  reasonsOf: (way: PipelineStatus) => ExitReason[];
  purpose: ExitDialogPurpose;
  initial?: ExitReasonChoice | null;
  saving?: boolean;
  onCancel: () => void;
}) {
  const editing = purpose.kind === "change";
  const [wayId, setWayId] = useState(initialWayId ?? (ways.length === 1 ? ways[0].id : ""));
  const [reasonId, setReasonId] = useState(initial?.reasonId ?? "");
  const [note, setNote] = useState(initial?.note ?? "");

  const way = ways.find((candidate) => candidate.id === wayId) ?? null;
  const reasons = way ? reasonsOf(way) : [];
  const reason = reasons.find((candidate) => candidate.id === reasonId) ?? null;
  const asksReason = reasons.length > 0;
  const needsNote = reason?.requiresNote === true;
  const reasonRequired = editing || asksReason;
  const ready = way !== null && (!reasonRequired || reason !== null) && (!needsNote || note.trim() !== "");

  const handleWay = (nextId: string) => {
    setWayId(nextId);
    const next = ways.find((candidate) => candidate.id === nextId);
    // A reason the new way out does not offer would be refused: keep it only where it still fits.
    if (!next || !reasonsOf(next).some((candidate) => candidate.id === reasonId)) setReasonId("");
  };
  const handleSubmit = () => {
    if (!ready || saving || !way) return;
    const choice = reason ? { reasonId: reason.id, note: note.trim() || null } : null;
    if (purpose.kind === "move") purpose.onConfirm(way, choice);
    else if (choice) purpose.onSave(choice);
  };

  const subtitle = editing
    ? `${way?.label ?? "Out of process"}. Their status stays; only the reason changes.`
    : stageLabel
      ? `Moving them out of process at ${stageLabel}.`
      : "Moving them out of process.";

  return (
    <Modal
      open
      onClose={onCancel}
      onSubmit={handleSubmit}
      title={editing ? `Change why ${name} left` : `Why did ${name} leave?`}
      subtitle={subtitle}
      footer={
        <>
          <span className="me-auto font-mono text-meta text-u-text3">Seen by your team only</span>
          <Button type="button" variant="secondary" size="md" onClick={onCancel}>
            Cancel
          </Button>
          <Button type="submit" size="md" disabled={!ready} loading={saving}>
            {editing ? "Save reason" : way ? `Move to ${way.label}` : "Move out of process"}
          </Button>
        </>
      }
    >
      <Field label="Way out">
        {ways.length === 1 ? (
          <span
            role="textbox"
            aria-readonly="true"
            aria-label="Way out"
            className="block w-full truncate rounded-u-control border border-u-border bg-u-surface px-3 py-2.5 font-mono text-body text-u-text"
          >
            {ways[0].label}
          </span>
        ) : (
          <Select
            aria-label="Way out"
            value={wayId}
            onChange={(event) => handleWay(event.target.value)}
            autoFocus={!way}
          >
            {!way && <option value="">Choose a way out</option>}
            {ways.map((option) => (
              <option key={option.id} value={option.id}>
                {option.label}
              </option>
            ))}
          </Select>
        )}
      </Field>
      {/* The hint always holds a line, so picking a way out changes words, never the dialog's height. */}
      <Field
        label="Reason"
        hint={
          !way
            ? "Each way out offers its own reasons."
            : asksReason
              ? `The reasons your team offers for ${way.label}.`
              : `${way.label} asks for no reason.`
        }
      >
        <Select
          aria-label="Reason"
          value={reasonId}
          disabled={!asksReason}
          onChange={(event) => setReasonId(event.target.value)}
          autoFocus={way !== null && asksReason}
        >
          <option value="">{!way ? "Choose a way out first" : asksReason ? "Choose a reason" : "None asked for"}</option>
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
