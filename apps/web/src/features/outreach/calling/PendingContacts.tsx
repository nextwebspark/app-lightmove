import { useCallback, useMemo, useState, type ReactNode } from "react";
import type { CandidatePhone } from "../../candidates/api/types";
import { RecentApproachConfirm } from "../../candidates/components/RecentApproachConfirm";
import { whatsappHrefOf } from "../../contactlookup/lib/phoneLinks";
import type { DialTarget } from "../../contactlookup/lib/phoneReach";
import { PendingContactsContext, type PendingContactsValue } from "./pendingContactsContext";
import { readPending, withPending, writePending, type PendingChannel, type PendingContact } from "./pendingContactStore";

interface HeldPress {
  target: DialTarget;
  channel: PendingChannel;
  phone: CandidatePhone;
  go: () => void;
}

export function PendingContacts({ children }: { children: ReactNode }) {
  const [pending, setPending] = useState<PendingContact[]>(() => readPending());
  const [held, setHeld] = useState<HeldPress | null>(null);

  const change = useCallback((next: (current: PendingContact[]) => PendingContact[]) => {
    setPending((current) => {
      const changed = next(current);
      writePending(changed);
      return changed;
    });
  }, []);

  const record = useCallback(
    (channel: PendingChannel, phone: CandidatePhone, target: DialTarget) =>
      change((current) =>
        withPending(current, {
          id: `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
          channel,
          projectId: target.projectId,
          candidateId: target.candidateId,
          personName: target.personName,
          positionTitle: target.positionTitle,
          number: phone.number,
          whatsappHref: whatsappHrefOf(phone),
          at: new Date().toISOString(),
        }),
      ),
    [change],
  );

  const reachOut = useCallback<PendingContactsValue["reachOut"]>(
    (event, channel, phone, target, go) => {
      if (target.approach?.tier === "CAUTION") {
        event?.preventDefault();
        setHeld({ target, channel, phone, go });
        return;
      }
      if (!event) go();
      record(channel, phone, target);
    },
    [record],
  );

  const settle = useCallback((id: string) => change((current) => current.filter((card) => card.id !== id)), [change]);
  const markWrongNumber = useCallback(
    (id: string) =>
      change((current) => current.map((card) => (card.id === id ? { ...card, loggedWrongNumber: true } : card))),
    [change],
  );
  const value = useMemo(
    () => ({ pending, reachOut, settle, markWrongNumber }),
    [pending, reachOut, settle, markWrongNumber],
  );

  return (
    <PendingContactsContext.Provider value={value}>
      {children}
      {held?.target.approach && (
        <RecentApproachConfirm
          open
          fullName={held.target.personName}
          approach={held.target.approach}
          confirmLabel={CONFIRM_LABELS[held.channel]}
          onConfirm={() => {
            held.go();
            record(held.channel, held.phone, held.target);
            setHeld(null);
          }}
          onClose={() => setHeld(null)}
        />
      )}
    </PendingContactsContext.Provider>
  );
}

const CONFIRM_LABELS: Record<PendingChannel, string> = {
  call: "Call anyway",
  whatsapp: "Open WhatsApp anyway",
  copy: "Copy anyway",
};
