import type { ReactNode } from "react";
import {
  Controller,
  useFieldArray,
  type Control,
  type FieldErrors,
  type FieldValues,
  type UseFormRegister,
  type UseFormSetValue,
  type UseFormWatch,
} from "react-hook-form";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Field, Input, Select, TextArea } from "../../../components/ui";
import { CountryField } from "../../../components/ui/CountryField";
import { cn } from "../../../lib/cn";
import { CURRENCIES } from "../../../lib/currencies";
import { formatNumber } from "../../../lib/format";
import { amountTyped } from "../lib/compensation";
import type { CandidateForm } from "../lib/candidateForm";
import { CANDIDATE_SENIORITIES, CANDIDATE_STATUSES } from "../lib/candidateVocabulary";
import { PackageTotal } from "./CompensationSummary";

/**
 * The profile's fields, one group per section, shared by the form that adds an executive and the
 * inline editors that correct one section of an existing profile. One rendering of each field,
 * because a Base salary asked for slightly differently on the two screens would read as two facts.
 */

export interface FieldGroupProps {
  register: UseFormRegister<CandidateForm>;
  errors: FieldErrors<CandidateForm>;
}

export function IdentityFields<TTransformed>({
  register,
  errors,
  control,
  employerLocked,
  autoFocus,
  statusField,
}: FieldGroupProps & {
  control: Control<CandidateForm, unknown, TTransformed>;
  /** True where the employer is one of the mandate's companies — the mapping and the name must not disagree. */
  employerLocked: boolean;
  autoFocus?: boolean;
  /** The Status select, which the add form places beside the employer and the editor leaves to the header. */
  statusField?: ReactNode;
}) {
  return (
    <>
      <Field label="Full name" error={errors.fullName?.message}>
        <Input
          {...register("fullName")}
          autoFocus={autoFocus}
          placeholder="Yasmin El-Sayed"
          invalid={Boolean(errors.fullName)}
        />
      </Field>
      <div className="grid gap-x-4 sm:grid-cols-2">
        <Field label="Title" error={errors.title?.message}>
          <Input {...register("title")} placeholder="VP Finance" />
        </Field>
        <Field label="Seniority" error={errors.seniority?.message}>
          <Select {...register("seniority")}>
            <option value="">Not established</option>
            {CANDIDATE_SENIORITIES.map((level) => (
              <option key={level} value={level}>
                {level}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="Employer" error={errors.employerName?.message}>
          <Input
            {...register("employerName")}
            readOnly={employerLocked}
            placeholder="Al Rawabi Dairy"
            className={employerLocked ? "text-text3" : undefined}
          />
        </Field>
        {statusField}
        <Field label="City" error={errors.locationCity?.message}>
          <Input {...register("locationCity")} placeholder="Dubai" />
        </Field>
        <Field label="Country" error={errors.locationCountry?.message}>
          <Controller
            name="locationCountry"
            control={control}
            render={({ field }) => (
              <CountryField
                listId="candidate-country"
                value={field.value}
                invalid={Boolean(errors.locationCountry)}
                placeholder="United Arab Emirates"
                onChange={field.onChange}
              />
            )}
          />
        </Field>
      </div>
    </>
  );
}

export function StatusField({ register, errors }: FieldGroupProps) {
  return (
    <Field label="Status" error={errors.status?.message}>
      <Select {...register("status")}>
        {CANDIDATE_STATUSES.map((status) => (
          <option key={status.value} value={status.value}>
            {status.label}
          </option>
        ))}
      </Select>
    </Field>
  );
}

export function SummaryFields({ register, errors, autoFocus }: FieldGroupProps & { autoFocus?: boolean }) {
  return (
    <Field label="Profile summary" error={errors.summary?.message}>
      <TextArea
        {...register("summary")}
        autoFocus={autoFocus}
        rows={5}
        placeholder="A decade in regional FMCG finance leadership…"
      />
    </Field>
  );
}

/**
 * Generic in the form's submit shape rather than fixed to the full profile, because the section
 * editor's form hands back only its own fields and `useFieldArray` reads that from the control.
 */
export function CareerFields<TTransformed extends FieldValues>({
  control,
  register,
  errors,
}: FieldGroupProps & { control: Control<CandidateForm, unknown, TTransformed> }) {
  const career = useFieldArray({ control, name: "career" });
  return (
    <div className="mb-4">
      <div className="mb-1.5 flex items-baseline justify-between">
        <span className="font-mono text-[10px] font-semibold uppercase tracking-[0.12em] text-text3">
          Career history
        </span>
        <span className="font-mono text-[11px] text-text3">Most recent first</span>
      </div>
      {career.fields.map((row, index) => (
        <div key={row.id} className="mb-2 flex items-start gap-2">
          <Input
            {...register(`career.${index}.company`)}
            placeholder="Company"
            aria-label={`Career ${index + 1} company`}
            className="flex-1"
          />
          <Input
            {...register(`career.${index}.title`)}
            placeholder="Title"
            aria-label={`Career ${index + 1} title`}
            className="flex-1"
          />
          <Input
            {...register(`career.${index}.period`)}
            placeholder="2021–Present"
            aria-label={`Career ${index + 1} period`}
            className="w-[110px] flex-none"
          />
          <button
            type="button"
            onClick={() => career.remove(index)}
            aria-label={`Remove career row ${index + 1}`}
            className="mt-2 flex-none rounded-md p-1.5 text-text3 transition hover:bg-panel2 hover:text-red"
          >
            <Icon d={ICONS.trash} size={14} />
          </button>
        </div>
      ))}
      <button
        type="button"
        onClick={() => career.append({ company: "", title: "", period: "" })}
        className="inline-flex items-center gap-1.5 rounded-[6px] border border-dashed border-line px-3 py-2 font-sans text-[13px] text-text2 transition hover:border-text3 hover:text-text"
      >
        <Icon d={ICONS.plus} size={14} />
        Add a post
      </button>
      {errors.career?.message && (
        <span role="alert" className="mt-1.5 block font-mono text-[11px] text-red">
          {errors.career.message}
        </span>
      )}
    </div>
  );
}

const AMOUNTS: { name: "baseSalary" | "bonus" | "allowances" | "longTermIncentive"; label: string; placeholder: string }[] = [
  { name: "baseSalary", label: "Base", placeholder: "420,000" },
  { name: "bonus", label: "Bonus", placeholder: "80,000" },
  { name: "allowances", label: "Allowances", placeholder: "40,000" },
  { name: "longTermIncentive", label: "Long-term incentive", placeholder: "0" },
];

/**
 * A package, as it is quoted: the currency chosen once and shown inside every figure it qualifies,
 * and the total worked out as the figures are typed — a consultant checking a number against what
 * was said on the phone reads the total, not four fields.
 *
 * <p>A currency the list does not carry — one stored before the picker existed, or by an import —
 * stays offered as an option: a select whose value matches no option posts blank, and that would
 * clear a fact nobody touched.
 */
export function CompensationFields({
  register,
  errors,
  watch,
  setValue,
  storedCurrency,
}: FieldGroupProps & {
  watch: UseFormWatch<CandidateForm>;
  setValue: UseFormSetValue<CandidateForm>;
  storedCurrency?: string | null;
}) {
  const [currency, base, bonus, allowances, longTermIncentive] = watch([
    "currency",
    "baseSalary",
    "bonus",
    "allowances",
    "longTermIncentive",
  ]);
  const currencies: string[] =
    storedCurrency && !(CURRENCIES as readonly string[]).includes(storedCurrency)
      ? [storedCurrency, ...CURRENCIES]
      : [...CURRENCIES];

  return (
    <>
      <div className="grid gap-x-4 sm:grid-cols-2">
        <Field label="Currency" error={errors.currency?.message}>
          <Select {...register("currency")} invalid={Boolean(errors.currency)}>
            <option value="">Not set</option>
            {currencies.map((code) => (
              <option key={code} value={code}>
                {code}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="Notice period" error={errors.noticePeriod?.message}>
          <Input {...register("noticePeriod")} placeholder="3 months" />
        </Field>
        {AMOUNTS.map((amount) => (
          <AmountField
            key={amount.name}
            name={amount.name}
            label={amount.label}
            placeholder={amount.placeholder}
            currency={currency}
            register={register}
            setValue={setValue}
            error={errors[amount.name]?.message}
          />
        ))}
      </div>
      <PackageTotal
        currency={currency}
        compensation={{
          baseSalary: amountTyped(base),
          bonus: amountTyped(bonus),
          allowances: amountTyped(allowances),
          longTermIncentive: amountTyped(longTermIncentive),
        }}
        live
      />
      <p className="mt-3 mb-4 font-mono text-[11px] text-text3">
        Whole units, in the currency it was quoted in. Nothing converts it.
      </p>
    </>
  );
}

/** A figure with its currency code set inside the field, tidied to thousands on blur. */
function AmountField({
  name,
  label,
  placeholder,
  currency,
  register,
  setValue,
  error,
}: {
  name: (typeof AMOUNTS)[number]["name"];
  label: string;
  placeholder: string;
  currency: string;
  register: UseFormRegister<CandidateForm>;
  setValue: UseFormSetValue<CandidateForm>;
  error?: string;
}) {
  const field = register(name);
  return (
    <Field label={label} error={error}>
      <div className="relative">
        {currency && (
          <span
            aria-hidden="true"
            className="pointer-events-none absolute inset-y-0 start-0 flex items-center ps-3 font-mono text-[11px] font-semibold text-text3"
          >
            {currency}
          </span>
        )}
        <Input
          {...field}
          // Named outright: the currency code sits inside the wrapping label, and would otherwise
          // read as part of the field's name.
          aria-label={label}
          inputMode="numeric"
          placeholder={placeholder}
          invalid={Boolean(error)}
          className={cn(currency && "ps-12")}
          onBlur={(event) => {
            void field.onBlur(event);
            const figure = amountTyped(event.target.value);
            if (figure !== null && event.target.value.trim() !== "") {
              setValue(name, formatNumber(figure));
            }
          }}
        />
      </div>
    </Field>
  );
}

export function BackgroundFields({ register, errors }: FieldGroupProps) {
  return (
    <>
      <div className="grid gap-x-4 sm:grid-cols-2">
        <Field
          label="Nationality"
          hint="Not the same fact as country — visa status and local credibility follow it."
          error={errors.nationality?.message}
        >
          <Input {...register("nationality")} placeholder="Egyptian" />
        </Field>
        <Field label="Years of experience" error={errors.yearsExperience?.message}>
          <Input {...register("yearsExperience")} inputMode="numeric" placeholder="18" />
        </Field>
      </div>
      <Field label="Languages" hint="Comma separated." error={errors.languages?.message}>
        <Input {...register("languages")} placeholder="English, Arabic" />
      </Field>
    </>
  );
}

export function ContactFields({ register, errors }: FieldGroupProps) {
  return (
    <>
      <div className="grid gap-x-4 sm:grid-cols-2">
        <Field label="Email" error={errors.email?.message}>
          <Input
            {...register("email")}
            inputMode="email"
            placeholder="yasmin@example.com"
            invalid={Boolean(errors.email)}
          />
        </Field>
        <Field label="Phone" error={errors.phone?.message}>
          <Input {...register("phone")} inputMode="tel" placeholder="+971 50 000 0000" />
        </Field>
      </div>
      <Field label="LinkedIn" error={errors.linkedinUrl?.message}>
        <Input
          {...register("linkedinUrl")}
          placeholder="linkedin.com/in/…"
          invalid={Boolean(errors.linkedinUrl)}
        />
      </Field>
    </>
  );
}

export function NoteFields({ register, errors }: FieldGroupProps) {
  return (
    <Field
      label="Note"
      hint="Your own remark on this person, for this mandate."
      error={errors.note?.message}
    >
      <TextArea {...register("note")} rows={3} placeholder="Met at a conference last year…" />
    </Field>
  );
}
