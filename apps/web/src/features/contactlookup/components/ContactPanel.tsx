import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useState, type ReactNode } from "react";
import { FormProvider, useForm } from "react-hook-form";
import { Button, FormError, useToast, type ToastFn } from "../../../components/ui";
import { DetailPill } from "../../../components/ui/DetailList";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { cn } from "../../../lib/cn";
import { copyText } from "../../../lib/clipboard";
import { codeOf, messageFor } from "../../../lib/errorCodes";
import { BILLING_KEY } from "../../billing/api/billingApi";
import { creditsLabel, isBillingRefusal } from "../../billing/lib/billingView";
import { useBillingRead } from "../../billing/lib/useBilling";
import * as candidatesApi from "../../candidates/api/candidatesApi";
import type { Candidate, CandidateContacts, CandidateEmail, CandidatePhone } from "../../candidates/api/types";
import { ContactEntriesFields } from "../../candidates/components/CandidateFieldGroups";
import { ProfileSectionForm } from "../../candidates/components/ProfileSectionForm";
import {
  contactInputsOf,
  contactSectionOf,
  contactSectionSchema,
  type ContactSectionForm,
  type ParsedContactSectionForm,
} from "../../candidates/lib/candidateForm";
import * as contactLookupApi from "../api/contactLookupApi";

/**
 * The Contact section: one row per channel — email and phone — listing everything the mandate
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
  const hasProfile = Boolean(candidate.linkedinUrl);
  const mayLookUp = lookupOffered && canWrite;

  const email = useContactLookup("email", projectId, candidate.id, onSaved, toast);
  const phone = useContactLookup("phone", projectId, candidate.id, onSaved, toast);
  const prices = useBillingRead(mayLookUp).data?.prices;

  return (
    <ContactChannels
      contacts={contacts}
      emailAction={
        mayLookUp && !contacts.emailsLookedUpAt ? (
          <FindButton
            channel="email"
            price={prices?.email}
            held={contacts.emails.length > 0}
            hasProfile={hasProfile}
            pending={email.isPending}
            disabled={phone.isPending}
            onFind={email.find}
          />
        ) : null
      }
      emailError={email.inlineError}
      phoneAction={
        mayLookUp && !contacts.phonesLookedUpAt ? (
          <FindButton
            channel="phone"
            price={prices?.phone}
            held={contacts.phones.length > 0}
            hasProfile={hasProfile}
            pending={phone.isPending}
            disabled={email.isPending}
            onFind={phone.find}
          />
        ) : null
      }
      phoneError={phone.inlineError}
    />
  );
}

/**
 * Email and phone as read: every value on the ledger with its pills and a copy button. The position drawer
 * hangs its Find buttons on the rows; the workspace drawer reads them bare. The profile link is the header's.
 */
export function ContactChannels({
  contacts,
  emailAction,
  emailError,
  phoneAction,
  phoneError,
}: {
  contacts: CandidateContacts;
  emailAction?: ReactNode;
  emailError?: string | null;
  phoneAction?: ReactNode;
  phoneError?: string | null;
}) {
  const toast = useToast();

  return (
    <div className="divide-y divide-u-border">
      <ContactChannel
        icon={<Icon d={ICONS.mail} size={14} />}
        label="Email"
        action={emailAction}
        error={emailError}
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
        action={phoneAction}
        error={phoneError}
      >
        {contacts.phones.length === 0 ? (
          <EmptyLine asked={Boolean(contacts.phonesLookedUpAt)} label="No phone on record" />
        ) : (
          contacts.phones.map((entry) => (
            <PhoneLine key={entry.number} entry={entry} onCopy={() => copy("Phone", entry.number)} />
          ))
        )}
      </ContactChannel>

    </div>
  );

  async function copy(what: string, value: string) {
    toast((await copyText(value)) ? `${what} copied` : "Couldn't copy");
  }
}

