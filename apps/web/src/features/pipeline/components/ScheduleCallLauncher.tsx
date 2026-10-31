import { useQuery } from "@tanstack/react-query";
import { Button, Modal } from "../../../components/ui";
import { messageFor } from "../../../lib/errorCodes";
import * as poolApi from "../../candidates/api/poolApi";
import { BookCallDialog } from "../../outreach/components/BookCallDialog";
import type { PipelineRow } from "../api/types";

/**
 * A card's Schedule: Book a call over the person's record, once it says the call can be booked — the same two
 * reasons the drawer's Meetings section withholds it for, said here rather than left as a dead button.
 */
export function ScheduleCallLauncher({
  projectId,
  row,
  onClose,
  onOpenContact,
}: {
  projectId: string;
  row: PipelineRow;
  onClose: () => void;
  onOpenContact: (row: PipelineRow) => void;
}) {
  const record = useQuery({
    queryKey: poolApi.PERSON_RECORD_KEY(row.personId),
    queryFn: ({ signal }) => poolApi.getPerson(row.personId, signal),
  });
  if (record.isPending) return null;

  const emails = record.data?.contacts.emails ?? [];
  const refusal = record.isError
    ? messageFor(record.error)
    : record.data.doNotContact
      ? `${row.fullName} is marked do not contact.`
      : emails.length === 0
        ? `There is no email on file to send ${row.fullName} the invite.`
        : null;

  if (refusal === null) {
    return (
      <BookCallDialog
        projectId={projectId}
        candidateId={row.candidateId}
        fullName={row.fullName}
        candidateStatus={row.status}
        emails={emails}
        onClose={onClose}
      />
    );
  }

  return (
    <Modal
      open
      onClose={onClose}
      title={`Book a call with ${row.fullName}`}
      closeButton
      footer={
        !record.isError && (
          <Button
            size="sm"
            variant="secondary"
            onClick={() => {
              onClose();
              onOpenContact(row);
            }}
          >
            Open Contact
          </Button>
        )
      }
    >
      <p className="py-2 text-body text-u-text2">{refusal}</p>
    </Modal>
  );
}
