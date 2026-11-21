import { useQueryClient } from "@tanstack/react-query";
import { Link, useOutletContext } from "react-router-dom";
import type { ProjectOutletContext } from "../../../components/layout/ProjectLayout";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { DetailBlock, DetailRow } from "../../../components/ui/DetailList";
import { cn } from "../../../lib/cn";
import { PersonAvatar } from "../../candidates/components/pool/PersonAvatar";
import { isReachable } from "../../contactlookup/lib/phoneReach";
import {
  CopyPhoneButton,
  PhoneNumberLink,
  WhatsAppLink,
  type PhoneReach,
} from "../../contactlookup/components/PhoneActions";
import { pipelineColourClass, pipelineSwatchClass } from "../../candidates/lib/pipelineStatus";
import { ClientVisibleSwitch } from "../../pipeline/components/ClientVisibleSwitch";
import { CONVERSATION_KEY, type Conversation } from "../api/conversationApi";
import { keyDateOf, lastCallKeyDateOf } from "../lib/conversationVocabulary";
import { lastCallTitle } from "../../candidates/lib/lastCall";
import { isLive, RUN_STATES, runNoteOf, sendTimeOf } from "../lib/runVocabulary";

/**
 * "Candidate details" beside the conversation (`Outreach.dc.html`): who they are, where they stand, whether the client
 * sees them, how to reach them, the dates that matter and the sequence they are in.
 */
