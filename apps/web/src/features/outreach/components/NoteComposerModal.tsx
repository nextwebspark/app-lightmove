import { Modal } from "../../../components/ui";
import type { PersonNoteKind } from "../../candidates/api/types";
import { PositionNoteComposer } from "../../candidates/components/PersonSections";

/** The conversation header's Log call and Add note: the drawer's own note composer, opened over the page. */
export function NoteComposerModal({
  projectId,
  candidateId,
  fullName,
  kind,
  onClose,
  onSaved,
}: {
  projectId: string;
  candidateId: string;
  fullName: string;
  /** Null while closed; `call` for Log call, `general` for Add note. */
  kind: PersonNoteKind | null;
  onClose: () => void;
  onSaved: () => void;
}) {
  return (
    <Modal
      open={kind !== null}
      onClose={onClose}
      title={kind === "call" ? `Log a call with ${fullName}` : `Add a note on ${fullName}`}
      closeButton
    >
      {kind !== null && (
        <PositionNoteComposer
          key={kind}
          projectId={projectId}
          candidateId={candidateId}
          initialKind={kind}
          onSaved={() => {
            onSaved();
            onClose();
          }}
        />
      )}
    </Modal>
  );
}
