import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { buttonClassName, EmptyState, Skeleton } from "../../../components/ui";
import { messageFor } from "../../../lib/errorCodes";
import * as sequenceApi from "../api/sequenceApi";
import type { Sequence } from "../api/sequenceApi";
import { scheduleLabelOf } from "../lib/sendSchedule";
import { SequenceStatePill } from "./SequenceStatePill";

/** A position's sequences as cards, each opening its editor, with the way to write a new one. */
export function SequenceCards({ projectId }: { projectId: string }) {
  const sequences = useQuery({
    queryKey: sequenceApi.SEQUENCES_KEY(projectId),
    queryFn: ({ signal }) => sequenceApi.getSequences(projectId, signal),
  });
  const newSequencePath = `/projects/${projectId}/outreach/sequences/new`;

  if (sequences.isError) {
    return (
      <p role="alert" className="text-body text-u-text3">
        {messageFor(sequences.error)}
      </p>
    );
  }
  if (sequences.isPending) {
    return <Skeleton className="h-24 w-full" />;
  }
  if (sequences.data.length === 0) {
    return (
      <EmptyState
        icon={<Icon d={ICONS.outreach} size={22} />}
        title="No sequences on this position yet"
        body="Write a sequence — a first email and up to two follow-ups in the same thread — then add people to it from In universe, Shortlisted or an executive's drawer."
      >
        <Link to={newSequencePath} className={buttonClassName("primary", "px-3.5 py-2 text-body")}>
          Write your first sequence
        </Link>
      </EmptyState>
    );
  }
  return (
    <section>
      <div className="mb-2.5 flex items-center gap-3">
        <h2 className="type-label text-u-text3">Sequences</h2>
        <Link to={newSequencePath} className={buttonClassName("secondary", "ms-auto px-3 py-1.5 text-note")}>
          New sequence
        </Link>
      </div>
      <div className="grid gap-3 [grid-template-columns:repeat(auto-fill,minmax(300px,1fr))]">
        {sequences.data.map((sequence) => (
          <SequenceCard key={sequence.id} projectId={projectId} sequence={sequence} />
        ))}
      </div>
    </section>
  );
}

function SequenceCard({ projectId, sequence }: { projectId: string; sequence: Sequence }) {
  return (
    <Link
      to={`/projects/${projectId}/outreach/sequences/${sequence.id}`}
      className="block rounded-u-card border border-u-border bg-u-surface px-4 py-3.5 hover:border-u-border-strong"
    >
      <div className="flex items-center gap-2">
        <span className="min-w-0 flex-1 truncate text-body font-semibold">{sequence.name}</span>
        <SequenceStatePill isLive={sequence.enrolledCount > 0} />
      </div>
      <div className="mt-1 font-mono text-meta text-u-text3">{sequenceMetaOf(sequence)}</div>
      <div className="mt-2.5 flex gap-4 font-mono text-note font-medium text-u-text2">
        <span>{sequence.enrolledCount} enrolled</span>
        <span>{sequence.sentCount} sent</span>
        <span className="text-u-direct">{sequence.repliedCount} replied</span>
      </div>
    </Link>
  );
}

/** "3 steps · day 0, +3, +5 sending days · Mon–Fri, 08:00–18:00 · by Yara Haddad", as the mockup's card meta reads. */
function sequenceMetaOf(sequence: Sequence): string {
  const steps = `${sequence.steps.length} ${sequence.steps.length === 1 ? "step" : "steps"}`;
  const days = sequence.steps.map((step, index) => (index === 0 ? "day 0" : `+${step.delayWorkingDays}`)).join(", ");
  const timing = sequence.steps.length > 1 ? ` · ${days} sending days` : "";
  const schedule = ` · ${scheduleLabelOf(sequence.schedule)}`;
  return `${steps}${timing}${schedule}${sequence.createdByName ? ` · by ${sequence.createdByName}` : ""}`;
}
