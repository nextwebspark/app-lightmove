import { useMutation } from "@tanstack/react-query";
import { useEffect, useRef, useState, type FormEvent, type KeyboardEvent, type ReactNode } from "react";
import { useForm, type UseFormReturn } from "react-hook-form";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Button, FormError, useToast } from "../../../components/ui";
import { cn } from "../../../lib/cn";
import { codeOf, messageFor } from "../../../lib/errorCodes";
import type { Candidate, SaveCandidatePayload } from "../api/types";
import {
  formOf,
  patchOf,
  sectionResolver,
  type CandidateForm,
  type ProfileFormSection,
  type SectionValues,
} from "../lib/candidateForm";

/**
 * The pencil on a section's heading. Dim until the section is hovered or the button focused, so a
 * profile reads as a profile and not as a page of controls — and disabled, not hidden, while another
 * section is open, so the reader sees why it does not answer.
 */
export function SectionEditButton({
  label,
  disabled,
  onClick,
}: {
  label: string;
  disabled?: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      aria-label={`Edit ${label}`}
      title={`Edit ${label}`}
      disabled={disabled}
      onClick={onClick}
      className={cn(
        "rounded-md p-1.5 text-text3 opacity-60 transition",
        "group-hover:opacity-100 hover:bg-panel2 hover:text-text focus-visible:opacity-100",
        "disabled:cursor-not-allowed disabled:opacity-30 disabled:hover:bg-transparent disabled:hover:text-text3",
      )}
    >
      <Icon d={ICONS.pencil} size={13} />
    </button>
  );
}

/**
 * The frame one section is edited in: the fields, then Cancel and Save on their own line. Escape
 * cancels the section and is stopped there, so it does not also close the panel the reader is still
 * in; Ctrl/⌘-Enter saves from inside a textarea, where Enter means a new line.
 */
export function ProfileSectionForm({
  onSubmit,
  onCancel,
  saving,
  error,
  saveLabel = "Save",
  children,
}: {
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  onCancel: () => void;
  saving: boolean;
  error: string | null;
  saveLabel?: string;
  children: ReactNode;
}) {
  const form = useRef<HTMLFormElement>(null);
  // The pencil that opened this is gone, so focus would otherwise sit on the panel — where Escape
  // means "close the panel" and the edit is lost. Land in the first field, and bring the section up
  // if it was opened from a header further down than its fields.
  useEffect(() => {
    form.current?.scrollIntoView?.({ block: "nearest", behavior: "smooth" });
    form.current?.querySelector<HTMLElement>("input, select, textarea")?.focus();
  }, []);

  const handleKeyDown = (event: KeyboardEvent<HTMLFormElement>) => {
    if (event.key === "Escape") {
      event.stopPropagation();
      event.preventDefault();
      onCancel();
      return;
    }
    if (event.key === "Enter" && (event.metaKey || event.ctrlKey)) {
      event.preventDefault();
      event.currentTarget.requestSubmit();
    }
  };

  return (
    <form ref={form} onSubmit={onSubmit} onKeyDown={handleKeyDown} noValidate className="pt-1">
      <FormError message={error} />
      {children}
      <div className="flex items-center justify-end gap-2 border-t border-line-soft pt-3">
        <span className="me-auto font-mono text-[10.5px] text-text3">Esc cancels · ⌘/Ctrl ↵ saves</span>
        <Button
          type="button"
          variant="secondary"
          onClick={onCancel}
          disabled={saving}
          className="px-3 py-1.5 text-[12.5px]"
        >
          Cancel
        </Button>
        <Button type="submit" variant="primary" loading={saving} className="px-3.5 py-1.5 text-[12.5px]">
          {saveLabel}
        </Button>
      </div>
    </form>
  );
}

/**
 * One section of a stored profile, as a form. Validates and hands back only that section's fields;
 * the caller writes them over the stored profile and sends the whole thing, because the server
 * replaces the record. The form is seeded once, when the section opens — a background refresh while
 * someone is mid-sentence must not rewrite what they are typing.
 */
export function SectionEditor<S extends ProfileFormSection>({
  section,
  candidate,
  save,
  doneMessage,
  onDone,
  onCancel,
  children,
}: {
  section: S;
  candidate: Candidate;
  /** Sends the stored profile with this patch over it, and resolves with what the server now holds. */
  save: (patch: Partial<SaveCandidatePayload>) => Promise<Candidate>;
  doneMessage: string;
  onDone: (saved: Candidate) => void;
  onCancel: () => void;
  children: (form: UseFormReturn<CandidateForm, unknown, SectionValues<S>>) => ReactNode;
}) {
  const toast = useToast();
  // State rather than a read of `saving.isError`, which outlives the thing it described: a duplicate
  // name marks the name field, and the moment the user edits that name the field error clears while
  // the mutation stays failed — so the same sentence reappeared over a form already corrected.
  const [submitError, setSubmitError] = useState<string | null>(null);
  const form = useForm<CandidateForm, unknown, SectionValues<S>>({
    resolver: sectionResolver(section, candidate.triageCompanyId !== null),
    defaultValues: formOf(candidate),
  });

  const saving = useMutation({
    mutationFn: (parsed: SectionValues<S>) =>
      save(patchOf(section, parsed, candidate.triageCompanyId !== null)),
    onSuccess: (saved) => {
      toast(doneMessage);
      onDone(saved);
    },
    onError: (error) => {
      // A name the mandate already maps belongs on the name field, because that is the field to
      // change. Anything else — a refused write, a dropped connection — is not about the name.
      if (codeOf(error) === "CANDIDATE_ALREADY_MAPPED") {
        form.setError("fullName", { message: messageFor(error) });
        return;
      }
      setSubmitError(messageFor(error));
    },
  });

  return (
    <ProfileSectionForm
      onSubmit={form.handleSubmit((parsed) => {
        setSubmitError(null);
        saving.mutate(parsed);
      })}
      onCancel={onCancel}
      saving={saving.isPending}
      error={submitError}
    >
      {children(form)}
    </ProfileSectionForm>
  );
}
