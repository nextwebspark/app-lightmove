import { Button, Modal } from "../../../components/ui";
import type { Candidate } from "../api/types";

/**
 * Confirms removing an executive from the mandate.
 *
 * <p>The copy does the work, as it does for a company. What goes is this mandate's research on a
 * person — the status, the note, the package that was established — and nothing else: another mandate
 * researching the same executive holds its own row, and that row is untouched.
 *
 * <p>It also says what a status could have said instead, because someone reaching for Remove often
 * means "not for this brief" rather than "never happened": that is Out of scope, and it keeps the
 * mapping the research paid for.
 */
export function RemoveCandidateDialog({
  candidate,
  removing,
  onCancel,
  onConfirm,
}: {
  /** The executive awaiting confirmation, or null when the dialog is closed. */
  candidate: Candidate | null;
  removing: boolean;
  onCancel: () => void;
  onConfirm: (candidate: Candidate) => void;
}) {
  if (!candidate) return null;

  return (
    <Modal open onClose={onCancel} title={`Remove ${candidate.fullName}?`}>
      <p className="text-[13px]/[1.6] text-text2">
        This removes this mandate's research on {candidate.fullName} — their status, note and
        compensation go with it.
      </p>
      <p className="mt-2.5 text-[13px]/[1.6] text-text3">
        If they are simply not right for this brief, set their status to Out of scope instead. The map
        keeps the work, and the next mandate sees they were already found.
      </p>

      <div className="mt-5 flex justify-end gap-2">
        <Button variant="secondary" onClick={onCancel} disabled={removing}>
          Cancel
        </Button>
        <Button variant="primary" loading={removing} onClick={() => onConfirm(candidate)}>
          Remove from mandate
        </Button>
      </div>
    </Modal>
  );
}
