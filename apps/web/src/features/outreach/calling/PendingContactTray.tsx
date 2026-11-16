import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState, type KeyboardEvent } from "react";
import { useNavigate } from "react-router-dom";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { useToast } from "../../../components/ui";
import { NetworkMark } from "../../../components/ui/NetworkMark";
import { messageFor } from "../../../lib/errorCodes";
import { formatTimeAgo } from "../../../lib/format";
import * as personCrmApi from "../../candidates/api/personCrmApi";
import type { NoteOutcome } from "../../candidates/api/types";
import { CALL_OUTCOMES, WHATSAPP_OUTCOMES } from "../../candidates/lib/noteOutcomes";
import { positionPipelinePath } from "../../pipeline/lib/pipelinePath";
import { CONVERSATION_KEY } from "../api/conversationApi";
import { NoteComposerModal } from "../components/NoteComposerModal";
import { usePendingContacts } from "./pendingContactsContext";
import type { PendingContact } from "./pendingContactStore";

/** From this many cards on, the newest shows and the rest wait a press away. */
const COLLAPSE_FROM = 3;

/** What the composer opens for, from a card: a Connected call to write up, or a WhatsApp note. */
interface Writing {
  card: PendingContact;
  outcome: NoteOutcome | null;
}

/**
 * The log-it tray (#1337, `Position.dc.html`): one card per number pressed in this tab, asking how it went. Never
 * modal, never takes focus, and never goes on its own; nothing is logged until an outcome is tapped. No answer, Left
 * voicemail and both WhatsApp outcomes log at once; Connected and Add note open the composer; Wrong number logs, then
 * offers Fix number.
 */
export function PendingContactTray() {
  const tray = usePendingContacts();
  const [isExpanded, setIsExpanded] = useState(false);
  const [writing, setWriting] = useState<Writing | null>(null);
  const now = useMinuteClock();
  const cards = tray?.pending ?? [];
  if (!tray || (cards.length === 0 && !writing)) return null;
  const isCollapsed = cards.length >= COLLAPSE_FROM && !isExpanded;
  const shown = isCollapsed ? cards.slice(0, 1) : cards;
  return (
    <>
      {cards.length > 0 && (
        <div role="region" aria-label="Calls and messages to log" aria-live="polite" className="log-tray z-[97] flex flex-col gap-2">
          {isCollapsed && (
            <button
              type="button"
              onClick={() => setIsExpanded(true)}
              className="self-end rounded-full border border-u-border bg-u-surface px-3 py-1 text-note font-medium text-u-text2 shadow-u-e2 hover:text-u-text"
            >
              {cards.length - 1} more to log
            </button>
          )}
          {shown.map((card) => (
            <PendingCard
              key={card.id}
              card={card}
              now={now}
              onWrite={(outcome) => setWriting({ card, outcome })}
            />
          ))}
        </div>
      )}
      {writing && (
        <NoteComposerModal
          projectId={writing.card.projectId}
          candidateId={writing.card.candidateId}
          fullName={writing.card.personName}
          kind={writing.card.channel === "whatsapp" ? "whatsapp" : "call"}
          presetOutcome={writing.outcome}
          presetOccurredAt={writing.card.at}
          onClose={() => setWriting(null)}
          onSaved={() => tray.settle(writing.card.id)}
        />
      )}
    </>
  );
}

