import { Modal } from "../../../components/ui";
import type { PersonNoteKind } from "../../candidates/api/types";
import { PositionNoteComposer } from "../../candidates/components/PersonSections";

/** The conversation header's Log call and Add note: the drawer's own note composer as a dialog, with room to write. */
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
  const title = kind === "call" ? `Log a call with ${fullName}` : `Add a note on ${fullName}`;
  return (
    <Modal open={kind !== null} onClose={onClose} title={title} closeButton className="md:w-[640px]">
      {kind !== null && (
        <PositionNoteComposer
          key={kind}
          projectId={projectId}
          candidateId={candidateId}
          initialKind={kind}
          title={title}
          presentation="dialog"
          onSaved={() => {
            onSaved();
            onClose();
          }}
        />
      )}
    </Modal>
  );
}
