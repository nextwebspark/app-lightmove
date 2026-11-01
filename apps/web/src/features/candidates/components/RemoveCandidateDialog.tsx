import { useState } from "react";
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
 *
 * <p>When they are the only one at their company, a tick box (unticked) offers to remove the company too; left
 * alone, the company stays in universe with nobody at it, as a company may.
 */
export function RemoveCandidateDialog({
  candidate,
  soleAtCompany = false,
  removing,
  onCancel,
  onConfirm,
}: {
  /** The executive awaiting confirmation, or null when the dialog is closed. */
  candidate: Candidate | null;
  /** They are under a company and nobody else is mapped there, on any stage. */
  soleAtCompany?: boolean;
  removing: boolean;
  onCancel: () => void;
  onConfirm: (candidate: Candidate, options: { withCompany: boolean }) => void;
}) {
  if (!candidate) return null;

  return (
    <RemoveCandidateConfirmation
      key={candidate.id}
      candidate={candidate}
      offersCompany={soleAtCompany && !!candidate.triageCompanyId && !!candidate.companyName}
      removing={removing}
      onCancel={onCancel}
      onConfirm={onConfirm}
    />
  );
}

function RemoveCandidateConfirmation({
  candidate,
  offersCompany,
  removing,
  onCancel,
  onConfirm,
}: {
  candidate: Candidate;
  offersCompany: boolean;
  removing: boolean;
  onCancel: () => void;
  onConfirm: (candidate: Candidate, options: { withCompany: boolean }) => void;
}) {
  const [withCompany, setWithCompany] = useState(false);

  return (
    <Modal open onClose={onCancel} title={`Remove ${candidate.fullName}?`}>
      <p className="text-[13px]/[1.6] text-u-text2">
        This removes this mandate's research on {candidate.fullName} — their status, note and
        compensation go with it.
      </p>
      <p className="mt-2.5 text-[13px]/[1.6] text-u-text3">
        If they are simply not right for this brief, set their status to Out of scope instead. The map
        keeps the work, and the next mandate sees they were already found.
      </p>
      {offersCompany && (
        <label className="mt-4 flex items-start gap-2.5 text-body text-u-text2">
          <input
            type="checkbox"
            checked={withCompany}
            onChange={(event) => setWithCompany(event.target.checked)}
            className="mt-[3px] size-3.5 flex-none accent-u-accent"
          />
          <span>
            Also remove {candidate.companyName} — no one else is at it
          </span>
        </label>
      )}

      <div className="mt-5 flex justify-end gap-2">
        <Button variant="secondary" onClick={onCancel} disabled={removing}>
          Cancel
        </Button>
        <Button
          variant="primary"
          loading={removing}
          onClick={() => onConfirm(candidate, { withCompany: offersCompany && withCompany })}
        >
          Remove from mandate
        </Button>
      </div>
    </Modal>
  );
}
