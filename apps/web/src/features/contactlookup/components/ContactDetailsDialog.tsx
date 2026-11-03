import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation } from "@tanstack/react-query";
import { useState } from "react";
import { Controller, FormProvider, useFieldArray, useForm, useFormContext } from "react-hook-form";
import { Button, FormError, Modal, useToast } from "../../../components/ui";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { cn } from "../../../lib/cn";
import { messageFor } from "../../../lib/errorCodes";
import { creditsLabel } from "../../billing/lib/billingView";
import { useBillingRead } from "../../billing/lib/useBilling";
import * as candidatesApi from "../../candidates/api/candidatesApi";
import type { Candidate } from "../../candidates/api/types";
import { PrimaryStar, type ContactEntriesForm } from "../../candidates/components/CandidateFieldGroups";
import {
  contactInputsOf,
  contactSectionOf,
  contactSectionSchema,
  EMPTY_CONTACT_LINE,
  type ContactSectionForm,
  type ParsedContactSectionForm,
} from "../../candidates/lib/candidateForm";
import { useContactLookup } from "../lib/useContactLookup";

type Channel = "email" | "phone";

interface ChannelFind {
  label: string;
  price: number | undefined;
  pending: boolean;
  disabled: boolean;
  needsProfile: boolean;
  reason?: string;
  onFind: () => void;
}

const CHANNEL_WORDS: Record<Channel, { heading: string; noun: string; none: string; placeholder: string }> = {
  email: { heading: "Email", noun: "email", none: "No email address yet", placeholder: "name@company.com" },
  phone: { heading: "Phone", noun: "phone", none: "No phone number yet", placeholder: "+971 50 000 0000" },
};

/**
 * Every email and phone of one person, edited in a centred window — the header's pencil, its "+N" and its Add
 * links open it. A held channel is a list with Find more on its heading; an empty one says so and offers typing
 * one in or finding it. Find waits while the lines hold unsaved edits, so a lookup's answer never lands over
 * what someone is typing.
 */
export function ContactDetailsDialog({
  projectId,
  candidate,
  lookupOffered,
  onSaved,
  onClose,
}: {
  projectId: string;
  candidate: Candidate;
  lookupOffered: boolean;
  onSaved: (saved: Candidate) => void;
  onClose: () => void;
}) {
  const toast = useToast();
  const [submitError, setSubmitError] = useState<string | null>(null);
  const form = useForm<ContactSectionForm, unknown, ParsedContactSectionForm>({
    resolver: zodResolver(contactSectionSchema),
    defaultValues: contactSectionOf(candidate),
  });
  const found = (saved: Candidate) => {
    onSaved(saved);
    form.reset(contactSectionOf(saved));
  };
  const email = useContactLookup("email", projectId, candidate.id, found, toast);
  const phone = useContactLookup("phone", projectId, candidate.id, found, toast);
  const prices = useBillingRead(lookupOffered).data?.prices;
  const contacts = candidate.contacts;
  const editing = form.formState.isDirty;
  const finding = email.isPending || phone.isPending;
  const hasProfile = Boolean(candidate.linkedinUrl);

  const saving = useMutation({
    mutationFn: (parsed: ParsedContactSectionForm) =>
      candidatesApi.replaceContacts(projectId, candidate.id, {
        emails: contactInputsOf(parsed.emails),
        phones: contactInputsOf(parsed.phones),
      }),
    onSuccess: (saved) => {
      toast("Contact saved");
      onSaved(saved);
      onClose();
    },
    onError: (error) => setSubmitError(messageFor(error)),
  });

  const findOf = (channel: Channel): ChannelFind | null => {
    const lookup = channel === "email" ? email : phone;
    const askedAt = channel === "email" ? contacts.emailsLookedUpAt : contacts.phonesLookedUpAt;
    if (!lookupOffered || askedAt) return null;
    const held = (channel === "email" ? contacts.emails : contacts.phones).length > 0;
    return {
      label: held ? "Find more" : `Find ${channel}`,
      price: prices?.[channel],
      pending: lookup.isPending,
      disabled: !hasProfile || editing || (finding && !lookup.isPending),
      needsProfile: !hasProfile,
      reason: !hasProfile
        ? "Add their LinkedIn profile URL so Uncava can find contacts"
        : editing
          ? "Save or cancel your changes first"
          : undefined,
      onFind: lookup.find,
    };
  };

  return (
    <Modal
      open
      onClose={onClose}
      title="Contact details"
      subtitle={candidate.fullName}
      closeButton
      className="md:w-[560px]"
      onSubmit={form.handleSubmit((parsed) => {
        setSubmitError(null);
        saving.mutate(parsed);
      })}
      footer={
        <div className="flex w-full flex-wrap items-center justify-end gap-2">
          {submitError && (
            <div className="me-auto">
              <FormError message={submitError} />
            </div>
          )}
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" loading={saving.isPending} disabled={finding || !editing}>
            Save
          </Button>
        </div>
      }
    >
      <FormProvider {...form}>
        <div className="space-y-6 pb-1 pt-2">
          <ChannelSection
            channel="email"
            find={findOf("email")}
            lookedUp={Boolean(contacts.emailsLookedUpAt)}
            error={email.inlineError}
          />
          <ChannelSection
            channel="phone"
            find={findOf("phone")}
            lookedUp={Boolean(contacts.phonesLookedUpAt)}
            error={phone.inlineError}
          />
          <p className="flex items-center gap-1.5 text-meta text-u-text3">
            <svg width="11" height="11" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true" className="flex-none text-u-accent">
              <path d={ICONS.star} />
            </svg>
            marks the one used first. Shared on every position this person is in.
          </p>
        </div>
      </FormProvider>
    </Modal>
  );
}

