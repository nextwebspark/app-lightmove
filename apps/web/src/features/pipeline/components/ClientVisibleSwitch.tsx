import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { useOutletContext } from "react-router-dom";
import type { ProjectOutletContext } from "../../../components/layout/ProjectLayout";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Toggle, useToast } from "../../../components/ui";
import { messageFor } from "../../../lib/errorCodes";
import * as shareConsentApi from "../../candidates/api/shareConsentApi";
import { ShareConsentPromptDialog } from "../../candidates/components/consent/ShareConsentPromptDialog";
import { useShareConsents } from "../../candidates/lib/useShareConsents";
import { useWorkspaceVocabulary } from "../../workspace/lib/vocabulary";
import { useClientVisibility } from "../lib/usePipelineWrites";

const CLIENT_VISIBLE_HINT = "Client-visible profile only. Emails and private notes remain internal.";

/**
 * Whether the client sees this executive, switched in place. Showing someone who has not agreed asks first where the
 * workspace keeps consent; `onChanged` hears the saved value so the caller's own read holds the switch's new place.
 */
export function ClientVisibleSwitch({
  projectId,
  candidateId,
  fullName,
  clientVisible,
  onChanged,
}: {
  projectId: string;
  candidateId: string;
  fullName: string;
  clientVisible: boolean;
  onChanged: (clientVisible: boolean) => void;
}) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const clientVisibility = useClientVisibility(projectId);
  const share = useShareConsents(projectId);
  const outlet = useOutletContext<ProjectOutletContext | undefined>();
  const vocabulary = useWorkspaceVocabulary();
  const clientName = outlet?.project.clientName ?? `the ${vocabulary.unitLower}`;
  const [sharePrompt, setSharePrompt] = useState(false);
  // Ask first from here goes straight away, in the confidential wording; naming the client is the Pipeline card's.
  const askShare = useMutation({
    mutationFn: () => shareConsentApi.requestShareConsent(projectId, candidateId, false),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: shareConsentApi.SHARE_CONSENTS_KEY(projectId) });
      toast.success(`Request sent to ${fullName.split(" ")[0]}.`);
      setSharePrompt(false);
    },
    onError: (failure) => {
      toast.error(messageFor(failure));
      setSharePrompt(false);
    },
  });
  const showToClient = (checked: boolean) =>
    clientVisibility.mutate(
      { candidateId, clientVisible: checked },
      { onSuccess: (saved) => onChanged(saved.clientVisible) },
    );
  const shown = clientVisibility.isPending
    ? (clientVisibility.variables?.clientVisible ?? clientVisible)
    : clientVisible;

  return (
    <div>
      <div className="flex items-center gap-2" title={CLIENT_VISIBLE_HINT}>
        <Icon d={ICONS.eye} size={14} className="text-u-text3" />
        <span className="text-body text-u-text">Client visible</span>
        <Icon d={ICONS.info} size={13} className="text-u-text3" />
        <span className="ms-auto">
          <Toggle
            checked={shown}
            label="Client visible"
            disabled={clientVisibility.isPending}
            onChange={(checked) => {
              if (checked && share.needsPrompt(candidateId)) setSharePrompt(true);
              else showToClient(checked);
            }}
          />
        </span>
      </div>
      <p className="sr-only">{CLIENT_VISIBLE_HINT}</p>
      {sharePrompt && (
        <ShareConsentPromptDialog
          fullName={fullName}
          clientName={clientName}
          row={share.rowOf(candidateId)}
          busy={askShare.isPending}
          onClose={() => setSharePrompt(false)}
          onShowAnyway={() => {
            setSharePrompt(false);
            showToClient(true);
          }}
          onAskFirst={() => askShare.mutate()}
        />
      )}
    </div>
  );
}
