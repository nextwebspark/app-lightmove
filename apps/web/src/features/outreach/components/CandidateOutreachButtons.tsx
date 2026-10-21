import { useState } from "react";
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
 * the consultant's mailbox can send.
 */
export function CandidateOutreachButtons({
  projectId,
  candidateId,
  fullName,
}: {
  projectId: string;
  candidateId: string;
  fullName: string;
}) {
  const mailbox = useMailbox();
  const connector = useMailboxConnect();
  const [open, setOpen] = useState<"email" | "sequence" | null>(null);
  if (mailbox.data?.offered !== true) return null;
  const blocker = mailboxBlockerOf(mailboxStateOf(mailbox));
  const actions = [
    { key: "email", icon: ICONS.mail, label: "Write email" },
    { key: "sequence", icon: ICONS.outreach, label: "Add to sequence" },
  ] as const;

  return (
    <>
      <div className="flex flex-wrap items-center gap-2">
        {actions.map((action) =>
          blocker ? (
            <ConnectFirstButton key={action.key} state={blocker} connector={connector}>
              <Icon d={action.icon} size={13} />
              {action.label}
            </ConnectFirstButton>
          ) : (
            <Button key={action.key} variant="secondary" size="xs" onClick={() => setOpen(action.key)}>
              <Icon d={action.icon} size={13} />
              {action.label}
            </Button>
          ),
        )}
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
