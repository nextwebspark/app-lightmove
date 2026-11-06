import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState, type ReactNode } from "react";
import { Link } from "react-router-dom";
import { Button, Field, FormError, Input, SegmentedControl, TextArea, Toggle, useToast } from "../../../components/ui";
import { ApiRequestError } from "../../../lib/apiClient";
import { cn } from "../../../lib/cn";
import { messageFor } from "../../../lib/errorCodes";
import { useAuth } from "../../auth/AuthProvider";
import { NoteEditor } from "../../notes/components/NoteEditor";
import { useWorkspaceVocabulary } from "../../workspace/lib/vocabulary";
import * as signaturesApi from "../api/signaturesApi";
import type { SignatureFields, SignatureLayout, SignatureSources } from "../api/signaturesApi";
import { customBodyFor, drawsLogo, drawsPhoto, LAYOUT_DETAILS, payloadOf, type SignatureDraft } from "../lib/signatureDraft";
import { SignaturePreviewFrame } from "./SignaturePreviewFrame";

/** How long typing settles before the server draws the preview again. */
const PREVIEW_DELAY_MS = 350;
/** `SaveSignatureRequest.customBody`'s `@Size`. */
const CUSTOM_BODY_MAX_LENGTH = 2000;

type FieldKey = keyof SignatureFields;