function ChannelSection({
  channel,
  find,
  lookedUp,
  error,
}: {
  channel: Channel;
  find: ChannelFind | null;
  lookedUp: boolean;
  error: string | null;
}) {
  const { control, formState, getValues, setValue } = useFormContext<ContactEntriesForm>();
  const name = channel === "email" ? "emails" : "phones";
  const lines = useFieldArray({ control, name });
  const words = CHANNEL_WORDS[channel];
  const count = lines.fields.length;
  const listError = formState.errors[name]?.message;

  const add = () => lines.append({ ...EMPTY_CONTACT_LINE, primary: count === 0 });
  const remove = (index: number) => {
    const wasPrimary = getValues(`${name}.${index}.primary`);
    lines.remove(index);
    if (wasPrimary && getValues(name).length > 0) {
      setValue(`${name}.0.primary`, true, { shouldDirty: true });
    }
  };
  const makePrimary = (index: number) =>
    getValues(name).forEach((_, other) =>
      setValue(`${name}.${other}.primary`, other === index, { shouldDirty: true }),
    );

  return (
    <section aria-label={words.heading}>
      <div className="mb-2 flex min-h-8 items-center justify-between gap-3">
        <h3 className="flex items-center gap-2 type-heading text-u-text">
          <Icon d={channel === "email" ? ICONS.mail : ICONS.phone} size={14} className="text-u-text3" />
          {words.heading}
          {count > 0 && (
            <span className="rounded-full bg-u-raised px-1.5 font-mono text-meta font-normal text-u-text3">{count}</span>
          )}
        </h3>
        {count > 0 && find && <FindAction find={find} />}
      </div>

      {count === 0 ? (
        <EmptyChannel channel={channel} find={find} lookedUp={lookedUp} onAdd={add} />
      ) : (
        <ul className="divide-y divide-u-border overflow-hidden rounded-u-chip border border-u-border bg-u-surface">
          {lines.fields.map((line, index) => (
            <ContactLine
              key={line.id}
              channel={channel}
              index={index}
              onMakePrimary={() => makePrimary(index)}
              onRemove={() => remove(index)}
            />
          ))}
          {count < 10 && (
            <li>
              <button
                type="button"
                onClick={add}
                className="flex w-full items-center gap-2 px-3 py-2.5 text-start text-note text-u-text3 transition hover:bg-u-raised hover:text-u-text"
              >
                <Icon d={ICONS.plus} size={13} />
                Add {words.noun}
              </button>
            </li>
          )}
        </ul>
      )}

      {listError && (
        <p role="alert" className="mt-1.5 font-mono text-meta text-u-offlimits">
          {listError}
        </p>
      )}
      {error && (
        <div className="mt-2">
          <FormError message={error} />
        </div>
      )}
    </section>
  );
}

