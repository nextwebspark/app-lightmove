import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { Button, Field, FormError, Input, SegmentedControl, TextArea, Toggle, useToast } from "../../../components/ui";
import { ApiRequestError } from "../../../lib/apiClient";
import { cn } from "../../../lib/cn";
import { messageFor } from "../../../lib/errorCodes";
import { NoteEditor } from "../../notes/components/NoteEditor";
import { useWorkspaceVocabulary } from "../../workspace/lib/vocabulary";
import * as signaturesApi from "../api/signaturesApi";
import type { SignatureFields, SignatureLayout, SignatureSources } from "../api/signaturesApi";
import { customBodyFor, drawsLogo, drawsPhoto, LAYOUT_DETAILS, payloadOf, type SignatureDraft } from "../lib/signatureDraft";
import { SignaturePreviewFrame } from "./SignaturePreviewFrame";

/** How long typing settles before the server draws the preview again. */
const PREVIEW_DELAY_MS = 350;

type FieldKey = keyof SignatureFields;

/**
 * Settings → Email signature's editor (`Settings.dc.html`, `signatureState=editor`): the fields beside a preview the
 * server draws, so what is shown is what an email carries. An empty field shows what it follows, in its placeholder.
 */
export function SignatureEditor({
  initial,
  signatureId,
  sources,
  onDone,
}: {
  initial: SignatureDraft;
  /** Null for a new signature. */
  signatureId: string | null;
  sources: SignatureSources;
  onDone: () => void;
}) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const vocabulary = useWorkspaceVocabulary();
  const [draft, setDraft] = useState(initial);
  const [dark, setDark] = useState(false);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const payload = payloadOf(draft);
  const previewKey = useSettled(JSON.stringify(payload), PREVIEW_DELAY_MS);

  const preview = useQuery({
    queryKey: ["email-signature-preview", previewKey],
    queryFn: ({ signal }) => signaturesApi.previewSignature(JSON.parse(previewKey), signal),
    placeholderData: (previous) => previous,
    retry: false,
  });

  const save = useMutation({
    mutationFn: () =>
      signatureId ? signaturesApi.updateSignature(signatureId, payload) : signaturesApi.createSignature(payload),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: signaturesApi.SIGNATURES_KEY });
      toast("Signature saved");
      onDone();
    },
    onError: (error) => {
      if (error instanceof ApiRequestError && Object.keys(error.fieldErrors).length > 0) {
        setFieldErrors(error.fieldErrors);
        return;
      }
      setFormError(messageFor(error));
    },
  });

  const setField = (key: FieldKey, value: string) => {
    setDraft((current) => ({ ...current, fields: { ...current.fields, [key]: value } }));
    setFieldErrors((current) => ({ ...current, [`fields.${key}`]: "" }));
  };
  const errorOf = (key: FieldKey) => fieldErrors[`fields.${key}`] || undefined;
  const hasPhoto = sources.photoUrl !== null;

  return (
    <div className="flex flex-wrap items-start gap-4">
      <div className="min-w-0 flex-[1_1_340px] rounded-u-card border border-u-border bg-u-raised p-5">
        <FormError message={formError} />
        <Field label="Signature name" error={fieldErrors.name || undefined}>
          <Input
            value={draft.name}
            maxLength={60}
            placeholder={LAYOUT_DETAILS[draft.layout].label}
            onChange={(event) => setDraft({ ...draft, name: event.target.value })}
          />
        </Field>

        <div className="mb-4">
          <span className="type-micro-label mb-1.5 block font-mono text-u-text3">Layout</span>
          <div role="radiogroup" aria-label="Layout" className="flex flex-wrap gap-1.5">
            {signaturesApi.SIGNATURE_LAYOUTS.map((layout) => (
              <LayoutChip
                key={layout}
                layout={layout}
                checked={draft.layout === layout}
                onPick={() =>
                  setDraft({
                    ...draft,
                    layout,
                    customBody:
                      layout === "CUSTOM" && draft.customBody.trim() === ""
                        ? customBodyFor(draft.fields.name || sources.name)
                        : draft.customBody,
                  })
                }
              />
            ))}
          </div>
        </div>

        {draft.layout === "CUSTOM" ? (
          <div className="mb-4">
            <span className="type-micro-label mb-1.5 block font-mono text-u-text3">Signature</span>
            <NoteEditor
              value={draft.customBody}
              format="markdown"
              onChange={(customBody) => setDraft((current) => ({ ...current, customBody }))}
              teammates={null}
              ariaLabel="Signature"
              variant="field"
              expandTitle="Signature"
            />
            <p className="mt-1.5 font-mono text-meta text-u-text3">
              Your logo comes from the switch below. A pasted picture is never sent.
            </p>
          </div>
        ) : (
          <>
            <h3 className="mb-2.5 text-note font-semibold">Your details</h3>
            <div className="grid grid-cols-1 gap-x-3.5 md:grid-cols-2">
              <TextField label="Name" source={sources.name} sourceName="your profile" value={draft.fields.name}
                error={errorOf("name")} onChange={(value) => setField("name", value)} />
              <TextField label="Title" source={sources.title} sourceName="your profile" value={draft.fields.title}
                error={errorOf("title")} onChange={(value) => setField("title", value)} />
              <TextField label="Phone" source={sources.phone} sourceName="your profile" value={draft.fields.phone}
                error={errorOf("phone")} onChange={(value) => setField("phone", value)} type="tel" />
              <TextField label="LinkedIn" source={sources.linkedinUrl} sourceName="your profile"
                value={draft.fields.linkedinUrl} error={errorOf("linkedinUrl")}
                onChange={(value) => setField("linkedinUrl", value)} type="url" />
              <Field label="Email" hint="The mailbox your email goes from">
                <div className="rounded-u-control border border-u-border bg-u-surface px-3 py-2.5 font-mono text-body text-u-text2">
                  {sources.email}
                </div>
              </Field>
            </div>
            <h3 className="mb-2.5 text-note font-semibold">{vocabulary.ownFirm}</h3>
            <div className="grid grid-cols-1 gap-x-3.5 md:grid-cols-2">
              <TextField label={vocabulary.ownFirm} source={sources.company} sourceName="your workspace"
                value={draft.fields.company} error={errorOf("company")} onChange={(value) => setField("company", value)} />
              <TextField label="Website" source={sources.website} sourceName="your workspace"
                value={draft.fields.website} error={errorOf("website")} onChange={(value) => setField("website", value)} />
              <TextField label="Address" source={null} sourceName="" value={draft.fields.address}
                error={errorOf("address")} onChange={(value) => setField("address", value)}
                placeholder="Office address (optional)" />
            </div>
          </>
        )}

        <div className="border-t border-u-border pt-1.5">
          {drawsPhoto(draft.layout) && (
            <SwitchRow
              label="Show my photo"
              hint={hasPhoto ? "Your profile photo. Change it in Profile." : "Upload a photo in Profile to show it here."}
              checked={draft.showPhoto && hasPhoto}
              disabled={!hasPhoto}
              onChange={(showPhoto) => setDraft({ ...draft, showPhoto })}
            />
          )}
          {drawsLogo(draft.layout) && (
            <SwitchRow
              label="Show the company logo"
              hint={
                sources.logoAvailable
                  ? `${sources.company}'s logo, from your workspace`
                  : "Your workspace has no logo yet. An admin picks the company in Settings → General."
              }
              checked={draft.showLogo && sources.logoAvailable}
              disabled={!sources.logoAvailable}
              onChange={(showLogo) => setDraft({ ...draft, showLogo })}
            />
          )}
          <SwitchRow
            label='Add "Book a call with me"'
            hint={
              sources.bookingLinkOffered
                ? "Your booking link, so an executive can pick a time"
                : "Connect your mailbox on the Outreach page to get a booking link."
            }
            checked={draft.showBookingLink && sources.bookingLinkOffered}
            disabled={!sources.bookingLinkOffered}
            onChange={(showBookingLink) => setDraft({ ...draft, showBookingLink })}
          />
          <SwitchRow
            label="Use as my default"
            hint="Ticked on every new email and reply. You can switch it per email."
            checked={draft.isDefault}
            onChange={(isDefault) => setDraft({ ...draft, isDefault })}
          />
        </div>

        <Field label="Disclaimer · optional" error={fieldErrors.disclaimer || undefined}>
          <TextArea
            rows={2}
            maxLength={1000}
            value={draft.disclaimer}
            placeholder="This email and any attachments are confidential…"
            onChange={(event) => setDraft({ ...draft, disclaimer: event.target.value })}
          />
        </Field>

        <div className="mt-2 flex flex-wrap justify-end gap-2.5">
          <Button variant="secondary" onClick={onDone}>
            Cancel
          </Button>
          <Button loading={save.isPending} onClick={() => save.mutate()}>
            Save signature
          </Button>
        </div>
      </div>

      <div className="min-w-0 flex-[1_1_340px] rounded-u-card border border-u-border bg-u-raised p-4 lg:sticky lg:top-0">
        <div className="mb-2.5 flex items-center gap-2.5">
          <h3 className="flex-1 text-note font-semibold">Preview</h3>
          <SegmentedControl
            label="Preview as"
            options={[
              { value: "light", label: "Light" },
              { value: "dark", label: "Dark" },
            ]}
            value={dark ? "dark" : "light"}
            onChange={(value) => setDark(value === "dark")}
          />
        </div>
        {preview.isError ? (
          <p className="text-note text-u-text3">{messageFor(preview.error)}</p>
        ) : preview.data ? (
          <SignaturePreviewFrame title="Signature preview" html={preview.data.html} dark={dark} />
        ) : (
          <div className="h-24 animate-pulse rounded-lg bg-u-surface" />
        )}
        <p className="mt-2.5 font-mono text-meta text-u-text3">
          Drawn as Gmail and Outlook show it. Your photo and logo are hosted by Uncava, so they show in replies and
          forwards too.
        </p>
      </div>
    </div>
  );
}

