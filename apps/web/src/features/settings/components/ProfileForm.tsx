import { zodResolver } from "@hookform/resolvers/zod";
import { useMemo, useState, type ReactNode } from "react";
import { useForm } from "react-hook-form";
import { Button, Field, FormError, Input, Select } from "../../../components/ui";
import { ApiRequestError } from "../../../lib/apiClient";
import { titleCase } from "../../../lib/format";
import { messageFor } from "../../../lib/errorCodes";
import type { WorkspaceRole } from "../../auth/api/types";
import { fallbackPreferences, formatDate, zoneCityOf, type DateTimePreferences } from "../../../lib/datetime";
import {
  dateFormatSelectOptions,
  timeFormatLabelOf,
  timeFormatSelectOptions,
  timeZoneLabelOf,
  timeZoneSelectOptions,
} from "../lib/dateTimeOptions";
import { browserTimeZone, LANGUAGE_OPTIONS } from "../lib/profileOptions";
import { DateTimePreview } from "./DateTimePreview";
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
  workspace,
  onSave,
}: {
  /** Read-only: the address is the identity, and changing it is a flow rather than a field. */
  email: string;
  roles: WorkspaceRole[];
  defaultValues: ProfileValues;
  /** The workspace's own zone and formats, what an empty choice follows; null outside a workspace. */
  workspace: DateTimePreferences | null;
  onSave: (values: ProfileValues) => Promise<void>;
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
  const [timezone, dateFormat, timeFormat] = watch(["timezone", "dateFormat", "timeFormat"]);
  const timezones = useMemo(() => timeZoneSelectOptions(defaultValues.timezone || null), [defaultValues.timezone]);
  const dateFormats = useMemo(() => dateFormatSelectOptions(), []);
  const timeFormats = useMemo(() => timeFormatSelectOptions(), []);
  const computerZone = useMemo(browserTimeZone, []);
  const fallback = workspace ?? fallbackPreferences();
  const followLabel = workspace ? "Workspace default" : "Standard";
  const effective: DateTimePreferences = {
    timeZone: timezone || fallback.timeZone,
    dateFormat: dateFormat || fallback.dateFormat,
    timeFormat: timeFormat || fallback.timeFormat,
  };

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

      <section aria-labelledby="profile-date-time" className="mt-2 border-t border-u-border pt-5">
        <h3 id="profile-date-time" className="text-sm font-semibold">
          Date &amp; time
        </h3>
        <p className="mb-4 mt-0.5 font-mono text-meta text-u-text3">
          {workspace
            ? "Keep your workspace's, or choose your own. Your choice applies in every workspace you belong to."
            : "Your choice applies in every workspace you belong to."}
        </p>

        <div className="grid grid-cols-1 gap-x-4 md:grid-cols-2">
          <div>
            <Field
              label="Timezone"
              error={errors.timezone?.message}
              hint="Sequences, scheduled emails and your daily digest follow this clock"
            >
              <Select invalid={!!errors.timezone} className="bg-u-surface" {...register("timezone")}>
                <option value="">
                  {followLabel} · {timeZoneLabelOf(fallback.timeZone)}
                </option>
                {timezones.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </Select>
            </Field>
            {computerZone && computerZone !== effective.timeZone && (
              <p className="-mt-2.5 mb-4 text-meta text-u-text3">
                Your computer is on {zoneCityOf(computerZone)} time.{" "}
                <button
                  type="button"
                  onClick={() => setValue("timezone", computerZone, { shouldDirty: true, shouldValidate: true })}
                  className="font-medium text-u-accent hover:underline"
                >
                  Use {zoneCityOf(computerZone)}
                </button>
              </p>
            )}
          </div>

          <Field label="Date format" error={errors.dateFormat?.message}>
            <Select invalid={!!errors.dateFormat} className="bg-u-surface" {...register("dateFormat")}>
              <option value="">
                {followLabel} · {formatDate(new Date(), { dateFormat: fallback.dateFormat })}
              </option>
              {dateFormats.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </Select>
          </Field>

          <Field label="Time format" error={errors.timeFormat?.message}>
            <Select invalid={!!errors.timeFormat} className="bg-u-surface" {...register("timeFormat")}>
              <option value="">
                {followLabel} · {timeFormatLabelOf(fallback.timeFormat)}
              </option>
              {timeFormats.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </Select>
          </Field>
        </div>

        <DateTimePreview preferences={effective} />
      </section>

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