function ContactLine({
  channel,
  index,
  onMakePrimary,
  onRemove,
}: {
  channel: Channel;
  index: number;
  onMakePrimary: () => void;
  onRemove: () => void;
}) {
  const { control, register, formState } = useFormContext<ContactEntriesForm>();
  const name = channel === "email" ? "emails" : "phones";
  const words = CHANNEL_WORDS[channel];
  const label = `${words.heading} ${index + 1}`;
  const valueError = formState.errors[name]?.[index]?.value?.message;

  return (
    <li className="px-2 py-1.5">
      <div className="flex flex-wrap items-center gap-x-1.5 gap-y-1">
        <Controller
          control={control}
          name={`${name}.${index}.primary`}
          render={({ field }) => (
            <PrimaryStar noun={channel} primary={field.value} onMakePrimary={onMakePrimary} />
          )}
        />
        <input
          {...register(`${name}.${index}.value`)}
          inputMode={channel === "email" ? "email" : "tel"}
          autoComplete="off"
          placeholder={words.placeholder}
          aria-label={label}
          aria-invalid={Boolean(valueError)}
          className={cn(
            "h-8 min-w-[180px] flex-1 rounded-u-control border bg-transparent px-2 font-mono text-body text-u-text outline-none transition",
            "placeholder:text-u-text3 hover:bg-u-raised focus:bg-u-bg",
            valueError ? "border-u-offlimits" : "border-transparent focus:border-u-accent",
          )}
        />
        <div className="ms-auto flex flex-none items-center gap-1">
          <Controller
            control={control}
            name={`${name}.${index}.kind`}
            render={({ field }) => (
              <div role="group" aria-label={`${label} kind`} className="flex items-center rounded-u-control bg-u-raised p-0.5">
                {(["work", "personal"] as const).map((kind) => (
                  <button
                    key={kind}
                    type="button"
                    aria-pressed={field.value === kind}
                    onClick={() => field.onChange(field.value === kind ? "" : kind)}
                    className={cn(
                      "rounded px-2 py-0.5 text-meta font-medium transition",
                      field.value === kind
                        ? "bg-u-surface text-u-text shadow-u-e1"
                        : "text-u-text3 hover:text-u-text2",
                    )}
                  >
                    {kind === "work" ? "Work" : "Personal"}
                  </button>
                ))}
              </div>
            )}
          />
          <Controller
            control={control}
            name={`${name}.${index}.verified`}
            render={({ field }) => (
              <button
                type="button"
                role="switch"
                aria-checked={field.value}
                aria-label={`${label} verified`}
                title={field.value ? "Verified — click to unmark" : "Mark as verified"}
                onClick={() => field.onChange(!field.value)}
                className={cn(
                  "inline-flex h-7 items-center gap-1 rounded-u-control px-1.5 text-meta font-medium transition",
                  field.value ? "text-u-direct" : "text-u-text3 hover:bg-u-raised hover:text-u-text2",
                )}
              >
                <Icon d={field.value ? ICONS.checkCircle : OPEN_CIRCLE} size={14} />
                Verified
              </button>
            )}
          />
          <button
            type="button"
            onClick={onRemove}
            aria-label={`Remove ${words.noun} ${index + 1}`}
            title="Remove"
            className="grid size-7 place-items-center rounded-u-control text-u-text3 transition hover:bg-u-offlimits-tint hover:text-u-offlimits"
          >
            <Icon d={ICONS.trash} size={14} />
          </button>
        </div>
      </div>
      {valueError && (
        <p role="alert" className="mt-0.5 ps-8 font-mono text-meta text-u-offlimits">
          {valueError}
        </p>
      )}
    </li>
  );
}

const OPEN_CIRCLE = "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18";

function EmptyChannel({
  channel,
  find,
  lookedUp,
  onAdd,
}: {
  channel: Channel;
  find: ChannelFind | null;
  lookedUp: boolean;
  onAdd: () => void;
}) {
  const words = CHANNEL_WORDS[channel];
  const hint = lookedUp
    ? "A lookup found none. Type one in if you have it."
    : !find
      ? "Type one in to keep it on their record."
      : find.needsProfile
        ? "Type one in. Uncava can find one once their LinkedIn profile URL is added."
        : "Type one in, or find it with Uncava.";
  return (
    <div className="flex flex-col gap-3 rounded-u-chip border border-dashed border-u-border-strong px-4 py-3.5 sm:flex-row sm:items-center">
      <div className="min-w-0 flex-1">
        <div className="text-body font-medium text-u-text2">{words.none}</div>
        <div className="text-note text-u-text3">{hint}</div>
      </div>
      <div className="flex flex-none flex-wrap items-center gap-2">
        <Button type="button" variant="secondary" size="xs" onClick={onAdd} className="gap-1.5">
          <Icon d={ICONS.plus} size={13} />
          Add {words.noun}
        </Button>
        {find && <FindAction find={find} />}
      </div>
    </div>
  );
}

/** Find, its price beside its name; a disabled one says why on hover, which a disabled button cannot. */
function FindAction({ find }: { find: ChannelFind }) {
  return (
    <span title={find.reason}>
      <Button
        type="button"
        variant="secondary"
        size="xs"
        loading={find.pending}
        disabled={find.disabled}
        onClick={find.onFind}
        className="gap-1.5 whitespace-nowrap"
      >
        {!find.pending && <Icon d={ICONS.search} size={13} />}
        {find.pending ? "Finding…" : find.label}
        {!find.pending && find.price !== undefined && (
          <span className="font-mono text-meta text-u-text3">· {creditsLabel(find.price)}</span>
        )}
      </Button>
    </span>
  );
}
