import { useMutation } from "@tanstack/react-query";
import { useState } from "react";
import { FormError, useToast } from "../../../../components/ui";
import { ConfirmDialog } from "../../../../components/ui/ConfirmDialog";
import { messageFor } from "../../../../lib/errorCodes";
import { saveBlob } from "../../../../lib/saveBlob";
import * as poolApi from "../../api/poolApi";

/**
 * Export personal data (`Candidates.dc.html`'s dialog, GDPR Art. 15 and 20): an admin downloads one zip of everything
 * held on the person to answer their request for it. Nothing changes but the audit log; the admin checks the file for
 * other people's details before sending it on.
 */
export function SubjectAccessExportDialog({
  personId,
  fullName,
  onClose,
}: {
  personId: string;
  fullName: string;
  onClose: () => void;
}) {
  const toast = useToast();
  const [error, setError] = useState<string | null>(null);
  const exporting = useMutation({
    mutationFn: async () => {
      const file = await poolApi.exportSubjectAccess(personId);
      saveBlob(file, subjectAccessFileName(fullName, new Date()));
    },
    onSuccess: () => {
      toast(`Exported everything held on ${fullName} — recorded in the audit log`);
      onClose();
    },
    onError: (failure) => setError(messageFor(failure)),
  });

  return (
    <ConfirmDialog
      open
      tone="primary"
      title={`Export everything held on ${fullName}`}
      confirmLabel="Download"
      pending={exporting.isPending}
      onConfirm={() => exporting.mutate()}
      onClose={onClose}
    >
      <FormError message={error} />
      <p>
        One zip file to answer their request for a copy of their data: a readable page, the same data as JSON, and the
        latest file of each document.
      </p>
      <span className="type-label mt-1 text-u-text3">What&apos;s in it</span>
      <ul aria-label="What's in it" className="list-disc space-y-0.5 ps-5">
        {CONTENTS.map((line) => (
          <li key={line}>{line}</li>
        ))}
      </ul>
      <p role="note" className="rounded-u-card border border-u-accent/35 bg-u-accent-tint px-3 py-2">
        <b className="font-semibold text-u-text">Check it before you send it.</b> Notes and call transcripts can
        mention other people. Remove their details where they are not needed.
      </p>
      <p className="text-meta text-u-text3">Recorded in the audit log. Only admins can export a person&apos;s data.</p>
    </ConfirmDialog>
  );
}

const CONTENTS = [
  "Their profile, contacts and where each one came from",
  "Every position they are on, with its status, scorecard and tasks",
  "Notes, including deleted ones not yet purged, and documents",
  "Emails, meetings, call recordings' transcripts and the timeline",
  "What the AI proposed about them, and how it works",
];

/**
 * `uncava-subject-access-noor-haddad-2026-11-11.zip`, the Candidates CSV's naming. Mirrors the name
 * `PersonSubjectAccessController` sends in `Content-Disposition` (`ZipArchiveWriter.slugOf`, the UTC date): change both.
 */
export function subjectAccessFileName(fullName: string, on: Date): string {
  const slug = fullName
    .normalize("NFKD")
    .replace(/\p{M}/gu, "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, 80);
  return `uncava-subject-access-${slug || "person"}-${on.toISOString().slice(0, 10)}.zip`;
}