function LayoutChip({ layout, checked, onPick }: { layout: SignatureLayout; checked: boolean; onPick: () => void }) {
  return (
    <button
      type="button"
      role="radio"
      aria-checked={checked}
      onClick={onPick}
      className={cn(
        "rounded-full border px-3 py-1 text-meta font-medium",
        checked ? "border-u-accent bg-u-accent-tint text-u-text" : "border-u-border-strong bg-u-surface text-u-text2",
      )}
    >
      {LAYOUT_DETAILS[layout].label}
    </button>
  );
}

/** A field that follows `source` while empty: the placeholder shows it, and the hint says where it comes from. */
function TextField({
  label,
  source,
  sourceName,
  value,
  error,
  onChange,
  type = "text",
  placeholder,
}: {
  label: string;
  source: string | null;
  sourceName: string;
  value: string;
  error?: string;
  onChange: (value: string) => void;
  type?: string;
  placeholder?: string;
}) {
  const hint = value.trim() !== "" ? "Only on this signature" : sourceName ? `From ${sourceName}` : "Only on this signature";
  return (
    <Field label={label} error={error} hint={hint}>
      <Input
        type={type}
        value={value}
        invalid={!!error}
        placeholder={source ?? placeholder ?? ""}
        onChange={(event) => onChange(event.target.value)}
      />
    </Field>
  );
}

function SwitchRow({
  label,
  hint,
  checked,
  disabled = false,
  onChange,
}: {
  label: string;
  hint: string;
  checked: boolean;
  disabled?: boolean;
  onChange: (checked: boolean) => void;
}) {
  return (
    <div className="flex items-center gap-3 py-2.5">
      <div className="min-w-0 flex-1">
        <div className="text-note font-medium">{label}</div>
        <div className="mt-0.5 font-mono text-meta text-u-text3">{hint}</div>
      </div>
      <Toggle label={label} checked={checked} disabled={disabled} onChange={onChange} />
    </div>
  );
}

/** `value`, once it has stopped changing for `delayMs`. */
function useSettled(value: string, delayMs: number): string {
  const [settled, setSettled] = useState(value);
  useEffect(() => {
    const timer = setTimeout(() => setSettled(value), delayMs);
    return () => clearTimeout(timer);
  }, [value, delayMs]);
  return settled;
}