function PendingCard({
  card,
  now,
  onWrite,
}: {
  card: PendingContact;
  now: Date;
  onWrite: (outcome: NoteOutcome | null) => void;
}) {
  const tray = usePendingContacts();
  const queryClient = useQueryClient();
  const toast = useToast();
  const navigate = useNavigate();
  const isWhatsApp = card.channel === "whatsapp";
  const logging = useMutation({
    mutationFn: (outcome: NoteOutcome) =>
      personCrmApi.writePersonNote(card.projectId, card.candidateId, {
        kind: isWhatsApp ? "whatsapp" : "call",
        body: "",
        format: "markdown",
        occurredAt: card.at,
        outcome,
      }),
    onSuccess: (_note, outcome) => {
      void queryClient.invalidateQueries({ queryKey: personCrmApi.PERSON_NOTES_KEY(card.projectId, card.candidateId) });
      void queryClient.invalidateQueries({ queryKey: personCrmApi.PERSON_TIMELINE_KEY(card.projectId, card.candidateId) });
      void queryClient.invalidateQueries({ queryKey: CONVERSATION_KEY(card.projectId, card.candidateId) });
      if (outcome === "wrong_number") {
        toast("Call logged · Wrong number");
        tray?.markWrongNumber(card.id);
        return;
      }
      toast(isWhatsApp ? "WhatsApp logged" : "Call logged");
      tray?.settle(card.id);
    },
    onError: (error) => toast.error(messageFor(error)),
  });
  const choose = (outcome: NoteOutcome) => {
    if (outcome === "connected") onWrite("connected");
    else logging.mutate(outcome);
  };
  const dismiss = () => tray?.settle(card.id);
  const pressed = sentenceCase(formatTimeAgo(card.at, now));
  const meta = [card.number, card.channel === "copy" ? `copied ${pressed.toLowerCase()}` : pressed, card.positionTitle]
    .filter(Boolean)
    .join(" · ");
  const title = isWhatsApp ? `WhatsApp to ${card.personName}` : `Call with ${card.personName}`;
  const isWrong = card.loggedWrongNumber === true;
  const outcomes = isWhatsApp ? WHATSAPP_OUTCOMES : CALL_OUTCOMES;
  return (
    <div
      onKeyDown={(event: KeyboardEvent) => event.key === "Escape" && dismiss()}
      className="rounded-u-card border border-u-border bg-u-surface px-3.5 py-3 shadow-u-e2"
    >
      <div className="flex items-start gap-2.5">
        {isWhatsApp ? (
          card.whatsappHref ? (
            <a
              href={card.whatsappHref}
              target="_blank"
              rel="noopener noreferrer"
              aria-label={`Open the chat with ${card.personName} in WhatsApp`}
              title="Open the chat in WhatsApp"
              className="grid size-6.5 flex-none place-items-center rounded-md hover:bg-u-raised"
            >
              <NetworkMark network="whatsapp" size={18} />
            </a>
          ) : (
            <span className="grid size-6.5 flex-none place-items-center">
              <NetworkMark network="whatsapp" size={18} />
            </span>
          )
        ) : (
          <span className="grid size-6.5 flex-none place-items-center rounded-full bg-u-accent-tint text-u-accent">
            <Icon d={ICONS.phoneCall} size={13} />
          </span>
        )}
        <div className="min-w-0 flex-1">
          <div className="text-body font-semibold text-u-text">{title}</div>
          <div className="mt-0.5 truncate font-mono text-meta text-u-text3">{meta}</div>
        </div>
        <button
          type="button"
          onClick={dismiss}
          aria-label={
            isWrong
              ? `Done with the call to ${card.personName}`
              : `Not now — don't log ${isWhatsApp ? "the WhatsApp" : "the call"} with ${card.personName}`
          }
          className="flex-none rounded-md px-1.5 py-0.5 text-note font-medium text-u-text3 hover:bg-u-raised hover:text-u-text"
        >
          {isWrong ? "Done" : "Not now"}
        </button>
      </div>
      {isWrong ? (
        <div className="ms-9 mt-2.5 flex flex-wrap items-center gap-2">
          <span className="text-note text-u-text2">Logged as Wrong number.</span>
          <button
            type="button"
            onClick={() => {
              dismiss();
              navigate(positionPipelinePath(card.projectId, { candidate: card.candidateId, section: "contact" }));
            }}
            className="rounded-md border border-u-border bg-u-surface px-2.5 py-1 text-note font-medium text-u-text hover:border-u-text3"
          >
            Fix number
          </button>
        </div>
      ) : (
        <>
          <div className="ms-9 mb-1.5 mt-2.5 text-meta font-medium text-u-text3">
            {isWhatsApp ? "What happened?" : "How did the call go?"}
          </div>
          <div
            role="group"
            aria-label={`${isWhatsApp ? "WhatsApp" : "Call"} outcome for ${card.personName}`}
            className="ms-9 flex flex-wrap gap-1.5"
          >
            {outcomes.map((option) => (
              <button
                key={option.value}
                type="button"
                disabled={logging.isPending}
                onClick={() => choose(option.value)}
                className="rounded-full border border-u-border px-2.5 py-1 text-note font-medium text-u-text2 transition hover:border-u-accent hover:text-u-text disabled:opacity-50"
              >
                {option.label}
              </button>
            ))}
            {isWhatsApp && (
              <button
                type="button"
                onClick={() => onWrite(null)}
                className="px-1 py-1 text-note font-medium text-u-accent hover:underline"
              >
                Add note
              </button>
            )}
          </div>
        </>
      )}
    </div>
  );
}

/** Re-reads the clock each minute, so "2 minutes ago" keeps counting while the card waits. */
function useMinuteClock(): Date {
  const [now, setNow] = useState(() => new Date());
  useEffect(() => {
    const timer = window.setInterval(() => setNow(new Date()), 60_000);
    return () => window.clearInterval(timer);
  }, []);
  return now;
}

function sentenceCase(text: string): string {
  return text.charAt(0).toUpperCase() + text.slice(1);
}
