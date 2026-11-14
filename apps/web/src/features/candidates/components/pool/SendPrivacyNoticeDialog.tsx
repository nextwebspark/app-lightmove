import { useMutation, useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { Icon, ICONS } from "../../../../components/layout/Icon";
import { Button, FormError, useToast } from "../../../../components/ui";
import { Modal } from "../../../../components/ui/Modal";
import { messageFor } from "../../../../lib/errorCodes";
import { useIsWorkspaceAdmin } from "../../../auth/useIsWorkspaceAdmin";
import * as candidatePrivacyApi from "../../api/candidatePrivacyApi";
import * as poolApi from "../../api/poolApi";
import type { BulkPrivacyNoticeResult, PersonRecord } from "../../api/types";
import { noticePreviewParagraphs, skipReasonLabel } from "../../lib/candidatePrivacy";

/** One person from the drawer, or the Candidates selection. */
export type PrivacyNoticeTarget =
  | { kind: "person"; person: PersonRecord }
  | { kind: "selection"; people: { personId: string; fullName: string }[] };

/**
 * Send privacy notice (#923, `Candidates.dc.html`'s `notice` and `noticeBulk`): an email from Uncava in the firm's
 * name saying it holds the person's details, where they came from, and how to object or ask for a copy. A bulk send
 * cannot tell who will be skipped until it runs, so it answers with who was left out and why before it closes.
 */
export function SendPrivacyNoticeDialog({
  target,
  onClose,
  onSent,
}: {
  target: PrivacyNoticeTarget;
  onClose: () => void;
  onSent: (record: PersonRecord | null) => void;
}) {
  const toast = useToast();
  const isAdmin = useIsWorkspaceAdmin();
  const navigate = useNavigate();
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<BulkPrivacyNoticeResult | null>(null);
  const settings = useQuery({
    queryKey: candidatePrivacyApi.CANDIDATE_PRIVACY_KEY,
    queryFn: ({ signal }) => candidatePrivacyApi.getCandidatePrivacy(signal),
  });

  const single = target.kind === "person" ? target.person : null;
  const people = target.kind === "selection" ? target.people : [];

  const sending = useMutation({
    mutationFn: async () => {
      if (single) return { record: await poolApi.sendPrivacyNotice(single.personId), bulk: null };
      return { record: null, bulk: await poolApi.sendPrivacyNotices(people.map((person) => person.personId)) };
    },
    onSuccess: ({ record, bulk }) => {
      onSent(record);
      if (record) {
        toast.success("Privacy notice sent");
        onClose();
        return;
      }
      if (bulk && bulk.skipped.length === 0) {
        toast.success(`${bulk.sent} privacy ${bulk.sent === 1 ? "notice" : "notices"} sent`);
        onClose();
        return;
      }
      setResult(bulk);
    },
    onError: (failure) => setError(messageFor(failure)),
  });

  const firm = settings.data;
  const title = single ? `Send a privacy notice to ${single.fullName}` : `Send privacy notices to ${people.length} people`;
  const email = single?.contacts.emails[0]?.address;

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
            {result.sent} sent · {result.skipped.length} skipped
          </p>
          <ul aria-label="Skipped" className="flex flex-col gap-2">
            {result.skipped.map((skip) => (
              <li key={skip.personId} className="flex items-baseline gap-2 text-note">
                <span className="min-w-0 flex-1 truncate text-u-text2">{names.get(skip.personId) ?? "Someone"}</span>
                <span className="flex-none font-mono text-meta text-u-text3">{skipReasonLabel(skip.reason)}</span>
              </li>
            ))}
          </ul>
        </div>
      </Modal>
    );
  }

  const ready = firm?.readyForNotices ?? false;
  const preview = firm
    ? noticePreviewParagraphs(firm.controllerName ?? "", single?.source, single?.privacy.lawfulBasis)
    : null;
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
          {ready ? (
            <Button loading={sending.isPending} onClick={() => sending.mutate()}>
              {single ? "Send notice" : `Send ${people.length === 1 ? "notice" : "notices"}`}
            </Button>
          ) : isAdmin && settings.isSuccess ? (
            <Button onClick={() => navigate("/settings/data")}>Open Data &amp; retention</Button>
          ) : null}
        </>
      }
    >
      <div className="flex flex-col gap-3 text-body text-u-text2">
        <FormError message={error ?? (settings.isError ? messageFor(settings.error) : null)} />
        {settings.isSuccess && !ready ? (
          <p>
            {isAdmin
              ? "Before anyone can send a privacy notice, fill in your firm's legal name, a privacy contact and your notice link in Settings → Data & retention."
              : "Before anyone can send a privacy notice, an admin needs to fill in your firm's privacy details in Settings → Data & retention."}
          </p>
        ) : (
          <>
            <p>
              {single
                ? `${single.fullName.split(" ")[0]} gets an email${email ? ` at ${email}` : ""} from Uncava in your firm's name, saying you hold their details, where they came from, and how to object or ask for a copy.`
                : "Each gets an email from Uncava in your firm's name saying you hold their details, where they came from, and how to object or ask for a copy. Anyone who can't be sent one is skipped, and you'll see who and why."}
            </p>
            {firm && (
              <div
                aria-label="Notice preview"
                className="rounded-u-card border border-u-border bg-u-raised px-3.5 py-3"
              >
                <div className="font-mono text-meta text-u-text3">
                  From: {firm.controllerName} via Uncava · Reply-to: {firm.privacyContactEmail}
                </div>
                <div className="mt-1 text-note font-semibold text-u-text">
                  How {firm.controllerName} holds your details
                </div>
                {preview?.map((paragraph) => (
                  <p key={paragraph} className="mt-2 text-note text-u-text2">
                    {paragraph}
                  </p>
                ))}
              </div>
            )}
            <div className="flex gap-2.5 rounded-u-chip border border-u-border bg-u-raised px-3 py-2.5 font-mono text-meta text-u-text2">
              <Icon d={ICONS.info} size={15} className="mt-px flex-none text-u-text3" />
              <span>
                The person is marked notified once it&apos;s sent, and it&apos;s recorded on their timeline. Anyone sent a
                notice in the last 14 days is skipped.
              </span>
            </div>
          </>
        )}
      </div>
    </Modal>
  );
}
