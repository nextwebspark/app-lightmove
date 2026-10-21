import { useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Link, useOutletContext } from "react-router-dom";
import type { ProjectOutletContext } from "../../../components/layout/ProjectLayout";
import { CandidateDrawerById } from "../../candidates/components/CandidateDrawerById";
import { PeopleInOutreach } from "../components/PeopleInOutreach";
import { SequenceCards } from "../components/SequenceCards";

/**
 * A position's sequences and everyone in them, reached from the Outreach page's New outreach menu: the cards, the
 * counts and the people table, each person opening their drawer.
 */
export function SequencesPage() {
  const queryClient = useQueryClient();
  const { project } = useOutletContext<ProjectOutletContext>();
  const [openCandidateId, setOpenCandidateId] = useState<string | null>(null);

  return (
    <div className="mx-auto max-w-screen-2xl px-4 pb-16 pt-7 md:px-7">
      <Link to={`/projects/${project.id}/outreach`} className="text-note text-u-text3 hover:text-u-text2">
        ← Conversations
      </Link>
      <div className="mb-4 mt-2">
        <h1 className="type-title">Sequences</h1>
        <p className="mt-1 font-mono text-note text-u-text3">
          A first email and up to two follow-ups in the same thread. A sequence stops the moment someone replies.
        </p>
      </div>
      <PeopleInOutreach projectId={project.id} onOpenCandidate={setOpenCandidateId}>
        <SequenceCards projectId={project.id} />
      </PeopleInOutreach>
      <CandidateDrawerById
        project={project}
        candidateId={openCandidateId}
        onClose={() => setOpenCandidateId(null)}
        onChanged={() => void queryClient.invalidateQueries({ queryKey: ["outreach", project.id] })}
      />
    </div>
  );
}
