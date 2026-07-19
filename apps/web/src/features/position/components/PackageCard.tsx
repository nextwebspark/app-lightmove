import { useState } from "react";
import type { NoticeUnit, PositionDetails } from "../api/types";
import { BENEFIT_PRESETS } from "../lib/benefits";
import { InlineInput, InlineSelect, MicroLabel, NumberInput, SectionHeading } from "./fields";

/** The currencies the modal offers — regional first, then the usual reserve set. */
const CURRENCIES = ["USD", "EUR", "GBP", "AED", "SAR", "QAR", "KWD", "BHD", "OMR", "EGP", "CHF", "SGD", "INR"];

const NOTICE_UNITS: { value: NoticeUnit; label: string }[] = [
  { value: "WEEKS", label: "Weeks" },
  { value: "MONTHS", label: "Months" },
];

/** Package details (comp fields on the left, benefits chips on the right), per the mockup's 2-col grid. */
export function PackageCard({
  details,
  disabled,
  onChange,
}: {
  details: PositionDetails;
  disabled: boolean;
  onChange: (patch: Partial<PositionDetails>) => void;
}) {
  return (
    <div className="mb-[22px]">
      <SectionHeading title="Package Details" />
      <div className="grid grid-cols-2 gap-4">
        <div className="rounded-[10px] border border-line-soft bg-panel2 p-4">
          <div className="grid grid-cols-2 gap-x-5 gap-y-3.5">
            <MoneyField
              label="Base salary min"
              value={details.salaryMin}
              onChange={(salaryMin) => onChange({ salaryMin })}
            />
            <MoneyField
              label="Base salary max"
              value={details.salaryMax}
              onChange={(salaryMax) => onChange({ salaryMax })}
            />
            <div>
              <MicroLabel>Currency</MicroLabel>
              <InlineSelect value={details.currency} onChange={(e) => onChange({ currency: e.target.value })}>
                {CURRENCIES.map((code) => (
                  <option key={code} value={code}>
                    {code}
                  </option>
                ))}
              </InlineSelect>
            </div>
            <div>
              <MicroLabel>Notice period</MicroLabel>
              <div className="flex items-center gap-2">
                <NumberInput
                  value={details.noticeValue}
                  onChange={(noticeValue) => onChange({ noticeValue })}
                  className="flex-1"
                />
                <InlineSelect
                  value={details.noticeUnit ?? "MONTHS"}
                  onChange={(e) => onChange({ noticeUnit: e.target.value as NoticeUnit })}
                  className="w-24"
                >
                  {NOTICE_UNITS.map((u) => (
                    <option key={u.value} value={u.value}>
                      {u.label}
                    </option>
                  ))}
                </InlineSelect>
              </div>
            </div>
            <div>
              <MicroLabel>Bonus target</MicroLabel>
              <NumberInput
                value={details.bonusTargetPct}
                onChange={(bonusTargetPct) => onChange({ bonusTargetPct })}
                max={100}
                suffix="% of base"
              />
            </div>
            <div>
              <MicroLabel>LTIP / Equity</MicroLabel>
              <InlineInput
                value={details.ltip ?? ""}
                onChange={(e) => onChange({ ltip: e.target.value || null })}
              />
            </div>
          </div>
        </div>

        <div className="rounded-[10px] border border-line-soft bg-panel2 p-4">
          <MicroLabel>Benefits</MicroLabel>
          <div className="flex flex-wrap gap-1.5">
            {details.benefits.map((benefit, index) => (
              <span
                key={`${benefit}-${index}`}
                className="inline-flex max-w-full items-center gap-1.5 whitespace-normal break-words rounded-2xl border border-line bg-panel px-2.5 py-[5px] font-mono text-xs font-medium text-text2"
              >
                {benefit}
                <button
                  type="button"
                  aria-label={`Remove ${benefit}`}
                  onClick={() =>
                    onChange({ benefits: details.benefits.filter((_, i) => i !== index) })
                  }
                  className="flex flex-none text-[11px] leading-none text-text3 transition hover:text-red"
                >
                  ✕
                </button>
              </span>
            ))}
            {!disabled && (
              <BenefitsField
                existing={details.benefits}
                onAdd={(label) => onChange({ benefits: [...details.benefits, label] })}
              />
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

/** Formats with thousands separators for display; only the digits reach the draft. */
function MoneyField({
  label,
  value,
  onChange,
}: {
  label: string;
  value: number | null;
  onChange: (value: number | null) => void;
}) {
  return (
    <div>
      <MicroLabel>{label}</MicroLabel>
      <InlineInput
        inputMode="numeric"
        value={value === null ? "" : value.toLocaleString("en-US")}
        onChange={(e) => {
          const digits = e.target.value.replace(/[^\d]/g, "");
          onChange(digits ? Number(digits) : null);
        }}
      />
    </div>
  );
}

/**
 * Add-benefit combobox: a text input backed by a datalist of common presets. Typing filters them;
 * picking one or typing a custom value commits it on Enter *or on blur* — the blur commit is the fix
 * for benefits that were silently dropped when the field only listened for Enter.
 */
function BenefitsField({ existing, onAdd }: { existing: string[]; onAdd: (label: string) => void }) {
  const [draft, setDraft] = useState("");
  const listId = "benefit-presets";

  const commit = () => {
    const label = draft.trim();
    if (!label) return;
    // Don't add a duplicate of one already on the list.
    if (!existing.some((b) => b.toLowerCase() === label.toLowerCase())) onAdd(label);
    setDraft("");
  };

  return (
    <span className="inline-flex items-center rounded-2xl border border-dashed border-line px-2.5 py-0.5">
      <input
        list={listId}
        value={draft}
        placeholder="+ add benefit"
        aria-label="Add a benefit"
        onChange={(e) => setDraft(e.target.value)}
        onBlur={commit}
        onKeyDown={(e) => {
          if (e.key === "Enter") {
            e.preventDefault();
            commit();
          }
        }}
        className="w-32 bg-transparent py-[3px] font-mono text-xs font-medium text-text outline-none"
      />
      <datalist id={listId}>
        {BENEFIT_PRESETS.filter((p) => !existing.includes(p)).map((preset) => (
          <option key={preset} value={preset} />
        ))}
      </datalist>
    </span>
  );
}
