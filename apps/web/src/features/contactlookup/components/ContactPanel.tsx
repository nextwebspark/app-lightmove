import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation } from "@tanstack/react-query";
import { useState, type ReactNode } from "react";
import { FormProvider, useForm } from "react-hook-form";
import { Button, FormError, useToast } from "../../../components/ui";
import { DetailPill } from "../../../components/ui/DetailList";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { cn } from "../../../lib/cn";
import { messageFor } from "../../../lib/errorCodes";
import { creditsLabel, shownPricesOf } from "../../billing/lib/billingView";
import { useBillingRead } from "../../billing/lib/useBilling";
import * as candidatesApi from "../../candidates/api/candidatesApi";
import type {
  Candidate,
  CandidateContacts,
  CandidateEmail,
  CandidatePhone,
  LastCall,
} from "../../candidates/api/types";
import { ContactEntriesFields, PrimaryStar } from "../../candidates/components/CandidateFieldGroups";
import { ProfileSectionForm } from "../../candidates/components/ProfileSectionForm";
import {
  contactInputsOf,
  contactSectionOf,
  contactSectionSchema,
  contactsWithPrimary,
  type ContactSectionForm,
  type ParsedContactSectionForm,
} from "../../candidates/lib/candidateForm";
import { lastCallLine, lastCallTitle } from "../../candidates/lib/lastCall";
import { useContactLookup } from "../lib/useContactLookup";
import { dialHrefOf } from "../lib/phoneLinks";
import { CopyContactButton } from "./CopyContactButton";
import { isReachable } from "../lib/phoneReach";
import {
  AddedCodePill,
  CopyPhoneButton,
  PhoneNumberLink,
  SaveInternationalButton,
  WhatsAppLink,
  type PhoneReach,
} from "./PhoneActions";

/**
 * The Contact section: one row per channel with its Find button. The pencil edits every line in place and Save writes
 * the section whole. A channel already looked up with nothing found says so with its date, to every reader.
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
  const prices = shownPricesOf(useBillingRead(mayLookUp).data);
  const makingPrimary = useMutation({
    mutationFn: ({ channel, value }: { channel: "email" | "phone"; value: string }) =>
      candidatesApi.replaceContacts(projectId, candidate.id, contactsWithPrimary(contacts, channel, value)),
    onSuccess: (saved, { channel }) => {
      toast(`Primary ${channel} set`);
      onSaved(saved);
    },
    onError: (error) => toast.error(messageFor(error)),
  });

  return (
    <ContactChannels
      contacts={contacts}
      onMakePrimary={
        canWrite && !makingPrimary.isPending
          ? (channel, value) => makingPrimary.mutate({ channel, value })
          : undefined
      }
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
 * Email and phone as read: every value on the ledger with its pills and a copy button, the primary first and
 * starred once a channel holds more than one. The position drawer hangs its Find buttons on the rows and offers
 * Make primary on the others; the workspace drawer reads them bare. The profile link is the header's. Given `reach`, a
 * phone is the button (#1337): Call, WhatsApp and Copy, its added calling code named and offered to keep. Given
 * `lastCall`, a staff-only read, the Phone row says who last called and how it went.
 */