/**
 * The same two rows, editable. Every line is an input, a kind, a verified toggle and a remove;
 * Save writes both channels in one request.
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
  onDone: (saved: Candidate) => void;
  onCancel: () => void;
}) {
  const toast = useToast();
  const [submitError, setSubmitError] = useState<string | null>(null);
  const form = useForm<ContactSectionForm, unknown, ParsedContactSectionForm>({
    resolver: zodResolver(contactSectionSchema),
    defaultValues: contactSectionOf(candidate),
  });

  const saving = useMutation({
    mutationFn: (parsed: ParsedContactSectionForm) =>
      candidatesApi.replaceContacts(projectId, candidate.id, {
        emails: contactInputsOf(parsed.emails),
        phones: contactInputsOf(parsed.phones),
      }),
    onSuccess: (saved) => {
      toast("Contact saved");
      onDone(saved);
    },
    onError: (error) => setSubmitError(messageFor(error)),
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
        </div>
      </ProfileSectionForm>
    </FormProvider>
  );
}

function ContactChannel({
  icon,
  label,
  action,
  error,
  children,
}: {
  icon: ReactNode;
  label: string;
  action?: ReactNode;
  error?: string | null;
  children: ReactNode;
}) {
  return (
    <div className="flex gap-3 py-3 first:pt-1 last:pb-1">
      <span className="mt-0.5 flex size-7 flex-none items-center justify-center rounded-full bg-u-raised text-u-text2">
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

/** An em dash for a channel nobody has asked about; the miss, once a lookup ran and found nothing. */
function EmptyLine({ asked, label }: { asked: boolean; label: string }) {
  return <li className="font-mono text-[13px] text-u-text3">{asked ? label : "—"}</li>;
}

/**
 * The button while the channel is worth pressing. It reads "Find more" beside a value a person
 * already supplied — the lookup adds what the provider holds and never overwrites what was typed —
 * and it is disabled, with the reason, until the person has a LinkedIn profile to look up. Its price is the
 * billing read's, and only a find spends it.
 */
function FindButton({
  channel,
  price,
  held,
  hasProfile,
  pending,
  disabled,
  onFind,
}: {
  channel: "email" | "phone";
  price: number | undefined;
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
        title={held ? "Your entry is kept" : undefined}
        className={cn("gap-1.5 whitespace-nowrap px-2.5 py-1.5 text-[12px]")}
      >
        {!pending && <Icon d={ICONS.search} size={13} />}
        {pending ? "Finding…" : label}
      </Button>
      {hasProfile ? (
        price !== undefined && <span className="text-end font-mono text-[10px] text-u-text3">{creditsLabel(price)}</span>
      ) : (
        <span className="text-end font-mono text-[10px] text-u-text3">Add a LinkedIn profile URL to look contacts up</span>
      )}
    </div>
  );
}

/**
 * One channel's lookup. A missing LinkedIn profile is the one refusal that belongs in the section —
 * the field it names is right there — so it stays inline; running out of credits opens the out-of-credits
 * sheet, and every other failure toasts.
 */
function useContactLookup(
  channel: "email" | "phone",
  projectId: string,
  candidateId: string,
  onSaved: (saved: Candidate) => void,
  toast: ToastFn,
) {
  const queryClient = useQueryClient();
  const [inlineError, setInlineError] = useState<string | null>(null);
  const lookup = useMutation({
    mutationFn: () =>
      channel === "email"
        ? contactLookupApi.findEmail(projectId, candidateId)
        : contactLookupApi.findPhone(projectId, candidateId),
    onSuccess: (result) => {
      onSaved(result.candidate);
      if (result.creditsSpent > 0) void queryClient.invalidateQueries({ queryKey: BILLING_KEY });
      if (result.outcome === "none") {
        toast(`No ${channel} on record for ${result.candidate.fullName}`);
      }
    },
    onError: (error) => {
      if (codeOf(error) === "CONTACT_LOOKUP_NO_PROFILE") {
        setInlineError(messageFor(error));
        return;
      }
      if (isBillingRefusal(error)) return;
      toast.error(messageFor(error));
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
