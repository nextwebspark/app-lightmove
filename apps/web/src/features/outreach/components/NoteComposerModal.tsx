import { useComposerDialog } from "../lib/useComposerDialog";
import { Modal } from "../../../components/ui";
import type { PersonNoteKind } from "../../candidates/api/types";
import { PositionNoteComposer } from "../../candidates/components/PersonSections";

/** The conversation dock's Log call, Log LinkedIn and Add note: the drawer's own note composer as a dialog, with room to write. */
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
  /** Null while closed; `call` for Log call, `linkedin` for Log LinkedIn, `general` for Add note. */
  kind: PersonNoteKind | null;
  onClose: () => void;
  onSaved: () => void;
}) {
  const dialog = useComposerDialog();
  const title =
    kind === "call"
      ? `Log a call with ${fullName}`
      : kind === "linkedin"
        ? `Log a LinkedIn message with ${fullName}`
        : `Add a note on ${fullName}`;
  const close = () => {
    dialog.reset();
    onClose();
  };
  return (
    <Modal
      open={kind !== null}
      onClose={close}
      title={title}
      closeButton
      {...dialog.modalProps}
    >
      {kind !== null && (
        <PositionNoteComposer
          key={kind}
          projectId={projectId}
          candidateId={candidateId}
          initialKind={kind}
          title={title}
          presentation="dialog"
          onExpandChange={dialog.onExpandChange}
          onDraftChange={dialog.onDraftChange}
          onSaved={() => {
            onSaved();
            close();
          }}
        />
      )}
    </Modal>
  );
}
