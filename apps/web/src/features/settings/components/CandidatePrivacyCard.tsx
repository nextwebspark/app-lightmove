import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Button, Field, Input, SegmentedControl, Select, Toggle, useToast } from "../../../components/ui";
import { ApiRequestError } from "../../../lib/apiClient";
import { cn } from "../../../lib/cn";
import { messageFor } from "../../../lib/errorCodes";
import * as candidatePrivacyApi from "../../candidates/api/candidatePrivacyApi";
import type {
  CandidatePrivacyMode,
  CandidatePrivacySettings,
  CandidatePrivacySettingsChange,
  KeepOnFileMonths,
} from "../../candidates/api/candidatePrivacyApi";
import type { LawfulBasis } from "../../candidates/api/types";
import { LAWFUL_BASES } from "../../candidates/lib/candidatePrivacy";

type Errors = Partial<Record<"privacyContactEmail" | "noticeUrl" | "renewalDaysBefore", string>>;

const MODES = [
  { value: "notices", label: "Notices" },
  { value: "consent", label: "Notices and consent requests" },
] as const;

const KEEP_MONTHS: readonly KeepOnFileMonths[] = [12, 24, 36];

const EMAIL = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;
const HTTPS_LINK = /^https:\/\/\S+\.\S+/i;

function draftOf(saved: CandidatePrivacySettings): CandidatePrivacySettingsChange {
  return {
    controllerName: saved.controllerName ?? "",
    privacyContactEmail: saved.privacyContactEmail ?? "",
    noticeUrl: saved.noticeUrl ?? "",
    defaultLawfulBasis: saved.defaultLawfulBasis,
    privacyMode: saved.privacyMode,
    keepOnFile: saved.keepOnFile,
    keepOnFileMonths: saved.keepOnFileMonths,
    shareWithClient: saved.shareWithClient,
    marketing: saved.marketing,
    renewalDaysBefore: saved.renewalDaysBefore,
  };
}

function modeHintOf(locked: boolean, mode: CandidatePrivacyMode): string {
  if (locked) return "Save your firm's name, privacy contact and notice link to choose a mode.";
  return mode === "consent"
    ? "Candidates are told you hold their details, and can be asked to agree to the uses below, one at a time."
    : "Candidates are told you hold their details. Nobody is asked for consent.";
}

function renewalHintOf(days: number): string {
  return days === 0
    ? "Never asked again: consent simply ends on its date."
    : `The candidate gets one email ${days} days before their consent ends. 0 never asks again.`;
}

function errorsOf(draft: CandidatePrivacySettingsChange): Errors {
  const errors: Errors = {};
  if (draft.privacyContactEmail.trim() && !EMAIL.test(draft.privacyContactEmail.trim())) {
    errors.privacyContactEmail = "Enter an email address";
  }
  if (draft.noticeUrl.trim() && !HTTPS_LINK.test(draft.noticeUrl.trim())) {
    errors.noticeUrl = "Enter a link starting with https://";
  }
  if (!Number.isInteger(draft.renewalDaysBefore) || draft.renewalDaysBefore < 0 || draft.renewalDaysBefore > 90) {
    errors.renewalDaysBefore = "Enter 0 to 90 days";
  }
  return errors;
}

/**
 * Settings → Data & retention → Candidate privacy (#923, `Settings.dc.html`, `data`): the firm as controller of the
 * people it holds. Every privacy notice and outreach email names it; no notice can go until the first three are set.
 * Consent mode (#1267, `privacyMode`) adds the purposes a candidate can be asked about.
 */
