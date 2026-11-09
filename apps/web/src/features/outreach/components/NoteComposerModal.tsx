import { useQuery } from "@tanstack/react-query";
import * as meetingApi from "../api/meetingApi";
import { callRowsOf } from "../lib/notetakerChip";
import { useComposerDialog } from "../lib/useComposerDialog";
import { useNotetaker } from "../lib/useNotetaker";
import { Modal } from "../../../components/ui";
import type { PersonNoteKind } from "../../candidates/api/types";
import { PositionNoteComposer } from "../../candidates/components/PersonSections";
import { LOGGED_CONTACT_KINDS } from "../../candidates/lib/noteKinds";

/**
 * Log call, Log LinkedIn, Log WhatsApp and Add note — the executive drawer's Outreach, the conversation dock, the Pipeline card: the
 * drawer's own note composer as a dialog, with room to write. A log is of the kind its button named; the dialog offers
 * no switch to another.
 */
export function NoteComposerModal({
  projectId,
  candidateId,
  fullName,
  kind,
  presetMeetingId = null,
  onClose,
  onSaved,
}: {
  projectId: string;
  candidateId: string;
  fullName: string;
  /** Null while closed; `call`, `linkedin` or `meeting` for the Log buttons, `general` for Add note. */
  kind: PersonNoteKind | null;
  /** Log meeting notes from a past call's ⋯: the dialog opens on that call. */
  presetMeetingId?: string | null;
  onClose: () => void;
  onSaved: () => void;
}) {
  const dialog = useComposerDialog();
  const logsContact = kind !== null && LOGGED_CONTACT_KINDS.includes(kind);
  const meetings = useQuery({
    queryKey: meetingApi.MEETINGS_KEY(projectId, candidateId),
    queryFn: ({ signal }) => meetingApi.getMeetings(projectId, candidateId, signal),
    enabled: logsContact,
  });
  // A call the calendar let go is still on file by its recording, and its notes may name it as any other.
  const notetaker = useNotetaker(projectId, candidateId, logsContact);
  const calls = callRowsOf(
    meetings.data?.upcoming ?? [],
    meetings.data?.past ?? [],
    notetaker.data?.recordings ?? [],
  );
  const now = Date.now();
  const pastMeetings = [...calls.upcoming, ...calls.past]
    .filter((meeting) => Date.parse(meeting.startsAt) <= now)
    .sort((a, b) => Date.parse(b.startsAt) - Date.parse(a.startsAt));
  const title =
    kind === "call"
      ? `Log a call with ${fullName}`
      : kind === "linkedin"
        ? `Log a LinkedIn message with ${fullName}`
        : kind === "whatsapp"
          ? `Log a WhatsApp message with ${fullName}`
          : kind === "meeting"
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
          key={`${kind}:${presetMeetingId ?? ""}`}
          projectId={projectId}
          candidateId={candidateId}
          initialKind={kind}
          kindChoices={logsContact ? [kind] : undefined}
          pastMeetings={pastMeetings}
          presetMeetingId={presetMeetingId}
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