export function ContactChannels({
  contacts,
  emailAction,
  emailError,
  phoneAction,
  phoneError,
  onMakePrimary,
  reach,
  lastCall,
}: {
  contacts: CandidateContacts;
  emailAction?: ReactNode;
  emailError?: string | null;
  phoneAction?: ReactNode;
  phoneError?: string | null;
  onMakePrimary?: (channel: "email" | "phone", value: string) => void;
  reach?: PhoneReach;
  lastCall?: LastCall | null;
}) {
  const starOf = (channel: "email" | "phone", count: number, value: string, index: number) =>
    count > 1 ? (
      <PrimaryStar
        noun={channel}
        primary={index === 0}
        onMakePrimary={onMakePrimary && (() => onMakePrimary(channel, value))}
      />
    ) : null;

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
          contacts.emails.map((entry, index) => (
            <EmailLine
              key={entry.address}
              entry={entry}
              star={starOf("email", contacts.emails.length, entry.address, index)}
            />
          ))
        )}
      </ContactChannel>

      <ContactChannel
        icon={<Icon d={ICONS.phone} size={14} />}
        label="Phone"
        action={phoneAction}
        error={phoneError}
        footnote={
          lastCall ? (
            <p title={lastCallTitle(lastCall)} className="mt-1.5 font-mono text-meta text-u-text3">
              {lastCallLine(lastCall)}
            </p>
          ) : null
        }
      >
        {contacts.phones.length === 0 ? (
          <EmptyLine asked={Boolean(contacts.phonesLookedUpAt)} label="No phone on record" />
        ) : (
          contacts.phones.map((entry, index) => (
            <PhoneLine
              key={entry.number}
              entry={entry}
              star={starOf("phone", contacts.phones.length, entry.number, index)}
              reach={reach}
            />
          ))
        )}
      </ContactChannel>

    </div>
  );
}

/**
 * The same two rows, editable. Every line is an input, a kind, a verified toggle and a remove;
 * Save writes both channels in one request.
 */
function ContactSectionEditor({
  projectId,
  candidate,
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
            <ContactEntriesFields channel="email" offerPrimary />
          </ContactChannel>
          <ContactChannel icon={<Icon d={ICONS.phone} size={14} />} label="Phone">
            <ContactEntriesFields channel="phone" offerPrimary />
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
  footnote,
}: {
  icon: ReactNode;
  label: string;
  action?: ReactNode;
  error?: string | null;
  children: ReactNode;
  footnote?: ReactNode;
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
            {footnote}
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

function EmailLine({ entry, star }: { entry: CandidateEmail; star: ReactNode }) {
  return (
    <li className="flex min-w-0 items-center gap-1.5">
      {star}
      <a
        href={`mailto:${entry.address}`}
        className="min-w-0 truncate font-mono text-[13px] text-u-text hover:underline"
      >
        {entry.address}
      </a>
      <ContactPills entry={entry} />
      <CopyContactButton what="Email" value={entry.address} className="ms-auto" />
    </li>
  );
}

const PHONE_TEXT = "min-w-0 truncate font-mono text-[13px] text-u-text";

function PhoneLine({
  entry,
  star,
  reach,
}: {
  entry: CandidatePhone;
  star: ReactNode;
  reach?: PhoneReach;
}) {
  if (reach) {
    return (
      <li className="flex min-w-0 flex-wrap items-center gap-1.5">
        {star}
        <PhoneNumberLink
          phone={entry}
          reach={reach}
          className={cn("font-mono text-body", isReachable(reach) ? "text-u-accent" : "text-u-text")}
        />
        <ContactPills entry={entry} />
        <AddedCodePill phone={entry} reach={reach} />
        <span className="ms-auto flex flex-none items-center gap-0.5">
          <WhatsAppLink phone={entry} reach={reach} />
          {isReachable(reach) && <CopyPhoneButton phone={entry} reach={reach} />}
        </span>
        <SaveInternationalButton phone={entry} reach={reach} />
      </li>
    );
  }
  const href = dialHrefOf(entry);
  return (
    <li className="flex min-w-0 items-center gap-1.5">
      {star}
      {href ? (
        <a href={href} className={cn(PHONE_TEXT, "hover:underline")}>
          {entry.number}
        </a>
      ) : (
        <span className={PHONE_TEXT}>{entry.number}</span>
      )}
      <ContactPills entry={entry} />
      <CopyContactButton what="Phone" value={entry.number} className="ms-auto" />
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
        <span className="text-end font-mono text-[10px] text-u-text3">Add their LinkedIn profile URL so Uncava can find contacts</span>
      )}
    </div>
  );
}
