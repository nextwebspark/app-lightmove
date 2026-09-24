import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation } from "@tanstack/react-query";
import { useState, type ReactNode } from "react";
import { FormProvider, useForm } from "react-hook-form";
import { Button, FormError, useToast } from "../../../components/ui";
import { DetailPill } from "../../../components/ui/DetailList";
import { NetworkMark } from "../../../components/ui/NetworkMark";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { cn } from "../../../lib/cn";
import { copyText } from "../../../lib/clipboard";
import { codeOf, messageFor } from "../../../lib/errorCodes";
import { toBrowsableUrl, toReadableUrl } from "../../../lib/url";
import * as candidatesApi from "../../candidates/api/candidatesApi";
import type { Candidate, CandidateEmail, CandidatePhone } from "../../candidates/api/types";
import { ContactEntriesFields, ContactFields } from "../../candidates/components/CandidateFieldGroups";
import { ProfileSectionForm } from "../../candidates/components/ProfileSectionForm";
import {
  contactInputsOf,
  contactSectionOf,
  contactSectionSchema,
  replayOf,
  type ContactSectionForm,
  type ParsedContactSectionForm,
} from "../../candidates/lib/candidateForm";
import * as contactLookupApi from "../api/contactLookupApi";

/**
 * The Contact section: one row per channel — email, phone, LinkedIn — listing everything the mandate
 * knows, with the button that goes and finds more sitting on the row it acts on.
 *
 * <p>Read, or edited in the same place: the pencil turns every line into its inputs, Save writes
 * the whole section at once, Cancel discards. Read mode has no editing affordance at all, so a
 * stray click cannot change a contact. A channel is offered, spent, or answered; a spent channel
 * with nothing from the provider says so with its date rather than offering the same purchase
 * again, and says so to every reader, not only to someone who could press the button.
 */
export function ContactPanel({
  projectId,
  candidate,
  canWrite,
  lookupOffered,
  onSaved,
  editing = false,
  onDone,
  onCancel,
}: {
  projectId: string;
  candidate: Candidate;
  canWrite: boolean;
  /** False where no provider is configured, which is when the buttons do not exist at all. */
  lookupOffered: boolean;
  onSaved: (saved: Candidate) => void;
  /** True while the section's pencil is open. */
  editing?: boolean;
  onDone?: (saved: Candidate) => void;
  onCancel?: () => void;
}) {
  if (editing) {
    return (
      <ContactSectionEditor
        projectId={projectId}
        candidate={candidate}
        onSaved={onSaved}
        onDone={(saved) => {
          onSaved(saved);
          onDone?.(saved);
        }}
        onCancel={() => onCancel?.()}
      />
    );
  }
  return (
    <ContactReadView
      projectId={projectId}
      candidate={candidate}
      canWrite={canWrite}
      lookupOffered={lookupOffered}
      onSaved={onSaved}
    />
  );
}

