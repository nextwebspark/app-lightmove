import { zodResolver } from "@hookform/resolvers/zod";
import { useMemo, useState, type ReactNode } from "react";
import { useForm } from "react-hook-form";
import { Button, Field, FormError, Input } from "../../../components/ui";
import { ListboxSelect, type ListboxOption } from "../../../components/ui/ListboxSelect";
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
 * The two-column profile form: validation, server field errors, submit state. What a save means is the page's
 * `onSave`. No mutation — the user is `AuthProvider` state — so `isSubmitting` is the loading flag.
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
  const [timezone, dateFormat, timeFormat, locale] = watch(["timezone", "dateFormat", "timeFormat", "locale"]);
  const computerZone = useMemo(browserTimeZone, []);
  const fallback = workspace ?? fallbackPreferences();
  const followLabel = workspace ? "Workspace default" : "Standard";
  const timezones = useMemo<ListboxOption<string>[]>(
    () => [
      { value: "", label: followLabel, detail: timeZoneLabelOf(fallback.timeZone), keywords: "workspace default" },
      ...timeZoneSelectOptions(defaultValues.timezone || null),
    ],
    [defaultValues.timezone, followLabel, fallback.timeZone],
  );
  const dateFormats = useMemo<ListboxOption<ProfileValues["dateFormat"]>[]>(
    () => [
      { value: "", label: followLabel, detail: formatDate(new Date(), { dateFormat: fallback.dateFormat }) },
      ...dateFormatSelectOptions(),
    ],
    [followLabel, fallback.dateFormat],
  );
  const timeFormats = useMemo<ListboxOption<ProfileValues["timeFormat"]>[]>(
    () => [
      { value: "", label: followLabel, detail: timeFormatLabelOf(fallback.timeFormat) },
      ...timeFormatSelectOptions(),
    ],
    [followLabel, fallback.timeFormat],
  );
  const choose = <K extends "timezone" | "dateFormat" | "timeFormat" | "locale">(field: K) =>
    (value: ProfileValues[K]) =>
      setValue(field, value as never, { shouldDirty: true, shouldValidate: true });
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

        <Field label="Phone" error={errors.phone?.message} hint="Shown in your email signature">
          <Input
            type="tel"
            autoComplete="tel"
            placeholder="+971 50 000 0000"
            invalid={!!errors.phone}
            className="bg-u-surface"
            {...register("phone")}
          />
        </Field>

        <Field label="LinkedIn" error={errors.linkedinUrl?.message} hint="Your own profile page">
          <Input
            type="url"
            placeholder="linkedin.com/in/…"
            invalid={!!errors.linkedinUrl}
            className="bg-u-surface"
            {...register("linkedinUrl")}
          />
        </Field>

        <Field label="Role">
          <ReadOnlyValue>{`${roles.map(titleCase).join(", ")} — set by workspace owner`}</ReadOnlyValue>
        </Field>

        <Field label="Language" error={errors.locale?.message}>
          <ListboxSelect
            aria-label="Language"
            value={locale}
            options={LANGUAGE_OPTIONS}
            invalid={!!errors.locale}
            onChange={choose("locale")}
            className="bg-u-surface"
          />
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
              <ListboxSelect
                aria-label="Timezone"
                value={timezone}
                options={timezones}
                invalid={!!errors.timezone}
                onChange={choose("timezone")}
                searchable
                searchPlaceholder="Search by city, country or GMT offset"
                noMatch="No time zone matches"
                width={340}
                className="bg-u-surface"
              />
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
            <ListboxSelect
              aria-label="Date format"
              value={dateFormat}
              options={dateFormats}
              invalid={!!errors.dateFormat}
              onChange={choose("dateFormat")}
              className="bg-u-surface"
            />
          </Field>

          <Field label="Time format" error={errors.timeFormat?.message}>
            <ListboxSelect
              aria-label="Time format"
              value={timeFormat}
              options={timeFormats}
              invalid={!!errors.timeFormat}
              onChange={choose("timeFormat")}
              className="bg-u-surface"
            />
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
