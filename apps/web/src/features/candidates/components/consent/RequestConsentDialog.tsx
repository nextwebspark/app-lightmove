import { useMutation, useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { Icon, ICONS } from "../../../../components/layout/Icon";
import { Button, FormError, Select, useToast } from "../../../../components/ui";
import { CheckBox } from "../../../../components/ui/FilterCheckRow";
import { Modal } from "../../../../components/ui/Modal";
import { cn } from "../../../../lib/cn";
import { formatInstantDate } from "../../../../lib/datetime";
import { messageFor } from "../../../../lib/errorCodes";
import type { CandidatePrivacySettings } from "../../api/candidatePrivacyApi";
import * as poolApi from "../../api/poolApi";
import * as shareConsentApi from "../../api/shareConsentApi";
import type { BulkConsentRequestResult, PersonConsent, PersonRecord } from "../../api/types";
import { sourcePhraseOf } from "../../lib/candidatePrivacy";
import {
  askedPurposeOf,
  consentRequestPreview,
  consentSkipLabel,
  PERSONAL_PURPOSES,
  type PersonalPurpose,
} from "../../lib/consentPreview";
import { useWorkspaceVocabulary } from "../../../workspace/lib/vocabulary";

const DAY_MS = 86_400_000;
const RECENTLY_DAYS = 14;

/** One person from the drawer, or the Candidates selection. */
export type ConsentRequestTarget =
  | { kind: "person"; person: PersonRecord }
  | { kind: "selection"; people: { personId: string; fullName: string }[] };

function day(instant: string | null): string {
  return formatInstantDate(instant) ?? "";
}

/** Why one purpose cannot be asked of this person now, as the dialog says it; null when it can. */
function lockOf(consent: PersonConsent | undefined, renewalDaysBefore: number, now: number): string | null {
  if (!consent) return null;
  if (consent.status === "given") {
    if (!consent.validUntil) return `Already given on ${day(consent.answeredAt)}`;
    if (Date.parse(consent.validUntil) - now > renewalDaysBefore * DAY_MS) {
      return `Already given, until ${day(consent.validUntil)}`;
    }
  }
  const recently = (instant: string | null) => !!instant && now - Date.parse(instant) < RECENTLY_DAYS * DAY_MS;
  const againFrom = (instant: string) => day(new Date(Date.parse(instant) + RECENTLY_DAYS * DAY_MS).toISOString());
  const refused = consent.status === "declined" || consent.status === "withdrawn";
  if (refused && consent.answeredAt && recently(consent.answeredAt)) {
    const said = consent.status === "declined" ? "Declined" : "Withdrawn";
    return `${said} on ${day(consent.answeredAt)}. You can ask again from ${againFrom(consent.answeredAt)}`;
  }
  if (consent.requestedAt && recently(consent.requestedAt)) {
    return `Asked on ${day(consent.requestedAt)}. You can ask again from ${againFrom(consent.requestedAt)}`;
  }
  return null;
}

/** Why the one person cannot be asked at all, before the server is; the rest the server answers. */
function blockOf(person: PersonRecord): string | null {
  if (person.contacts.emails.length === 0) return "no email on file";
  if (person.doNotContact) return "do not contact";
  if (person.processingRestriction) return "processing restricted";
  return null;
}

/**
 * Ask for consent (#1267, `Candidates.dc.html`'s `consent` and `consentBulk`): one email from Uncava in the firm's name,
 * asking about each purpose ticked; the person answers each on the page it links to. Nothing they don't answer changes.
 */
export function RequestConsentDialog({
  target,
  firm,
  onClose,
  onSent,
}: {
  target: ConsentRequestTarget;
  firm: CandidatePrivacySettings;
  onClose: () => void;
  onSent: (record: PersonRecord | null) => void;
}) {
  const vocabulary = useWorkspaceVocabulary();
  const toast = useToast();
  const [now] = useState(() => Date.now());
  const single = target.kind === "person" ? target.person : null;
  const people = target.kind === "selection" ? target.people : [];
  const offered = PERSONAL_PURPOSES.filter((purpose) =>
    purpose === "keep_on_file" ? firm.keepOnFile : firm.marketing,
  );
  const locks = new Map<PersonalPurpose, string | null>(
    offered.map((purpose) => [
      purpose,
      single
        ? lockOf(
            single.privacy.consents.find((consent) => consent.purpose === purpose && !consent.projectId),
            firm.renewalDaysBefore,
            now,
          )
        : null,
    ]),
  );
  const [ticked, setTicked] = useState<Set<PersonalPurpose>>(
    () => new Set(offered.filter((purpose) => !locks.get(purpose))),
  );
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<BulkConsentRequestResult | null>(null);
  const asking = offered.filter((purpose) => ticked.has(purpose) && !locks.get(purpose));
  const blocked = single ? blockOf(single) : null;

  // Phase 3: one person can also be asked about sharing their profile for one of their positions.
  const positions = single && firm.shareWithClient ? single.positions : [];
  const [shareTicked, setShareTicked] = useState(false);
  const [shareProjectId, setShareProjectId] = useState(positions[0]?.projectId ?? "");
  const [nameClient, setNameClient] = useState(false);
  const shareTarget = useQuery({
    queryKey: shareConsentApi.SHARE_CONSENTS_KEY(shareProjectId),
    queryFn: ({ signal }) => shareConsentApi.getShareConsents(shareProjectId, signal),
    enabled: shareTicked && !!shareProjectId,
  });
  const shareLock = single
    ? lockOf(
        single.privacy.consents.find(
          (consent) => consent.purpose === "share_with_client" && consent.projectId === shareProjectId,
        ),
        firm.renewalDaysBefore,
        now,
      )
    : null;
  const clientNamable = shareTarget.data?.clientNamable ?? true;
  const sharing = shareTicked && !!shareProjectId && !shareLock;
  const roleTitle = positions.find((position) => position.projectId === shareProjectId)?.positionTitle ?? "";
  const named = sharing && clientNamable && nameClient;
  const sharePreview = sharing
    ? { roleTitle, clientName: named ? (shareTarget.data?.clientName ?? null) : null }
    : null;

  const sending = useMutation({
    mutationFn: async () => {
      if (single) {
        const record = sharing
          ? await poolApi.requestConsent(single.personId, [...asking, "share_with_client"], {
              projectId: shareProjectId,
              nameClient: named,
            })
          : await poolApi.requestConsent(single.personId, asking);
        return { record, bulk: null };
      }
      return {
        record: null,
        bulk: await poolApi.requestConsents(
          people.map((person) => person.personId),
          asking,
        ),
      };
    },
    onSuccess: ({ record, bulk }) => {
      onSent(record);
      if (record) {
        toast.success("Consent request sent");
        onClose();
        return;
      }
      if (bulk && bulk.skipped.length === 0) {
        toast.success(`${bulk.sent} consent ${bulk.sent === 1 ? "request" : "requests"} sent`);
        onClose();
        return;
      }
      setResult(bulk);
    },
    onError: (failure) => setError(messageFor(failure)),
  });

  const title = single ? `Ask ${single.fullName} for consent` : `Ask ${people.length} people for consent`;

  if (result) {
    const names = new Map(people.map((person) => [person.personId, person.fullName]));
    return (
      <Modal
        open
        onClose={onClose}
        title={title}
        footer={
          <Button size="md" onClick={onClose}>
            Done
          </Button>
        }
      >
        <div className="flex flex-col gap-2 text-body text-u-text2">
          <p className="type-label text-u-text3">
            {result.sent} asked · {result.skipped.length} skipped
          </p>
          <ul aria-label="Skipped" className="flex flex-col gap-2">
            {result.skipped.map((skip) => (
              <li key={skip.personId} className="flex items-baseline gap-2 text-note">
                <span className="min-w-0 flex-1 truncate text-u-text2">{names.get(skip.personId) ?? "Someone"}</span>
                <span className="flex-none font-mono text-meta text-u-text3">{consentSkipLabel(skip.reason)}</span>
              </li>
            ))}
          </ul>
        </div>
      </Modal>
    );
  }

  const email = single?.contacts.emails[0]?.address;
  const preview = consentRequestPreview(
    firm.controllerName ?? "",
    sourcePhraseOf(single?.source),
    asking,
    firm.keepOnFileMonths,
    sharePreview,
  );
  const noneToSend = (asking.length === 0 && !sharing) || !!blocked;
  const toggle = (purpose: PersonalPurpose) =>
    setTicked((current) => {
      const next = new Set(current);
      if (next.has(purpose)) next.delete(purpose);
      else next.add(purpose);
      return next;
    });

  return (
    <Modal
      open
      onClose={onClose}
      dismissible={!sending.isPending}
      title={title}
      footer={
        <>
          <Button variant="secondary" disabled={sending.isPending} onClick={onClose}>
            Cancel
          </Button>
          <Button loading={sending.isPending} disabled={noneToSend} onClick={() => sending.mutate()}>
            {single ? "Send request" : `Ask ${people.length} ${people.length === 1 ? "person" : "people"}`}
          </Button>
        </>
      }
    >
      <div className="flex flex-col gap-3 text-body text-u-text2">
        <FormError message={error} />
        <p>
          {single
            ? `${single.fullName.split(" ")[0]} gets one email at ${email ?? "their address"} from Uncava in your firm's name, and answers each on the page it links to.`
            : "Each gets one email from Uncava in your firm's name, asking about what you tick, and answers each on the page it links to."}
        </p>
        <div>
          <div className="type-label text-u-text3">Ask about</div>
          <div className="mt-2 grid gap-1.5" role="group" aria-label="Ask about">
            {offered.map((purpose) => {
              const lock = locks.get(purpose);
              const on = !lock && ticked.has(purpose);
              const text = askedPurposeOf(purpose, firm.keepOnFileMonths, firm.renewalDaysBefore);
              return (
                <button
                  key={purpose}
                  type="button"
                  role="checkbox"
                  aria-checked={on}
                  disabled={!!lock}
                  onClick={() => toggle(purpose)}
                  className={cn(
                    "flex w-full items-start gap-2.5 rounded-u-chip border border-u-border bg-u-raised px-3 py-2.5 text-left",
                    lock ? "cursor-not-allowed opacity-55" : "cursor-pointer",
                  )}
                >
                  <CheckBox checked={on} />
                  <span className="min-w-0 flex-1">
                    <span className="block text-note font-medium text-u-text">{text.label}</span>
                    <span className="mt-0.5 block font-mono text-meta text-u-text3">{lock ?? text.sub}</span>
                  </span>
                </button>
              );
            })}
            {positions.length > 0 && (
              <div className="rounded-u-chip border border-u-border bg-u-raised px-3 py-2.5">
                <button
                  type="button"
                  role="checkbox"
                  aria-checked={sharing}
                  disabled={!!shareLock}
                  onClick={() => setShareTicked((current) => !current)}
                  className={cn("flex w-full items-start gap-2.5 text-left", shareLock ? "cursor-not-allowed opacity-55" : "")}
                >
                  <CheckBox checked={sharing} />
                  <span className="min-w-0 flex-1">
                    <span className="block text-note font-medium text-u-text">Share their profile with a {vocabulary.unitLower}</span>
                    <span className="mt-0.5 block font-mono text-meta text-u-text3">
                      {shareLock ??
                        (named
                          ? `For one position. The email names ${shareTarget.data?.clientName ?? "the client"}`
                          : 'For one position. The email says "a confidential client"')}
                    </span>
                  </span>
                </button>
                {shareTicked && (
                  <div className="mt-2 flex flex-col gap-2 ps-7">
                    <Select
                      aria-label="Position"
                      density="compact"
                      value={shareProjectId}
                      onChange={(event) => setShareProjectId(event.target.value)}
                    >
                      {positions.map((position) => (
                        <option key={position.projectId} value={position.projectId}>
                          {position.positionTitle ?? "Position"}
                        </option>
                      ))}
                    </Select>
                    <button
                      type="button"
                      role="checkbox"
                      aria-checked={named}
                      disabled={!clientNamable}
                      onClick={() => setNameClient((current) => !current)}
                      className={cn("flex items-start gap-2 text-left", !clientNamable && "cursor-not-allowed opacity-55")}
                    >
                      <CheckBox checked={named} />
                      <span className="text-note text-u-text2">
                        {clientNamable
                          ? `Name the ${vocabulary.unitLower}${shareTarget.data?.clientName ? ` (${shareTarget.data.clientName})` : ""}`
                          : 'This position is confidential, so the email says "a confidential client".'}
                      </span>
                    </button>
                  </div>
                )}
              </div>
            )}
          </div>
        </div>
        <div aria-label="Consent request preview" className="rounded-u-card border border-u-border bg-u-raised px-3.5 py-3">
          <div className="font-mono text-meta text-u-text3">
            From: {firm.controllerName} via Uncava · Reply-to: {firm.privacyContactEmail}
          </div>
          <div className="mt-1 text-note font-semibold text-u-text">{preview.subject}</div>
          {preview.asking.length === 0 && sharePreview ? (
            <p className="mt-2 text-note text-u-text2">{preview.intro}</p>
          ) : (
            <>
              <p className="mt-2 text-note text-u-text2">
                {preview.intro} We&apos;d like your permission to:
              </p>
              <ul className="mt-1.5 list-disc ps-5 text-note text-u-text2">
                {preview.asking.length > 0 ? (
                  preview.asking.map((ask) => <li key={ask}>{ask}</li>)
                ) : (
                  <li>(tick at least one)</li>
                )}
              </ul>
            </>
          )}
          <p className="mt-1.5 text-note text-u-text2">{preview.closing}</p>
          <div className="mt-2.5 flex flex-wrap items-center gap-2">
            <span className="rounded-u-chip bg-u-accent-solid px-2.5 py-1.5 text-note font-semibold text-white">
              Choose your answers
            </span>
            <span className="font-mono text-meta text-u-accent">Our privacy notice</span>
          </div>
        </div>
        {blocked && single && (
          <p role="alert" className="rounded-u-chip border border-u-offlimits bg-u-offlimits-tint px-3 py-2.5 text-note">
            {single.fullName} can&apos;t be asked: {blocked}.
          </p>
        )}
        <div className="flex gap-2.5 rounded-u-chip border border-u-border bg-u-raised px-3 py-2.5 font-mono text-meta text-u-text2">
          <Icon d={ICONS.info} size={15} className="mt-px flex-none text-u-text3" />
          <span>
            The Yes and No for each are on the page the link opens. The request closes after 30 days, and no answer
            changes nothing.
          </span>
        </div>
      </div>
    </Modal>
  );
}