function ContactReadView({
  projectId,
  candidate,
  canWrite,
  lookupOffered,
  onSaved,
}: {
  projectId: string;
  candidate: Candidate;
  canWrite: boolean;
  lookupOffered: boolean;
  onSaved: (saved: Candidate) => void;
}) {
  const toast = useToast();
  const contacts = candidate.contacts;
  const profileUrl = toBrowsableUrl(candidate.linkedinUrl);
  const hasProfile = Boolean(candidate.linkedinUrl);
  const mayLookUp = lookupOffered && canWrite;

  const email = useContactLookup("email", projectId, candidate.id, onSaved, toast);
  const phone = useContactLookup("phone", projectId, candidate.id, onSaved, toast);

  return (
    <div className="divide-y divide-u-border">
      <ContactChannel
        icon={<Icon d={ICONS.mail} size={14} />}
        label="Email"
        action={
          mayLookUp && !contacts.emailsLookedUpAt ? (
            <FindButton
              channel="email"
              held={contacts.emails.length > 0}
              hasProfile={hasProfile}
              pending={email.isPending}
              disabled={phone.isPending}
              onFind={email.find}
            />
          ) : null
        }
        error={email.inlineError}
      >
        {contacts.emails.length === 0 ? (
          <EmptyLine asked={Boolean(contacts.emailsLookedUpAt)} label="No email on record" />
        ) : (
          contacts.emails.map((entry) => (
            <EmailLine key={entry.address} entry={entry} onCopy={() => copy("Email", entry.address)} />
          ))
        )}
      </ContactChannel>

      <ContactChannel
        icon={<Icon d={ICONS.phone} size={14} />}
        label="Phone"
        action={
          mayLookUp && !contacts.phonesLookedUpAt ? (
            <FindButton
              channel="phone"
              held={contacts.phones.length > 0}
              hasProfile={hasProfile}
              pending={phone.isPending}
              disabled={email.isPending}
              onFind={phone.find}
            />
          ) : null
        }
        error={phone.inlineError}
      >
        {contacts.phones.length === 0 ? (
          <EmptyLine asked={Boolean(contacts.phonesLookedUpAt)} label="No phone on record" />
        ) : (
          contacts.phones.map((entry) => (
            <PhoneLine key={entry.number} entry={entry} onCopy={() => copy("Phone", entry.number)} />
          ))
        )}
      </ContactChannel>

      <ContactChannel icon={<NetworkMark network="linkedin" size={22} />} label="LinkedIn" disc={false}>
        {/* Through `toBrowsableUrl` rather than straight into the href. Every write is already gated
            by SuppliedText, but trusting that from the render side makes this line the one place a
            value stored before the gate — or posted by the browser plugin, whose CandidateSource is
            already in the schema — could reach a browser as something it should not follow.
            `lib/url.ts` states the rule; the grids and the company panel already keep it. */}
        {profileUrl ? (
          <li className="flex min-w-0 items-center gap-1.5">
            <a
              href={profileUrl}
              target="_blank"
              rel="noreferrer noopener"
              title={profileUrl}
              className="group flex min-w-0 items-center gap-1 font-mono text-[13px] text-u-accent"
            >
              <ReadableUrl url={profileUrl} />
              <Icon d={ICONS.externalLink} size={12} className="flex-none text-u-text3 group-hover:text-u-accent" />
            </a>
            <CopyButton what="LinkedIn URL" onCopy={() => copy("LinkedIn URL", profileUrl)} />
          </li>
        ) : (
          <EmptyLine asked={false} label="" />
        )}
      </ContactChannel>
    </div>
  );

  async function copy(what: string, value: string) {
    toast((await copyText(value)) ? `${what} copied` : "Couldn't copy");
  }
}

/**
 * The same three rows, editable. Every line is an input, a kind, a verified toggle and a remove;
 * Save writes both channels in one request and the profile link in a second only if it changed.
 * A person the plugin captured keeps their link locked: it is the page they were read off.
 */