export function CandidatePrivacyCard({ saved }: { saved: CandidatePrivacySettings }) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const [draft, setDraft] = useState(() => draftOf(saved));
  const [errors, setErrors] = useState<Errors>({});

  const save = useMutation({
    mutationFn: () => candidatePrivacyApi.putCandidatePrivacy(draft),
    onSuccess: (next) => {
      queryClient.setQueryData(candidatePrivacyApi.CANDIDATE_PRIVACY_KEY, next);
      setDraft(draftOf(next));
      toast("Candidate privacy saved");
    },
    onError: (error) => {
      if (error instanceof ApiRequestError && Object.keys(error.fieldErrors).length > 0) {
        setErrors({
          privacyContactEmail: error.fieldErrors.privacyContactEmail,
          noticeUrl: error.fieldErrors.noticeUrl,
          renewalDaysBefore: error.fieldErrors.renewalDaysBefore,
        });
        return;
      }
      toast.error(messageFor(error));
    },
  });

  const unchanged = JSON.stringify(draft) === JSON.stringify(draftOf(saved));
  const set = <K extends keyof CandidatePrivacySettingsChange>(key: K, value: CandidatePrivacySettingsChange[K]) => {
    setDraft((current) => ({ ...current, [key]: value }));
    setErrors((current) => ({ ...current, [key]: undefined }));
  };
  const modeLocked = !saved.readyForNotices;
  const mode = modeLocked ? "notices" : draft.privacyMode;
  const purposes = [
    {
      key: "keepOnFile",
      label: "Keep my details on file for future roles",
      description: "While it's given, the candidate is kept past your retention period.",
    },
    {
      key: "shareWithClient",
      label: "Share my profile with a client for a specific role",
      description: 'Asked for one position at a time. The email says "a confidential client" unless you name the client.',
    },
    {
      key: "marketing",
      label: "Career and market updates",
      description: "Recorded only. Uncava sends no updates: it tells you who agreed to yours.",
    },
  ] as const;

  const handleSave = () => {
    const found = errorsOf(draft);
    if (Object.keys(found).length > 0) {
      setErrors(found);
      return;
    }
    save.mutate();
  };

  return (
    <section aria-label="Candidate privacy" className="rounded-u-card border border-u-border bg-u-raised p-5">
      <div className="text-body font-semibold text-u-text">Candidate privacy</div>
      <p className="mt-0.5 font-mono text-meta text-u-text3">
        What the people you hold are told about who keeps their data, and why.
      </p>
      <div className="mt-3.5 max-w-[560px]">
        <Field label="Firm's legal name" hint="Named as the controller in every privacy notice and outreach email.">
          <Input
            value={draft.controllerName}
            maxLength={200}
            placeholder="Meridian Search Partners FZ-LLC"
            onChange={(event) => set("controllerName", event.target.value)}
          />
        </Field>
        <Field label="Privacy contact email" error={errors.privacyContactEmail} hint="Replies to a privacy notice go here.">
          <Input
            type="email"
            value={draft.privacyContactEmail}
            maxLength={320}
            placeholder="privacy@yourfirm.com"
            invalid={!!errors.privacyContactEmail}
            onChange={(event) => set("privacyContactEmail", event.target.value)}
          />
        </Field>
        <Field
          label="Privacy notice link"
          error={errors.noticeUrl}
          hint="Your firm's own privacy notice. Every notice and outreach email links to it."
        >
          <Input
            type="url"
            value={draft.noticeUrl}
            maxLength={2048}
            placeholder="https://yourfirm.com/privacy"
            invalid={!!errors.noticeUrl}
            onChange={(event) => set("noticeUrl", event.target.value)}
          />
        </Field>
        <Field
          label="Default lawful basis"
          hint="Recorded for every candidate unless someone records another basis on them."
        >
          <Select
            density="compact"
            className="w-auto"
            value={draft.defaultLawfulBasis}
            onChange={(event) => set("defaultLawfulBasis", event.target.value as LawfulBasis)}
          >
            {LAWFUL_BASES.map((basis) => (
              <option key={basis.value} value={basis.value}>
                {basis.label}
              </option>
            ))}
          </Select>
        </Field>
        <div className="mb-3.5">
          <div className="mb-1.5 text-note font-medium text-u-text2">Mode</div>
          <fieldset disabled={modeLocked} className={cn("m-0 border-0 p-0", modeLocked && "opacity-60")}>
            <SegmentedControl
              label="Mode"
              variant="uncava"
              options={MODES}
              value={mode}
              onChange={(next) => set("privacyMode", next)}
            />
          </fieldset>
          <p className={cn("mt-1.5 font-mono text-meta", modeLocked ? "text-u-signal" : "text-u-text3")}>
            {modeHintOf(modeLocked, mode)}
          </p>
        </div>
      </div>
      {mode === "consent" && (
        <div aria-label="Consent requests" role="group" className="mt-1 max-w-[620px] border-t border-u-border pt-4">
          <div className="text-note font-semibold text-u-text">Consent requests</div>
          <p className="mt-0.5 font-mono text-meta text-u-text3">
            Each is asked on its own, with nothing ticked. No answer changes nothing.
          </p>
          <div className="mt-3 divide-y divide-u-border">
            {purposes.map((purpose) => (
              <div key={purpose.key} className="flex items-start justify-between gap-4 py-3">
                <div className="min-w-0">
                  <div className="text-note font-medium text-u-text">{purpose.label}</div>
                  <p className="mt-0.5 text-note text-u-text3">{purpose.description}</p>
                  {purpose.key === "keepOnFile" && (
                    <label className="mt-2 flex items-center gap-2 text-note text-u-text2">
                      <span>Valid for</span>
                      <Select
                        density="compact"
                        className="w-auto"
                        aria-label="Valid for"
                        disabled={!draft.keepOnFile}
                        value={draft.keepOnFileMonths}
                        onChange={(event) => set("keepOnFileMonths", Number(event.target.value) as KeepOnFileMonths)}
                      >
                        {KEEP_MONTHS.map((months) => (
                          <option key={months} value={months}>
                            {months} months
                          </option>
                        ))}
                      </Select>
                    </label>
                  )}
                </div>
                <Toggle
                  label={purpose.label}
                  checked={draft[purpose.key]}
                  onChange={(checked) => set(purpose.key, checked)}
                />
              </div>
            ))}
          </div>
          <div className="mt-1 border-t border-u-border pt-3">
            <label className="flex flex-wrap items-center gap-2 text-note font-medium text-u-text">
              Ask again
              <Input
                type="number"
                min={0}
                max={90}
                aria-label="Days before consent ends"
                className="w-16"
                invalid={!!errors.renewalDaysBefore}
                value={String(draft.renewalDaysBefore)}
                onChange={(event) => set("renewalDaysBefore", Number(event.target.value))}
              />
              days before consent ends
            </label>
            <p
              role={errors.renewalDaysBefore ? "alert" : undefined}
              className={cn("mt-1.5 font-mono text-meta", errors.renewalDaysBefore ? "text-u-offlimits" : "text-u-text3")}
            >
              {errors.renewalDaysBefore ?? renewalHintOf(draft.renewalDaysBefore)}
            </p>
          </div>
        </div>
      )}
      <div className="mt-3.5 flex gap-2.5 rounded-u-chip border border-u-border bg-u-surface px-3 py-2.5 font-mono text-meta text-u-text2">
        <Icon d={ICONS.info} size={15} className="mt-px flex-none text-u-text3" />
        <span>
          {!saved.readyForNotices
            ? "Fill in your firm's name, a privacy contact and your notice link before anyone can send a privacy notice."
            : mode === "consent"
              ? `Privacy notices and consent requests go from Uncava in ${saved.controllerName}'s name, and replies reach your privacy contact. Each answer is recorded on the candidate.`
              : `Privacy notices go from Uncava in ${saved.controllerName}'s name, and replies reach your privacy contact.`}
        </span>
      </div>
      <div className="mt-4 flex justify-end">
        <Button type="button" size="md" loading={save.isPending} disabled={unchanged} onClick={handleSave}>
          Save changes
        </Button>
      </div>
    </section>
  );
}
