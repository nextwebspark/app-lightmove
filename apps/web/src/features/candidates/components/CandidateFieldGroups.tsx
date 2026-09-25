import { useEffect, useState, type ReactNode } from "react";
import {
  Controller,
  useFieldArray,
  useFormContext,
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
import { SegmentedControl, type SegmentedOption } from "../../../components/ui/SegmentedControl";
import { cn } from "../../../lib/cn";
import { CURRENCIES, currencyOptionLabel, DEFAULT_CURRENCY } from "../../../lib/currencies";
import { formatNumber } from "../../../lib/format";
import { NOTICE_PERIODS } from "../../../lib/noticePeriod";
import { toReadableUrl } from "../../../lib/url";
import {
  allowanceTotalOf,
  amountTyped,
  annualBaseOf,
  bonusAmountOf,
  bonusPercentOf,
  LONG_TERM_INCENTIVE_TYPES,
  shareTyped,
  toggleIncentiveType,
  type BaseCadence,
  type BonusBasis,
} from "../lib/compensation";
import { EMPTY_CONTACT_LINE, type CandidateForm, type ContactEntryForm } from "../lib/candidateForm";
import {
  CANDIDATE_GENDERS,
  CANDIDATE_NATIONALITIES,
  CANDIDATE_SENIORITIES,
  CANDIDATE_STATUSES,
} from "../lib/candidateVocabulary";

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
            className={employerLocked ? "text-u-text3" : undefined}
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
        <span className="font-mono text-[10px] font-semibold uppercase tracking-[0.12em] text-u-text3">
          Career history
        </span>
        <span className="font-mono text-[11px] text-u-text3">Most recent first</span>
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
            className="mt-2 flex-none rounded-md p-1.5 text-u-text3 transition hover:bg-u-raised hover:text-u-offlimits"
          >
            <Icon d={ICONS.trash} size={14} />
          </button>
        </div>
      ))}
      <button
        type="button"
        onClick={() => career.append({ company: "", title: "", period: "" })}
        className="inline-flex items-center gap-1.5 rounded-[6px] border border-dashed border-u-border-strong px-3 py-2 font-sans text-[13px] text-u-text2 transition hover:border-u-text3 hover:text-u-text"
      >
        <Icon d={ICONS.plus} size={14} />
        Add a post
      </button>
      {errors.career?.message && (
        <span role="alert" className="mt-1.5 block font-mono text-[11px] text-u-offlimits">
          {errors.career.message}
        </span>
      )}
    </div>
  );
}

const CADENCES: readonly SegmentedOption<BaseCadence>[] = [
  { value: "annual", label: "Annual" },
  { value: "monthly", label: "Monthly" },
];

const BONUS_BASES: readonly SegmentedOption<BonusBasis>[] = [
  { value: "percent", label: "% of Base" },
  { value: "fixed", label: "Fixed" },
];

const MAX_ALLOWANCE_LINES = 12;

const TOGGLE_CLASS = "mt-2 rounded-[6px] border-u-border bg-u-raised";

/** The mockup's figure field: soft border, 14px bold, the currency set in the same weight as the figure. */
const FIGURE_INPUT = "border-u-border py-[9px] text-[14px] font-bold";

type CurrencyMode = "auto" | "override" | "brief";

/**
 * A package, as it is quoted (claude-design/Position.dc.html, the drawer's Compensation editor): the
 * currency the brief is quoted in unless somebody overrides it, the base and bonus typed the way they
 * were said — per month, as a share of base — the allowances line by line, the instruments an LTIP is
 * paid in, and the total worked out as it is typed. What is stored is always the annual amounts; the
 * two toggles are how a figure was spoken, and the patch converts them.
 *
 * <p>A currency the list does not carry — one stored before the picker existed, or by an import —
 * stays offered as an option: a select whose value matches no option posts blank, and that would
 * clear a fact nobody touched. The notice period, which was a text box until the five became the
 * vocabulary, is kept the same way; "Not established" is the blank every sibling picker carries, and
 * is not "None" — see lib/noticePeriod.ts.
 */
