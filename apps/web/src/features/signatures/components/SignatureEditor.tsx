import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { Button, Field, FormError, Input, LinesSkeleton, SegmentedControl, TextArea, Toggle, useToast } from "../../../components/ui";
import { ApiRequestError } from "../../../lib/apiClient";
import { messageFor } from "../../../lib/errorCodes";
import { NoteEditor, type NotePictures } from "../../notes/components/NoteEditor";
import * as signaturesApi from "../api/signaturesApi";
import type { SaveSignaturePayload, SignatureSources } from "../api/signaturesApi";
import { payloadOf, type SignatureDraft } from "../lib/signatureDraft";
import { SignaturePreviewFrame } from "./SignaturePreviewFrame";

/** How long typing settles before the server draws the preview again. */
const PREVIEW_DELAY_MS = 350;
/** `SaveSignatureRequest.customBody`'s `@Size`. */
const CUSTOM_BODY_MAX_LENGTH = 2000;
/** `SignaturePicture.MAX_PER_SIGNATURE`. */
const MAX_PICTURES = 6;

/**
 * Settings → Email signature's editor: one editor for the whole signature, beside a preview the server draws. A
 * starting point, or a signature saved in an older fixed layout, is first turned into the editor's words by the server
 * (`starterFrom`), filled in from the profile, the workspace and the mailbox; from there anything can be typed, and up
 * to six pictures inserted.
 */
export function SignatureEditor({
  initial,
  starterFrom,
  ...rest
}: {
  initial: SignatureDraft;
  /** Null when the draft already holds its words. */
  starterFrom: SaveSignaturePayload | null;
  /** Null for a new signature. */
  signatureId: string | null;
  sources: SignatureSources;
  onDone: () => void;
}) {
  const starter = useQuery({
    queryKey: ["email-signature-starter", starterFrom],
    queryFn: () => signaturesApi.signatureStarter(starterFrom!),
    enabled: starterFrom !== null,
    staleTime: Infinity,
    gcTime: 0,
    retry: false,
  });
  if (starterFrom === null) return <SignatureForm initial={initial} {...rest} />;
  if (starter.isError) return <p className="text-note text-u-text3">{messageFor(starter.error)}</p>;
  if (!starter.data) return <LinesSkeleton />;
  return <SignatureForm initial={{ ...initial, customBody: starter.data.customBody }} {...rest} />;
}

function SignatureForm({
  initial,
  signatureId,
  sources,
  onDone,
}: {
  initial: SignatureDraft;
  signatureId: string | null;
  sources: SignatureSources;
  onDone: () => void;
}) {
  const queryClient = useQueryClient();
  const toast = useToast();
  // A link that is not offered is saved off, so a mailbox connected later does not bring back a switch shown off.
  const [draft, setDraft] = useState(() =>
    sources.bookingLinkOffered ? initial : { ...initial, showBookingLink: false },
  );
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
        // A field this form draws no error under still has to say something, or Save looks like it did nothing.
        if (!Object.keys(error.fieldErrors).some(isDrawnField)) setFormError(messageFor(error));
        return;
      }
      setFormError(messageFor(error));
    },
  });

  const sendTest = useMutation({
    mutationFn: () => signaturesApi.sendSignatureTest(payload),
    onSuccess: ({ sentTo }) => toast(`Test sent to ${sentTo}`),
    onError: (error) => {
      if (error instanceof ApiRequestError && Object.keys(error.fieldErrors).length > 0) {
        setFieldErrors(error.fieldErrors);
      }
      toast.error(messageFor(error));
    },
  });

  const [pictures] = useState<NotePictures>(() => ({
    upload: signaturesApi.uploadSignaturePicture,
    max: MAX_PICTURES,
    onLimit: () => toast.error(`A signature holds up to ${MAX_PICTURES} pictures`),
    onError: (error) => toast.error(messageFor(error)),
  }));

  const customBodyTooLong = draft.customBody.length > CUSTOM_BODY_MAX_LENGTH;
  const customBodyError =
    fieldErrors.customBody || (customBodyTooLong ? `Keep it to ${CUSTOM_BODY_MAX_LENGTH.toLocaleString()} characters` : "");

  return (
    <div className="flex flex-wrap items-start gap-4">
      <div className="min-w-0 flex-[1_1_340px] rounded-u-card border border-u-border bg-u-raised p-5">
        <FormError message={formError} />
        <Field label="Signature name" error={fieldErrors.name || undefined}>
          <Input
            className="bg-u-surface"
            value={draft.name}
            maxLength={60}
            placeholder="Signature"
            onChange={(event) => setDraft({ ...draft, name: event.target.value })}
          />
        </Field>

        <div className="mb-4">
          <span className="type-micro-label mb-1.5 block font-mono text-u-text3">Signature</span>
          <div className="rounded-u-card border border-u-border-strong bg-u-surface px-3 py-2 transition focus-within:border-u-accent">
            <NoteEditor
              value={draft.customBody}
              format="markdown"
              onChange={(customBody) => {
                setDraft((current) => ({ ...current, customBody }));
                setFieldErrors((current) => ({ ...current, customBody: "" }));
              }}
              teammates={null}
              ariaLabel="Signature"
              variant="field"
              expandTitle="Signature"
              pictures={pictures}
            />
          </div>
          <span aria-live="polite">
            {customBodyError && (
              <span className="mt-1.5 block font-mono text-meta text-u-offlimits">
                {customBodyError} ({draft.customBody.length.toLocaleString()} now).
              </span>
            )}
          </span>
          <p className="mt-1.5 font-mono text-meta text-u-text3">
            Type anything — another phone number, an address — and insert up to {MAX_PICTURES} pictures, like a group
            logo or a membership badge. These are your own words: a later change to your profile won't update them.
          </p>
        </div>

        <div className="border-t border-u-border pt-1.5">
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
            hint={
              initial.isDefault && signatureId
                ? "This is your default. Make another one the default to change it."
                : "Ticked on every new email and reply. You can switch it per email."
            }
            checked={draft.isDefault}
            disabled={initial.isDefault && signatureId !== null}
            onChange={(isDefault) => setDraft({ ...draft, isDefault })}
          />
        </div>

        <div className="mt-2">
          <Field label="Disclaimer · optional" error={fieldErrors.disclaimer || undefined}>
            <TextArea
              className="bg-u-surface"
              rows={2}
              maxLength={1000}
              value={draft.disclaimer}
              placeholder="This email and any attachments are confidential…"
              onChange={(event) => setDraft({ ...draft, disclaimer: event.target.value })}
            />
          </Field>
        </div>

        <div className="mt-2 flex flex-wrap justify-end gap-2.5">
          <Button variant="secondary" onClick={onDone}>
            Cancel
          </Button>
          <Button
            variant="secondary"
            loading={sendTest.isPending}
            disabled={customBodyTooLong}
            onClick={() => sendTest.mutate()}
          >
            Send test to me
          </Button>
          <Button loading={save.isPending} disabled={customBodyTooLong} onClick={() => save.mutate()}>
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
          Drawn as Gmail and Outlook show it. Your pictures are hosted by Uncava, so they show in replies and forwards
          too.
        </p>
      </div>
    </div>
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

function isDrawnField(key: string): boolean {
  return key === "name" || key === "disclaimer" || key === "customBody";
}
