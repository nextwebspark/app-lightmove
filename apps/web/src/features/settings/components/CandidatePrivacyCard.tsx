import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Button, Field, Input, Select, useToast } from "../../../components/ui";
import { ApiRequestError } from "../../../lib/apiClient";
import { messageFor } from "../../../lib/errorCodes";
import * as candidatePrivacyApi from "../../candidates/api/candidatePrivacyApi";
import type {
  CandidatePrivacySettings,
  CandidatePrivacySettingsChange,
} from "../../candidates/api/candidatePrivacyApi";
import type { LawfulBasis } from "../../candidates/api/types";
import { LAWFUL_BASES } from "../../candidates/lib/candidatePrivacy";

type Errors = Partial<Record<"privacyContactEmail" | "noticeUrl", string>>;

const EMAIL = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;
const HTTPS_LINK = /^https:\/\/\S+\.\S+/i;

function draftOf(saved: CandidatePrivacySettings): CandidatePrivacySettingsChange {
  return {
    controllerName: saved.controllerName ?? "",
    privacyContactEmail: saved.privacyContactEmail ?? "",
    noticeUrl: saved.noticeUrl ?? "",
    defaultLawfulBasis: saved.defaultLawfulBasis,
  };
}

function errorsOf(draft: CandidatePrivacySettingsChange): Errors {
  const errors: Errors = {};
  if (draft.privacyContactEmail.trim() && !EMAIL.test(draft.privacyContactEmail.trim())) {
    errors.privacyContactEmail = "Enter an email address";
  }
  if (draft.noticeUrl.trim() && !HTTPS_LINK.test(draft.noticeUrl.trim())) {
    errors.noticeUrl = "Enter a link starting with https://";
  }
  return errors;
}

/**
 * Settings → Data & retention → Candidate privacy (#923, `Settings.dc.html`, `data`): the firm as controller of the
 * people it holds. Every privacy notice and outreach email names it; no notice can go until the first three are set.
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
        });
        return;
      }
      toast.error(messageFor(error));
    },
  });

  const unchanged = JSON.stringify(draft) === JSON.stringify(draftOf(saved));
  const set = (key: keyof CandidatePrivacySettingsChange, value: string) => {
    setDraft((current) => ({ ...current, [key]: value }));
    setErrors((current) => ({ ...current, [key]: undefined }));
  };

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
      </div>
      <div className="mt-3.5 flex gap-2.5 rounded-u-chip border border-u-border bg-u-surface px-3 py-2.5 font-mono text-meta text-u-text2">
        <Icon d={ICONS.info} size={15} className="mt-px flex-none text-u-text3" />
        <span>
          {saved.readyForNotices
            ? `Privacy notices go from Uncava in ${saved.controllerName}'s name, and replies reach your privacy contact.`
            : "Fill in your firm's name, a privacy contact and your notice link before anyone can send a privacy notice."}
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
