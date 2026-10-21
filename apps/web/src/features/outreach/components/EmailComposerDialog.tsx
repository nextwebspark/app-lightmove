import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { LinesSkeleton, Modal } from "../../../components/ui";
import { messageFor } from "../../../lib/errorCodes";
import * as projectsApi from "../../projects/api/projectsApi";
import * as conversationApi from "../api/conversationApi";
import { mailboxStateOf } from "../lib/mailboxState";
import { useMailbox } from "../lib/useMailbox";
import { useMailboxConnect } from "../lib/useMailboxConnect";
import { ReplyComposer } from "./ReplyComposer";

/**
 * One email to one executive, written where they were found: the Outreach page's own composer — a reply in their
 * thread or a first email, now or scheduled — with the way to their whole conversation.
 */
export function EmailComposerDialog({
  projectId,
  candidateId,
  fullName,
  onClose,
}: {
  projectId: string;
  candidateId: string;
  fullName: string;
  onClose: () => void;
}) {
  const mailbox = useMailbox();
  const connector = useMailboxConnect();
  const conversation = useQuery({
    queryKey: conversationApi.CONVERSATION_KEY(projectId, candidateId),
    queryFn: ({ signal }) => conversationApi.getConversation(projectId, candidateId, signal),
  });
  const projects = useQuery({ queryKey: projectsApi.PROJECTS_KEY, queryFn: projectsApi.projects });
  const isArchived = Boolean(projects.data?.find((project) => project.id === projectId)?.archivedAt);

  return (
    <Modal
      open
      onClose={onClose}
      title={`Email ${fullName}`}
      closeButton
      className="md:w-[640px]"
      footer={
        <Link
          to={`/projects/${projectId}/outreach?candidate=${candidateId}`}
          onClick={onClose}
          className="me-auto text-note font-medium text-u-accent hover:underline"
        >
          Open their conversation
        </Link>
      }
    >
      {conversation.isError ? (
        <p role="alert" className="py-4 text-body text-u-text3">
          {messageFor(conversation.error)}
        </p>
      ) : conversation.isPending ? (
        <LinesSkeleton className="py-4" />
      ) : (
        <div className="-mx-5 md:-mx-[22px]">
          <ReplyComposer
            projectId={projectId}
            conversation={conversation.data}
            mailbox={mailboxStateOf(mailbox)}
            connector={connector}
            isArchived={isArchived}
            autoFocus
            onSent={onClose}
          />
        </div>
      )}
    </Modal>
  );
}
