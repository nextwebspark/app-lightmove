import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { Button, Field, FormError, Input } from "../../../components/ui";
import { ApiRequestError } from "../../../lib/apiClient";
import { messageFor } from "../../../lib/errorCodes";
import { passwordChangeSchema, type PasswordChangeValues } from "../lib/passwordChangeSchema";

const EMPTY_FORM: PasswordChangeValues = {
  currentPassword: "",
  newPassword: "",
  confirmPassword: "",
};

/**
 * The mockup's change-password card.
 *
 * Owns the form and nothing else: what a successful change *means* — a new session, a stale session
 * list, telling the user — belongs to the page, which passes `onSave`. A failure thrown from there
 * lands back here, where the fields that caused it live.
 */
export function ChangePasswordForm({
  onSave,
}: {
  onSave: (values: PasswordChangeValues) => Promise<void>;
}) {
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<PasswordChangeValues>({
    resolver: zodResolver(passwordChangeSchema),
    defaultValues: EMPTY_FORM,
  });

  const submit = async (values: PasswordChangeValues) => {
    setFormError(null);
    try {
      await onSave(values);
      // Not a courtesy: three filled password boxes left on screen after a successful change are three
      // live secrets sitting in the DOM.
      reset(EMPTY_FORM);
    } catch (error) {
      if (!(error instanceof ApiRequestError)) {
        setFormError("Could not reach LightMove. Check your connection and try again.");
        return;
      }

      const fieldErrors = error.fieldErrors;
      const fields = Object.keys(fieldErrors) as (keyof PasswordChangeValues)[];
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
        <div className="col-span-2">
          <Field label="Current password" error={errors.currentPassword?.message}>
            <Input
              type="password"
              autoComplete="current-password"
              placeholder="••••••••"
              invalid={!!errors.currentPassword}
              className="!bg-panel"
              {...register("currentPassword")}
            />
          </Field>
        </div>

        <Field label="New password" error={errors.newPassword?.message}>
          <Input
            type="password"
            autoComplete="new-password"
            placeholder="8+ characters"
            invalid={!!errors.newPassword}
            className="!bg-panel"
            {...register("newPassword")}
          />
        </Field>

        <Field label="Confirm new password" error={errors.confirmPassword?.message}>
          <Input
            type="password"
            autoComplete="new-password"
            placeholder="Repeat it"
            invalid={!!errors.confirmPassword}
            className="!bg-panel"
            {...register("confirmPassword")}
          />
        </Field>
      </div>

      <div className="mt-4 flex justify-end">
        <Button type="submit" variant="secondary" loading={isSubmitting}>
          Update password
        </Button>
      </div>
    </form>
  );
}
