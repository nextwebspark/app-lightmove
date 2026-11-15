import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useState, type ReactNode } from "react";
import { Link, useOutletContext } from "react-router-dom";
import type { ProjectOutletContext } from "../../../components/layout/ProjectLayout";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Toggle, useToast } from "../../../components/ui";
import { cn } from "../../../lib/cn";
import * as shareConsentApi from "../../candidates/api/shareConsentApi";
import { ShareConsentPromptDialog } from "../../candidates/components/consent/ShareConsentPromptDialog";
import { PersonAvatar } from "../../candidates/components/pool/PersonAvatar";
import {
  CopyPhoneButton,
  PhoneNumberLink,
  WhatsAppLink,
  type PhoneReach,
} from "../../contactlookup/components/PhoneActions";
import { useShareConsents } from "../../candidates/lib/useShareConsents";
import { useWorkspaceVocabulary } from "../../workspace/lib/vocabulary";
import { messageFor } from "../../../lib/errorCodes";
import { pipelineColourClass, pipelineSwatchClass } from "../../candidates/lib/pipelineStatus";
import { useClientVisibility } from "../../pipeline/lib/usePipelineWrites";
import { CONVERSATION_KEY, type Conversation } from "../api/conversationApi";
import { keyDateOf } from "../lib/conversationVocabulary";
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
  const toast = useToast();
  const clientVisibility = useClientVisibility(projectId);
  const share = useShareConsents(projectId);
  const outlet = useOutletContext<ProjectOutletContext | undefined>();
  const vocabulary = useWorkspaceVocabulary();
  const clientName = outlet?.project.clientName ?? `the ${vocabulary.unitLower}`;
  const [sharePrompt, setSharePrompt] = useState(false);
  const { executive, contact, keyDates, currentRun } = conversation;
  // Ask first from here goes straight away, in the confidential wording; naming the client is the Pipeline card's.
  const askShare = useMutation({
    mutationFn: () => shareConsentApi.requestShareConsent(projectId, executive.candidateId, false),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: shareConsentApi.SHARE_CONSENTS_KEY(projectId) });
      toast.success(`Request sent to ${executive.fullName.split(" ")[0]}.`);
      setSharePrompt(false);
    },
    onError: (failure) => {
      toast.error(messageFor(failure));
      setSharePrompt(false);
    },
  });
  const showToClient = (checked: boolean) =>
    clientVisibility.mutate(
      { candidateId: executive.candidateId, clientVisible: checked },
      {
        // Written into the read at once, so the switch holds its new place until the refetch confirms it.
        onSuccess: (saved) =>
          queryClient.setQueryData<Conversation>(CONVERSATION_KEY(projectId, executive.candidateId), (held) =>
            held ? { ...held, executive: { ...held.executive, clientVisible: saved.clientVisible } } : held,
          ),
        onSettled: () =>
          void queryClient.invalidateQueries({ queryKey: CONVERSATION_KEY(projectId, executive.candidateId) }),
      },
    );
  const run = currentRun.run;
  const location = [contact.locationCity, contact.locationCountry].filter(Boolean).join(", ");
  const phoneReach: PhoneReach = {
    personName: executive.fullName,
    locationCountry: contact.locationCountry,
    actionable: !contact.doNotContact,
    refusal: contact.doNotContact ? "Do not contact is set" : undefined,
  };
  const clientVisible = clientVisibility.isPending
    ? (clientVisibility.variables?.clientVisible ?? executive.clientVisible)
    : executive.clientVisible;

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

      <div>
        <div className="flex items-center gap-2">
          <Icon d={ICONS.eye} size={14} className="text-u-text3" />
          <span className="text-body text-u-text">Client visible</span>
          <span className="ms-auto">
            <Toggle
              checked={clientVisible}
              label="Client visible"
              disabled={clientVisibility.isPending}
              onChange={(checked) => {
                if (checked && share.needsPrompt(executive.candidateId)) setSharePrompt(true);
                else showToClient(checked);
              }}
            />
          </span>
        </div>
        <p className="mt-1 text-meta text-u-text3">
          Client-visible profile only. Emails and private notes remain internal.
        </p>
        {sharePrompt && (
          <ShareConsentPromptDialog
            fullName={executive.fullName}
            clientName={clientName}
            row={share.rowOf(executive.candidateId)}
            busy={askShare.isPending}
            onClose={() => setSharePrompt(false)}
            onShowAnyway={() => {
              setSharePrompt(false);
              showToClient(true);
            }}
            onAskFirst={() => askShare.mutate()}
          />
        )}
      </div>

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
                  className={cn("font-mono", phoneReach.actionable && "text-u-accent")}
                />
                <WhatsAppLink phone={phone} reach={phoneReach} size={12} />
                {phoneReach.actionable && <CopyPhoneButton phone={phone} size={11} />}
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

function DetailBlock({ title, aside, children }: { title: string; aside?: ReactNode; children: ReactNode }) {
  return (
    <section className="flex flex-col gap-2 border-t border-u-border pt-4">
      <div className="flex items-center">
        <h3 className="type-micro-label text-u-text3">{title}</h3>
        {aside && <span className="ms-auto">{aside}</span>}
      </div>
      {children}
    </section>
  );
}

function DetailRow({ label, mono = false, children }: { label: string; mono?: boolean; children: ReactNode }) {
  return (
    <div className="flex gap-3 text-note">
      <span className="w-24 flex-none text-u-text3">{label}</span>
      <div className={cn("min-w-0 flex-1 text-u-text2", mono && "font-mono")}>{children}</div>
    </div>
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