/**
 * Settings → Email signature's editor (`Settings.dc.html`, `signatureState=editor`): the fields beside a preview the
 * server draws, so what is shown is what an email carries. Only what can be typed into is a field: a value the profile or
 * the workspace gives is text until Change makes it this signature's own.
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
  const { user } = useAuth();
  const isAdmin = user?.workspace?.roles.includes("ADMIN") ?? false;
  // A link that is not offered is saved off, so a mailbox connected later does not bring back a switch shown off.
  const [draft, setDraft] = useState(() =>
    sources.bookingLinkOffered ? initial : { ...initial, showBookingLink: false },
  );
  const [dark, setDark] = useState(false);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [opened, setOpened] = useState<ReadonlySet<FieldKey>>(() => new Set());
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

  const setField = (key: FieldKey, value: string) => {
    setDraft((current) => ({ ...current, fields: { ...current.fields, [key]: value } }));
    setFieldErrors((current) => ({ ...current, [`fields.${key}`]: "" }));
  };
  const customBodyTooLong = draft.layout === "CUSTOM" && draft.customBody.length > CUSTOM_BODY_MAX_LENGTH;
  const customBodyError =
    fieldErrors.customBody || (customBodyTooLong ? `Keep it to ${CUSTOM_BODY_MAX_LENGTH.toLocaleString()} characters` : "");
  const errorOf = (key: FieldKey) => fieldErrors[`fields.${key}`] || undefined;
  const open = (key: FieldKey) => setOpened((current) => new Set(current).add(key));
  const followSource = (key: FieldKey) => {
    setField(key, "");
    setOpened((current) => {
      const next = new Set(current);
      next.delete(key);
      return next;
    });
  };
  const sourcedRow = (key: FieldKey, label: string, source: string | null, origin: "profile" | "workspace", type = "text") => (
    <SourcedRow
      label={label}
      source={source}
      origin={origin}
      value={draft.fields[key]}
      isOpen={opened.has(key) || draft.fields[key] !== "" || !!errorOf(key)}
      error={errorOf(key)}
      type={type}
      onOpen={() => open(key)}
      onChange={(value) => setField(key, value)}
      onFollowSource={() => followSource(key)}
    />
  );
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
              onChange={(customBody) => {
                setDraft((current) => ({ ...current, customBody }));
                setFieldErrors((current) => ({ ...current, customBody: "" }));
              }}
              teammates={null}
              ariaLabel="Signature"
              variant="field"
              expandTitle="Signature"
            />
            <span aria-live="polite">
              {customBodyError && (
                <span className="mt-1.5 block font-mono text-meta text-u-offlimits">
                  {customBodyError} ({draft.customBody.length.toLocaleString()} now).
                </span>
              )}
            </span>
            <p className="mt-1.5 font-mono text-meta text-u-text3">
              Your logo comes from the switch below. A pasted picture is never sent.
            </p>
          </div>
        ) : (
          <>
            <RowGroup
              title="Your details"
              sourceLink={<Link to="/settings/profile" className="text-note font-medium text-u-accent hover:underline">Edit profile</Link>}
            >
              {sourcedRow("name", "Name", sources.name, "profile")}
              {sourcedRow("title", "Title", sources.title, "profile")}
              {sourcedRow("phone", "Phone", sources.phone, "profile", "tel")}
              {sourcedRow("linkedinUrl", "LinkedIn", sources.linkedinUrl, "profile", "url")}
              <DetailRow label="Email">
                <div className="flex min-h-6 items-center gap-3">
                  <span className="min-w-0 flex-1 truncate font-mono text-body text-u-text2">{sources.email}</span>
                  <span
                    title="Emails go from the mailbox you connected"
                    className="inline-flex shrink-0 items-center gap-1.5 font-mono text-meta text-u-text3"
                  >
                    <LockGlyph />
                    Your mailbox
                  </span>
                </div>
              </DetailRow>
            </RowGroup>
            <RowGroup
              title={vocabulary.ownFirm}
              sourceLink={
                isAdmin ? (
                  <Link to="/settings/general" className="text-note font-medium text-u-accent hover:underline">
                    Edit in General
                  </Link>
                ) : null
              }
            >
              {sourcedRow("company", "Name", sources.company, "workspace")}
              {sourcedRow("website", "Website", sources.website, "workspace", "url")}
              <DetailRow label="Address" isField>
                <Input
                  aria-label="Address"
                  value={draft.fields.address}
                  invalid={!!errorOf("address")}
                  placeholder="Office address"
                  onChange={(event) => setField("address", event.target.value)}
                />
                <RowNote error={errorOf("address")}>Only on this signature · optional</RowNote>
              </DetailRow>
            </RowGroup>
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

function RowGroup({ title, sourceLink, children }: { title: string; sourceLink: ReactNode; children: ReactNode }) {
  return (
    <section className="mb-4.5">
      <div className="mb-2 flex items-baseline gap-2.5">
        <h3 className="flex-1 text-note font-semibold">{title}</h3>
        {sourceLink}
      </div>
      <div className="divide-y divide-u-border rounded-u-card border border-u-border bg-u-surface">{children}</div>
    </section>
  );
}

function DetailRow({ label, isField = false, children }: { label: string; isField?: boolean; children: ReactNode }) {
  return (
    <div className={cn("flex gap-3 px-3.5 py-2.5", isField ? "items-start" : "items-center")}>
      <span className={cn("w-21 shrink-0 text-note font-medium text-u-text3", isField && "pt-2.5")}>{label}</span>
      <div className="min-w-0 flex-1">{children}</div>
    </div>
  );
}

function RowNote({ error, children }: { error?: string; children: ReactNode }) {
  return (
    <span aria-live="polite" className="mt-1.5 block font-mono text-meta">
      {error ? <span className="text-u-offlimits">{error}</span> : <span className="text-u-text3">{children}</span>}
    </span>
  );
}

/** Text tagged with where it comes from, until Change (+ Add with nothing to follow) makes it this signature's own field. */
function SourcedRow({
  label,
  source,
  origin,
  value,
  isOpen,
  error,
  type,
  onOpen,
  onChange,
  onFollowSource,
}: {
  label: string;
  source: string | null;
  origin: "profile" | "workspace";
  value: string;
  isOpen: boolean;
  error?: string;
  type: string;
  onOpen: () => void;
  onChange: (value: string) => void;
  onFollowSource: () => void;
}) {
  if (isOpen) {
    return (
      <DetailRow label={label} isField>
        <Input
          aria-label={label}
          type={type}
          value={value}
          invalid={!!error}
          placeholder={source ?? ""}
          autoFocus={value === "" && !error}
          onChange={(event) => onChange(event.target.value)}
        />
        <span className="mt-1.5 flex flex-wrap items-baseline gap-x-2 font-mono text-meta">
          {error ? <span aria-live="polite" className="text-u-offlimits">{error}</span> : <span className="text-u-text3">Only on this signature</span>}
          <button type="button" onClick={onFollowSource} className="font-sans font-medium text-u-accent hover:underline">
            {source ? `Use ${origin}'s` : "Remove"}
          </button>
        </span>
      </DetailRow>
    );
  }
  return (
    <DetailRow label={label}>
      <div className="flex min-h-6 items-center gap-3">
        {source ? (
          <span className="flex min-w-0 flex-1 items-center gap-2">
            <span className="truncate text-body text-u-text">{source}</span>
            <span className="shrink-0 rounded-full border border-u-border bg-u-raised px-1.5 font-mono text-eyebrow text-u-text3">
              {origin === "profile" ? "Profile" : "Workspace"}
            </span>
          </span>
        ) : (
          <span className="min-w-0 flex-1 text-body text-u-text3 italic">Not in your {origin}</span>
        )}
        <Button variant="secondary" size="xs" aria-label={`${source ? "Change" : "Add"} ${label.toLowerCase()}`} onClick={onOpen}>
          {source ? "Change" : "+ Add"}
        </Button>
      </div>
    </DetailRow>
  );
}

function LockGlyph() {
  return (
    <svg aria-hidden="true" width="11" height="11" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.6">
      <rect x="3" y="7" width="10" height="7" rx="1.5" />
      <path d="M5.5 7V5a2.5 2.5 0 0 1 5 0v2" />
    </svg>
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
  return key === "name" || key === "disclaimer" || key === "customBody" || key.startsWith("fields.");
}
