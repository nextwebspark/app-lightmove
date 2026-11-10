import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useRef, useState } from "react";
import { Button, Field, Input, Toggle, useToast } from "../../../components/ui";
import { ApiRequestError } from "../../../lib/apiClient";
import { messageFor } from "../../../lib/errorCodes";
import { NoteEditor, type NoteEditorHandle } from "../../notes/components/NoteEditor";
import { TokenInsertBar } from "../../outreach/components/TokenInsertBar";
import { templateTokenOptions } from "../../outreach/lib/sequenceTokens";
import { SignatureUnderEmail } from "../../signatures/components/SignatureChooser";
import { useSignatureChoice } from "../../signatures/lib/useSignatureChoice";
import * as emailTemplatesApi from "../api/emailTemplatesApi";
import type { EmailTemplate } from "../api/emailTemplatesApi";
import { templateVocabulary, variableProblemOf } from "../lib/templateVariables";

const NAME_MAX_LENGTH = 80;
const SUBJECT_MAX_LENGTH = 200;
const BODY_MAX_LENGTH = 10_000;
const NEW_TEMPLATE_BODY = "Hi {{firstName}},\n\n";

type Field = "subject" | "body";

/**
 * `Settings.dc.html`'s email template editor: name, subject and the email in the notes editor, under the sequence
 * editor's own Insert bar, so a variable is chosen, placed and read exactly as in a sequence step. The author's default
 * signature is drawn greyed beneath, since Include signature adds the sender's own when the email goes.
 */
