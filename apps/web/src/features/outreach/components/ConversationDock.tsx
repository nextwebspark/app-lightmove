import { useState } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Button, Modal } from "../../../components/ui";
import type { PersonNoteKind } from "../../candidates/api/types";
import type { Conversation } from "../api/conversationApi";
import type { MailboxState } from "../lib/mailboxState";
import { canEnrolFrom } from "../lib/runVocabulary";
import { useComposerDialog } from "../lib/useComposerDialog";
import type { MailboxConnect } from "../lib/useMailboxConnect";
import { ScheduleCallLauncher } from "../../pipeline/components/ScheduleCallLauncher";
import { OUTREACH_ACTION_ROW } from "./CandidateOutreachButtons";
import { EnrolDialog } from "./EnrolDialog";
import { LogContactRow } from "./LogContactRow";
import { NoteComposerModal } from "./NoteComposerModal";
import { ReplyComposer } from "./ReplyComposer";

/**
 * The foot of the conversation, laid out as the executive drawer's Outreach tab is: Reply or Write email, Book a meeting and
 * Add to sequence on one row, what is logged beneath them (Call, Meeting, LinkedIn, WhatsApp, Add note), each opening its own
 * dialog over the history.
 */
export function ConversationDock({
  projectId,
  conversation,
  mailbox,
  connector,
  isArchived,
  focusComposer,
  onNoteSaved,
}: {
  projectId: string;
  conversation: Conversation;
  mailbox: MailboxState;
  connector: MailboxConnect;
  isArchived: boolean;
  /** Opened to write to them: the email dialog starts open. */
  focusComposer: boolean;
  onNoteSaved: () => void;
}) {
  const { executive, composer, currentRun } = conversation;
  const [isEmailing, setIsEmailing] = useState(focusComposer);
  const emailDialog = useComposerDialog();
  const [noteKind, setNoteKind] = useState<PersonNoteKind | null>(null);
  const [isEnrolling, setIsEnrolling] = useState(false);
  const [isBooking, setIsBooking] = useState(false);
  const canEnrol = !isArchived && canEnrolFrom(mailbox, currentRun.run);
  const closeEmail = () => {
    setIsEmailing(false);
    emailDialog.reset();
  };

  return (
    <div
      role="toolbar"
      aria-label="Conversation actions"
      className="flex flex-none flex-col gap-2.5 border-t border-u-border bg-u-surface px-5 py-3"
    >
      <div className={OUTREACH_ACTION_ROW}>
        <Button variant="primary" size="xs" onClick={() => setIsEmailing(true)}>
          <Icon d={ICONS.mail} size={13} />
          {composer.replySubject !== null ? "Reply" : "Write email"}
        </Button>
        {mailbox.kind === "ready" && !isArchived && (
          <Button variant="secondary" size="xs" onClick={() => setIsBooking(true)}>
            <Icon d={ICONS.calendar} size={13} />
            Book a meeting
          </Button>
        )}
        {canEnrol && (
          <Button variant="secondary" size="xs" onClick={() => setIsEnrolling(true)}>
            <Icon d={ICONS.outreach} size={13} />
            Add to sequence
          </Button>
        )}
      </div>
      <LogContactRow onLog={setNoteKind} onAddNote={() => setNoteKind("general")} />

      <Modal
        open={isEmailing}
        onClose={closeEmail}
        title={`Email ${executive.fullName}`}
        closeButton
        {...emailDialog.modalProps}
      >
        <ReplyComposer
          projectId={projectId}
          conversation={conversation}
          mailbox={mailbox}
          connector={connector}
          isArchived={isArchived}
          autoFocus
          onSent={closeEmail}
          onDraftChange={emailDialog.onDraftChange}
          onExpandChange={emailDialog.onExpandChange}
        />
      </Modal>
      <NoteComposerModal
        projectId={projectId}
        candidateId={executive.candidateId}
        fullName={executive.fullName}
        kind={noteKind}
        onClose={() => setNoteKind(null)}
        onSaved={onNoteSaved}
      />
      {isBooking && (
        <ScheduleCallLauncher projectId={projectId} row={executive} onClose={() => setIsBooking(false)} />
      )}
      {isEnrolling && (
        <EnrolDialog
          projectId={projectId}
          scope={{ candidateIds: [executive.candidateId] }}
          source={executive.fullName}
          onClose={() => setIsEnrolling(false)}
        />
      )}
    </div>
  );
}
