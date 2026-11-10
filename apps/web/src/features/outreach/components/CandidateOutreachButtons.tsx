import { useState, type ReactNode } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Button } from "../../../components/ui";
import { mailboxStateOf } from "../lib/mailboxState";
import { useMailbox } from "../lib/useMailbox";
import { useMailboxConnect } from "../lib/useMailboxConnect";
import { cn } from "../../../lib/cn";
import { ConnectFirstButton, mailboxBlockerOf, WAITING_ON_MAILBOX } from "./ConnectFirstButton";
import { EmailComposerDialog } from "./EmailComposerDialog";
import { EnrolDialog } from "./EnrolDialog";

/**
 * The executive drawer's two ways to reach one person: an email of the consultant's own, written in place, or a
 * sequence that writes for them. Offered only where outreach is set up, and quiet — offering the connection — until
 * the consultant's mailbox can send. The Outreach tab puts Book a call `between` the two, Write email first, the three
 * one row of equal widths, and what it logs `below` them as a quieter row.
 */
export function CandidateOutreachButtons({
  projectId,
  candidateId,
  fullName,
  between,
  below,
  sequenceHeldBy = null,
}: {
  projectId: string;
  candidateId: string;
  fullName: string;
  /** Drawn after Write email and before Add to sequence — and alone where outreach is not set up. */
  between?: ReactNode;
  /** A second, quieter row under the three. */
  below?: ReactNode;
  /** Why Add to sequence is held (a sequence already running); null where it is offered. */
  sequenceHeldBy?: string | null;
}) {
  const mailbox = useMailbox();
  const connector = useMailboxConnect();
  const [open, setOpen] = useState<"email" | "sequence" | null>(null);
  if (mailbox.data?.offered !== true) {
    return between || below ? (
      <div className="flex flex-col gap-2.5">
        {between && <div className={OUTREACH_ACTION_ROW}>{between}</div>}
        {below}
      </div>
    ) : null;
  }
  const blocker = mailboxBlockerOf(mailboxStateOf(mailbox));
  const actionOf = (key: "email" | "sequence", icon: string, label: string) =>
    blocker ? (
      <ConnectFirstButton
        key={key}
        state={blocker}
        connector={connector}
        triggerClassName={cn(WAITING_ON_MAILBOX, "justify-center")}
      >
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
      <div className="flex flex-col gap-2.5">
        <div className={OUTREACH_ACTION_ROW}>
          {actionOf("email", ICONS.mail, "Write email")}
          {between}
          {actionOf("sequence", ICONS.outreach, "Add to sequence")}
        </div>
        {below}
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

/** Equal widths while they fit on one line; on a narrow screen each wraps to a full-width line of its own. */
export const OUTREACH_ACTION_ROW = "flex flex-wrap items-center gap-2 *:min-w-fit *:flex-1 *:whitespace-nowrap";
