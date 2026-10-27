import { useState } from "react";
import { Modal } from "../../../components/ui";
import type { PersonNoteKind } from "../../candidates/api/types";
import { PositionNoteComposer } from "../../candidates/components/PersonSections";

/** The conversation dock's Log call and Add note: the drawer's own note composer as a dialog, with room to write. */
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
  const [isLarge, setIsLarge] = useState(false);
  const title = kind === "call" ? `Log a call with ${fullName}` : `Add a note on ${fullName}`;
  const close = () => {
    setIsLarge(false);
    onClose();
  };
  return (
    <Modal
      open={kind !== null}
      onClose={close}
      title={title}
      closeButton
      fills
      className={isLarge ? "md:h-[85dvh] md:w-[880px]" : "md:w-[640px]"}
    >
      {kind !== null && (
        <PositionNoteComposer
          key={kind}
          projectId={projectId}
          candidateId={candidateId}
          initialKind={kind}
          title={title}
          presentation="dialog"
          onExpandInPlace={setIsLarge}
          onSaved={() => {
            onSaved();
            close();
          }}
        />
      )}
    </Modal>
  );
}
