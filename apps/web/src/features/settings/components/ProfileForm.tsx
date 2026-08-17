import { zodResolver } from "@hookform/resolvers/zod";
import { useState, type ReactNode } from "react";
import { useForm } from "react-hook-form";
import { Button, Field, FormError, Input, Select } from "../../../components/ui";
import { ApiRequestError } from "../../../lib/apiClient";
import { titleCase } from "../../../lib/format";
import { messageFor } from "../../../lib/errorCodes";
import type { WorkspaceRole } from "../../auth/api/types";
import { LANGUAGE_OPTIONS, TIMEZONE_OPTIONS } from "../lib/profileOptions";
import { profileSchema, type ProfileValues } from "../lib/profileSchema";

/**
 * The mockup's two-column profile form.
 *
 * Owns nothing but the form: validation, the server's field errors, and the submit state. What a save
 * *means* — refreshing the session, telling the user — belongs to the page, which passes `onSave`. A
 * failure thrown from there lands back here, where the fields that caused it live.
 *
 * No react-query mutation: the current user is not a query, it is `AuthProvider`'s state, so
 * react-hook-form's own `isSubmitting` is the only loading flag there is to read.
 */
export function ProfileForm({
  email,
  roles,
  defaultValues,
  onSave,
}: {
  /** Read-only: the address is the identity, and changing it is a flow rather than a field. */
  email: string;
  roles: WorkspaceRole[];
  defaultValues: ProfileValues;
  onSave: (values: ProfileValues) => Promise<void>;
}) {
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<ProfileValues>({ resolver: zodResolver(profileSchema), defaultValues });

  const submit = async (values: ProfileValues) => {
    setFormError(null);
    try {
      await onSave(values);
    } catch (error) {
      if (!(error instanceof ApiRequestError)) {
        setFormError("Could not reach LightMove. Check your connection and try again.");
        return;
      }

      // A rule only the server knows — an unknown timezone, a language it has stopped offering —
      // arrives attributed to its field, in the same shape Bean Validation produces.
      const fieldErrors = error.fieldErrors;
      const fields = Object.keys(fieldErrors) as (keyof ProfileValues)[];
      if (fields.length > 0) {
        fields.forEach((field) => setError(field, { message: fieldErrors[field] }));
        return;
      }
      setFormError(messageFor(error));
    }
  };

  return (
    <form onSubmit={handleSubmit(submit)} noValidate>
      <FormError message={formError} />

      <div className="grid grid-cols-2 gap-x-4 gap-y-3.5">
        <Field label="Full name" error={errors.fullName?.message}>
          <Input
            autoComplete="name"
            invalid={!!errors.fullName}
            className="!bg-panel"
            {...register("fullName")}
          />
        </Field>

        <Field label="Work email" hint="Your sign-in address — ask an admin to change it">
          <ReadOnlyValue>{email}</ReadOnlyValue>
        </Field>

        <Field label="Title" error={errors.title?.message}>
          <Input
            placeholder="Managing Partner"
            invalid={!!errors.title}
            className="!bg-panel"
            {...register("title")}
          />
        </Field>

        <Field label="Role">
          <ReadOnlyValue>{`${roles.map(titleCase).join(", ")} — set by workspace owner`}</ReadOnlyValue>
        </Field>

        <Field label="Timezone" error={errors.timezone?.message}>
          <Select invalid={!!errors.timezone} className="!bg-panel" {...register("timezone")}>
            {TIMEZONE_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </Select>
        </Field>

        <Field label="Language" error={errors.locale?.message}>
          <Select invalid={!!errors.locale} className="!bg-panel" {...register("locale")}>
            {LANGUAGE_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </Select>
        </Field>
      </div>

      <div className="mt-5 flex justify-end">
        <Button type="submit" loading={isSubmitting}>
          Save changes
        </Button>
      </div>
    </form>
  );
}

/** A value the caller may read and not edit — the treatment the mockup gives Workspace URL and Role. */
function ReadOnlyValue({ children }: { children: ReactNode }) {
  return (
    <div className="rounded-lg border border-line-soft bg-panel px-3 py-[9px] font-mono text-[13px] font-medium text-text2">
      {children}
    </div>
  );
}
