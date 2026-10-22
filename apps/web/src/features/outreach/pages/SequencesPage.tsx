import { useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { useOutletContext } from "react-router-dom";
import type { ProjectOutletContext } from "../../../components/layout/ProjectLayout";
import { CandidateDrawerById } from "../../candidates/components/CandidateDrawerById";
import { MailboxBanner } from "../components/MailboxBanner";
import { MailboxChip } from "../components/MailboxChip";
import { NewSequenceLink, OutreachHeader } from "../components/OutreachHeader";
import { PeopleInOutreach } from "../components/PeopleInOutreach";
import { SequenceCards } from "../components/SequenceCards";
import { mailboxStateOf } from "../lib/mailboxState";
import { useMailbox } from "../lib/useMailbox";
import { useMailboxConnect } from "../lib/useMailboxConnect";

/**
 * A position's sequences and everyone in them, the Outreach header's second view: the cards, the counts and the
 * people table, each person opening their drawer.
 */
export function SequencesPage() {
  const queryClient = useQueryClient();
  const { project } = useOutletContext<ProjectOutletContext>();
  const [openCandidateId, setOpenCandidateId] = useState<string | null>(null);
  const mailbox = useMailbox();
  const mailboxState = mailboxStateOf(mailbox);
  const connector = useMailboxConnect();

  return (
    <div className="flex h-full min-h-0 flex-col">
      <h1 className="sr-only">Sequences</h1>
      <OutreachHeader projectId={project.id} section="sequences">
        <MailboxChip state={mailboxState} connector={connector} onAsked={() => void mailbox.refetch()} />
        <NewSequenceLink projectId={project.id} />
      </OutreachHeader>
      <MailboxBanner state={mailboxState} connector={connector} />
      <div className="min-h-0 flex-1 overflow-y-auto">
        <div className="mx-auto max-w-screen-2xl px-4 pb-16 pt-5 md:px-7">
          <p className="mb-4 font-mono text-note text-u-text3">
            A first email and up to two follow-ups in the same thread. A sequence stops the moment someone replies.
          </p>
          <PeopleInOutreach projectId={project.id} onOpenCandidate={setOpenCandidateId}>
            <SequenceCards projectId={project.id} />
          </PeopleInOutreach>
        </div>
      </div>
      <CandidateDrawerById
        project={project}
        candidateId={openCandidateId}
        onClose={() => setOpenCandidateId(null)}
        onChanged={() => void queryClient.invalidateQueries({ queryKey: ["outreach", project.id] })}
      />
    </div>
  );
}
