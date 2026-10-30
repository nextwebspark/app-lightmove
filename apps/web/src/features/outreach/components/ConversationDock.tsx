import { useState, type ReactNode } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Modal } from "../../../components/ui";
import type { PersonNoteKind } from "../../candidates/api/types";
import type { Conversation } from "../api/conversationApi";
import type { MailboxState } from "../lib/mailboxState";
import { canEnrolFrom } from "../lib/runVocabulary";
import { useComposerDialog } from "../lib/useComposerDialog";
import type { MailboxConnect } from "../lib/useMailboxConnect";
import { NoteKindMark } from "../../candidates/components/NoteKindMark";
import { EnrolDialog } from "./EnrolDialog";
import { NoteComposerModal } from "./NoteComposerModal";
import { ReplyComposer } from "./ReplyComposer";

/**
 * The foot of the conversation: Reply or Write email, Log call, Log LinkedIn, Add note and Add to sequence, each opening its own
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
  const canEnrol = !isArchived && canEnrolFrom(mailbox, currentRun.run);
  const closeEmail = () => {
    setIsEmailing(false);
    emailDialog.reset();
  };

  return (
    <div
      role="toolbar"
      aria-label="Conversation actions"
      className="flex flex-none flex-wrap items-center gap-1.5 border-t border-u-border bg-u-surface px-5 py-2.5"
    >
      <DockButton icon={ICONS.mail} onClick={() => setIsEmailing(true)}>
        {composer.replySubject !== null ? "Reply" : "Write email"}
      </DockButton>
      <DockButton icon={ICONS.phone} onClick={() => setNoteKind("call")}>
        Log call
      </DockButton>
      <DockButton icon={<NoteKindMark kind="linkedin" size={14} />} onClick={() => setNoteKind("linkedin")}>
        Log LinkedIn
      </DockButton>
      <DockButton icon={ICONS.pencil} onClick={() => setNoteKind("general")}>
        Add note
      </DockButton>
      {canEnrol && (
        <span className="ms-auto">
          <DockButton icon={ICONS.outreach} onClick={() => setIsEnrolling(true)}>
            Add to sequence
          </DockButton>
        </span>
      )}

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

/** `icon` is a stroke glyph's path, or a drawn mark where the glyph is a brand's own (LinkedIn's). */
function DockButton({ icon, onClick, children }: { icon: string | ReactNode; onClick: () => void; children: ReactNode }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="inline-flex items-center gap-1.5 rounded-u-control border border-u-border-strong bg-u-surface px-3 py-1.5 text-note font-medium text-u-text2 transition hover:border-u-text3 hover:text-u-text"
    >
      {typeof icon === "string" ? <Icon d={icon} size={14} /> : icon}
      {children}
    </button>
  );
}
