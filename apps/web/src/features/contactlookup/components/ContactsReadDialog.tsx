import { Button, Modal } from "../../../components/ui";
import type { CandidateContacts } from "../../candidates/api/types";
import { ContactChannels } from "./ContactPanel";

/** Every email and phone on file, for a reader who changes none of them: the header's "+N" opens it. */
export function ContactsReadDialog({
  fullName,
  contacts,
  note,
  onClose,
}: {
  fullName: string;
  contacts: CandidateContacts;
  /** A line under the channels, such as why lookups are off. */
  note?: string | null;
  onClose: () => void;
}) {
  return (
    <Modal
      open
      onClose={onClose}
      title="Contact details"
      subtitle={fullName}
      closeButton
      footer={
        <Button type="button" variant="secondary" className="ms-auto" onClick={onClose}>
          Close
        </Button>
      }
    >
      <ContactChannels contacts={contacts} />
      {note && <p className="mt-2 text-note text-u-offlimits">{note}</p>}
    </Modal>
  );
}
