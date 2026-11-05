import { useState, type ReactNode } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Button } from "../../../components/ui";
import { mailboxStateOf } from "../lib/mailboxState";
import { useMailbox } from "../lib/useMailbox";
import { useMailboxConnect } from "../lib/useMailboxConnect";
import { ConnectFirstButton, mailboxBlockerOf } from "./ConnectFirstButton";
import { EmailComposerDialog } from "./EmailComposerDialog";
import { EnrolDialog } from "./EnrolDialog";

/**
 * The executive drawer's two ways to reach one person: an email of the consultant's own, written in place, or a
 * sequence that writes for them. Offered only where outreach is set up, and quiet — offering the connection — until
 * the consultant's mailbox can send. The Outreach tab puts its other actions `between` the two, Write email first.
 */
export function CandidateOutreachButtons({
  projectId,
  candidateId,
  fullName,
  between,
  sequenceHeldBy = null,
}: {
  projectId: string;
  candidateId: string;
  fullName: string;
  /** Drawn after Write email and before Add to sequence — and alone where outreach is not set up. */
  between?: ReactNode;
  /** Why Add to sequence is held (a sequence already running); null where it is offered. */
  sequenceHeldBy?: string | null;
}) {
  const mailbox = useMailbox();
  const connector = useMailboxConnect();
  const [open, setOpen] = useState<"email" | "sequence" | null>(null);
  if (mailbox.data?.offered !== true) {
    return between ? <div className="flex flex-wrap items-center gap-2">{between}</div> : null;
  }
  const blocker = mailboxBlockerOf(mailboxStateOf(mailbox));
  const actionOf = (key: "email" | "sequence", icon: string, label: string) =>
    blocker ? (
      <ConnectFirstButton key={key} state={blocker} connector={connector}>
        <Icon d={icon} size={13} />
        {label}
      </ConnectFirstButton>
    ) : key === "sequence" && sequenceHeldBy ? (
      <Button key={key} variant="secondary" size="xs" disabled title={sequenceHeldBy}>
        <Icon d={icon} size={13} />
        {label}
      </Button>
    ) : (
      <Button key={key} variant={key === "email" && between ? "primary" : "secondary"} size="xs" onClick={() => setOpen(key)}>
        <Icon d={icon} size={13} />
        {label}
      </Button>
    );

  return (
    <>
      <div className="flex flex-wrap items-center gap-2">
        {actionOf("email", ICONS.mail, "Write email")}
        {between}
        {actionOf("sequence", ICONS.outreach, "Add to sequence")}
      </div>
      {open === "email" && (
        <EmailComposerDialog
          projectId={projectId}
          candidateId={candidateId}
          fullName={fullName}
          onClose={() => setOpen(null)}
        />
      )}
      {open === "sequence" && (
        <EnrolDialog
          projectId={projectId}
          scope={{ candidateIds: [candidateId] }}
          source={`From ${fullName}'s profile`}
          onClose={() => setOpen(null)}
        />
      )}
    </>
  );
}
