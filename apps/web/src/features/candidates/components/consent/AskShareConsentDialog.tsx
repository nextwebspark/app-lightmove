import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Icon, ICONS } from "../../../../components/layout/Icon";
import { Button, FormError, useToast } from "../../../../components/ui";
import { CheckBox } from "../../../../components/ui/FilterCheckRow";
import { Modal } from "../../../../components/ui/Modal";
import { cn } from "../../../../lib/cn";
import { formatInstantDate } from "../../../../lib/datetime";
import { messageFor } from "../../../../lib/errorCodes";
import * as shareConsentApi from "../../api/shareConsentApi";
import type { ShareConsentRow } from "../../api/shareConsentApi";
import { useWorkspaceVocabulary } from "../../../workspace/lib/vocabulary";

const DAY_MS = 86_400_000;
const RECENTLY_DAYS = 14;

/** Why this person cannot be asked again now, as the dialog says it; null when they can. */
function blockOf(firstName: string, row: ShareConsentRow | undefined): string | null {
  if (!row) return null;
  if (row.status === "given") return `${firstName} already agreed on ${formatInstantDate(row.answeredAt) ?? ""}.`;
  const open = row.requestOpenUntil && Date.parse(row.requestOpenUntil) > Date.now();
  if (open && row.requestedAt && Date.now() - Date.parse(row.requestedAt) < RECENTLY_DAYS * DAY_MS) {
    const again = new Date(Date.parse(row.requestedAt) + RECENTLY_DAYS * DAY_MS).toISOString();
    return `${firstName} was asked on ${formatInstantDate(row.requestedAt) ?? ""}. You can ask again from ${formatInstantDate(again) ?? ""}.`;
  }
  return null;
}

/**
 * Ask to share someone's profile with the position's client (`Position.dc.html`, `dialog=shareConsent`). The email
 * says "a confidential client" unless the client is named, which a confidential search never allows.
 */
export function AskShareConsentDialog({
  projectId,
  candidateId,
  fullName,
  roleTitle,
  clientName,
  clientNamable,
  row,
  onClose,
}: {
  projectId: string;
  candidateId: string;
  fullName: string;
  roleTitle: string;
  clientName: string;
  clientNamable: boolean;
  row: ShareConsentRow | undefined;
  onClose: () => void;
}) {
  const vocabulary = useWorkspaceVocabulary();
  const queryClient = useQueryClient();
  const toast = useToast();
  const [nameClient, setNameClient] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const firstName = fullName.split(" ")[0];
  const blocked = blockOf(firstName, row);
  const sending = useMutation({
    mutationFn: () => shareConsentApi.requestShareConsent(projectId, candidateId, clientNamable && nameClient),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: shareConsentApi.SHARE_CONSENTS_KEY(projectId) });
      toast.success(`Request sent to ${firstName}`);
      onClose();
    },
    onError: (failure) => setError(messageFor(failure)),
  });
  const named = clientNamable && nameClient;
  return (
    <Modal
      open
      onClose={onClose}
      dismissible={!sending.isPending}
      title={`Ask to share ${fullName}'s profile`}
      footer={
        <>
          <Button variant="secondary" disabled={sending.isPending} onClick={onClose}>
            Cancel
          </Button>
          <Button loading={sending.isPending} disabled={!!blocked} onClick={() => sending.mutate()}>
            Send request
          </Button>
        </>
      }
    >
      <div className="flex flex-col gap-3 text-body text-u-text2">
        <FormError message={error} />
        <p>
          {firstName} gets one email from Uncava in your firm&apos;s name, asking whether you may share their profile
          for the {roleTitle} role. They answer Yes or No on the page it links to.
        </p>
        <button
          type="button"
          role="checkbox"
          aria-checked={named}
          disabled={!clientNamable}
          onClick={() => setNameClient((current) => !current)}
          className={cn(
            "flex w-full items-start gap-2.5 rounded-u-chip border border-u-border bg-u-raised px-3 py-2.5 text-left",
            clientNamable ? "cursor-pointer" : "cursor-not-allowed opacity-55",
          )}
        >
          <CheckBox checked={named} />
          <span className="min-w-0 flex-1">
            <span className="block text-note font-medium text-u-text">Name the {vocabulary.unitLower}</span>
            <span className="mt-0.5 block font-mono text-meta text-u-text3">
              {!clientNamable
                ? `This position is confidential, so the email never names the ${vocabulary.unitLower}.`
                : named
                  ? `The email names ${clientName}.`
                  : 'The email says "a confidential client".'}
            </span>
          </span>
        </button>
        {blocked && (
          <p role="status" className="rounded-u-chip border border-u-border bg-u-raised px-3 py-2.5 text-note">
            {blocked}
          </p>
        )}
        <div className="flex gap-2.5 rounded-u-chip border border-u-border bg-u-raised px-3 py-2.5 font-mono text-meta text-u-text2">
          <Icon d={ICONS.info} size={15} className="mt-px flex-none text-u-text3" />
          <span>
            No answer changes nothing, and the request closes after 30 days. Whether the {vocabulary.unitLower} sees them stays your
            switch.
          </span>
        </div>
      </div>
    </Modal>
  );
}
