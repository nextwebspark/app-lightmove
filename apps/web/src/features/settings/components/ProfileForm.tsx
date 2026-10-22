import { zodResolver } from "@hookform/resolvers/zod";
import { useMemo, useState, type ReactNode } from "react";
import { useForm } from "react-hook-form";
import { Button, Field, FormError, Input, Select } from "../../../components/ui";
import { ApiRequestError } from "../../../lib/apiClient";
import { titleCase } from "../../../lib/format";
import { messageFor } from "../../../lib/errorCodes";
import type { WorkspaceRole } from "../../auth/api/types";
import { browserTimeZone, cityOf, LANGUAGE_OPTIONS, timezoneGroups } from "../lib/profileOptions";
import { profileSchemaFor, type ProfileValues } from "../lib/profileSchema";

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
  beforeSave = null,
}: {
  /** Read-only: the address is the identity, and changing it is a flow rather than a field. */
  email: string;
  roles: WorkspaceRole[];
  defaultValues: ProfileValues;
  onSave: (values: ProfileValues) => Promise<void>;
  /** A setting that saves itself, drawn above Save changes: it is the profile's, but no field of this form. */
  beforeSave?: ReactNode;
}) {
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    setError,
    setValue,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<ProfileValues>({ resolver: zodResolver(profileSchemaFor(defaultValues.timezone)), defaultValues });
  const timezone = watch("timezone");
  const timezones = useMemo(() => timezoneGroups(defaultValues.timezone), [defaultValues.timezone]);
  const computerZone = useMemo(browserTimeZone, []);

  const submit = async (values: ProfileValues) => {
    setFormError(null);
    try {
      await onSave(values);
    } catch (error) {
      if (!(error instanceof ApiRequestError)) {
        setFormError("Could not reach Uncava. Check your connection and try again.");
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

      <div className="grid grid-cols-1 gap-x-4 gap-y-3.5 md:grid-cols-2">
        <Field label="Full name" error={errors.fullName?.message}>
          <Input
            autoComplete="name"
            invalid={!!errors.fullName}
            className="!bg-u-surface"
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
            className="!bg-u-surface"
            {...register("title")}
          />
        </Field>

        <Field label="Role">
          <ReadOnlyValue>{`${roles.map(titleCase).join(", ")} — set by workspace owner`}</ReadOnlyValue>
        </Field>

        <div>
          <Field
            label="Timezone"
            error={errors.timezone?.message}
            hint="Sequences, scheduled emails and your daily digest follow this clock"
          >
            <Select invalid={!!errors.timezone} className="!bg-u-surface" {...register("timezone")}>
              {timezones.map((group) => (
                <optgroup key={group.label} label={group.label}>
                  {group.options.map((option) => (
                    <option key={option.value} value={option.value}>
                      {option.label}
                    </option>
                  ))}
                </optgroup>
              ))}
            </Select>
          </Field>
          {computerZone && computerZone !== timezone && (
            <p className="-mt-2.5 mb-4 text-meta text-u-text3">
              Your computer is on {cityOf(computerZone)} time.{" "}
              <button
                type="button"
                onClick={() => setValue("timezone", computerZone, { shouldDirty: true, shouldValidate: true })}
                className="font-medium text-u-accent hover:underline"
              >
                Use {cityOf(computerZone)}
              </button>
            </p>
          )}
        </div>

        <Field label="Language" error={errors.locale?.message}>
          <Select invalid={!!errors.locale} className="!bg-u-surface" {...register("locale")}>
            {LANGUAGE_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </Select>
        </Field>
      </div>

      {beforeSave}

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
    <div className="rounded-lg border border-u-border bg-u-surface px-3 py-[9px] font-mono text-[13px] font-medium text-u-text2">
      {children}
    </div>
  );
}
