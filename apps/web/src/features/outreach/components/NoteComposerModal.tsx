import { useState } from "react";
import { useComposerDialog } from "../lib/useComposerDialog";
import { Modal } from "../../../components/ui";
import type { PersonNoteKind } from "../../candidates/api/types";
import { PositionNoteComposer } from "../../candidates/components/PersonSections";
import { LOGGED_CONTACT_KINDS } from "../../candidates/lib/noteKinds";

/**
 * Log call, Log LinkedIn and Add note — the executive drawer's Outreach, the conversation dock, the Pipeline card: the
 * drawer's own note composer as a dialog, with room to write. A call or a LinkedIn message switches between the two.
 */
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
  /** Null while closed; `call`, `linkedin` or `meeting` for the Log buttons, `general` for Add note. */
  kind: PersonNoteKind | null;
  onClose: () => void;
  onSaved: () => void;
}) {
  const dialog = useComposerDialog();
  // Log call and Log LinkedIn are one dialog switching between the two; the title follows the switch.
  const [chosen, setChosen] = useState<{ for: PersonNoteKind | null; kind: PersonNoteKind | null }>({ for: kind, kind });
  if (chosen.for !== kind) setChosen({ for: kind, kind });
  const current = chosen.kind;
  const logsContact = kind !== null && LOGGED_CONTACT_KINDS.includes(kind);
  const title =
    current === "call"
      ? `Log a call with ${fullName}`
      : current === "linkedin"
        ? `Log a LinkedIn message with ${fullName}`
        : current === "meeting"
          ? `Log a meeting with ${fullName}`
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
          kindChoices={logsContact ? LOGGED_CONTACT_KINDS : undefined}
          onKindChange={(next) => setChosen({ for: kind, kind: next })}
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
