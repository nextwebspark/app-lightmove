import { Icon, ICONS } from "../../../../components/layout/Icon";
import { Button, useToast } from "../../../../components/ui";
import { closeCopilotWindow, openCopilotWindow, useCopilotWindowOpen } from "../../lib/copilotWindow";
import { CopilotPanel } from "./CopilotPanel";

/**
 * The executive drawer's Copilot tab: the call's copilot at the drawer's own width, or — while it is popped out over
 * the call — a word that it is there, never a second copy.
 */
export function CopilotTab({
  projectId,
  candidateId,
  recordingId,
  firstName,
  onShow,
}: {
  projectId: string;
  candidateId: string;
  recordingId: string;
  firstName: string;
  /** Brings the drawer to this tab, for the floating window's Show in Uncava. */
  onShow: () => void;
}) {
  const toast = useToast();
  const isPoppedOut = useCopilotWindowOpen(recordingId);

  const handlePopOut = () => {
    void openCopilotWindow({ projectId, candidateId, recordingId }, onShow).then((opened) => {
      if (opened === "blocked") toast.error("Your browser blocked the window. Allow pop-ups for Uncava and try again.");
    });
  };

  if (isPoppedOut) {
    return (
      <div className="grid h-full place-items-center px-6 text-center">
        <div className="max-w-sm">
          <span className="mx-auto mb-3 grid size-10 place-items-center rounded-full bg-u-raised text-u-text2">
            <Icon d={ICONS.externalLink} size={18} />
          </span>
          <p className="text-body font-semibold">Copilot is in its own window</p>
          <p className="mt-1 text-note text-u-text3">
            It stays on top of your call. Show it here to keep it beside {firstName}'s record.
          </p>
          <div className="mt-4 flex justify-center">
            <Button size="sm" onClick={() => closeCopilotWindow(recordingId)}>
              Show here
            </Button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <CopilotPanel
      projectId={projectId}
      candidateId={candidateId}
      recordingId={recordingId}
      variant="tab"
      onPopOut={handlePopOut}
    />
  );
}
