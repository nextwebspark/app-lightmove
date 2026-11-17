import { useQuery } from "@tanstack/react-query";
import * as meetingApi from "../api/meetingApi";
import { callRowsOf } from "../lib/notetakerChip";
import { useComposerDialog } from "../lib/useComposerDialog";
import { useNotetaker } from "../lib/useNotetaker";
import { Modal } from "../../../components/ui";
import type { NoteOutcome, PersonNoteKind } from "../../candidates/api/types";
import { PositionNoteComposer } from "../../candidates/components/PersonSections";
import { LOGGED_CONTACT_KINDS } from "../../candidates/lib/noteKinds";

const DIALOG_TITLES: Record<PersonNoteKind, (fullName: string) => string> = {
  general: (fullName) => `Add a note on ${fullName}`,
  call: (fullName) => `Log a call with ${fullName}`,
  meeting: (fullName) => `Log a meeting with ${fullName}`,
  email: (fullName) => `Add a note on ${fullName}`,
  linkedin: (fullName) => `Log a LinkedIn message with ${fullName}`,
  whatsapp: (fullName) => `Log a WhatsApp message with ${fullName}`,
};

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
  presetOutcome = null,
  presetOccurredAt = null,
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
  /** From the log-it tray: Connected, picked already. */
  presetOutcome?: NoteOutcome | null;
  /** From the log-it tray: when the number was pressed. */
  presetOccurredAt?: string | null;
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
  const title = (kind ? DIALOG_TITLES[kind] : DIALOG_TITLES.general)(fullName);
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
          key={`${kind}:${presetMeetingId ?? ""}:${presetOutcome ?? ""}:${presetOccurredAt ?? ""}`}
          projectId={projectId}
          candidateId={candidateId}
          initialKind={kind}
          kindChoices={logsContact ? [kind] : undefined}
          pastMeetings={pastMeetings}
          presetMeetingId={presetMeetingId}
          presetOutcome={presetOutcome}
          presetOccurredAt={presetOccurredAt}
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