export function EmailTemplateEditor({
  template,
  bookingLinkOffered,
  onDone,
  onDelete,
  onDuplicate,
}: {
  template: EmailTemplate | null;
  bookingLinkOffered: boolean;
  onDone: () => void;
  onDelete: (template: EmailTemplate) => void;
  onDuplicate: (template: EmailTemplate) => void;
}) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const signature = useSignatureChoice();
  const [name, setName] = useState(template?.name ?? "");
  const [subject, setSubject] = useState(template?.subject ?? "");
  const [body, setBody] = useState(template?.body ?? NEW_TEMPLATE_BODY);
  const [shared, setShared] = useState(template?.shared ?? false);
  const [serverErrors, setServerErrors] = useState<Record<string, string>>({});
  const [lastField, setLastField] = useState<Field>("body");
  const subjectRef = useRef<HTMLInputElement>(null);
  const bodyEditor = useRef<NoteEditorHandle | null>(null);

  const vocabulary = templateVocabulary(bookingLinkOffered);
  const subjectProblem = variableProblemOf(subject, vocabulary) ?? serverErrors.subject ?? null;
  const bodyProblem =
    variableProblemOf(body, vocabulary) ??
    (body.length > BODY_MAX_LENGTH ? `Too long. Shorten it to ${BODY_MAX_LENGTH.toLocaleString("en-GB")} characters.` : null) ??
    serverErrors.body ??
    null;
  const canSave = name.trim() !== "" && body.trim() !== "" && !subjectProblem && !bodyProblem;

  const save = useMutation({
    mutationFn: async () => {
      const content = { name: name.trim(), subject: subject.trim() || null, body };
      if (!template) return emailTemplatesApi.createEmailTemplate({ ...content, shared });
      const updated = await emailTemplatesApi.updateEmailTemplate(template.id, { ...content, version: template.version });
      return updated.shared === shared
        ? updated
        : emailTemplatesApi.shareEmailTemplate(template.id, shared, updated.version);
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: emailTemplatesApi.EMAIL_TEMPLATES_KEY });
      toast("Template saved");
      onDone();
    },
    onError: (error) => {
      if (error instanceof ApiRequestError && error.code === "VALIDATION_FAILED") {
        setServerErrors(error.fieldErrors);
        return;
      }
      toast.error(messageFor(error));
    },
  });

  /** Into whichever field the caret was last in, as the sequence editor does. */
  const insertVariable = (token: string) => {
    setServerErrors({});
    if (lastField === "body") {
      bodyEditor.current?.insertText(token);
      return;
    }
    const field = subjectRef.current;
    const start = field?.selectionStart ?? subject.length;
    const end = field?.selectionEnd ?? subject.length;
    setSubject(subject.slice(0, start) + token + subject.slice(end));
    requestAnimationFrame(() => {
      field?.focus();
      field?.setSelectionRange(start + token.length, start + token.length);
    });
  };

  const isColleagues = template !== null && !template.mine;

  return (
    <div className="rounded-u-card border border-u-border bg-u-raised p-5">
      {template?.shared && (
        <p role="status" className="mb-4 rounded-u-control border border-u-border bg-u-surface px-3 py-2.5 text-note text-u-text2">
          {isColleagues
            ? `${template.owner.fullName ?? "A colleague"}'s template, shared with the workspace. You can change it as an admin: everyone who uses it next gets your version, and the change is in the audit log.`
            : "Shared with the workspace. A change reaches everyone who uses it next, and is recorded in the audit log."}
        </p>
      )}
      <Field label="Name" error={serverErrors.name}>
        <Input
          value={name}
          maxLength={NAME_MAX_LENGTH}
          placeholder="Thanks — let's find a time"
          invalid={Boolean(serverErrors.name)}
          onChange={(event) => setName(event.target.value)}
        />
      </Field>

      <TokenInsertBar options={templateTokenOptions(bookingLinkOffered)} onInsert={insertVariable} className="mb-1.5" />
      <p className="mb-4 font-mono text-meta text-u-text3">
        Each variable fills in for the executive when you write the email. Give one a fallback for anyone with none:{" "}
        {"{{firstName|there}}"}.
      </p>

      <Field
        label="Subject"
        hint="Optional. Used only by a new email; a reply keeps its own."
        error={subjectProblem ?? undefined}
      >
        <Input
          ref={subjectRef}
          value={subject}
          maxLength={SUBJECT_MAX_LENGTH}
          invalid={Boolean(subjectProblem)}
          onFocus={() => setLastField("subject")}
          onChange={(event) => {
            setServerErrors(({ subject: _dropped, ...rest }) => rest);
            setSubject(event.target.value.replace(/[\r\n]+/g, " "));
          }}
        />
      </Field>

      <div onFocusCapture={() => setLastField("body")}>
        <span className="mb-1.5 block type-micro-label font-mono text-u-text3">Email</span>
        <NoteEditor
          value={body}
          format="markdown"
          onChange={(next) => {
            setServerErrors(({ body: _dropped, ...rest }) => rest);
            setBody(next);
          }}
          teammates={null}
          ariaLabel="Email"
          variant="field"
          roomy
          expandTitle={name.trim() || "Email template"}
          editorRef={bodyEditor}
        />
        {bodyProblem && (
          <p role="alert" className="mt-1.5 font-mono text-meta text-u-offlimits">
            {bodyProblem}
          </p>
        )}
        <SignatureUnderEmail choice={signature} />
        {signature.included && (
          <p className="mt-1 font-mono text-meta text-u-text3">
            Your default signature. Include signature adds the sender's own when the email goes; a template never holds
            one.
          </p>
        )}
      </div>

      <div className="mt-4 flex items-start gap-2.5 rounded-u-control border border-u-border bg-u-surface p-3">
        <Toggle checked={shared} onChange={setShared} label="Share with workspace" />
        <span>
          <span className="block text-body font-medium text-u-text">Share with workspace</span>
          <span className="mt-0.5 block text-note text-u-text3">
            Everyone on the team can use it, filled with their own name and booking link. Only you and admins can change
            or delete it.
          </span>
        </span>
      </div>

      <div className="mt-5 flex flex-wrap items-center gap-2">
        {template && (
          <>
            <Button variant="ghost" size="sm" className="text-u-offlimits" onClick={() => onDelete(template)}>
              Delete
            </Button>
            <Button variant="ghost" size="sm" onClick={() => onDuplicate(template)}>
              Duplicate
            </Button>
          </>
        )}
        <span className="flex-1" />
        <Button variant="secondary" size="sm" onClick={onDone}>
          Cancel
        </Button>
        <Button size="sm" disabled={!canSave} loading={save.isPending} onClick={() => save.mutate()}>
          Save template
        </Button>
      </div>
    </div>
  );
}
