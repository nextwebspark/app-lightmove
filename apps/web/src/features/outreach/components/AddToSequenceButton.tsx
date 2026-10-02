import { useState } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { useMailbox } from "../lib/useMailbox";
import { EnrolDialog } from "./EnrolDialog";

/** The executive drawer's way into Add to sequence, for this one person. Offered only where outreach is set up. */
export function AddToSequenceButton({
  projectId,
  candidateId,
  fullName,
}: {
  projectId: string;
  candidateId: string;
  fullName: string;
}) {
  const mailbox = useMailbox();
  const [isEnrolling, setIsEnrolling] = useState(false);
  if (mailbox.data?.offered !== true) return null;

  return (
    <>
      <button
        type="button"
        onClick={() => setIsEnrolling(true)}
        className="inline-flex items-center gap-1.5 rounded-md border border-u-border px-2 py-1 font-mono text-[11.5px] font-semibold text-u-text2 transition hover:border-u-border-strong hover:text-u-text"
      >
        <Icon d={ICONS.mail} size={12} />
        Add to sequence
      </button>
      {isEnrolling && (
        <EnrolDialog
          projectId={projectId}
          scope={{ candidateIds: [candidateId] }}
          source={`From ${fullName}'s profile`}
          onClose={() => setIsEnrolling(false)}
        />
      )}
    </>
  );
}