function ContactSectionEditor({
  projectId,
  candidate,
  onSaved,
  onDone,
  onCancel,
}: {
  projectId: string;
  candidate: Candidate;
  /** The contacts landed but the section stays open: the link write after them failed. */
  onSaved: (saved: Candidate) => void;
  onDone: (saved: Candidate) => void;
  onCancel: () => void;
}) {
  const toast = useToast();
  const [submitError, setSubmitError] = useState<string | null>(null);
  const linkLocked = candidate.source === "extension";
  const form = useForm<ContactSectionForm, unknown, ParsedContactSectionForm>({
    resolver: zodResolver(contactSectionSchema),
    defaultValues: contactSectionOf(candidate),
  });

  const saving = useMutation({
    mutationFn: async (parsed: ParsedContactSectionForm) => {
      const withContacts = await candidatesApi.replaceContacts(projectId, candidate.id, {
        emails: contactInputsOf(parsed.emails),
        phones: contactInputsOf(parsed.phones),
      });
      const link = parsed.linkedinUrl || undefined;
      if (linkLocked || link === (candidate.linkedinUrl ?? undefined)) {
        return withContacts;
      }
      try {
        return await candidatesApi.updateCandidate(projectId, candidate.id, {
          ...replayOf(withContacts),
          linkedinUrl: link,
        });
      } catch (error) {
        // The contacts are saved even though the link is not: hand them up so the drawer shows what
        // the server now holds, and leave the link's error on its field.
        onSaved(withContacts);
        throw error;
      }
    },
    onSuccess: (saved) => {
      toast("Contact saved");
      onDone(saved);
    },
    onError: (error) => {
      if (codeOf(error) === "CANDIDATE_PROFILE_URL_LOCKED") {
        form.setError("linkedinUrl", { message: messageFor(error) });
        return;
      }
      setSubmitError(messageFor(error));
    },
  });

  return (
    <FormProvider {...form}>
      <ProfileSectionForm
        onSubmit={form.handleSubmit((parsed) => {
          setSubmitError(null);
          saving.mutate(parsed);
        })}
        onCancel={onCancel}
        saving={saving.isPending}
        error={submitError}
      >
        <div className="divide-y divide-u-border">
          <ContactChannel icon={<Icon d={ICONS.mail} size={14} />} label="Email">
            <ContactEntriesFields channel="email" />
          </ContactChannel>
          <ContactChannel icon={<Icon d={ICONS.phone} size={14} />} label="Phone">
            <ContactEntriesFields channel="phone" />
          </ContactChannel>
          <ContactChannel icon={<NetworkMark network="linkedin" size={22} />} label="LinkedIn" disc={false}>
            <ContactFields lockedUrl={linkLocked ? candidate.linkedinUrl : null} />
          </ContactChannel>
        </div>
      </ProfileSectionForm>
    </FormProvider>
  );
}

function ContactChannel({
  icon,
  label,
  disc = true,
  action,
  error,
  children,
}: {
  icon: ReactNode;
  label: string;
  /** Our own glyphs sit in a grey disc; LinkedIn's logo may not, so it gets the same slot, bare. */
  disc?: boolean;
  action?: ReactNode;
  error?: string | null;
  children: ReactNode;
}) {
  return (
    <div className="flex gap-3 py-3 first:pt-1 last:pb-1">
      <span
        className={cn(
          "mt-0.5 flex size-7 flex-none items-center justify-center rounded-full text-u-text2",
          disc && "bg-u-raised",
        )}
      >
        {icon}
      </span>
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-start justify-between gap-x-3 gap-y-2">
          <div className="min-w-0 flex-1">
            <div className="mb-1 font-mono text-[9.5px] font-semibold uppercase tracking-[0.08em] text-u-text3">
              {label}
            </div>
            <ul className="space-y-1">{children}</ul>
          </div>
          {action}
        </div>
        {error && (
          <div className="mt-2">
            <FormError message={error} />
          </div>
        )}
      </div>
    </div>
  );
}

function EmailLine({ entry, onCopy }: { entry: CandidateEmail; onCopy: () => void }) {
  return (
    <li className="flex min-w-0 items-center gap-1.5">
      <a
        href={`mailto:${entry.address}`}
        className="min-w-0 truncate font-mono text-[13px] text-u-text hover:underline"
      >
        {entry.address}
      </a>
      <ContactPills entry={entry} />
      <CopyButton what="email" onCopy={onCopy} />
    </li>
  );
}

function PhoneLine({ entry, onCopy }: { entry: CandidatePhone; onCopy: () => void }) {
  return (
    <li className="flex min-w-0 items-center gap-1.5">
      <a
        href={`tel:${entry.number.replace(/\s+/g, "")}`}
        className="min-w-0 truncate font-mono text-[13px] text-u-text hover:underline"
      >
        {entry.number}
      </a>
      <ContactPills entry={entry} />
      <CopyButton what="phone" onCopy={onCopy} />
    </li>
  );
}

/**
 * Work / Personal as tagged, then Verified. Who tagged or vouched is deliberately not on the pill:
 * that is the audit trail's answer, and a label naming a provider beside a value a person has since
 * edited would be wrong the moment they saved.
 */
