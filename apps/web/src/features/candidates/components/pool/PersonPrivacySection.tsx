import { useMutation } from "@tanstack/react-query";
import { Icon, ICONS } from "../../../../components/layout/Icon";
import { Button, Select, useToast } from "../../../../components/ui";
import { cn } from "../../../../lib/cn";
import { formatInstantDate, formatInstantDateTime } from "../../../../lib/datetime";
import { messageFor } from "../../../../lib/errorCodes";
import type { CandidatePrivacySettings } from "../../api/candidatePrivacyApi";
import * as poolApi from "../../api/poolApi";
import type { LawfulBasis, PersonConsent, PersonRecord } from "../../api/types";
import { LAWFUL_BASES, lawfulBasisLabel, noticeChannelLabel } from "../../lib/candidatePrivacy";
import { type ConsentTone, consentRowOf, PERSONAL_PURPOSES, type PersonalPurpose } from "../../lib/consentPreview";

const DEFAULT = "default";

const PILL_TONES: Record<ConsentTone, string> = {
  idle: "bg-u-surface text-u-text3",
  asked: "bg-u-accent-tint text-u-accent",
  given: "bg-u-direct-tint text-u-direct",
  refused: "bg-u-offlimits-tint text-u-offlimits",
  ended: "bg-u-border text-u-text2",
};

/** The purposes the workspace offers, and any other this person was ever asked about. */
function rowPurposesOf(firm: CandidatePrivacySettings, consents: PersonConsent[]): PersonalPurpose[] {
  const offered = { keep_on_file: firm.keepOnFile, marketing: firm.marketing };
  return PERSONAL_PURPOSES.filter(
    (purpose) => offered[purpose] || consents.some((consent) => consent.purpose === purpose),
  );
}

/**
 * The drawer's Privacy block (#923, `Candidates.dc.html`): the lawful basis the firm records for holding this person,
 * and whether they were told; in consent mode (#1267) where each purpose stands. Staff-only, as the whole drawer is; a
 * client seat never reads it.
 */
export function PersonPrivacySection({
  person,
  onChanged,
  onSendNotice,
  onMarkNotified,
  consentFirm,
  onAskConsent,
  onRecordConsent,
}: {
  person: PersonRecord;
  onChanged: (record: PersonRecord) => void;
  onSendNotice: () => void;
  onMarkNotified: () => void;
  /** The firm's settings while it asks for consent (#1267); absent in notices mode. */
  consentFirm?: CandidatePrivacySettings;
  onAskConsent?: () => void;
  onRecordConsent?: () => void;
}) {
  const toast = useToast();
  const privacy = person.privacy;
  const recording = useMutation({
    mutationFn: (basis: LawfulBasis | null) => poolApi.setLawfulBasis(person.personId, basis),
    onSuccess: (record) => {
      onChanged(record);
      toast.success("Lawful basis recorded");
    },
    onError: (error) => toast.error(messageFor(error)),
  });

  const notified = !!privacy.notifiedAt;

  return (
    <section aria-label="Privacy" className="mb-4 flex gap-3 rounded-u-card border border-u-border bg-u-raised p-3">
      <span className="grid size-7 flex-none place-items-center rounded-full bg-u-surface text-u-text3">
        <Icon d={ICONS.lock} size={14} />
      </span>
      <div className="min-w-0 flex-1">
        <div className="type-label mb-1.5 text-u-text3">Privacy</div>
        <label className="flex flex-wrap items-center gap-2">
          <span className="text-note text-u-text2">Lawful basis</span>
          <Select
            aria-label="Lawful basis"
            density="compact"
            className="w-auto"
            disabled={recording.isPending}
            value={privacy.basisIsDefault ? DEFAULT : privacy.lawfulBasis}
            onChange={(event) =>
              recording.mutate(event.target.value === DEFAULT ? null : (event.target.value as LawfulBasis))
            }
          >
            <option value={DEFAULT}>
              {privacy.basisIsDefault ? `${lawfulBasisLabel(privacy.lawfulBasis)} (workspace default)` : "Workspace default"}
            </option>
            {LAWFUL_BASES.map((basis) => (
              <option key={basis.value} value={basis.value}>
                {basis.label}
              </option>
            ))}
          </Select>
        </label>
        {!privacy.basisIsDefault && privacy.basisSetByName && (
          <div className="mt-1 font-mono text-meta text-u-text3">
            Recorded by {privacy.basisSetByName}
            {privacy.basisSetAt && ` · ${formatInstantDateTime(privacy.basisSetAt)}`}
          </div>
        )}
        <div className="mt-2.5 flex flex-wrap items-center gap-2">
          <span
            className={cn(
              "type-tag rounded px-1.5 py-0.5",
              notified ? "bg-u-direct-tint text-u-direct" : "bg-u-signal-tint text-u-signal",
            )}
          >
            {notified ? "Notified" : "Not notified"}
          </span>
          <span className="font-mono text-meta text-u-text3">
            {notified && privacy.noticeChannel
              ? `${formatInstantDate(privacy.notifiedAt)} · ${noticeChannelLabel(privacy.noticeChannel)}`
              : `On file since ${formatInstantDate(person.addedAt)}`}
          </span>
        </div>
        {!notified && (
          <div className="mt-2 flex flex-wrap gap-1.5">
            <Button variant="secondary" size="xs" onClick={onSendNotice}>
              <Icon d={ICONS.mail} size={12} />
              Send privacy notice
            </Button>
            <Button variant="secondary" size="xs" onClick={onMarkNotified}>
              Mark as notified
            </Button>
          </div>
        )}
        {consentFirm && (
          <div aria-label="Consent" role="group" className="mt-3 border-t border-u-border pt-2.5">
            <div className="type-label mb-1 text-u-text3">Consent</div>
            {privacy.consentWarning && (
              <p role="note" className="my-1.5 rounded-u-chip bg-u-signal-tint px-2 py-1.5 text-note text-u-text2">
                <b className="text-u-signal">No consent recorded.</b> Their lawful basis is Consent, but they haven&apos;t
                given any. Nothing is blocked: ask them, or record another basis.
              </p>
            )}
            <ul className="flex flex-col">
              {rowPurposesOf(consentFirm, privacy.consents).map((purpose) => {
                const consent = privacy.consents.find((held) => held.purpose === purpose && !held.projectId) ?? null;
                const row = consentRowOf(purpose, consent, (instant) => formatInstantDate(instant) ?? "");
                return (
                  <li key={purpose} className="flex items-start gap-2.5 py-1.5">
                    <span className="min-w-0 flex-1">
                      <span className="block text-note font-medium text-u-text">{row.label}</span>
                      <span className="mt-0.5 block font-mono text-meta text-u-text3">{row.line}</span>
                    </span>
                    <span className={cn("type-tag flex-none rounded px-1.5 py-0.5", PILL_TONES[row.tone])}>
                      {row.pill}
                    </span>
                  </li>
                );
              })}
            </ul>
            <div className="mt-1.5 flex flex-wrap gap-1.5">
              <Button variant="secondary" size="xs" onClick={onAskConsent}>
                <Icon d={ICONS.shield} size={12} />
                Ask for consent
              </Button>
              <Button variant="secondary" size="xs" onClick={onRecordConsent}>
                Record an answer
              </Button>
            </div>
          </div>
        )}
      </div>
    </section>
  );
}