export function CompensationFields<TTransformed extends FieldValues>({
  register,
  errors,
  control,
  watch,
  setValue,
  briefCurrency,
  storedCurrency,
  storedNoticePeriod,
}: FieldGroupProps & {
  control: Control<CandidateForm, unknown, TTransformed>;
  watch: UseFormWatch<CandidateForm>;
  setValue: UseFormSetValue<CandidateForm>;
  /** The mandate's currency from the brief, which a package follows until somebody overrides it. */
  briefCurrency?: string | null;
  /** What the profile already holds; a stored code other than the brief's opens overridden. */
  storedCurrency?: string | null;
  storedNoticePeriod?: string | null;
}) {
  const [currencyMode, setCurrencyMode] = useState<CurrencyMode>("auto");
  const allowances = useFieldArray({ control, name: "allowanceLines" });
  const [currency, base, baseCadence, bonus, bonusBasis, allowanceLines, longTermIncentive, incentiveTypes] =
    watch([
      "currency",
      "baseSalary",
      "baseCadence",
      "bonus",
      "bonusBasis",
      "allowanceLines",
      "longTermIncentive",
      "longTermIncentiveTypes",
    ]);

  const followsBrief =
    Boolean(briefCurrency) &&
    (currencyMode === "brief" ||
      (currencyMode === "auto" && (!storedCurrency || storedCurrency === briefCurrency)));

  // The brief is read beside the grid and can land after the form opened; while the package follows
  // it, it takes it. With no brief and nothing on file, the package starts in the default currency.
  // Once overridden, nothing here touches the pick.
  useEffect(() => {
    if (followsBrief && briefCurrency && currency !== briefCurrency) {
      setValue("currency", briefCurrency, { shouldDirty: true });
    } else if (!briefCurrency && !storedCurrency && currencyMode === "auto" && !currency) {
      setValue("currency", DEFAULT_CURRENCY, { shouldDirty: true });
    }
  }, [followsBrief, briefCurrency, storedCurrency, currencyMode, currency, setValue]);

  const currencyField = register("currency");
  const bonusPercentField = register("bonus");
  const currencies: string[] = [...CURRENCIES];
  for (const held of [storedCurrency, briefCurrency]) {
    if (held && !currencies.includes(held)) currencies.unshift(held);
  }
  const offVocabularyNotice =
    storedNoticePeriod && !NOTICE_PERIODS.some((period) => period.label === storedNoticePeriod)
      ? storedNoticePeriod
      : null;

  const annualBase = annualBaseOf(amountTyped(base), baseCadence);
  const bonusAmount = bonusAmountOf(annualBase, shareTyped(bonus), bonusBasis);
  const allowanceTotal = allowanceTotalOf(allowanceLines.map((line) => amountTyped(line.amount)));
  const total = (annualBase ?? 0) + (bonusAmount ?? 0) + (allowanceTotal ?? 0) + (amountTyped(longTermIncentive) ?? 0);

  const handleBonusBasis = (next: BonusBasis) => {
    if (next === bonusBasis) return;
    // Carry the figure across rather than reading 45 as AED 45: the switch changes how the bonus is
    // stated, not what it is.
    const typed = shareTyped(bonus);
    if (typed !== null) {
      const restated =
        next === "fixed"
          ? bonusAmount === null ? "" : formatNumber(bonusAmount)
          : annualBase ? `${bonusPercentOf(annualBase, typed)}%` : "";
      setValue("bonus", restated, { shouldDirty: true });
    }
    setValue("bonusBasis", next, { shouldDirty: true });
  };

  return (
    <>
      <div className="mb-3.5 flex items-center gap-2.5">
        {followsBrief ? (
          <div
            aria-label="Currency"
            className="min-w-0 flex-1 truncate rounded-[6px] border border-u-border bg-u-raised px-3 py-[9px] font-mono text-[13px] font-semibold text-u-text"
          >
            {currencyOptionLabel(currency)}
          </div>
        ) : (
          <Select
            {...currencyField}
            // A pick is a statement that this package is quoted otherwise: a brief read after it must
            // not take it back.
            onChange={(event) => {
              setCurrencyMode("override");
              void currencyField.onChange(event);
            }}
            aria-label="Currency"
            invalid={Boolean(errors.currency)}
            className="min-w-0 flex-1 border-u-border py-[9px] font-semibold"
          >
            <option value="">Not set</option>
            {currencies.map((code) => (
              <option key={code} value={code}>
                {currencyOptionLabel(code)}
              </option>
            ))}
          </Select>
        )}
        {followsBrief && (
          <span className="flex-none rounded-[5px] bg-u-direct-tint px-2 py-1 font-mono text-[10px] font-bold uppercase tracking-[0.05em] text-u-direct">
            From brief
          </span>
        )}
        {briefCurrency && (
          <button
            type="button"
            onClick={() => setCurrencyMode(followsBrief ? "override" : "brief")}
            className="flex-none font-sans text-[12.5px] font-semibold text-u-accent hover:underline"
          >
            {followsBrief ? "Override" : "Reset to brief"}
          </button>
        )}
      </div>

      <div className="grid gap-x-4 gap-y-4 sm:grid-cols-2">
        <div>
          <AmountField
            name="baseSalary"
            label="Base"
            placeholder="1,800,000"
            currency={currency}
            register={register}
            setValue={setValue}
            error={errors.baseSalary?.message}
          />
          <SegmentedControl
            label="Base salary is"
            options={CADENCES}
            value={baseCadence}
            onChange={(next) => setValue("baseCadence", next, { shouldDirty: true })}
            variant="uncava"
            className={TOGGLE_CLASS}
          />
        </div>

        <div>
          {bonusBasis === "fixed" ? (
            <AmountField
              name="bonus"
              label="Bonus"
              placeholder="150,000"
              currency={currency}
              register={register}
              setValue={setValue}
              error={errors.bonus?.message}
            />
          ) : (
            <CompensationField label="Bonus" error={errors.bonus?.message}>
              <Input
                {...bonusPercentField}
                aria-label="Bonus"
                inputMode="decimal"
                placeholder="30%"
                invalid={Boolean(errors.bonus)}
                className={FIGURE_INPUT}
                onBlur={(event) => {
                  void bonusPercentField.onBlur(event);
                  const share = shareTyped(event.target.value);
                  if (share !== null) setValue("bonus", `${share}%`);
                }}
              />
            </CompensationField>
          )}
          <SegmentedControl
            label="Bonus is"
            options={BONUS_BASES}
            value={bonusBasis}
            onChange={handleBonusBasis}
            variant="uncava"
            className={TOGGLE_CLASS}
          />
          {bonusBasis === "percent" && bonusAmount !== null && (
            <p className="mt-2 font-mono text-[11.5px] text-u-text3">
              Calculated: {`${currency} ${formatNumber(bonusAmount)}`.trim()}
            </p>
          )}
          {/* A share of no base comes to nothing the server can store, so say so before Save drops it. */}
          {bonusBasis === "percent" && annualBase === null && shareTyped(bonus) !== null && (
            <p className="mt-2 font-mono text-[11.5px] text-u-signal">Enter a base to work this out</p>
          )}
        </div>

        <div>
          <span className="mb-1.5 block font-mono text-[10px] font-semibold uppercase tracking-[0.12em] text-u-text3">
            Allowances
          </span>
          {allowances.fields.length > 0 && (
            <ul className="overflow-hidden rounded-[6px] border border-u-border">
              {allowances.fields.map((line, index) => (
                <AllowanceLineRow
                  key={line.id}
                  index={index}
                  register={register}
                  setValue={setValue}
                  error={
                    errors.allowanceLines?.[index]?.amount?.message ?? errors.allowanceLines?.[index]?.label?.message
                  }
                />
              ))}
            </ul>
          )}
          {allowances.fields.length < MAX_ALLOWANCE_LINES && (
            <button
              type="button"
              onClick={() => allowances.append({ label: "", amount: "" })}
              className="mt-2 font-sans text-[12.5px] font-semibold text-u-accent hover:underline"
            >
              + Add
            </button>
          )}
        </div>

        <div>
          <AmountField
            name="longTermIncentive"
            label="LTIP"
            placeholder="1,000,000"
            currency={currency}
            register={register}
            setValue={setValue}
            error={errors.longTermIncentive?.message}
          />
          <div role="group" aria-label="LTIP paid in" className="mt-2 flex flex-wrap gap-1.5">
            {LONG_TERM_INCENTIVE_TYPES.map((type) => {
              const pressed = incentiveTypes.includes(type.value);
              return (
                <button
                  key={type.value}
                  type="button"
                  aria-pressed={pressed}
                  onClick={() =>
                    setValue("longTermIncentiveTypes", toggleIncentiveType(incentiveTypes, type.value), {
                      shouldDirty: true,
                    })
                  }
                  className={cn(
                    "flex-none rounded-full border px-3 py-[5px] font-sans text-[11.5px] font-semibold transition",
                    pressed
                      ? "border-u-accent-ring bg-u-accent-tint text-u-accent"
                      : "border-u-border text-u-text3 hover:text-u-text",
                  )}
                >
                  {type.label}
                </button>
              );
            })}
          </div>
        </div>
      </div>

      <div className="mt-4 sm:w-1/2 sm:pe-2">
        <CompensationField label="Notice period" error={errors.noticePeriod?.message}>
          <Select
            {...register("noticePeriod")}
            invalid={Boolean(errors.noticePeriod)}
            className="border-u-border py-[9px]"
          >
            <option value="">Not established</option>
            {NOTICE_PERIODS.map((period) => (
              <option key={period.label} value={period.label}>
                {period.label}
              </option>
            ))}
            {offVocabularyNotice && (
              <option value={offVocabularyNotice}>{offVocabularyNotice} (as recorded)</option>
            )}
          </Select>
        </CompensationField>
      </div>

      <div className="mt-4 flex items-center justify-between rounded-[6px] border border-u-accent bg-u-accent-tint px-3.5 py-3">
        <span className="font-mono text-[10px] font-bold uppercase tracking-[0.08em] text-u-text2">Total package</span>
        <span
          data-testid="package-total"
          className={cn("font-mono text-[17px] font-bold", total > 0 ? "text-u-text" : "text-u-text3")}
        >
          {total > 0 ? `${currency} ${formatNumber(total)}`.trim() : "—"}
        </span>
      </div>
    </>
  );
}

