import { useMutation } from "@tanstack/react-query";
import { useToast } from "../../../components/ui";
import { DetailGrid, DetailPill, DetailTile } from "../../../components/ui/DetailList";
import { Skeleton } from "../../../components/ui/Skeleton";
import { messageFor } from "../../../lib/errorCodes";
import { formatInstantDate } from "../../../lib/format";
import { toBrowsableUrl } from "../../../lib/url";
import type { Candidate, CandidateEmail } from "../../candidates/api/types";
import * as contactLookupApi from "../api/contactLookupApi";

/**
 * The Contact section: what is known, and the two buttons that go and find the rest.
 *
 * <p>Each channel is offered, spent, or answered — and a channel that was asked and came back empty
 * shows that it was asked rather than another button, because the provider charges the same to say
 * "nothing" twice.
 */
export function ContactPanel({
  projectId,
  candidate,
  canWrite,
  lookupOffered,
  onSaved,
}: {
  projectId: string;
  candidate: Candidate;
  canWrite: boolean;
  /** False where no provider is configured, which is when the buttons do not exist at all. */
  lookupOffered: boolean;
  onSaved: (saved: Candidate) => void;
}) {
  const toast = useToast();
  const contacts = candidate.contacts;
  const profileUrl = toBrowsableUrl(candidate.linkedinUrl);

  const email = useContactLookup("email", projectId, candidate.id, onSaved, toast);
  const phone = useContactLookup("phone", projectId, candidate.id, onSaved, toast);

  const emails = contacts.emails;
  const others = emails.filter((entry) => entry.address !== candidate.email);

  return (
    <div className="space-y-3">
      <DetailGrid>
        <DetailTile
          label="Email"
          value={
            email.isPending ? (
              <Skeleton className="h-4 w-full" />
            ) : (
              <EmailValue address={candidate.email} entry={entryFor(emails, candidate.email)} />
            )
          }
        />
        <DetailTile
          label="Phone"
          value={phone.isPending ? <Skeleton className="h-4 w-full" /> : candidate.phone}
        />
        {/* Through `toBrowsableUrl` rather than straight into the href. Every write is already gated
            by SuppliedText, but trusting that from the render side makes this tile the one place a
            value stored before the gate — or posted by the browser plugin, whose CandidateSource is
            already in the schema — could reach a browser as something it should not follow.
            `lib/url.ts` states the rule; the grids and the company panel already keep it. */}
        <DetailTile
          label="LinkedIn"
          full
          value={
            profileUrl ? (
              <a
                href={profileUrl}
                target="_blank"
                rel="noreferrer noopener"
                className="text-sky hover:underline"
              >
                {profileUrl}
              </a>
            ) : null
          }
        />
      </DetailGrid>

      {others.length > 0 && (
        <ul className="space-y-1">
          {others.map((entry) => (
            <li key={entry.address} className="flex items-center gap-2 font-mono text-[12px] text-text2">
              <span className="truncate">{entry.address}</span>
              <EmailBadges entry={entry} />
            </li>
          ))}
        </ul>
      )}

      {contacts.phones.length > 1 && (
        <ul className="space-y-1">
          {contacts.phones.slice(1).map((number) => (
            <li key={number} className="font-mono text-[12px] text-text2">
              {number}
            </li>
          ))}
        </ul>
      )}

      {lookupOffered && canWrite && (
        <div className="flex flex-wrap items-center gap-x-4 gap-y-1.5">
          <ChannelAction
            label="Find email"
            askedAt={contacts.emailsLookedUpAt}
            found={emails.length > 0}
            emptyLabel="No email on record"
            pending={email.isPending}
            disabled={phone.isPending}
            onFind={() => email.mutate()}
          />
          <ChannelAction
            label="Find phone"
            askedAt={contacts.phonesLookedUpAt}
            found={contacts.phones.length > 0}
            emptyLabel="No phone on record"
            pending={phone.isPending}
            disabled={email.isPending}
            onFind={() => phone.mutate()}
          />
        </div>
      )}

      {contacts.source && (
        <p className="font-mono text-[11px] text-text3">
          Found via {contacts.source}
          {contacts.emailsLookedUpAt && ` · ${formatInstantDate(contacts.emailsLookedUpAt)}`}
        </p>
      )}
    </div>
  );
}

/**
 * One channel's control: the button while it is worth pressing, and afterwards what pressing it
 * established. A spent channel with nothing found says so with its date rather than offering the
 * same purchase again.
 */
function ChannelAction({
  label,
  askedAt,
  found,
  emptyLabel,
  pending,
  disabled,
  onFind,
}: {
  label: string;
  askedAt: string | null;
  found: boolean;
  emptyLabel: string;
  pending: boolean;
  disabled: boolean;
  onFind: () => void;
}) {
  if (askedAt && !found) {
    return (
      <span className="font-mono text-[11px] text-text3">
        {emptyLabel} · checked {formatInstantDate(askedAt)}
      </span>
    );
  }
  if (askedAt) return null;
  return (
    <button
      type="button"
      onClick={onFind}
      disabled={pending || disabled}
      className="font-mono text-[11px] font-semibold uppercase tracking-[0.06em] text-amber transition hover:underline disabled:opacity-50"
    >
      {pending ? "Finding…" : label}
    </button>
  );
}

function EmailValue({ address, entry }: { address: string | null; entry: CandidateEmail | null }) {
  if (!address) return null;
  return (
    <span className="flex items-center gap-2">
      <span className="truncate">{address}</span>
      {entry && <EmailBadges entry={entry} />}
    </span>
  );
}

function EmailBadges({ entry }: { entry: CandidateEmail }) {
  return (
    <>
      {entry.kind && <DetailPill label={entry.kind} />}
      {entry.status && (
        <DetailPill
          label={entry.status}
          className={isVerified(entry) ? "bg-green-dim text-green" : undefined}
        />
      )}
    </>
  );
}

const isVerified = (entry: CandidateEmail) => entry.status?.toLowerCase() === "verified";

const entryFor = (emails: CandidateEmail[], address: string | null) =>
  emails.find((entry) => entry.address === address) ?? null;

function useContactLookup(
  channel: "email" | "phone",
  projectId: string,
  candidateId: string,
  onSaved: (saved: Candidate) => void,
  toast: (message: string) => void,
) {
  return useMutation({
    mutationFn: () =>
      channel === "email"
        ? contactLookupApi.findEmail(projectId, candidateId)
        : contactLookupApi.findPhone(projectId, candidateId),
    onSuccess: (result) => {
      onSaved(result.candidate);
      if (result.outcome === "none") {
        toast(`No ${channel} on record for ${result.candidate.fullName}`);
      }
    },
    onError: (error) => toast(messageFor(error)),
  });
}
