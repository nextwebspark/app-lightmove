import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Button, Field, Input, Modal, Toggle, useToast } from "../../../components/ui";
import { messageFor } from "../../../lib/errorCodes";
import * as emailTemplatesApi from "../api/emailTemplatesApi";

const NAME_MAX_LENGTH = 80;

/**
 * Save as template, from the composer's picker or a sequence step: a name and Share with workspace. It keeps the words
 * as written, their variables included, and a new email's subject — never the signature, which only the sent email
 * carries.
 */
export function SaveAsTemplateDialog({
  open,
  subject,
  buildBody,
  title = "Save as template",
  subtitle = "The words as written, variables included. Your signature is never part of a template.",
  refusal = null,
  onClose,
}: {
  open: boolean;
  /** Null for a reply, which keeps its thread's subject. */
  subject: string | null;
  /** The template's Markdown, made when it is saved: a step's plain text is converted then, off the editor's chunk. */
  buildBody: () => string | Promise<string>;
  title?: string;
  subtitle?: string;
  /** Why this cannot be saved: said in place of the form. */
  refusal?: string | null;
  onClose: () => void;
}) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const [name, setName] = useState("");
  const [shared, setShared] = useState(false);
  // One dialog serves every step of a sequence: a name typed and cancelled for one must not come back for the next.
  const close = () => {
    setName("");
    setShared(false);
    onClose();
  };

  const save = useMutation({
    mutationFn: async () =>
      emailTemplatesApi.createEmailTemplate({
        name: name.trim(),
        subject: subject?.trim() || null,
        body: await buildBody(),
        shared,
      }),
    onSuccess: (template) => {
      void queryClient.invalidateQueries({ queryKey: emailTemplatesApi.EMAIL_TEMPLATES_KEY });
      toast(`Saved as ${template.name}`);
      close();
    },
    onError: (error) => toast.error(messageFor(error)),
  });

  return (
    <Modal
      open={open}
      onClose={close}
      title={title}
      subtitle={subtitle}
      footer={
        <>
          <Button variant="secondary" size="md" onClick={close}>
            {refusal ? "Close" : "Cancel"}
          </Button>
          {!refusal && (
            <Button size="md" disabled={!name.trim()} loading={save.isPending} onClick={() => save.mutate()}>
              Save template
            </Button>
          )}
        </>
      }
    >
      {refusal ? (
        <p role="alert" className="rounded-u-control border border-u-offlimits bg-u-offlimits-tint p-3 text-body text-u-text">
          {refusal}
        </p>
      ) : (
        <>
          <Field label="Name">
            <Input
              autoFocus
              value={name}
              maxLength={NAME_MAX_LENGTH}
              placeholder="Thanks — let's find a time"
              onChange={(event) => setName(event.target.value)}
              onKeyDown={(event) => {
                // Not a form: the composer may sit in another dialog, and a submit bubbles through portals to its form.
                if (event.key === "Enter" && name.trim() && !save.isPending) save.mutate();
              }}
            />
          </Field>
          <div className="flex items-start gap-2.5">
            <Toggle checked={shared} onChange={setShared} label="Share with workspace" />
            <span>
              <span className="block text-body font-medium text-u-text">Share with workspace</span>
              <span className="mt-0.5 block text-note text-u-text3">
                Everyone on the team can use it, filled with their own name and booking link. Only you and admins can
                change or delete it.
              </span>
            </span>
          </div>
        </>
      )}
    </Modal>
  );
}