export function CandidateDetailsPane({
  projectId,
  conversation,
  onOpenRecord,
}: {
  projectId: string;
  conversation: Conversation;
  /** Opens the person's full record in place; `activity` lands on its Activity tab. */
  onOpenRecord: (tab: "activity" | null) => void;
}) {
  const queryClient = useQueryClient();
  const outlet = useOutletContext<ProjectOutletContext | undefined>();
  const { executive, contact, keyDates, currentRun } = conversation;
  // Written into the read at once, so the switch holds its new place until the refetch confirms it.
  const keepClientVisible = (clientVisible: boolean) => {
    queryClient.setQueryData<Conversation>(CONVERSATION_KEY(projectId, executive.candidateId), (held) =>
      held ? { ...held, executive: { ...held.executive, clientVisible } } : held,
    );
    void queryClient.invalidateQueries({ queryKey: CONVERSATION_KEY(projectId, executive.candidateId) });
  };
  const run = currentRun.run;
  const location = [contact.locationCity, contact.locationCountry].filter(Boolean).join(", ");
  const phoneReach: PhoneReach = {
    personName: executive.fullName,
    locationCountry: contact.locationCountry,
    mayReachOut: true,
    doNotContact: contact.doNotContact,
    target: {
      projectId,
      candidateId: executive.candidateId,
      personName: executive.fullName,
      positionTitle: outlet?.project.positionTitle ?? null,
      approach: conversation.composer?.recentApproach ?? null,
    },
  };
  return (
    <aside aria-label="Candidate details" className="flex flex-col gap-5 px-5 py-4">
      <div className="type-label text-u-text3">Candidate details</div>
      <div className="flex items-center gap-3">
        <PersonAvatar person={{ ...executive, enrichedAt: executive.enrichedAt ?? null }} size="lg" />
        <div className="min-w-0">
          <div className="truncate text-body font-semibold text-u-text">{executive.fullName}</div>
          <div className="truncate text-note text-u-text3">{roleOf(executive.title, executive.companyName)}</div>
        </div>
      </div>

      <div className="flex items-center gap-3">
        <span className="text-note text-u-text3">Pipeline stage</span>
        <span className="ms-auto">
          <StatusPill label={executive.statusLabel} colour={executive.statusColour} />
        </span>
      </div>

      <ClientVisibleSwitch
        projectId={projectId}
        candidateId={executive.candidateId}
        fullName={executive.fullName}
        clientVisible={executive.clientVisible}
        onChanged={keepClientVisible}
      />

      <DetailBlock title="Contact details">
        <DetailRow label="Email">
          {contact.emails.length === 0 ? (
            <span className="text-u-text3">Not found</span>
          ) : (
            contact.emails.map((email) => (
              <div key={email.address} className="truncate font-mono">
                {email.address}
              </div>
            ))
          )}
        </DetailRow>
        <DetailRow label="Mobile">
          {contact.phones.length === 0 ? (
            <span className="text-u-text3">Not found</span>
          ) : (
            contact.phones.map((phone) => (
              <div key={phone.number} className="flex min-w-0 items-center gap-0.5">
                <PhoneNumberLink
                  phone={phone}
                  reach={phoneReach}
                  className={cn("font-mono", isReachable(phoneReach) && "text-u-accent")}
                />
                <WhatsAppLink phone={phone} reach={phoneReach} size={12} />
                {isReachable(phoneReach) && <CopyPhoneButton phone={phone} reach={phoneReach} size={11} />}
              </div>
            ))
          )}
        </DetailRow>
        <DetailRow label="Location">{location || <span className="text-u-text3">—</span>}</DetailRow>
        {contact.doNotContact && (
          <p className="text-meta font-semibold text-u-offlimits">Marked do not contact</p>
        )}
      </DetailBlock>

      <DetailBlock title="Key dates">
        <DetailRow label="Added" mono>
          {keyDateOf(keyDates.addedAt)}
        </DetailRow>
        <DetailRow label="First contacted" mono>
          {keyDateOf(keyDates.firstContactedAt)}
        </DetailRow>
        <DetailRow label="Replied" mono>
          {keyDateOf(keyDates.repliedAt)}
        </DetailRow>
        <DetailRow label="Last updated" mono>
          {keyDateOf(keyDates.lastActivityAt)}
        </DetailRow>
        {keyDates.lastCall && (
          <DetailRow label="Last call" mono>
            <span title={lastCallTitle(keyDates.lastCall)}>{lastCallKeyDateOf(keyDates.lastCall)}</span>
          </DetailRow>
        )}
      </DetailBlock>

      <DetailBlock
        title="Current sequence"
        aside={run ? <span className="font-mono text-eyebrow text-u-text3">{RUN_STATES[run.status].label.toUpperCase()}</span> : null}
      >
        {run ? (
          <>
            <div className="flex items-baseline gap-2">
              <span className="min-w-0 flex-1 truncate text-body text-u-text">{run.sequenceName ?? "A sequence"}</span>
              <span className="flex-none font-mono text-note text-u-text3">
                {run.sentCount} / {run.stepCount}
              </span>
            </div>
            <div className="flex gap-1" aria-hidden="true">
              {Array.from({ length: run.stepCount }, (_, index) => (
                <span
                  key={index}
                  className={cn("h-0.5 flex-1 rounded-full", index < run.sentCount ? "bg-u-accent" : "bg-u-border-strong")}
                />
              ))}
            </div>
            <DetailRow label="Send status">{sendStatusOf(run)}</DetailRow>
            <div className="flex flex-wrap gap-x-4 gap-y-1 text-note">
              <Link
                to={`/projects/${projectId}/outreach/sequences/${run.sequenceId}`}
                className="font-medium text-u-accent hover:underline"
              >
                View sequence
              </Link>
              <button type="button" className="font-medium text-u-accent hover:underline" onClick={() => onOpenRecord("activity")}>
                View history
              </button>
            </div>
          </>
        ) : (
          <span className="text-note text-u-text3">Not in a sequence.</span>
        )}
      </DetailBlock>

      <button
        type="button"
        className="inline-flex items-center gap-1.5 self-start text-note font-medium text-u-accent hover:underline"
        onClick={() => onOpenRecord(null)}
      >
        <Icon d={ICONS.externalLink} size={13} />
        Open persistent candidate record
      </button>
    </aside>
  );
}

export function StatusPill({ label, colour }: { label: string; colour: string }) {
  return (
    <span className={cn("inline-flex items-center gap-1.5 rounded-full px-2 py-0.5 text-meta font-medium", pipelineColourClass(colour))}>
      <span aria-hidden="true" className={cn("size-1.5 rounded-full", pipelineSwatchClass(colour))} />
      {label}
    </span>
  );
}

function roleOf(title: string | null, companyName: string | null): string {
  return [title, companyName].filter(Boolean).join(" · ");
}

function sendStatusOf(run: NonNullable<Conversation["currentRun"]["run"]>): string {
  if (isLive(run)) {
    return run.nextSendAt ? `Step ${run.sentCount + 1} due ${sendTimeOf(run.nextSendAt)}.` : "Waiting for its sending hours.";
  }
  const note = runNoteOf(run);
  return note ? `${RUN_STATES[run.status].label} · ${note}. No sends scheduled.` : `${RUN_STATES[run.status].label}. No sends scheduled.`;
}