/**
 * The editor's own label: the mockup sets these at 9.5px, a step under the shared `Field`, and stacks
 * each toggle 8px under its input rather than a field's width of space.
 */
function CompensationField({ label, error, children }: { label: string; error?: string; children: ReactNode }) {
  return (
    <label className="block">
      <span className="mb-1.5 block font-mono text-[9.5px] font-semibold uppercase tracking-[0.08em] text-u-text3">
        {label}
      </span>
      {children}
      {error && (
        <span role="alert" className="mt-1 block font-mono text-[11px] text-u-offlimits">
          {error}
        </span>
      )}
    </label>
  );
}

/** One allowance: its name on the left, its annual figure on the right, as one row of a ruled list. */
function AllowanceLineRow({
  index,
  register,
  setValue,
  error,
}: {
  index: number;
  register: UseFormRegister<CandidateForm>;
  setValue: UseFormSetValue<CandidateForm>;
  error?: string;
}) {
  const amount = register(`allowanceLines.${index}.amount`);
  return (
    <li className="border-b border-u-border px-2.5 py-2 last:border-b-0">
      <div className="flex items-center gap-2">
        <input
          {...register(`allowanceLines.${index}.label`)}
          aria-label={`Allowance ${index + 1} name`}
          placeholder="Allowance"
          className="min-w-0 flex-1 bg-transparent font-mono text-[12.5px] text-u-text2 outline-none placeholder:text-u-text3"
        />
        <input
          {...amount}
          aria-label={`Allowance ${index + 1} amount`}
          aria-invalid={Boolean(error)}
          inputMode="numeric"
          placeholder="0"
          className="w-[110px] flex-none bg-transparent text-end font-mono text-[12.5px] font-bold text-u-text outline-none placeholder:text-u-text3"
          onBlur={(event) => {
            void amount.onBlur(event);
            const figure = amountTyped(event.target.value);
            if (figure !== null) setValue(`allowanceLines.${index}.amount`, formatNumber(figure));
          }}
        />
      </div>
      {error && (
        <p role="alert" className="mt-1 font-mono text-[11px] text-u-offlimits">
          {error}
        </p>
      )}
    </li>
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
  name: "baseSalary" | "bonus" | "longTermIncentive";
  label: string;
  placeholder: string;
  currency: string;
  register: UseFormRegister<CandidateForm>;
  setValue: UseFormSetValue<CandidateForm>;
  error?: string;
}) {
  const field = register(name);
  return (
    <CompensationField label={label} error={error}>
      <div className="relative">
        {currency && (
          <span
            aria-hidden="true"
            className="pointer-events-none absolute inset-y-0 start-0 flex items-center ps-3 font-mono text-[14px] font-bold text-u-text"
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
          className={cn(FIGURE_INPUT, currency && "ps-[2.9rem]")}
          onBlur={(event) => {
            void field.onBlur(event);
            const figure = amountTyped(event.target.value);
            if (figure !== null && event.target.value.trim() !== "") {
              setValue(name, formatNumber(figure));
            }
          }}
        />
      </div>
    </CompensationField>
  );
}

/**
 * A nationality outside the nine groups — typed before the picker existed, or stated by an import —
 * stays offered, for the reason a stored currency does: see {@link CompensationFields}.
 */
export function BackgroundFields({
  register,
  errors,
  storedNationality,
}: FieldGroupProps & { storedNationality?: string | null }) {
  const offGroup =
    storedNationality && !CANDIDATE_NATIONALITIES.includes(storedNationality) ? storedNationality : null;
  return (
    <>
      <div className="grid gap-x-4 sm:grid-cols-2">
        <Field
          label="Nationality"
          hint="Not the same fact as country — visa status and local credibility follow it."
          error={errors.nationality?.message}
        >
          <Select {...register("nationality")}>
            <option value="">Not recorded</option>
            {CANDIDATE_NATIONALITIES.map((group) => (
              <option key={group} value={group}>
                {group}
              </option>
            ))}
            {offGroup && <option value={offGroup}>{offGroup} (as recorded)</option>}
          </Select>
        </Field>
        <Field label="Years of experience" error={errors.yearsExperience?.message}>
          <Input {...register("yearsExperience")} inputMode="numeric" placeholder="18" />
        </Field>
      </div>
      <div className="grid gap-x-4 sm:grid-cols-2">
        <Field
          label="Gender"
          hint="Only where it is known — the diversity report counts it and never guesses it from a name."
          error={errors.gender?.message}
        >
          <Select {...register("gender")}>
            <option value="">Not recorded</option>
            {CANDIDATE_GENDERS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </Select>
        </Field>
      </div>
      <Field label="Languages" hint="Comma separated." error={errors.languages?.message}>
        <Input {...register("languages")} placeholder="English, Arabic" />
      </Field>
    </>
  );
}

/**
 * The profile link. Locked for a person the plugin captured: the URL is the page it read them off,
 * and research and contact lookup both key on it, so retyping it can only break them. Reads the
 * enclosing form through context, like the contact lines, because two differently shaped forms
 * hold it.
 */
export function ContactFields({ lockedUrl = null }: { lockedUrl?: string | null }) {
  const { register, formState } = useFormContext<{ linkedinUrl: string }>();
  const errors = formState.errors;
  if (lockedUrl) {
    return (
      <Field label="LinkedIn" hint="Captured from this profile page — not editable">
        <div className="flex items-center gap-2 rounded-lg border border-u-border bg-u-raised px-3 py-2 font-mono text-[13px] text-u-text2">
          <Icon d={ICONS.lock} size={13} className="flex-none text-u-text3" />
          <span className="min-w-0 truncate">{toReadableUrl(lockedUrl)}</span>
        </div>
      </Field>
    );
  }
  return (
    <Field label="LinkedIn" error={errors.linkedinUrl?.message}>
      <Input
        {...register("linkedinUrl")}
        placeholder="linkedin.com/in/…"
        invalid={Boolean(errors.linkedinUrl)}
      />
    </Field>
  );
}

/** Just the two channels: the form that holds them provides its context. */
export interface ContactEntriesForm {
  emails: ContactEntryForm[];
  phones: ContactEntryForm[];
}

/**
 * One channel's lines, editable: the value, its kind, whether the person vouches for it, and a
 * remove. Shared by the Add form and the Contact section's edit mode, so adding a person by hand
 * and correcting one later look identical. Reads the enclosing form through context, because the
 * two forms that hold these lines have different shapes around them.
 */
export function ContactEntriesFields({ channel }: { channel: "email" | "phone" }) {
  const { control, register, formState } = useFormContext<ContactEntriesForm>();
  const name = channel === "email" ? "emails" : "phones";
  const lines = useFieldArray({ control, name });
  const errors = formState.errors[name];
  const noun = channel === "email" ? "email" : "phone";
  return (
    <div>
      <ul className="space-y-2">
        {lines.fields.map((line, index) => (
          <li key={line.id}>
            <div className="flex flex-wrap items-center gap-2">
              <Input
                {...register(`${name}.${index}.value`)}
                inputMode={channel === "email" ? "email" : "tel"}
                placeholder={channel === "email" ? "name@company.com" : "+971 50 000 0000"}
                aria-label={`${capitalised(noun)} ${index + 1}`}
                invalid={Boolean(errors?.[index]?.value)}
                className="min-w-[160px] flex-1"
              />
              <Select
                {...register(`${name}.${index}.kind`)}
                aria-label={`${capitalised(noun)} ${index + 1} kind`}
                className="w-[112px] flex-none"
              >
                <option value="">—</option>
                <option value="work">Work</option>
                <option value="personal">Personal</option>
              </Select>
              <Controller
                control={control}
                name={`${name}.${index}.verified`}
                render={({ field }) => (
                  <button
                    type="button"
                    role="switch"
                    aria-checked={field.value}
                    aria-label={`${capitalised(noun)} ${index + 1} verified`}
                    onClick={() => field.onChange(!field.value)}
                    className={cn(
                      "flex-none rounded-[5px] border px-[7px] py-[3px] font-mono text-[9.5px] font-bold uppercase tracking-[0.06em] transition",
                      field.value
                        ? "border-transparent bg-u-direct-tint text-u-direct"
                        : "border-u-border-strong text-u-text3 hover:border-u-text3 hover:text-u-text2",
                    )}
                  >
                    Verified
                  </button>
                )}
              />
              <button
                type="button"
                onClick={() => lines.remove(index)}
                aria-label={`Remove ${noun} ${index + 1}`}
                className="flex-none rounded-md p-1.5 text-u-text3 transition hover:bg-u-raised hover:text-u-offlimits"
              >
                <Icon d={ICONS.trash} size={14} />
              </button>
            </div>
            {errors?.[index]?.value?.message && (
              <span role="alert" className="mt-1 block font-mono text-[11px] text-u-offlimits">
                {errors[index]?.value?.message}
              </span>
            )}
          </li>
        ))}
      </ul>
      <button
        type="button"
        onClick={() => lines.append({ ...EMPTY_CONTACT_LINE })}
        disabled={lines.fields.length >= 10}
        className="mt-2 inline-flex items-center gap-1.5 rounded-[6px] border border-dashed border-u-border-strong px-3 py-1.5 font-sans text-[12.5px] text-u-text2 transition hover:border-u-text3 hover:text-u-text disabled:cursor-not-allowed disabled:opacity-50"
      >
        <Icon d={ICONS.plus} size={13} />
        Add {noun}
      </button>
      {errors?.message && (
        <span role="alert" className="mt-1.5 block font-mono text-[11px] text-u-offlimits">
          {errors.message}
        </span>
      )}
    </div>
  );
}

const capitalised = (word: string) => word.charAt(0).toUpperCase() + word.slice(1);

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