function ContactPills({ entry }: { entry: CandidateEmail | CandidatePhone }) {
  return (
    <>
      {entry.kind && (
        <DetailPill
          label={entry.kind}
          className={entry.kind === "work" ? "bg-u-accent-tint text-u-accent" : "bg-u-accent-tint text-u-accent"}
        />
      )}
      {entry.verified && <DetailPill label="Verified" className="bg-u-direct-tint text-u-direct" />}
    </>
  );
}

function CopyButton({ what, onCopy }: { what: string; onCopy: () => void }) {
  return (
    <button
      type="button"
      onClick={onCopy}
      aria-label={`Copy ${what}`}
      title="Copy"
      className="ms-auto flex flex-none rounded-md p-1 text-u-text3 transition hover:bg-u-raised hover:text-u-text"
    >
      <Icon d={ICONS.copy} size={13} />
    </button>
  );
}

/** `linkedin.com/in/` muted, the handle in the link colour — the part a person actually reads. */
function ReadableUrl({ url }: { url: string }) {
  const readable = toReadableUrl(url);
  const lastSlash = readable.lastIndexOf("/");
  if (lastSlash <= 0) return <span className="truncate hover:underline">{readable}</span>;
  return (
    <span className="min-w-0 truncate">
      <span className="text-u-text3">{readable.slice(0, lastSlash + 1)}</span>
      <span className="group-hover:underline">{readable.slice(lastSlash + 1)}</span>
    </span>
  );
}

/** An em dash for a channel nobody has asked about; the miss, once a lookup ran and found nothing. */
function EmptyLine({ asked, label }: { asked: boolean; label: string }) {
  return <li className="font-mono text-[13px] text-u-text3">{asked ? label : "—"}</li>;
}

/**
 * The button while the channel is worth pressing. It reads "Find more" beside a value a person
 * already supplied — the lookup adds what the provider holds and never overwrites what was typed —
 * and it is disabled, with the reason, until the person has a LinkedIn profile to look up.
 */
function FindButton({
  channel,
  held,
  hasProfile,
  pending,
  disabled,
  onFind,
}: {
  channel: "email" | "phone";
  held: boolean;
  hasProfile: boolean;
  pending: boolean;
  disabled: boolean;
  onFind: () => void;
}) {
  const label = held ? "Find more" : `Find ${channel}`;
  return (
    <div className="flex flex-col items-end gap-1">
      <Button
        type="button"
        variant="secondary"
        loading={pending}
        disabled={disabled || !hasProfile}
        onClick={onFind}
        title={`Spends one ContactOut ${channel} credit${held ? " · your entry is kept" : ""}`}
        className={cn("gap-1.5 whitespace-nowrap px-2.5 py-1.5 text-[12px]")}
      >
        {!pending && <Icon d={ICONS.search} size={13} />}
        {pending ? "Finding…" : label}
      </Button>
      <span className="text-end font-mono text-[10px] text-u-text3">
        {hasProfile ? "Spends 1 credit" : "Add a LinkedIn profile URL to look contacts up"}
      </span>
    </div>
  );
}

/**
 * One channel's lookup. A missing LinkedIn profile is the one refusal that belongs in the section —
 * the field it names is right there — so it stays inline; every other failure toasts.
 */
function useContactLookup(
  channel: "email" | "phone",
  projectId: string,
  candidateId: string,
  onSaved: (saved: Candidate) => void,
  toast: (message: string) => void,
) {
  const [inlineError, setInlineError] = useState<string | null>(null);
  const lookup = useMutation({
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
    onError: (error) => {
      if (codeOf(error) === "CONTACT_LOOKUP_NO_PROFILE") {
        setInlineError(messageFor(error));
        return;
      }
      toast(messageFor(error));
    },
  });
  return {
    isPending: lookup.isPending,
    inlineError,
    find: () => {
      setInlineError(null);
      lookup.mutate();
    },
  };
}
